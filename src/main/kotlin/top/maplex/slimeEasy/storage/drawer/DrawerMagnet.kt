package top.maplex.slimeEasy.storage.drawer

import org.bukkit.block.Block
import org.bukkit.entity.Item
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 抽屉磁铁升级的物品吸附逻辑。
 *
 * 每 tick 扫描抽屉周围 [Drawer.MAGNET_RADIUS] 内的掉落物, 把与抽屉锁定物品同类者
 * 吸入库存 (空抽屉则由首个吸入物锁定)。命中虚空销毁表的掉落物按保留数量封顶,
 * 超出部分直接湮灭 (与货运 / 手动存入一致)。经验磁铁改由 [MagnetOrbListener] 在球
 * 生成瞬间拦截, 不走此处。须在主线程调用。
 */
object DrawerMagnet {

    fun absorb(drawer: Drawer, block: Block) {
        val center = block.location.toCenterLocation()
        val world = block.world
        val storage = drawer.storageAt(block)
        val hasVoid = UpgradeStore.resolve(block.location).hasVoid
        var changed = false
        for (drop in world.getNearbyEntitiesByType(Item::class.java, center, Drawer.MAGNET_RADIUS)) {
            val stack = drop.itemStack
            if (stack.type.isAir || stack.amount <= 0) continue
            drawer.refreshCapacity(block, storage, stack)
            val key = ItemKey.of(stack) ?: continue
            // 虚空过滤: 封顶到保留数量, 超出部分湮灭 (未标记则原样尝试吸入)
            val admit = if (hasVoid) VoidFilter.admit(block.location, stack, storage.count(key), stack.amount.toLong())
                        else stack.amount.toLong()
            val leftover = if (admit > 0) storage.insert(stack, admit, simulate = false) else admit
            val remaining = leftover.toInt() // 留在地面 = 未入库部分 (湮灭部分已消失)
            if (remaining == stack.amount) continue // 未吸入也未湮灭 (类型不符 / 已满且未过滤)
            if (admit - leftover > 0) changed = true // 有真正入库才需落盘
            if (remaining <= 0) drop.remove() else drop.itemStack = stack.clone().apply { amount = remaining }
        }
        if (changed) drawer.saveStorage(block, storage)
    }
}
