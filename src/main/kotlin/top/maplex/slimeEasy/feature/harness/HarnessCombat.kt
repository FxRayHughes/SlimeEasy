package top.maplex.slimeEasy.feature.harness

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Enemy
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.EquipmentSlot
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.registry.Items
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 战斗挽具的作战逻辑。
 *
 * 一个全局定时任务周期性扫描各世界的**快乐恶魂 (乐魂)**; 对身上装备了战斗挽具的乐魂,
 * 以守护者激光的方式攻击其**视线内** ([LivingEntity.hasLineOfSight], 不穿透方块) 最近的敌对生物:
 * 绘制粒子激光束 + 播放守护者攻击音效 + 造成与挽具档位对应的**魔法 (药水) 伤害** (无视护甲)。
 *
 * 战斗挽具本身是原版 `*_HARNESS` 物品的 Slimefun 变体, 玩家右键乐魂即由原版装备 (PDC 保留),
 * 故无需自行处理"穿戴"; 本逻辑只读取乐魂装备槽识别挽具档位。
 */
object HarnessCombat {

    /** 挽具 Slimefun ID → 每次激光的魔法伤害 (点)。 */
    private val DAMAGE: Map<String, Double> = mapOf(
        Items.COMBAT_HARNESS_I_ID to 5.0,
        Items.COMBAT_HARNESS_II_ID to 10.0,
        Items.COMBAT_HARNESS_III_ID to 20.0,
        Items.COMBAT_HARNESS_IV_ID to 25.0
    )

    /** 交战半径 (格)。 */
    private const val RANGE = 16.0

    /** 每只乐魂两次开火的最小间隔 (毫秒)。 */
    private const val COOLDOWN_MS = 1500L

    /** 扫描周期 (tick)。 */
    private const val PERIOD_TICKS = 15L

    /** 每只乐魂上次开火时间 (键为乐魂 UUID)。 */
    private val lastAttack = ConcurrentHashMap<UUID, Long>()

    /** 启动全局定时任务 (onEnable 调用一次)。 */
    fun start() {
        Bukkit.getScheduler().runTaskTimer(SlimeEasy.instance, Runnable { tick() }, PERIOD_TICKS, PERIOD_TICKS)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                if (entity.type != EntityType.HAPPY_GHAST) continue
                val ghast = entity as? LivingEntity ?: continue
                val damage = harnessDamage(ghast) ?: continue
                if (now - (lastAttack[ghast.uniqueId] ?: 0L) < COOLDOWN_MS) continue
                val target = nearestHostile(ghast) ?: continue
                lastAttack[ghast.uniqueId] = now
                fire(ghast, target, damage)
            }
        }
    }

    /** 乐魂身上战斗挽具对应的伤害; 未装备则 null (扫描全部装备槽以适配挽具所在槽)。 */
    private fun harnessDamage(ghast: LivingEntity): Double? {
        val equipment = ghast.equipment ?: return null
        for (slot in EquipmentSlot.entries) {
            val item = equipment.getItem(slot)
            if (item.type.isAir) continue
            val id = SlimefunItem.getByItem(item)?.id ?: continue
            DAMAGE[id]?.let { return it }
        }
        return null
    }

    /**
     * 视线内 (不穿墙) 最近的敌对生物。
     *
     * 用通用敌对接口 [Enemy] 判定 (涵盖 [org.bukkit.entity.Monster] 地面怪 与 幻翼 / 恶魂 / 史莱姆
     * 等飞行/其他敌对); 先筛 [LivingEntity] 以便造成伤害与视线检测 (Enemy 本身仅继承 Entity)。
     */
    private fun nearestHostile(ghast: LivingEntity): LivingEntity? =
        ghast.getNearbyEntities(RANGE, RANGE, RANGE)
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it is Enemy && it.isValid && !it.isDead && ghast.hasLineOfSight(it) }
            .minByOrNull { it.location.distanceSquared(ghast.location) }

    /** 开火: 激光束 + 音效 + 魔法伤害。 */
    private fun fire(ghast: LivingEntity, target: LivingEntity, damage: Double) {
        drawBeam(ghast, target)
        val world = ghast.world
        world.playSound(ghast.location, Sound.ENTITY_GUARDIAN_ATTACK, 1.4f, 1f)
        world.spawnParticle(Particle.CRIT, target.location.add(0.0, target.height * 0.5, 0.0), 12, 0.2, 0.2, 0.2, 0.1)
        // 魔法 (药水) 伤害: 无攻击者、无视护甲
        target.damage(damage, DamageSource.builder(DamageType.MAGIC).build())
    }

    /** 从乐魂到目标绘制一道 END_ROD 粒子激光束。 */
    private fun drawBeam(from: LivingEntity, to: LivingEntity) {
        val start: Location = from.location.add(0.0, from.height * 0.5, 0.0)
        val end: Location = to.location.add(0.0, to.height * 0.5, 0.0)
        val dir = end.toVector().subtract(start.toVector())
        val dist = dir.length()
        if (dist < 0.1) return
        dir.normalize()
        val world = from.world
        var d = 0.0
        while (d <= dist) {
            world.spawnParticle(Particle.END_ROD, start.clone().add(dir.clone().multiply(d)), 1, 0.0, 0.0, 0.0, 0.0)
            d += 0.4
        }
    }
}
