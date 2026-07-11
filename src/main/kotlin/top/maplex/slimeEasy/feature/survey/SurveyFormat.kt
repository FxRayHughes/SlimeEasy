package top.maplex.slimeEasy.feature.survey

import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.config.SEConfig

/**
 * 勘察结果的燃料规划文案。
 *
 * 把"可挖矿石总量"换算成工业矿机三种开采燃料的用量估算, 供玩家做材料规划。
 * 每种燃料给出 `总量/单位容量` 形式 (如 `(123/96)桶`): 分子为扫描到的矿石总数,
 * 分母为该燃料每单位可开采的矿石数 (可配置)。GUI 标题与聊天栏共用本文案。
 */
object SurveyFormat {

    /** 桶 (岩浆桶) 每单位可开采矿石数。实时读取配置。 */
    private val PER_BUCKET: Int get() = SEConfig.surveyPerBucket

    /** 原 (原煤) 每单位可开采矿石数。实时读取配置。 */
    private val PER_RAW: Int get() = SEConfig.surveyPerRaw

    /** 燃 (燃料块) 每单位可开采矿石数。实时读取配置。 */
    private val PER_FUEL: Int get() = SEConfig.surveyPerFuel

    /**
     * 构造总量 + 三种燃料用量估算串, 如:
     * `共123个 (123/96)桶 (123/128)原 (123/256)燃`。
     */
    fun fuelSummary(total: Int): String = I18n.text(
        "formats.survey.fuel-summary",
        "total" to total,
        "buckets" to total / PER_BUCKET,
        "rawFuel" to total / PER_RAW,
        "fuelBlocks" to total / PER_FUEL
    )
}
