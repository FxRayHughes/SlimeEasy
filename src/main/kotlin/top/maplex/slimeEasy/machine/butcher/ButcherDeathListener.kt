package top.maplex.slimeEasy.machine.butcher

import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.persistence.PersistentDataType

/**
 * 屠夫机器的掉落 / 经验兜底监听器。
 *
 * 机器攻击时会给目标写入 [ButcherLogic.KEY_KILLER] 标记 (owner UUID)。当所有者
 * 在线并作为伤害 causing entity 时, 原版会自动给出经验与抢夺掉落; 但所有者离线
 * 时怪物被"非玩家"击杀, 经验会归零。本监听器对带标记且经验为 0 的怪物补一个
 * 保底经验值, 保证机器击杀始终有经验产出。
 *
 * 基础掉落物 (如腐肉) 不依赖玩家击杀, 原版已放入 event.drops, 无需干预。
 */
class ButcherDeathListener : Listener {

    /** 常见怪物的保底经验值 (近似原版); 未列出的取 [DEFAULT_EXP]。 */
    private val EXP_TABLE: Map<EntityType, Int> = mapOf(
        EntityType.ZOMBIE to 5, EntityType.SKELETON to 5, EntityType.SPIDER to 5,
        EntityType.CREEPER to 5, EntityType.ENDERMAN to 5, EntityType.WITCH to 5,
        EntityType.SLIME to 4, EntityType.BLAZE to 10, EntityType.WITHER_SKELETON to 5,
        EntityType.PIGLIN to 5, EntityType.HOGLIN to 5, EntityType.COW to 3,
        EntityType.PIG to 3, EntityType.SHEEP to 3, EntityType.CHICKEN to 3
    )

    private val DEFAULT_EXP = 5

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(e: EntityDeathEvent) {
        val entity = e.entity
        val marker = entity.persistentDataContainer
            .get(ButcherLogic.KEY_KILLER, PersistentDataType.STRING) ?: return
        if (marker.isEmpty()) return
        // 已有经验 (owner 在线作为 killer, 原版已给) → 不覆盖
        if (e.droppedExp > 0) return
        e.droppedExp = EXP_TABLE[entity.type] ?: DEFAULT_EXP
    }
}
