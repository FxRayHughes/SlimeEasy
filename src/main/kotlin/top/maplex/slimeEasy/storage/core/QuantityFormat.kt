package top.maplex.slimeEasy.storage.core

import java.util.Locale

/**
 * 数量的展示格式化。
 *
 * 存储的真实数量为 long, 远超物品堆叠上限, 需以文字形式展示给玩家
 * (物品名 / lore / 展示框浮空文字)。统一在此格式化, 保证各处一致。
 */
object QuantityFormat {

    /** 千分位分组, 如 47231 → "47,231"。 */
    fun grouped(value: Long): String =
        String.format(Locale.US, "%,d", value)

    /**
     * 紧凑单位表示, 用于空间受限处 (展示框): 1.2K / 3.4M / 5.6B。
     *
     * 千以下直接显示原值; 达到千/百万/十亿级别时保留一位小数并加单位。
     */
    fun compact(value: Long): String = when {
        value < 1_000L -> value.toString()
        value < 1_000_000L -> trim(value / 1_000.0) + "K"
        value < 1_000_000_000L -> trim(value / 1_000_000.0) + "M"
        else -> trim(value / 1_000_000_000.0) + "B"
    }

    /** 保留一位小数, 整数则去掉小数部分 (1.0 → "1", 1.2 → "1.2")。 */
    private fun trim(v: Double): String {
        val oneDecimal = Math.round(v * 10.0) / 10.0
        return if (oneDecimal % 1.0 == 0.0) oneDecimal.toLong().toString()
        else String.format(Locale.US, "%.1f", oneDecimal)
    }

    /**
     * 生成字符进度条, 如 `§a■■■■■§7■■■■■§r`。
     *
     * @param current 当前值
     * @param max 满值 (≤0 视为空条)
     * @param length 方块字符总数
     * @return 已填充段 (绿) + 未填充段 (灰) 的着色字符串
     */
    fun bar(current: Long, max: Long, length: Int = 20): String {
        val ratio = if (max <= 0) 0.0 else (current.toDouble() / max).coerceIn(0.0, 1.0)
        val filled = (ratio * length).toInt().coerceIn(0, length)
        val color = when {
            ratio >= 1.0 -> "§c" // 满: 红
            ratio >= 0.75 -> "§6" // 偏满: 金
            else -> "§a"          // 一般: 绿
        }
        return buildString {
            append(color)
            repeat(filled) { append('■') }
            append("§7")
            repeat(length - filled) { append('■') }
        }
    }

    /** 百分比整数字符串, 如 `50%`。 */
    fun percent(current: Long, max: Long): String {
        val ratio = if (max <= 0) 0.0 else (current.toDouble() / max).coerceIn(0.0, 1.0)
        return "${Math.round(ratio * 100).toInt()}%"
    }
}
