package top.maplex.slimeEasy.feature.ward

import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ExplosionPrimeEvent

/**
 * 苦力怕管控监听器: 在受保护区块内禁止其自然生成, 并阻止其爆炸。
 *
 * 生成拦截仅作用于自然生成体系, 放行玩家显式意图的生成 (见 [isBlockedReason]);
 * 爆炸拦截作用于任意来源的苦力怕, 只要引爆点位于 [ProtectedChunks] 保护区块内。
 */
class CreeperControlListener : Listener {

    // HIGH 且 ignoreCancelled: 已被其他插件取消的事件无需重复处理
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        if (event.entityType != EntityType.CREEPER) return
        if (!isBlockedReason(event.spawnReason)) return
        if (ProtectedChunks.isProtected(event.location.chunk)) {
            event.isCancelled = true
        }
    }

    /**
     * 苦力怕即将引爆 (点燃膨胀完成) 时, 若身处保护区块则取消引爆。
     *
     * 取消后苦力怕停止本次爆炸; 配合驱逐推力, 它通常会先被推出区域。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onExplosionPrime(event: ExplosionPrimeEvent) {
        if (event.entityType != EntityType.CREEPER) return
        if (ProtectedChunks.isProtected(event.entity.location.chunk)) {
            event.isCancelled = true
        }
    }

    /**
     * 兜底: 苦力怕爆炸真正发生时, 若爆炸点位于保护区块则整体取消。
     *
     * 覆盖 [onExplosionPrime] 未能拦下的边缘情形 (如在区外点燃、移动后于区内爆),
     * 确保保护区块内不产生任何苦力怕爆炸破坏。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (event.entityType != EntityType.CREEPER) return
        if (ProtectedChunks.isProtected(event.location.chunk)) {
            event.isCancelled = true
        }
    }

    /**
     * 判断该生成来源是否在拦截范围内。
     *
     * 拦截自然生成体系 (自然、刷怪笼、区块生成、增援); 放行指令 / 刷怪蛋 / 插件
     * 自定义等玩家显式意图的生成, 避免驱逐方块干扰玩家主动操作。
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
