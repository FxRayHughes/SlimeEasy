package top.maplex.slimeEasy.feature.survey

/**
 * 勘察结果的燃料规划文案。
 *
 * 把"可挖矿石总量"换算成工业矿机三种开采燃料的用量估算, 供玩家做材料规划。
 * 每种燃料给出 `总量/单位容量` 形式 (如 `(123/96)桶`): 分子为扫描到的矿石总数,
 * 分母为该燃料每单位可开采的矿石数 (固定常量)。GUI 标题与聊天栏共用本文案。
 */
object SurveyFormat {

    /** 桶 (岩浆桶) 每单位可开采矿石数。 */
    private const val PER_BUCKET = 96

    /** 原 (原煤) 每单位可开采矿石数。 */
    private const val PER_RAW = 128

    /** 燃 (燃料块) 每单位可开采矿石数。 */
    private const val PER_FUEL = 256

    /**
     * 构造总量 + 三种燃料用量估算串, 如:
     * `共123个 (123/96)桶 (123/128)原 (123/256)燃`。
     */
    fun fuelSummary(total: Int): String =
        "§7共§a$total§7个 " +
            "§8(§a$total§7/§f$PER_BUCKET§8)§7桶 " +
            "§8(§a$total§7/§f$PER_RAW§8)§7原 " +
            "§8(§a$total§7/§f$PER_FUEL§8)§7燃"
}
