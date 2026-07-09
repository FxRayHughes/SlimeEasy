package top.maplex.slimeEasy.storage.box

import org.bukkit.block.Block
import org.bukkit.entity.Item
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 翻页箱磁铁升级的吸附逻辑。
 *
 * 每 tick 把箱子周围 [MAGNET_RADIUS] 内的掉落物吸入库存 (多物品, 受种类数与
 * 单格容量约束); 命中虚空销毁表的掉落物直接湮灭。须在主线程调用。
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
            if (hasVoid && VoidFilter.contains(block.location, stack)) { drop.remove(); changed = true; continue }
            val leftover = storage.insert(stack, stack.amount.toLong(), simulate = false)
            if (leftover >= stack.amount.toLong()) continue
            changed = true
            if (leftover <= 0) drop.remove() else drop.itemStack = stack.clone().apply { amount = leftover.toInt() }
        }
        if (changed) box.saveStorage(block, storage)
    }
}
