package top.maplex.slimeEasy.machine.sieve

import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldUnloadEvent

/**
 * 清理筛子的非持久运行态。
 *
 * 筛子由活板门和其下方的发射器组成，因此任意方块变化都同时尝试清理“该方块本身是活板门锚点”
 * 和“该方块上方是活板门锚点”两种情况。对普通方块调用清理只是无命中的哈希表删除，不扫描世界。
 *
 * 方块破坏类事件在 MONITOR 阶段且仅在未取消时处理，避免保护插件取消破坏后误删仍有效的进度。
 */
internal class SieveCleanupListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        clearAffectedSieve(event.block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        clearAffectedSieve(event.block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplosion(event: BlockExplodeEvent) {
        event.blockList().forEach(::clearAffectedSieve)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplosion(event: EntityExplodeEvent) {
        event.blockList().forEach(::clearAffectedSieve)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        event.blocks.forEach { moved ->
            clearPistonPath(moved, event.direction)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        event.blocks.forEach { moved ->
            clearPistonPath(moved, event.direction)
        }
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        SieveRuntime.clearChunk(
            worldId = event.world.uid,
            chunkX = event.chunk.x,
            chunkZ = event.chunk.z
        )
    }

    @EventHandler
    fun onWorldUnload(event: WorldUnloadEvent) {
        SieveRuntime.clearWorld(event.world.uid)
    }

    /**
     * 同时清理源位置和活塞朝向两侧的候选位置，统一覆盖伸出与回缩的移动方向差异，
     * 并且仍然只是常数次哈希删除。
     */
    private fun clearPistonPath(movedBlock: Block, pistonFacing: BlockFace) {
        clearAffectedSieve(movedBlock)
        clearAffectedSieve(movedBlock.getRelative(pistonFacing))
        clearAffectedSieve(movedBlock.getRelative(pistonFacing.oppositeFace))
    }

    private fun clearAffectedSieve(changedBlock: Block) {
        SieveRuntime.clear(changedBlock)
        SieveRuntime.clear(changedBlock.getRelative(BlockFace.UP))
        SieveRuntime.clear(changedBlock.getRelative(BlockFace.UP, 2))
    }
}
