package top.maplex.slimeEasy.storage.network

import org.bukkit.block.Block
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.VirtualStorage

/**
 * 一个存储网络的运行时视图 (由 [NetworkScan] 构建, 缓存于 [NetworkRegistry])。
 *
 * 聚合控制器所连的全部成员容器 (抽屉 / 箱子), 对外提供"合并库存"读写:
 * - [aggregate]: 合并各成员同物品的数量, 供聚合 GUI 展示;
 * - [insert]: 按优先级把物品分发进各成员 (先补已有该物品者, 再填有空位者);
 * - [extract]: 跨成员取出某物品直至满足数量。
 *
 * @property members 成员容器方块与其对应的 [CargoBufferBlock] 逻辑
 */
class StorageNetwork(
    val controller: Block,
    val members: List<Pair<Block, CargoBufferBlock>>,
    val inputPorts: List<Block>,
    val outputPorts: List<Block>
) {

    /** 合并全部成员的库存 (按物品身份求和), 保持成员内顺序。 */
    fun aggregate(): List<Pair<ItemKey, Long>> {
        val merged = LinkedHashMap<ItemKey, Long>()
        for ((block, logic) in members) {
            for ((key, amount) in logic.storageAt(block).entries()) {
                merged[key] = (merged[key] ?: 0L) + amount
            }
        }
        return merged.map { it.key to it.value }
    }

    /**
     * 把 [amount] 个 [key] 物品插入网络; 返回未能放入的剩余数量。
     *
     * 优先级 (借鉴 SophisticatedStorage 的分发次序):
     * ① **已含该物品**的成员 (按现存量降序, 合堆减少碎片);
     * ② **记住该物品身份但已取空**的成员 (让物品优先回到原存储位);
     * ③ 其余尚有空余种类位的成员。
     */
    fun insert(key: ItemKey, amount: Long): Long {
        var left = amount
        // 依三档优先级排序: 先"在存量降序", 同为空时"记忆命中者"优先
        val ordered = members.sortedWith(
            compareByDescending<Pair<Block, CargoBufferBlock>> { (b, l) -> l.storageAt(b).count(key) }
                .thenByDescending { (b, l) -> l.storageAt(b).remembers(key) }
        )
        for ((block, logic) in ordered) {
            if (left <= 0) break
            val storage = logic.storageAt(block)
            logic.prepareForInsert(block, key.template)
            val before = left
            left = storage.insert(key.template, left, simulate = false)
            if (left != before) logic.saveStorage(block, storage)
        }
        return left
    }

    /**
     * 跨成员取出 [amount] 个 [key]; 返回实际取出数量。
     */
    fun extract(key: ItemKey, amount: Long): Long {
        var need = amount
        var got = 0L
        for ((block, logic) in members) {
            if (need <= 0) break
            val storage: VirtualStorage = logic.storageAt(block)
            val taken = storage.extract(key, need, simulate = false)
            if (taken > 0) { logic.saveStorage(block, storage); got += taken; need -= taken }
        }
        return got
    }
}
