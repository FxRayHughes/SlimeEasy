package top.maplex.slimeEasy.villager.core

import top.maplex.slimeEasy.SlimeEasy

/**
 * 简易村民功能的配置读取入口。
 *
 * 所有"时间间隔"配置以**秒**为单位暴露给玩家, 内部转为毫秒并配合**墙钟时间戳**判定,
 * 从而"不受任何外部因素影响"(tick 频率 / 区块加载 / 服务器重载均不影响补货或产出节奏)。
 *
 * 直接读取插件内存态 [org.bukkit.plugin.java.JavaPlugin.getConfig], 无需额外缓存: 该对象
 * 在 onEnable 的 saveDefaultConfig 后常驻内存, 读取为纯内存操作。
 */
object VillagerConfig {

    private val cfg get() = SlimeEasy.instance.config

    /** 交易器补货间隔 (毫秒)。 */
    val traderRestockMillis: Long get() = seconds("trader.restock-interval-seconds", 30)

    /** 刷铁机产铁间隔 (毫秒, 未计速度升级)。 */
    val ironProduceMillis: Long get() = seconds("iron-farm.produce-interval-seconds", 20)

    /** 刷铁机每周期消耗的食物饱食度。 */
    val ironFoodPerCycle: Int get() = cfg.getInt("iron-farm.food-per-cycle", 1).coerceAtLeast(0)

    /** 刷铁机每周期产出的铁锭数量。 */
    val ironPerCycle: Int get() = cfg.getInt("iron-farm.iron-per-cycle", 1).coerceAtLeast(1)

    /** 刷铁机速度升级级数上限。 */
    val ironSpeedMaxLevel: Int get() = cfg.getInt("iron-farm.speed-upgrade-max-level", 5).coerceAtLeast(0)

    /** 速度升级每级对间隔的缩短系数 (间隔 = 基础 / (1 + 级数 × 步长))。 */
    val ironSpeedStep: Double get() = cfg.getDouble("iron-farm.speed-upgrade-step", 0.5).coerceAtLeast(0.0)

    /** 村民小学的转化耗时 (毫秒)。 */
    val schoolConvertMillis: Long get() = seconds("school.convert-seconds", 30)

    /** 村民治愈机把僵尸村民治愈为普通村民的耗时 (毫秒)。 */
    val healerConvertMillis: Long get() = seconds("healer.convert-seconds", 30)

    /** 读取一个"秒"配置并转毫秒 (至少 1 秒)。 */
    private fun seconds(path: String, def: Int): Long =
        cfg.getInt(path, def).coerceAtLeast(1).toLong() * 1000L
}
