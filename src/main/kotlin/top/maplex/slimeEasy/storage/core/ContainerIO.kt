package top.maplex.slimeEasy.storage.core

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.storage.upgrade.FaceConfig
import top.maplex.slimeEasy.storage.upgrade.ItemFilter
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 容器物品进出逻辑 (抽屉 / 箱子共用; 前身为 HopperExtract)。
 *
 * - [pull] 抽取升级: 每 tick 从相邻**六个方向**的容器 (漏斗 / 箱子 / 发射器等) 主动提取物品入库。
 * - [push] 输出升级: 每 tick 把库存物品主动推送到相邻六向的容器。
 *
 * 两者均经黑 / 白名单 ([ItemFilter]) 过滤, 并**排除本插件自己的存储方块** ([adjacentSources]):
 * 本插件存储块外观是木桶、真实库存为虚拟 (BlockData), 往其原版库存推 / 从中吸都会吞物
 * 或做无效搬运, 排除后也天然杜绝存储块之间的循环搬运。
 *
 * 结构对齐磁铁吸取: 累积 [Boolean] changed 仅一次落盘。须在主线程调用
 * (由 [CargoBufferBlock] 的 BlockTicker 保证)。
 */
object ContainerIO {

    private val FACES = listOf(
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    )

    /**
     * 收集六向相邻的可交互容器库存 (活体, `getState(false)`), **排除本插件存储方块**。
     * 供 [pull] / [push] 与自动点击器补料共用。
     */
    fun adjacentSources(block: Block, faces: Collection<BlockFace> = FACES): List<Inventory> {
        val result = ArrayList<Inventory>(faces.size)
        for (face in faces) {
            val nb = block.getRelative(face)
            if (StorageCacheUtils.getSlimefunItem(nb.location) is CargoBufferBlock) continue
            val holder = nb.getState(false) as? InventoryHolder ?: continue
            result.add(holder.inventory)
        }
        return result
    }

    /**
     * 收集六向相邻的**本插件存储容器** (方块与其逻辑), 跳过经验模式容器 (存经验不存物品)。
     *
     * 这些容器真实库存是虚拟的, 必须经其 [CargoBufferBlock.storageAt] 虚拟库存对接
     * (绝不能碰其原版库存, 见 memory: 木桶吞物)。供 [pull] / [push] 与点击器补料共用。
     */
    fun adjacentPluginStores(block: Block, faces: Collection<BlockFace> = FACES): List<Pair<Block, CargoBufferBlock>> {
        val result = ArrayList<Pair<Block, CargoBufferBlock>>(faces.size)
        for (face in faces) {
            val nb = block.getRelative(face)
            val sf = StorageCacheUtils.getSlimefunItem(nb.location) as? CargoBufferBlock ?: continue
            if (UpgradeStore.resolve(nb.location).hasExpStorage) continue // 经验容器不参与物品搬运
            result.add(nb to sf)
        }
        return result
    }

    /**
     * 抽取升级: 从相邻容器逐格提取物品入库。命中黑 / 白名单 ([ItemFilter.EXTRACT]) 放行者
     * 才抽取, 再叠加虚空封顶。物料守恒: 移出源容器量 = 真正入库 + 虚空湮灭。
     */
    fun pull(box: CargoBufferBlock, block: Block) {
        val storage = box.storageAt(block)
        box.prepareForInsert(block, box.item) // 同步槽位预算与倍率
        val hasVoid = UpgradeStore.resolve(block.location).hasVoid
        val faces = FaceConfig.EXTRACT.faces(block.location)
        var budget = transferBudget(SEConfig.storageIoPullMaxItemsPerTick)
        var changed = false
        sourceLoop@
        for (inv in adjacentSources(block, faces)) {
            for (i in 0 until inv.size) {
                if (budget <= 0) break@sourceLoop
                val stack = inv.getItem(i) ?: continue
                if (stack.type.isAir || stack.amount <= 0) continue
                if (!ItemFilter.EXTRACT.allows(block.location, stack)) continue // 黑 / 白名单过滤
                val key = ItemKey.of(stack) ?: continue
                val requested = minOf(stack.amount.toLong(), budget)
                val admit = if (hasVoid) VoidFilter.admit(block.location, stack, storage.count(key), requested)
                            else requested
                val leftover = if (admit > 0) storage.insert(stack, admit, simulate = false) else admit
                val consumed = (requested - leftover).toInt() // 离开源容器 = 真正入库 + 湮灭
                if (consumed <= 0) continue
                changed = true
                budget -= consumed
                inv.setItem(i, stack.clone().apply { amount = stack.amount - consumed }.takeIf { it.amount > 0 })
            }
        }
        // 从相邻本插件存储容器的虚拟库存抽取 (不走虚空湮灭, 避免销毁玩家的存储内容)
        for ((nb, logic) in adjacentPluginStores(block, faces)) {
            if (budget <= 0) break
            val src = logic.storageAt(nb)
            var srcChanged = false
            for ((key, amount) in src.entries()) {
                if (budget <= 0) break
                if (amount <= 0) continue
                if (!ItemFilter.EXTRACT.allows(block.location, key.template)) continue
                val requested = minOf(amount, budget)
                val leftover = storage.insert(key.template, requested, simulate = false)
                val moved = requested - leftover
                if (moved > 0) {
                    src.extract(key, moved, simulate = false)
                    budget -= moved
                    changed = true
                    srcChanged = true
                }
            }
            if (srcChanged) logic.saveStorage(nb, src)
        }
        if (changed) box.saveStorage(block, storage)
    }

    /**
     * 输出升级: 把库存物品主动推送到相邻容器。命中黑 / 白名单 ([ItemFilter.OUTPUT]) 放行者
     * 才推送, 推入多少扣减多少。
     */
    fun push(box: CargoBufferBlock, block: Block) {
        val storage = box.storageAt(block)
        if (storage.isEmpty()) return
        val faces = FaceConfig.OUTPUT.faces(block.location)
        var budget = transferBudget(SEConfig.storageIoPushMaxItemsPerTick)
        var changed = false
        // ① 推入相邻原版容器
        val targets = adjacentSources(block, faces)
        for ((key, amount) in storage.entries()) {
            if (budget <= 0) break
            if (amount <= 0) continue
            if (!ItemFilter.OUTPUT.allows(block.location, key.template)) continue // 黑 / 白名单过滤
            var remaining = amount
            for (inv in targets) {
                if (remaining <= 0 || budget <= 0) break
                val push = minOf(remaining, key.vanillaMaxStack.toLong(), budget).toInt()
                if (push <= 0) break
                // addItem 返回未放入的物品; 实际放入 = 请求 - 未放入
                val notAdded = inv.addItem(key.template.clone().apply { this.amount = push })
                    .values.sumOf { it.amount }
                val added = push - notAdded
                if (added > 0) {
                    storage.extract(key, added.toLong(), simulate = false)
                    remaining -= added
                    budget -= added
                    changed = true
                }
            }
        }
        // ② 推入相邻本插件存储容器的虚拟库存
        for ((nb, logic) in adjacentPluginStores(block, faces)) {
            if (budget <= 0) break
            val dst = logic.storageAt(nb)
            logic.prepareForInsert(nb, logic.item)
            var dstChanged = false
            for ((key, amount) in storage.entries()) {
                if (budget <= 0) break
                if (amount <= 0) continue
                if (!ItemFilter.OUTPUT.allows(block.location, key.template)) continue
                val requested = minOf(amount, budget)
                val leftover = dst.insert(key.template, requested, simulate = false)
                val moved = requested - leftover
                if (moved > 0) {
                    storage.extract(key, moved, simulate = false)
                    budget -= moved
                    changed = true
                    dstChanged = true
                }
            }
            if (dstChanged) logic.saveStorage(nb, dst)
        }
        if (changed) box.saveStorage(block, storage)
    }

    private fun transferBudget(configuredLimit: Int): Long =
        if (configuredLimit <= 0) Long.MAX_VALUE else configuredLimit.toLong()
}
