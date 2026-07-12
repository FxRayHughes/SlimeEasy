package top.maplex.slimeEasy.feature.goggles

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import top.maplex.slimeEasy.registry.Items

/**
 * 工程师夜视护目镜的效果定义与登录自愈入口。
 *
 * SlimefunArmorPiece 的全局护甲任务依赖玩家档案且默认间隔较长；玩家登录时药水效果已被服务端清除，
 * 因此护目镜自己的共享任务需要在确认头盔身份后补齐效果，不能只等待全局护甲缓存刷新。
 */
internal object EngineerGogglesNightVision {

    /** 与原版 Slimefun 夜视眼镜保持一致的 30 秒效果定义，避免组合前后行为发生变化。 */
    fun effect(): PotionEffect = PotionEffect(
        PotionEffectType.NIGHT_VISION,
        EFFECT_DURATION_TICKS,
        EFFECT_AMPLIFIER
    )

    /**
     * 仅在效果缺失或即将进入闪烁区间时刷新；无限时长的外部效果必须保留，不能被本物品覆盖。
     * 该入口由主线程护目镜任务调用，不依赖异步 PlayerProfile 加载完成。
     */
    fun refreshIfWearing(player: Player) {
        val helmetId = SlimefunItem.getByItem(player.inventory.helmet)?.id
        if (helmetId != Items.ENGINEER_NIGHT_VISION_GOGGLES_ID) return
        val current = player.getPotionEffect(PotionEffectType.NIGHT_VISION)
        if (current != null && (current.isInfinite || current.duration > REFRESH_THRESHOLD_TICKS)) return
        player.addPotionEffect(effect(), true)
    }

    private const val EFFECT_DURATION_TICKS = 600
    private const val EFFECT_AMPLIFIER = 20

    /** 夜视低于 10 秒会闪烁；提前一秒刷新，为调度延迟保留余量。 */
    private const val REFRESH_THRESHOLD_TICKS = 220
}
