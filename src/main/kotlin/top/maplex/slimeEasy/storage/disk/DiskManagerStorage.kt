package top.maplex.slimeEasy.storage.disk

import org.bukkit.block.Block
import org.bukkit.block.ChiseledBookshelf
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.CapacityPolicy
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.StorageChangeBus
import top.maplex.slimeEasy.storage.core.VirtualStorage

/**
 * 雕纹书架六个原生书槽与磁盘虚拟库存之间的适配层。
 *
 * 书架库存是磁盘安装状态的唯一真相源，磁盘 UUID 对应的内容来自 Slimefun UniversalData。
 * 六盘内容仅在内存中合并，供现有 [top.maplex.slimeEasy.storage.network.StorageNetwork] 访问。
 */
object DiskManagerStorage {

    /** 雕纹书架固定为六个书槽；槽序同时是 UI 与世界方块展示协议。 */
    const val SLOT_COUNT = 6

    data class MountedDisk(
        val slot: Int,
        val item: ItemStack,
        val tier: DiskTier,
        val storage: VirtualStorage
    )

    /** 创建带磁盘字节容量策略的合并运行时库存。 */
    fun runtime(block: Block): VirtualStorage = VirtualStorage(
        maxTypes = SLOT_COUNT * DiskStore.MAX_TYPES,
        maxSlots = Int.MAX_VALUE,
        stackMultiplier = 1.0,
        capacityPolicy = CapacityPolicy { _, item, requested -> acceptedAmount(block, item, requested) }
    )

    /** 从六张磁盘的 UniversalData 查询结果构造管理器内存合并库存。 */
    fun loadAggregate(block: Block, target: VirtualStorage) {
        val aggregate = VirtualStorage(SLOT_COUNT * DiskStore.MAX_TYPES, Int.MAX_VALUE, 1.0)
        for (disk in mounted(block)) for ((key, amount) in disk.storage.entries()) {
            aggregate.insert(key.template, amount, simulate = false)
        }
        // load 只作用于内存对象，不会把合并数据写回任何 PDC 或 BlockData。
        target.load(aggregate.serialize())
    }

    /** 读取当前安装的有效磁盘；非本插件磁盘不会被当作存储元件。 */
    fun mounted(block: Block): List<MountedDisk> {
        val shelf = shelf(block) ?: return emptyList()
        return (0 until SLOT_COUNT).mapNotNull { slot ->
            val item = shelf.inventory.getItem(slot)?.takeIf { !it.type.isAir } ?: return@mapNotNull null
            val tier = DiskTier.of(item) ?: return@mapNotNull null
            MountedDisk(slot, item.clone().apply { amount = 1 }, tier, DiskStore.read(item))
        }
    }

    /** 把合并库存的变化增量同步回各磁盘，尽量保留原有物品所属磁盘。 */
    fun syncToDisks(block: Block, target: VirtualStorage) {
        val shelf = shelf(block) ?: return
        val disks = mounted(block).toMutableList()
        val desired = target.entries().associate { it.first to it.second }
        val dirtySlots = HashSet<Int>()

        // 先从后装入的磁盘回收全局超量，避免一次取出导致其它磁盘同类物品被错误清空。
        val currentTotals = totals(disks)
        for ((key, current) in currentTotals) {
            var excess = current - (desired[key] ?: 0L)
            if (excess <= 0) continue
            for (disk in disks.asReversed()) {
                if (excess <= 0) break
                val taken = disk.storage.extract(key, excess, simulate = false)
                if (taken > 0) dirtySlots.add(disk.slot)
                excess -= taken
            }
        }

        // 再补齐新增量：已有同类的盘优先，减少每盘重复支付种类索引字节。
        val afterRemoval = totals(disks)
        for ((key, wanted) in desired) {
            var missing = wanted - (afterRemoval[key] ?: 0L)
            if (missing <= 0) continue
            val ordered = disks.sortedByDescending { it.storage.count(key) > 0 }
            for (disk in ordered) {
                if (missing <= 0) break
                val accepted = minOf(missing, DiskStore.roomFor(disk.tier, disk.storage, key.template))
                if (accepted <= 0) continue
                disk.storage.insert(key.template, accepted, simulate = false)
                dirtySlots.add(disk.slot)
                missing -= accepted
            }
        }

        // 仅写回实际变化的盘，避免一次网络操作无谓刷新其它五张 UniversalData 记录。
        for (disk in disks.filter { it.slot in dirtySlots }) shelf.inventory.setItem(
            disk.slot, DiskStore.write(disk.item, disk.tier, disk.storage)
        )
    }

    /**
     * 磁盘安装或拆卸后清理管理器的内存合并库存。
     * 下次访问会按书本 UUID 重新查询 UniversalData，确保热插拔立即反映到网络。
     */
    fun rebuildAggregate(manager: DiskManager, block: Block) {
        manager.clearCache(block)
        StorageChangeBus.fire(block)
    }

    /** 清空书架安装槽并返回其中磁盘，供方块破坏时安全掉落。 */
    fun removeAll(block: Block): List<ItemStack> {
        val shelf = shelf(block) ?: return emptyList()
        val disks = mounted(block).map { DiskStore.write(it.item, it.tier, it.storage) }
        for (slot in 0 until SLOT_COUNT) shelf.inventory.setItem(slot, null)
        return disks
    }

    /** 写入单个物理书槽；调用方负责校验物品类型和数量。 */
    fun setSlot(block: Block, slot: Int, item: ItemStack?) {
        val shelf = shelf(block) ?: return
        shelf.inventory.setItem(slot, item?.clone()?.apply { amount = 1 })
    }

    private fun acceptedAmount(block: Block, item: ItemStack, requested: Long): Long {
        if (requested <= 0 || DiskStore.isNonEmptyDisk(item)) return 0
        val key = ItemKey.of(item) ?: return 0
        val disks = mounted(block).sortedByDescending { it.storage.count(key) > 0 }
        var left = requested
        for (disk in disks) {
            left -= minOf(left, DiskStore.roomFor(disk.tier, disk.storage, item))
            if (left <= 0) break
        }
        return requested - left
    }

    private fun totals(disks: List<MountedDisk>): Map<ItemKey, Long> {
        val result = LinkedHashMap<ItemKey, Long>()
        for (disk in disks) for ((key, amount) in disk.storage.entries()) {
            result[key] = (result[key] ?: 0L) + amount
        }
        return result
    }

    private fun shelf(block: Block): ChiseledBookshelf? = block.state as? ChiseledBookshelf
}
