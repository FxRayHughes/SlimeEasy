package top.maplex.slimeEasy.machine.clicker

import top.maplex.slimeEasy.config.I18n
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
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.machine.butcher.ButcherLogic
import top.maplex.slimeEasy.machine.common.MachineProtection
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import top.maplex.slimeEasy.storage.core.ContainerIO
import top.maplex.slimeEasy.storage.core.UpgradeHost
import top.maplex.slimeEasy.storage.upgrade.FaceConfig
import top.maplex.slimeEasy.storage.upgrade.ItemFilter
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.UpgradeType
import top.maplex.slimeEasy.util.locationKey
import java.util.concurrent.ConcurrentHashMap

/**
 * 自动点击器。
 *
 * 本体为观察者 (OBSERVER), 脸朝向 = 点击方向。**需红石激活**: 仅当被红石 (直接或间接) 充能时,
 * 才随 Slimefun 原生 ticker 周期性对正前方方块模拟玩家点击 (右键 + 左键, 见 [AutoClickerLogic])。
 *
 * 内置**一格容积** ([AutoClickerMenuPreset.ITEM_SLOT], 右键打开界面放入): 点击时假玩家手持该物品交互
 * (如骨粉施肥 / 桶取放液体), 消耗后回写。物品可用**相邻漏斗** (输出对准本机) 或物流网络自动补充。
 * 支持点击 Slimefun (粘液) 方块并**绕过其研究解锁限制**。断电即停。
 */
class AutoClicker(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe), UpgradeHost {

    /** 每台机器的 tick 累加器 (键为方块位置字符串), 仅用于节流, 无需持久化。 */
    private val counters = ConcurrentHashMap<String, Double>()

    /** 单个 tick 内最多连点次数 (安全上限, 防止极端间隔造成瞬时过量点击); 实时读取配置。 */
    private val maxClicksPerTick: Int get() = SEConfig.autoClickerMaxClicksPerTick

    /** 自动点击器仅支持抽取升级 (扩展补料能力); 其余存储类升级对点击器无意义, 拒绝安装。 */
    override fun rejectUpgradeChange(block: Block, type: UpgradeType, install: Boolean): String? =
        if (install && type != UpgradeType.EXTRACT) I18n.text("messages.auto-clicker.unsupported-upgrade") else null

    override fun preRegister() {
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) {
                MachineProtection.recordOwner(e.block, e.player)
            }
        })
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                spillMenu(e.block, drops)
                drops.addAll(UpgradeStore.readItems(e.block.location)) // 返还已装升级组件
                counters.remove(e.block.locationKey())
            }
        })
        AutoClickerMenuPreset(id, itemName, this)
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) = onTick(b)
            // 涉及事件派发、方块 handler 调用与容器读写, 必须主线程
            override fun isSynchronized(): Boolean = true
        })
    }

    /** 单台机器一次 tick: 漏斗补料 → 红石充能 + 左右键开关 + 间隔节流后, 手持物品点击正前方。 */
    private fun onTick(machine: Block) {
        val menu = StorageCacheUtils.getMenu(machine.location) ?: return
        pullFromHoppers(machine, menu) // 无论是否通电都允许补料
        if (!machine.isBlockPowered && !machine.isBlockIndirectlyPowered) return // 未激活

        val doLeft = AutoClickerState.leftEnabled(machine)
        val doRight = AutoClickerState.rightEnabled(machine)
        if (!doLeft && !doRight) return // 默认均关闭: 不点击

        val facing = ButcherLogic.facingOf(machine) ?: return
        val key = machine.locationKey()
        val interval = AutoClickerState.interval(machine)

        // 累加器: 每 tick +1; 每满一个间隔触发一次点击。间隔 <1 时单个 tick 内连点多次 (高频)
        var acc = (counters[key] ?: 0.0) + 1.0
        var clicks = 0
        while (acc >= interval && clicks < maxClicksPerTick) {
            acc -= interval
            val hand = menu.getItemInSlot(AutoClickerMenuPreset.ITEM_SLOT)?.clone()
            val after = AutoClickerLogic.click(machine, facing, hand, doLeft, doRight)
            menu.replaceExistingItem(AutoClickerMenuPreset.ITEM_SLOT, after) // 回写物品槽 (反映消耗)
            clicks++
        }
        counters[key] = acc
    }

    /**
     * 补料到物品槽 (观察者非容器, 无法被推入, 故主动抽取)。
     *
     * - **无抽取升级** (默认): 仅从"输出方向正对本机"的相邻漏斗抽取 (维持原行为);
     * - **装抽取升级**: 从相邻任意容器 (漏斗 + 箱子等, 排除本插件存储块) 抽取, 并经抽取
     *   黑 / 白名单 ([ItemFilter.EXTRACT]) 过滤, 不再要求漏斗朝向。
     */
    private fun pullFromHoppers(machine: Block, menu: BlockMenu) {
        val loc = machine.location
        if (UpgradeStore.resolve(loc).hasExtract) {
            // 抽取升级: 按配置吞吐补充物品槽, 仅在配置的生效面
            val faces = FaceConfig.EXTRACT.faces(loc)
            var budget = SEConfig.autoClickerExtractMaxItemsPerTick
            for (inv in ContainerIO.adjacentSources(machine, faces)) {
                budget -= fillFromInventory(menu, inv, loc, budget)
                if (budget <= 0) return
            }
            for ((nb, logic) in ContainerIO.adjacentPluginStores(machine, faces)) {
                budget -= fillFromStore(menu, nb, logic, loc, budget)
                if (budget <= 0) return
            }
            return
        }
        for (face in NEIGHBOR_FACES) {
            val neighbor = machine.getRelative(face)
            if (neighbor.type != Material.HOPPER) continue
            val dir = (neighbor.blockData as? Directional)?.facing ?: continue
            if (dir != face.oppositeFace) continue // 漏斗输出方向须正对本机
            val hopper = neighbor.state as? org.bukkit.block.Hopper ?: continue
            if (drainOne(menu, hopper.inventory, null)) return // 每 tick 至多抽一个, 贴近原版节流
        }
    }

    /**
     * 从容器取一个物品塞入物品槽 (槽满或异类则失败); 成功返回 true。
     * [filterLoc] 非 null 时经抽取黑 / 白名单过滤 (仅抽取升级路径传入)。
     */
    private fun drainOne(menu: BlockMenu, inv: Inventory, filterLoc: Location?): Boolean {
        for (i in 0 until inv.size) {
            val stack = inv.getItem(i) ?: continue
            if (stack.type.isAir) continue
            if (filterLoc != null && !ItemFilter.EXTRACT.allows(filterLoc, stack)) continue
            val one = stack.clone().apply { amount = 1 }
            if (menu.pushItem(one, AutoClickerMenuPreset.ITEM_SLOT) != null) continue // 槽满 / 异类
            stack.amount -= 1
            inv.setItem(i, stack.takeIf { it.amount > 0 })
            return true
        }
        return false
    }

    /**
     * 从原版容器补充物品槽, 最多移动 [maxItems] 个; 返回实际移动量。
     */
    private fun fillFromInventory(menu: BlockMenu, inv: Inventory, loc: Location, maxItems: Int): Int {
        var movedTotal = 0
        for (i in 0 until inv.size) {
            if (movedTotal >= maxItems) break
            val stack = inv.getItem(i) ?: continue
            if (stack.type.isAir) continue
            if (!ItemFilter.EXTRACT.allows(loc, stack)) continue
            val requested = minOf(stack.amount, maxItems - movedTotal)
            val leftAmount = menu.pushItem(
                stack.clone().apply { amount = requested }, AutoClickerMenuPreset.ITEM_SLOT
            )?.amount ?: 0
            val moved = requested - leftAmount
            if (moved > 0) {
                stack.amount -= moved
                movedTotal += moved
                inv.setItem(i, stack.takeIf { it.amount > 0 })
                if (leftAmount > 0) break // 同类但物品槽已满
            }
            // moved == 0 且有剩余: 与槽内物品异类, 继续找同类
        }
        return movedTotal
    }

    /**
     * 从相邻本插件容器的虚拟库存补充物品槽, 最多移动 [maxItems] 个; 返回实际移动量。
     */
    private fun fillFromStore(
        menu: BlockMenu,
        nb: Block,
        logic: CargoBufferBlock,
        loc: Location,
        maxItems: Int
    ): Int {
        val store = logic.storageAt(nb)
        var storeChanged = false
        var movedTotal = 0
        for ((key, amount) in store.entries()) {
            if (movedTotal >= maxItems) break
            if (amount <= 0) continue
            if (!ItemFilter.EXTRACT.allows(loc, key.template)) continue
            val push = minOf(amount, key.vanillaMaxStack.toLong(), (maxItems - movedTotal).toLong()).toInt()
            if (push <= 0) continue
            val leftAmount = menu.pushItem(
                key.template.clone().apply { this.amount = push }, AutoClickerMenuPreset.ITEM_SLOT
            )?.amount ?: 0
            val moved = push - leftAmount
            if (moved > 0) {
                store.extract(key, moved.toLong(), simulate = false)
                movedTotal += moved
                storeChanged = true
            }
            if (moved > 0 && leftAmount > 0) break // 同类但物品槽已满
        }
        if (storeChanged) logic.saveStorage(nb, store)
        return movedTotal
    }

    /** 破坏时把物品槽内容作为掉落散落。 */
    private fun spillMenu(machine: Block, drops: MutableList<ItemStack>) {
        val menu = StorageCacheUtils.getMenu(machine.location) ?: return
        menu.getItemInSlot(AutoClickerMenuPreset.ITEM_SLOT)?.let { if (!it.type.isAir) drops.add(it.clone()) }
    }

    private companion object {
        /** 抽取漏斗时扫描的六个相邻方向。 */
        val NEIGHBOR_FACES = arrayOf(
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        )
    }
}
