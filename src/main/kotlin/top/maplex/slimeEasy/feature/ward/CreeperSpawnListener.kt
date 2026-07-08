package top.maplex.slimeEasy.feature.ward

import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent

/**
 * 苦力怕生成拦截监听器。
 *
 * 当生成实体为苦力怕、且生成点所在区块处于 [ProtectedChunks] 保护中时, 取消生成。
 * 仅拦截自然生成与刷怪笼等自动来源, 不干预插件/指令强制生成 (见 [isBlockedReason])。
 */
class CreeperSpawnListener : Listener {

    // 用 HIGH 优先级但不 ignoreCancelled: 若其他插件已取消则无需重复处理,
    // 这里显式让出被取消的事件。
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        if (event.entityType != EntityType.CREEPER) return
        if (!isBlockedReason(event.spawnReason)) return
        if (ProtectedChunks.isProtected(event.location.chunk)) {
            event.isCancelled = true
        }
    }

    /**
     * 判断该生成来源是否在拦截范围内。
     *
     * 拦截自然生成体系 (自然、刷怪笼、区块生成等); 放行指令 / 插件 / 刷怪蛋等
     * 玩家显式意图的生成, 避免驱逐方块干扰玩家主动操作。
     */
    private fun isBlockedReason(reason: CreatureSpawnEvent.SpawnReason): Boolean = when (reason) {
        CreatureSpawnEvent.SpawnReason.NATURAL,
        CreatureSpawnEvent.SpawnReason.SPAWNER,
        CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
        CreatureSpawnEvent.SpawnReason.DEFAULT,
        CreatureSpawnEvent.SpawnReason.REINFORCEMENTS -> true
        else -> false
    }
}
