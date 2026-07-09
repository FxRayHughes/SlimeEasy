package top.maplex.slimeEasy.storage.box

import org.bukkit.block.Block
import org.bukkit.entity.Item
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 翻页箱磁铁升级的吸附逻辑。
 *
 * 每 tick 把箱子周围 [MAGNET_RADIUS] 内的掉落物吸入库存 (多物品, 受种类数与
 * 单格容量约束); 命中虚空销毁表的掉落物按保留数量封顶, 超出部分直接湮灭。须在主线程调用。
 */
object BoxMagnet {

    private const val MAGNET_RADIUS = 6.0

    fun absorb(box: PagedBox, block: Block) {
        val center = block.location.toCenterLocation()
        val world = block.world
        val storage = box.storageAt(block)
        val hasVoid = UpgradeStore.resolve(block.location).hasVoid
        box.prepareForInsert(block, box.item) // 同步槽位预算与倍率
        var changed = false
        for (drop in world.getNearbyEntitiesByType(Item::class.java, center, MAGNET_RADIUS)) {
            val stack = drop.itemStack
            if (stack.type.isAir || stack.amount <= 0) continue
            val key = ItemKey.of(stack) ?: continue
            // 虚空过滤: 封顶到保留数量, 超出部分湮灭 (未标记则原样尝试吸入)
            val admit = if (hasVoid) VoidFilter.admit(block.location, stack, storage.count(key), stack.amount.toLong())
                        else stack.amount.toLong()
            val leftover = if (admit > 0) storage.insert(stack, admit, simulate = false) else admit
            val remaining = leftover.toInt() // 留在地面 = 未入库部分 (湮灭部分已消失)
            if (remaining == stack.amount) continue // 未吸入也未湮灭
            if (admit - leftover > 0) changed = true // 有真正入库才需落盘
            if (remaining <= 0) drop.remove() else drop.itemStack = stack.clone().apply { amount = remaining }
        }
        if (changed) box.saveStorage(block, storage)
    }
}
