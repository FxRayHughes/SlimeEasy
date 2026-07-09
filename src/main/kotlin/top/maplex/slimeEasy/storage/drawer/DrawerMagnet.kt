package top.maplex.slimeEasy.storage.drawer

import org.bukkit.block.Block
import org.bukkit.entity.Item

/**
 * 抽屉磁铁升级的物品吸附逻辑。
 *
 * 每 tick 扫描抽屉周围 [Drawer.MAGNET_RADIUS] 内的掉落物, 把与抽屉锁定物品同类者
 * 吸入库存 (空抽屉则由首个吸入物锁定)。经验磁铁改由 [MagnetOrbListener] 在球生成
 * 瞬间拦截, 不走此处。须在主线程调用。
 */
object DrawerMagnet {

    fun absorb(drawer: Drawer, block: Block) {
        val center = block.location.toCenterLocation()
        val world = block.world
        val storage = drawer.storageAt(block)
        var changed = false
        for (drop in world.getNearbyEntitiesByType(Item::class.java, center, Drawer.MAGNET_RADIUS)) {
            val stack = drop.itemStack
            if (stack.type.isAir || stack.amount <= 0) continue
            drawer.refreshCapacity(block, storage, stack)
            val leftover = storage.insert(stack, stack.amount.toLong(), simulate = false)
            if (leftover >= stack.amount.toLong()) continue // 一个都没吸入 (类型不符 / 已满)
            changed = true
            if (leftover <= 0) drop.remove() else drop.itemStack = stack.clone().apply { amount = leftover.toInt() }
        }
        if (changed) drawer.saveStorage(block, storage)
    }
}
