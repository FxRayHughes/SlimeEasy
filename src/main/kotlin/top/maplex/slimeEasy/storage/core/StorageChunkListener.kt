package top.maplex.slimeEasy.storage.core

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkUnloadEvent

/**
 * 区块卸载监听器。
 *
 * 区块卸载时通知 [CargoBufferBlock] 清理该区块内全部存储方块的内存缓存, 防止
 * 长期运行的服务器上缓存只增不减。BlockData 为真相源, 缓存仅为副本, 丢弃安全。
 */
class StorageChunkListener : Listener {

    @EventHandler
    fun onChunkUnload(e: ChunkUnloadEvent) {
        CargoBlockRegistry.onChunkUnload(e.world.name, e.chunk.x, e.chunk.z)
    }
}
