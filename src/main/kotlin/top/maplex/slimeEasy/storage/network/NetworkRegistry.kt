package top.maplex.slimeEasy.storage.network

import org.bukkit.block.Block
import java.util.concurrent.ConcurrentHashMap

/**
 * 存储网络缓存。
 *
 * 以控制器位置为键缓存已构建的 [StorageNetwork], 避免每次访问 / tick 都重新
 * BFS。任何网络相关方块 (控制器 / 连接器 / 成员 / 端口) 放置或破坏时调用
 * [invalidateAll] 全量失效 —— 网络规模小、重建廉价, 全量失效实现简单且正确,
 * 无需精确追踪受影响的控制器。
 */
object NetworkRegistry {

    private val cache = ConcurrentHashMap<String, StorageNetwork>()

    /** 获取控制器对应网络 (缺失则即时 BFS 构建并缓存)。 */
    fun get(controller: Block): StorageNetwork =
        cache.getOrPut(locKey(controller)) { NetworkScan.build(controller) }

    /** 使全部缓存失效 (拓扑变更时)。 */
    fun invalidateAll() = cache.clear()

    /** 移除某控制器缓存 (控制器自身被破坏时)。 */
    fun remove(controller: Block) { cache.remove(locKey(controller)) }

    private fun locKey(b: Block): String = "${b.world.name}:${b.x}:${b.y}:${b.z}"
}
