package top.maplex.slimeEasy.machine.butcher

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.core.attributes.RandomMobDrop
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.implementation.items.misc.BasicCircuitBoard
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.ThreadLocalRandom

/**
 * 屠夫机器的掉落 / 经验兜底监听器。
 *
 * 机器攻击时会给目标写入 [ButcherLogic.KEY_KILLER] 标记；值通常是 owner UUID，未绑定旧机器
 * 允许使用空值，是否属于机器击杀只由键存在决定。假玩家击杀时怪物的
 * [org.bukkit.entity.LivingEntity.getKiller] 为 null, 导致:
 * - 原版经验归零 → 本监听按 [EXP_TABLE] 补保底经验;
 * - Slimefun 自身的 [io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.MobDropListener]
 *   因 killer 为 null 而跳过 → 铁傀儡的电路板等**自定义怪物掉落不会掉**, 本监听据 Slimefun 掉落表补发。
 *
 * 基础原版掉落物 (如腐肉) 不依赖玩家击杀, 原版已放入 event.drops, 无需干预。
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
        if (!entity.persistentDataContainer.has(ButcherLogic.KEY_KILLER, PersistentDataType.STRING)) return

        // 补发 Slimefun 自定义怪物掉落 (如铁傀儡的电路板): 假玩家 killer 为 null 使原生监听跳过
        addSlimefunMobDrops(e)

        // 经验: 已有 (owner 在线作为 killer) 则不覆盖, 否则补保底
        if (e.droppedExp <= 0) e.droppedExp = EXP_TABLE[entity.type] ?: DEFAULT_EXP
    }

    /**
     * 按 Slimefun 掉落表为本次击杀补发自定义掉落。
     *
     * 复刻 [io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.MobDropListener] 的判定:
     * [RandomMobDrop] 走概率、[BasicCircuitBoard] 受"是否由傀儡掉落"开关约束; 因机器无玩家研究上下文,
     * 跳过 canUse 研究门槛 (视为机器所有者已解锁)。
     */
    private fun addSlimefunMobDrops(e: EntityDeathEvent) {
        val customDrops = Slimefun.getRegistry().mobDrops[e.entityType] ?: return
        for (drop in customDrops) {
            if (shouldDrop(drop)) e.drops.add(drop.clone())
        }
    }

    /** 单个 Slimefun 掉落是否命中 (概率 + 傀儡开关)。 */
    private fun shouldDrop(item: ItemStack): Boolean {
        val sfItem = SlimefunItem.getByItem(item) ?: return true
        if (sfItem is RandomMobDrop && sfItem.mobDropChance <= ThreadLocalRandom.current().nextInt(100)) return false
        if (sfItem is BasicCircuitBoard) return sfItem.isDroppedFromGolems
        return true
    }
}
