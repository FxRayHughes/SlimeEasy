package top.maplex.slimeEasy.storage.drawer

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import top.maplex.slimeEasy.storage.core.HopperExtract
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.VirtualStorage
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 抽屉 (木桶外观)。
 *
 * 存储**单一物品**: 首次放入即锁定物品身份, 之后仅接受同类, 容量随堆叠升级
 * 提升 (真实数量为 long, 远超原版堆叠)。面向玩家的一面生成 [DrawerDisplay]
 * 展示图标与存量; 交互 (放入 / 取出 / 开升级 GUI) 由 [DrawerListener] 驱动。
 *
 * 能力升级:
 * - 经验存储: 改存经验 (拒绝物品货运), 详见 [DrawerExp];
 * - 磁铁: 每 tick 吸附附近掉落物 / 经验球;
 * - 虚空: 命中销毁表的物品入库前湮灭。
 *
 * 货运接入与库存缓存由基类 [CargoBufferBlock] 提供。
 */
class Drawer(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : CargoBufferBlock(itemGroup, item, recipeType, recipe) {

    override val storageDataKey: String = "se_drawer_items"

    override fun freshStorage(block: Block): VirtualStorage {
        // 单一物品; 抽屉内部有 [DRAWER_SLOTS] 个槽位, 单槽 = 原版堆叠 × 堆叠升级倍率
        val mult = UpgradeStore.resolve(block.location).capacityMultiplier
        return VirtualStorage(maxTypes = 1, maxSlots = DRAWER_SLOTS, stackMultiplier = mult)
    }

    /** 按当前堆叠升级重算单槽倍率 ([sample] 参数保留以兼容旧调用, 不再使用)。 */
    fun refreshCapacity(block: Block, storage: VirtualStorage, sample: ItemStack?) {
        storage.stackMultiplier = UpgradeStore.resolve(block.location).capacityMultiplier
    }

    override fun acceptsCargoItems(block: Block): Boolean =
        !UpgradeStore.resolve(block.location).hasExpStorage

    override fun providesCargoItems(block: Block): Boolean =
        !UpgradeStore.resolve(block.location).hasExpStorage

    override fun rejectUpgradeChange(
        block: Block,
        type: top.maplex.slimeEasy.storage.upgrade.UpgradeType,
        install: Boolean
    ): String? {
        // 翻页扩容仅对箱子有意义, 抽屉无分页概念, 直接拒绝安装
        if (install && type == top.maplex.slimeEasy.storage.upgrade.UpgradeType.PAGE_EXPANSION)
            return "§c翻页扩容仅适用于翻页存储箱"
        if (type != top.maplex.slimeEasy.storage.upgrade.UpgradeType.EXP_STORAGE) return null
        // 装经验升级前要求物品库存为空 (两套库存不可共存, 避免残留物品与经验显示互相覆盖)
        if (install && !storageAt(block).isEmpty())
            return "§c请先取空抽屉内的物品, 再安装经验升级"
        // 存有经验时禁止卸下经验升级 (否则经验数据被孤立)
        if (!install && DrawerExp.get(block) > 0)
            return "§c请先取出全部经验, 再卸下经验升级"
        return null
    }

    override fun filterInsert(block: Block, item: ItemStack, amount: Long): Long {
        val storage = storageAt(block)
        // 借货运插入前的时机, 顺带按来料重算容量
        refreshCapacity(block, storage, item)
        if (!UpgradeStore.resolve(block.location).hasVoid) return amount
        // 虚空过滤: 封顶到保留数量, 超出部分湮灭 (未标记则原样放入)
        val key = ItemKey.of(item) ?: return amount
        return VoidFilter.admit(block.location, item, storage.count(key), amount)
    }

    override fun onStorageChanged(block: Block, storage: VirtualStorage) {
        val first = storage.entries().firstOrNull()
        DrawerDisplay.update(block, first?.first, first?.second ?: 0L)
    }

    override fun onCustomTick(block: Block) {
        // 存量抽屉展示实体版本迁移: 旧参数 (固定框/文字被遮) 自动按新参数重建一次
        if (DrawerDisplay.ensureCurrent(block)) refreshDisplay(block)

        val upgrades = UpgradeStore.resolve(block.location)
        // 抽取升级: 从相邻六向漏斗主动提取物品入库 (经验模式下容器改存经验, 不抽物品)
        if (upgrades.hasExtract && !upgrades.hasExpStorage) HopperExtract.pull(this, block)
        if (!upgrades.hasMagnet) return
        if (upgrades.hasExpStorage) {
            // 经验磁铁: ① 登记, 由 MagnetOrbListener 在球生成瞬间拦截 (抢在原版吸向玩家前);
            //           ② tick 主动扫描已存在的经验球兜底 (不依赖生成事件时机 / TTL)
            MagnetRegistry.mark(block)
            absorbNearbyOrbs(block)
        } else {
            // 物品磁铁: 每 tick 吸附范围内同类掉落物
            DrawerMagnet.absorb(this, block)
        }
    }

    /** 按当前模式刷新展示 (重建展示实体后调用, 立即填充内容)。 */
    private fun refreshDisplay(block: Block) {
        if (UpgradeStore.resolve(block.location).hasExpStorage) {
            DrawerExp.refreshDisplay(block)
        } else {
            val first = storageAt(block).entries().firstOrNull()
            DrawerDisplay.update(block, first?.first, first?.second ?: 0L)
        }
    }

    /** 吸取范围内已存在的经验球 (兜底; 生成拦截为主)。 */
    private fun absorbNearbyOrbs(block: Block) {
        val center = block.location.toCenterLocation()
        for (orb in block.world.getNearbyEntitiesByType(org.bukkit.entity.ExperienceOrb::class.java, center, MAGNET_RADIUS)) {
            DrawerExp.addAbsorbed(block, orb.experience.toLong())
            orb.remove()
        }
    }

    /** 供破坏 handler 调用: 清理本方块的库存缓存, 防止内存泄漏。 */
    fun clearCache(block: Block) = evictCache(block)

    override fun onUpgradesChanged(block: Block) {
        val storage = storageAt(block)
        refreshCapacity(block, storage, null)
        saveStorage(block, storage)
        top.maplex.slimeEasy.storage.network.RemoteBind.sync(block) // 同步远程升级的挂靠绑定
    }

    override fun capacityAllowsRemoval(block: Block, remainingStackMultiplier: Double): Boolean {
        // 卸下堆叠升级后倍率变小 → 单槽容量变小 → 所需槽位可能超出抽屉槽数, 则拒绝
        return storageAt(block).slotsUnder(remainingStackMultiplier) <= DRAWER_SLOTS
    }

    override fun prepareForInsert(block: Block, item: ItemStack) {
        refreshCapacity(block, storageAt(block), item)
    }

    override fun preRegister() {
        super.preRegister()
        addItemHandler(DrawerHandlers.place(this))
        addItemHandler(DrawerHandlers.break_(this))
        addItemHandler(DrawerHandlers.use(this)) // 右键木桶本体开操作界面
    }

    companion object {
        /** 磁铁吸附半径 (格)。 */
        const val MAGNET_RADIUS = 6.0

        /** 抽屉内部槽位数 (单一物品的大容量; 单槽 = 原版堆叠 × 堆叠升级倍率)。 */
        const val DRAWER_SLOTS = 32
    }
}
