package top.maplex.slimeEasy.storage.drawer

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy

/**
 * 经验容器的取出交付方式。
 *
 * [storedValue] 是写入 Slimefun BlockData 的持久化协议值；已有容器没有该数据时必须回退到
 * [DIRECT]，以保持升级前的直接给予行为。
 */
enum class ExperiencePayoutMode(
    val storedValue: String,
    val icon: Material,
    val menuKey: String
) {
    DIRECT("direct", Material.LIME_DYE, "menus.experience.withdraw-mode.direct"),
    ORBS("orbs", Material.EXPERIENCE_BOTTLE, "menus.experience.withdraw-mode.orbs");

    /** 两种模式循环切换，供经验菜单的单一按钮使用。 */
    fun next(): ExperiencePayoutMode = if (this == DIRECT) ORBS else DIRECT

    companion object {
        /** 未知值同样按直接给予处理，避免损坏数据改变玩家预期。 */
        fun fromStored(value: String?): ExperiencePayoutMode =
            entries.firstOrNull { it.storedValue == value } ?: DIRECT
    }
}

/** 按所选模式把已取出的经验交付给玩家。 */
object ExperiencePayout {

    /**
     * 标记由经验容器主动发放的经验球，使附近磁铁不把它们立即吸回。
     * 这是实体 PDC 的固定协议键，修改会导致已生成但尚未拾取的球失去豁免。
     */
    private val payoutOrbKey by lazy { NamespacedKey(SlimeEasy.instance, "experience_payout_orb") }

    /** 直接增加玩家经验，或在玩家位置生成最多 [MAX_ORBS] 个经验球。 */
    fun give(player: Player, points: Int, mode: ExperiencePayoutMode) {
        if (points <= 0) return
        if (mode == ExperiencePayoutMode.DIRECT) {
            ExpUtil.give(player, points)
            return
        }

        val location = player.location.add(0.0, 0.5, 0.0)
        // 先转 Long 再做向上取整，避免 points 接近 Int 上限时加法溢出。
        val perOrb = maxOf(1, ((points.toLong() + MAX_ORBS - 1) / MAX_ORBS).toInt())
        var remaining = points
        while (remaining > 0) {
            val value = minOf(remaining, perOrb)
            player.world.spawn(location, ExperienceOrb::class.java) { orb ->
                // Consumer 在实体进入世界并触发生成监听器前执行，保证磁铁监听器能识别豁免标记。
                orb.persistentDataContainer.set(payoutOrbKey, PersistentDataType.BYTE, 1)
                orb.experience = value
            }
            remaining -= value
        }
    }

    /** 判断经验球是否由取出操作生成，供磁铁生成拦截器跳过。 */
    fun isPayoutOrb(orb: ExperienceOrb): Boolean =
        orb.persistentDataContainer.has(payoutOrbKey, PersistentDataType.BYTE)

    /** 限制一次取出产生的实体数量，兼顾经验球反馈与服务端负载。 */
    private const val MAX_ORBS = 40
}
