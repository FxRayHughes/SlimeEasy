package top.maplex.slimeEasy.storage.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * [CargoBufferBlock] 实例登记处。
 *
 * 每个存储方块类型注册一个单例, 登记于此; 区块卸载时统一遍历各实例清理其在该
 * 区块的内存缓存。独立为顶层 object 而非放进 protected 伴生对象, 以便监听器访问。
 */
internal object CargoBlockRegistry {

    private val instances = CopyOnWriteArrayList<CargoBufferBlock>()

    fun register(block: CargoBufferBlock) { instances.add(block) }

    /** 区块卸载时清理全部实例在该区块的缓存。 */
    fun onChunkUnload(worldName: String, cx: Int, cz: Int) {
        for (inst in instances) inst.evictChunk(worldName, cx, cz)
    }
}
