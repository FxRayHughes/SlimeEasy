package top.maplex.slimeEasy.villager.core

import top.maplex.slimeEasy.config.SEConfig

/**
 * 简易村民功能的配置读取入口 (兼容外观)。
 *
 * 历史上直接读取根级 `trader.*` / `iron-farm.*` 等配置键; 现全部统一到中央配置层
 * [SEConfig] 下的 `villager.*` 分区, 本对象仅作**转发外观**保留既有调用点不变。
 *
 * 所有"时间间隔"仍以秒暴露给玩家, 内部转毫秒并配合墙钟时间戳判定, 不受 tick / 区块 / 重载影响。
 * 数值经 [SEConfig] 实时读取, /se reload 后即时生效。
 */
object VillagerConfig {

    /** 交易器补货间隔 (毫秒)。 */
    val traderRestockMillis: Long get() = SEConfig.traderRestockMillis

    /** 刷铁机产铁间隔 (毫秒, 未计速度升级)。 */
    val ironProduceMillis: Long get() = SEConfig.ironProduceMillis

    /** 刷铁机每周期消耗的食物饱食度。 */
    val ironFoodPerCycle: Int get() = SEConfig.ironFoodPerCycle

    /** 刷铁机每周期产出的铁锭数量。 */
    val ironPerCycle: Int get() = SEConfig.ironPerCycle

    /** 刷铁机速度升级级数上限。 */
    val ironSpeedMaxLevel: Int get() = SEConfig.ironSpeedMaxLevel

    /** 速度升级每级对间隔的缩短系数 (间隔 = 基础 / (1 + 级数 × 步长))。 */
    val ironSpeedStep: Double get() = SEConfig.ironSpeedStep

    /** 村民小学的转化耗时 (毫秒)。 */
    val schoolConvertMillis: Long get() = SEConfig.schoolConvertMillis

    /** 村民治愈机把僵尸村民治愈为普通村民的耗时 (毫秒)。 */
    val healerConvertMillis: Long get() = SEConfig.healerConvertMillis
}
