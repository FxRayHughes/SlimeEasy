package top.maplex.slimeEasy.machine.butcher

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.machine.common.MachineProtection
import top.maplex.slimeEasy.util.locationKey
import java.util.concurrent.ConcurrentHashMap

/**
 * 屠夫机器。
 *
 * 本体为观察者 (OBSERVER)。放置后随 Slimefun 原生 ticker 运行, 周期性对脸朝向
 * (facing) **正前方的扁平 N×N 截面 + 纵深**区域内的非玩家生物发动范围横扫 (N 与纵深
 * 随范围升级组件增大, 基础 3×3 截面 / 纵深 1 格):
 * 1. 取首把有效武器 (耐久耗尽自动切下一把), 合并附魔书附魔;
 * 2. 攻击次数余量耗尽时惰性消耗食物折算补充 (无食物则停机);
 * 3. 以假玩家 (手持武器) 为伤害来源击杀, 使原版正常掉经验 / 稀有掉落 / 抢夺缩放;
 * 4. 两侧 [ButcherDisplay] 展示武器并做挥砍动画。
 *
 * 界面由 Slimefun 原生 [ButcherMenuPreset] 提供 (右键自动打开): 武器/书/食物/升级为
 * 自由放置且**自动持久化**的槽位, tick 直接读写该 BlockMenu —— 无独立物品序列化、
 * 无快照回存, 因而无并发覆盖问题。食物输入支持货运 (预设透明接入) 与原版漏斗
 * ([pullFromHoppers] 主动抽取)。仅攻击次数余量 (数字) 存于 BlockData ([ButcherStorage])。
 */
class Butcher(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /** 每台机器的 tick 计数器 (键为方块位置字符串), 仅用于节流, 无需持久化。 */
    private val counters = ConcurrentHashMap<String, Int>()

    /** 攻击间隔 (Slimefun tick 数; 原生每 tick 约 0.5 秒, 故约每秒一次)。 */
    private val interval = 2

    override fun preRegister() {
        // 放置: 记录 owner (供领地校验), 生成两侧展示实体
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) {
                MachineProtection.recordOwner(e.block, e.player)
                ButcherLogic.facingOf(e.block)?.let { ButcherDisplay.spawn(e.block, it) }
            }
        })
        // 破坏: 菜单内物品散落, 清理展示实体
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                spillMenu(e.block, drops)
                ButcherDisplay.remove(e.block)
                counters.remove(e.block.locationKey())
            }
        })
        // 注册原生可开启菜单 (构造即自注册; 右键由 Slimefun 自动打开)
        ButcherMenuPreset(id, itemName)
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) = onTick(b)
            // 涉及实体伤害与菜单写入, 必须主线程
            override fun isSynchronized(): Boolean = true
        })
    }

    /** 单台机器的一次 tick。 */
    private fun onTick(machine: Block) {
        // 展示实体版本迁移 (旧机器按新参数重建一次)
        val menu = StorageCacheUtils.getMenu(machine.location)
        if (ButcherDisplay.ensureCurrent(machine)) {
            ButcherDisplay.update(machine, menu?.let { ButcherMenuPreset.firstWeapon(it)?.second })
        }
        if (menu == null) return
        pullFromHoppers(machine, menu) // 原版漏斗喂食
        val key = machine.locationKey()
        val count = counters.getOrDefault(key, 0) + 1
        if (count < interval) { counters[key] = count; return }
        counters[key] = 0
        attack(machine, menu)
    }

    /**
     * 从"输出方向对准本机"的相邻漏斗抽取物品 (原版漏斗喂料)。
     *
     * 观察者非容器, 原版漏斗无法**推入**; 故反向由本机每 tick **主动抽取**: 扫六个
     * 相邻方块, 对每个 facing 指向本机的漏斗, 取其首个**可归类**物品 (武器/书/食物/
     * 升级) 推入对应槽。多个面的漏斗只要对准本机均可喂料。纯 Bukkit 实现, 无需 NMS。
     */
    private fun pullFromHoppers(machine: Block, menu: BlockMenu) {
        for (face in NEIGHBOR_FACES) {
            val neighbor = machine.getRelative(face)
            if (neighbor.type != Material.HOPPER) continue
            val dir = (neighbor.blockData as? org.bukkit.block.data.Directional)?.facing ?: continue
            if (dir != face.oppositeFace) continue // 漏斗输出方向须正对本机
            val hopper = neighbor.state as? org.bukkit.block.Hopper ?: continue
            if (drainOne(menu, hopper)) return // 每 tick 至多抽一次, 贴近原版节流
        }
    }

    /**
     * 从漏斗取一个可归类物品推入其对应槽; 成功返回 true。
     *
     * 按 [ButcherMenuPreset.routeSlots] 判定物品去向 (武器/书/食物/升级); 目标槽满或
     * 物品无法归类则试下一个槽内物品, 全部失败返回 false。
     */
    private fun drainOne(menu: BlockMenu, hopper: org.bukkit.block.Hopper): Boolean {
        val inv = hopper.inventory
        for (i in 0 until inv.size) {
            val stack = inv.getItem(i) ?: continue
            if (stack.type.isAir) continue
            val routes = ButcherMenuPreset.routeSlots(menu, stack)
            if (routes.isEmpty()) continue // 无法归类 (或升级已满): 试下一格
            val one = stack.clone().apply { amount = 1 }
            // pushItem 返回未放入的剩余; 非 null 表示目标槽已满, 试下一格
            if (menu.pushItem(one, *routes) != null) continue
            stack.amount -= 1
            inv.setItem(i, stack.takeIf { it.amount > 0 })
            return true
        }
        return false
    }

    /** 一次攻击编排: 取武器→查余量/吃食物→收目标→领地校验→横扫→扣余量/耐久。 */
    private fun attack(machine: Block, menu: BlockMenu) {
        val face = ButcherLogic.facingOf(machine) ?: return
        val (slot, weapon) = ButcherMenuPreset.firstWeapon(menu) ?: run {
            ButcherDisplay.update(machine, null)
            ButcherMenuPreset.updateInfo(menu, ButcherStorage.getFuel(machine))
            return // 无有效武器: 停机 (firstWeapon 已跳过非武器杂物)
        }
        ButcherDisplay.update(machine, weapon)

        // 攻击次数余量: 不足则惰性吞食物折算填至上限
        var fuel = ButcherStorage.getFuel(machine)
        if (fuel <= 0) {
            fuel = refuelFromFood(machine, menu)
            if (fuel <= 0) return // 无食物: 停机
        }

        val rangeLevel = ButcherMenuPreset.rangeLevel(menu)
        val targets = ButcherLogic.collectTargets(machine, face, rangeLevel)
        if (targets.isEmpty()) return // 无目标: 不空耗余量与耐久

        // 领地校验: 以机器所有者身份, 校验对面前方块位置的攻击权
        if (!MachineProtection.canAttack(machine, machine.getRelative(face))) return

        val book = ButcherMenuPreset.bookAt(menu)?.takeIf { ButcherLogic.isBook(it) }
        val effective = ButcherLogic.effectiveWeapon(weapon, book)
        val dmg = ButcherLogic.computeDamage(weapon, book, ButcherMenuPreset.damageLevel(menu))
        val fire = ButcherLogic.fireAspectLevel(weapon, book)
        val owner = MachineProtection.ownerOf(machine) ?: ""

        ButcherLogic.performSweep(machine, targets, effective, dmg, fire, owner)
        ButcherDisplay.sweep(machine)

        fuel -= 1
        ButcherStorage.setFuel(machine, fuel)
        consumeDurability(menu, slot, weapon)
        ButcherMenuPreset.updateInfo(menu, fuel)
    }

    /**
     * 从食物槽连续预吞食物, 把内部余量缓存填至上限 [ButcherLogic.MAX_FUEL]; 返回新余量。
     *
     * 逐个吞入: 每个折算 nutrition×[ButcherLogic.ATTACKS_PER_NUTRITION] 攻击次数, **仅当
     * 整份都装得下**才吞 (不半吞、不溢出浪费), 直到缓存填满 / 食物吃光 / 下一份会超出。
     * 故缓存上限恒为 100 饱食度, 多余食物留槽 (可继续被漏斗/货运补充), 无浪费。
     */
    private fun refuelFromFood(machine: Block, menu: BlockMenu): Long {
        val food = ButcherMenuPreset.foodAt(menu) ?: return 0
        val nutrition = ButcherLogic.nutritionOf(food.type)
        if (nutrition <= 0) return 0
        val gainPer = nutrition.toLong() * ButcherLogic.ATTACKS_PER_NUTRITION
        var fuel = ButcherStorage.getFuel(machine)
        var remain = food.amount
        while (remain > 0 && fuel + gainPer <= ButcherLogic.MAX_FUEL) {
            fuel += gainPer
            remain -= 1
        }
        if (remain == food.amount) return fuel // 一份都没吞下 (缓存已接近满): 维持现状
        menu.replaceExistingItem(ButcherMenuPreset.FOOD_SLOT, food.apply { amount = remain }.takeIf { it.amount > 0 })
        ButcherStorage.setFuel(machine, fuel)
        return fuel
    }

    /** 消耗首把武器 1 点耐久 (遵循耐久附魔), 耗尽则清空该槽 (下次自动切下一把)。 */
    private fun consumeDurability(menu: BlockMenu, slot: Int, weapon: ItemStack) {
        val unbreaking = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING)
        if (unbreaking > 0 && java.util.concurrent.ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) return
        val meta = weapon.itemMeta as? org.bukkit.inventory.meta.Damageable ?: return
        val max = weapon.type.maxDurability.toInt()
        if (max <= 0) return
        val newDamage = meta.damage + 1
        if (newDamage >= max) {
            menu.replaceExistingItem(slot, null) // 损毁: 清空, 自动切下一把
        } else {
            meta.damage = newDamage
            weapon.itemMeta = meta
            menu.replaceExistingItem(slot, weapon)
        }
    }

    /** 破坏时把菜单内全部功能槽物品作为真实掉落散落。 */
    private fun spillMenu(machine: Block, drops: MutableList<ItemStack>) {
        val menu = StorageCacheUtils.getMenu(machine.location) ?: return
        for (slot in ButcherMenuPreset.FUNCTIONAL_SLOTS) {
            menu.getItemInSlot(slot)?.let { if (!it.type.isAir) drops.add(it.clone()) }
        }
    }

    private companion object {
        /** 抽取漏斗时扫描的六个相邻方向。 */
        val NEIGHBOR_FACES = arrayOf(
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        )
    }
}
