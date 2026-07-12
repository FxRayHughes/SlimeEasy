package top.maplex.slimeEasy.territory

import java.util.UUID

/**
 * 不持久化的短期交互会话。
 * 扩区选择与二次拆除都设置明确过期时间，避免旧点击在之后意外生效。
 */
internal object TerritorySessions {
    private const val EXPANSION_TTL = 60_000L
    private const val BREAK_TTL = 10_000L
    private val expansion = mutableMapOf<UUID, Timed<UUID>>()
    private val coreBreak = mutableMapOf<UUID, Timed<TerritoryBlock>>()

    /** 为玩家选择下一面旗帜的唯一归属领地。 */
    fun selectExpansion(player: UUID, territory: UUID) {
        expansion[player] = Timed(territory, System.currentTimeMillis() + EXPANSION_TTL)
    }

    /** 预检时只读取扩区选择，避免事件之后被其它保护插件取消却提前消费会话。 */
    fun expansionSelection(player: UUID): UUID? {
        val selected = expansion[player]?.validValue()
        if (selected == null) expansion.remove(player)
        return selected
    }

    /** 仅在旗帜扩区确实提交后消费与预检一致的一次性选择。 */
    fun consumeExpansion(player: UUID, expected: UUID?) {
        if (expected != null && expansion[player]?.validValue() == expected) expansion.remove(player)
    }

    /** 第一次调用建立10秒确认，第二次命中同一核心才返回 true。 */
    fun confirmCoreBreak(player: UUID, block: TerritoryBlock): Boolean {
        val now = System.currentTimeMillis()
        val previous = coreBreak[player]
        if (previous?.value == block && previous.deadline >= now) {
            coreBreak.remove(player)
            return true
        }
        coreBreak[player] = Timed(block, now + BREAK_TTL)
        return false
    }

    private data class Timed<T>(val value: T, val deadline: Long) {
        fun validValue(): T? = value.takeIf { deadline >= System.currentTimeMillis() }
    }
}
