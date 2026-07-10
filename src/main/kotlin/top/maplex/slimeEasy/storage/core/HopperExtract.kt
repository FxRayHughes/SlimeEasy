package top.maplex.slimeEasy.storage.core

import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Hopper
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 抽取升级的漏斗提取逻辑 (抽屉 / 箱子共用)。
 *
 * 每 tick 扫容器周围**六个方向**相邻的漏斗, 把其中物品主动提取入库 (任意可入库物品:
 * 抽屉 [maxTypes]=1 天然只收锁定的单一物品, 箱子填到种类满)。命中虚空销毁表的物品按
 * 保留数量封顶, 超出部分直接湮灭。**不判漏斗朝向** —— 六向任意相邻漏斗均提取。
 *
 * 结构对齐磁铁吸取 ([top.maplex.slimeEasy.storage.box.BoxMagnet]): 累积 [changed]
 * 仅一次落盘。须在主线程调用 (由 [CargoBufferBlock] 的 BlockTicker 保证)。
 */
object HopperExtract {

    private val FACES = listOf(
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    )

    fun pull(box: CargoBufferBlock, block: Block) {
        val storage = box.storageAt(block)
        box.prepareForInsert(block, box.item) // 同步槽位预算与倍率
        val hasVoid = UpgradeStore.resolve(block.location).hasVoid
        var changed = false
        for (face in FACES) {
            val hopper = block.getRelative(face).state as? Hopper ?: continue
            val inv = hopper.inventory
            for (i in 0 until inv.size) {
                val stack = inv.getItem(i) ?: continue
                if (stack.type.isAir || stack.amount <= 0) continue
                // 虚空过滤: 封顶到保留数量, 超出部分湮灭 (未标记则原样尝试入库)
                val key = ItemKey.of(stack) ?: continue
                val admit = if (hasVoid) VoidFilter.admit(block.location, stack, storage.count(key), stack.amount.toLong())
                            else stack.amount.toLong()
                val leftover = if (admit > 0) storage.insert(stack, admit, simulate = false) else admit
                val consumed = stack.amount - leftover.toInt() // 离开漏斗 = 真正入库 + 湮灭
                if (consumed <= 0) continue
                changed = true
                inv.setItem(i, stack.clone().apply { amount = stack.amount - consumed }.takeIf { it.amount > 0 })
            }
        }
        if (changed) box.saveStorage(block, storage)
    }
}
