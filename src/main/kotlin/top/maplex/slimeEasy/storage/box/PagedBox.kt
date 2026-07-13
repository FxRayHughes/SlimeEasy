package top.maplex.slimeEasy.storage.box

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import top.maplex.slimeEasy.storage.core.ContainerIO
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.VirtualStorage
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.VoidFilter
import top.maplex.slimeEasy.storage.upgrade.CompressionProcessor

/**
 * 翻页存储箱 (木桶外观, 以 Slimefun ID 与抽屉区分)。
 *
 * 存储**多种物品**, 每种一格 (页数由翻页扩容升级决定, 每页 [PAGE_TYPES] 种), 各格容量统一以原版 64 堆
 * 为基准并随堆叠升级放大 (真实数量 long)。右键打开分页 GUI 存取, 详见
 * [PagedBoxMenu]; 支持磁铁 / 虚空 / 堆叠升级 (经验存储为抽屉专属, 此处忽略)。
 *
 * 货运接入与库存缓存由基类 [CargoBufferBlock] 提供。
 */
class PagedBox(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : CargoBufferBlock(itemGroup, item, recipeType, recipe) {

    override val storageDataKey: String = "se_box_items"

    override fun freshStorage(block: Block): VirtualStorage {
        val budget = slotBudget(block)
        return VirtualStorage(maxTypes = budget, maxSlots = budget, stackMultiplier = multiplier(block))
    }

    /** 当前页数 (基础 1 页, 每个翻页扩容 +1, 上限由配置决定)。 */
    fun pages(block: Block): Int = UpgradeStore.resolve(block.location).boxPages

    /** 总槽位预算 = 页数 × 每页格数; 存储容量与展示均以此为上限。 */
    fun slotBudget(block: Block): Int = pages(block) * PAGE_TYPES

    /** 当前堆叠升级倍率 (单槽 = 原版堆叠 × 此倍率)。 */
    private fun multiplier(block: Block): Double = UpgradeStore.resolve(block.location).capacityMultiplier

    /** 把存储的槽位预算与倍率同步到当前升级状态。 */
    private fun syncStorage(block: Block, storage: VirtualStorage) {
        val budget = slotBudget(block)
        storage.maxTypes = budget
        storage.maxSlots = budget
        storage.stackMultiplier = multiplier(block)
    }

    override fun filterInsert(block: Block, item: ItemStack, amount: Long): Long {
        val storage = storageAt(block)
        syncStorage(block, storage)
        if (!UpgradeStore.resolve(block.location).hasVoid) return amount
        // 虚空过滤: 封顶到保留数量, 超出部分湮灭 (未标记则原样放入)
        val key = ItemKey.of(item) ?: return amount
        return VoidFilter.admit(block.location, item, storage.count(key), amount)
    }

    // 经验模式: 拒绝物品货运进出 (容器改存经验)
    override fun acceptsCargoItems(block: Block): Boolean =
        !UpgradeStore.resolve(block.location).hasExpStorage

    override fun providesCargoItems(block: Block): Boolean =
        !UpgradeStore.resolve(block.location).hasExpStorage

    override fun onUpgradesChanged(block: Block) {
        val storage = storageAt(block)
        syncStorage(block, storage) // 同步槽位预算 (翻页扩容) 与倍率 (堆叠升级)
        saveStorage(block, storage)
        top.maplex.slimeEasy.storage.network.RemoteBind.sync(block) // 同步远程升级的挂靠绑定
        // 页数可能缩减: 通知已打开的界面即时重绘 (页码会被夹取到新范围)
        PagedBoxMenu.refreshAll(block)
    }

    /** 压制升级需要与上次持久化快照比较，因此仅翻页箱主动读取该 BlockData。 */
    override fun previousStorageData(block: Block): String? =
        StorageCacheUtils.getData(block.location, storageDataKey)

    override fun beforeStorageSave(block: Block, storage: VirtualStorage, previousData: String?) {
        val gridSize = UpgradeStore.resolve(block.location).compressionGridSize
        if (gridSize > 0) CompressionProcessor.compressChanged(block, storage, previousData, gridSize)
    }

    override fun rejectUpgradeChange(
        block: Block,
        type: top.maplex.slimeEasy.storage.upgrade.UpgradeType,
        install: Boolean
    ): String? {
        if (install && type.isCompression && UpgradeStore.resolve(block.location).hasCompression)
            return I18n.text("messages.paged-box.compression-upgrade-conflict")
        val expType = top.maplex.slimeEasy.storage.upgrade.UpgradeType.EXP_STORAGE
        if (type == expType) {
            // 装经验升级前要求库存为空 (物品与经验两套库存不可共存)
            if (install && !storageAt(block).isEmpty())
                return I18n.text("messages.paged-box.empty-items-before-experience")
            // 存有经验时禁止卸下经验升级 (否则经验数据被孤立)
            if (!install && top.maplex.slimeEasy.storage.drawer.DrawerExp.get(block) > 0)
                return I18n.text("messages.paged-box.empty-experience-before-remove")
        }
        // 卸下翻页扩容若使槽位不足以容纳现有内容, 则拒绝 (避免数据被截断丢失)
        if (!install && type == top.maplex.slimeEasy.storage.upgrade.UpgradeType.PAGE_EXPANSION) {
            val current = UpgradeStore.resolve(block.location)
            val newPages = (1 + (current.pageExpansionCount - 1)).coerceIn(1, top.maplex.slimeEasy.storage.upgrade.UpgradeSet.MAX_PAGES)
            val newBudget = newPages * PAGE_TYPES
            if (storageAt(block).usedSlots() > newBudget) return I18n.text("messages.paged-box.page-upgrade-required")
        }
        return null
    }

    override fun capacityAllowsRemoval(block: Block, remainingStackMultiplier: Double): Boolean {
        // 卸下堆叠升级后倍率变小 → 单槽容量变小 → 所需槽位增多, 超出预算则拒绝
        return storageAt(block).slotsUnder(remainingStackMultiplier) <= slotBudget(block)
    }

    override fun prepareForInsert(block: Block, item: ItemStack) {
        syncStorage(block, storageAt(block))
    }

    override fun onCustomTick(block: Block) {
        val upgrades = UpgradeStore.resolve(block.location)
        // 抽取升级: 从相邻六向的漏斗 / 箱子等容器主动提取物品入库 (经验模式下容器改存经验, 不抽物品)
        if (upgrades.hasExtract && !upgrades.hasExpStorage) ContainerIO.pull(this, block)
        // 输出升级: 把库存物品主动推送到相邻六向的容器
        if (upgrades.hasOutput && !upgrades.hasExpStorage) ContainerIO.push(this, block)
        if (!upgrades.hasMagnet) return
        if (upgrades.hasExpStorage) {
            // 经验磁铁: 登记 + tick 主动吸取附近经验球
            top.maplex.slimeEasy.storage.drawer.MagnetRegistry.mark(block)
            val center = block.location.toCenterLocation()
            for (orb in block.world.getNearbyEntitiesByType(
                org.bukkit.entity.ExperienceOrb::class.java,
                center,
                SEConfig.storageBoxMagnetRadius
            )) {
                top.maplex.slimeEasy.storage.drawer.DrawerExp.addAbsorbed(block, orb.experience.toLong())
                orb.remove()
            }
        } else {
            BoxMagnet.absorb(this, block)
        }
    }

    override fun preRegister() {
        super.preRegister()
        addItemHandler(BoxHandlers.place())
        addItemHandler(BoxHandlers.break_(this))
        addItemHandler(BoxHandlers.use(this))
    }

    /** 供 handler 调用: 清理缓存。 */
    fun clearCache(block: Block) = evictCache(block)

    companion object {
        /** 每页展示格数 = 单页物品种类上限。实时读取配置。 */
        val PAGE_TYPES: Int get() = SEConfig.storageBoxPageTypes
    }
}
