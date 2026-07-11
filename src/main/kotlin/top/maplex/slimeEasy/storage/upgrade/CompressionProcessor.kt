package top.maplex.slimeEasy.storage.upgrade

import org.bukkit.block.Block
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.VirtualStorage
import java.util.ArrayDeque

/** 翻页箱压制执行器：只处理相对上次持久化后数量增加的物品。 */
object CompressionProcessor {

    /**
     * 压制本次发生正向数量变化的物品，并把新产物继续送入压制队列。
     *
     * [previousData] 是写入前的 BlockData 快照。升级安装只会触发一次无内容差异的保存，
     * 因而不会扫描或压制既有库存；玩家、货运、磁铁、网络等后续入库则都会形成正差量。
     */
    fun compressChanged(block: Block, storage: VirtualStorage, previousData: String?, maxGridSize: Int) {
        // 旧库存只用于数量对比，放宽容量避免历史内容因当前升级变化而影响载入。
        val previous = VirtualStorage(Int.MAX_VALUE, Int.MAX_VALUE, storage.stackMultiplier).apply { load(previousData) }
        val queue = ArrayDeque<ItemKey>()
        storage.entries()
            .filter { (key, count) -> count > previous.count(key) }
            .mapTo(queue) { it.first }
        val processed = HashSet<ItemKey>()
        val location = block.location
        val allowIrreversible = CompressionState.allowsIrreversible(location)

        while (queue.isNotEmpty()) {
            val inputKey = queue.removeFirst()
            if (!processed.add(inputKey)) continue
            if (!ItemFilter.COMPRESSION.allows(location, inputKey.template)) continue
            val recipe = CompressionRecipes.find(inputKey.template, maxGridSize, allowIrreversible) ?: continue
            val crafts = storage.count(inputKey) / recipe.inputAmount
            if (crafts <= 0) continue
            val produced = runCatching { Math.multiplyExact(crafts, recipe.output.amount.toLong()) }.getOrNull() ?: continue
            val consumed = crafts * recipe.inputAmount

            // 先在库存副本中完整试算，确保新增产物放得下；失败时真实库存保持原样。
            val trial = VirtualStorage(storage.maxTypes, storage.maxSlots, storage.stackMultiplier).apply {
                load(storage.serialize())
                extract(inputKey, consumed, simulate = false)
            }
            if (trial.insert(recipe.output, produced, simulate = false) != 0L) continue

            storage.extract(inputKey, consumed, simulate = false)
            if (storage.insert(recipe.output, produced, simulate = false) != 0L) continue
            // 产物也可能拥有更高阶压缩配方，例如高级升级可继续形成压缩链。
            ItemKey.of(recipe.output)?.let(queue::addLast)
        }
    }
}
