package top.maplex.slimeEasy.storage.drawer

import org.bukkit.entity.Player
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 玩家经验点换算工具。
 *
 * 仅依赖长期稳定的 Bukkit API ([Player.getLevel]/[Player.getExp]/[Player.giveExp] 等),
 * 配合原版标准经验公式在"等级+进度"与"总经验点"间换算, 规避各版本对总点数
 * getter 支持不一的问题。
 */
object ExpUtil {

    /** 从 0 累积到 [level] 级所需的总经验点 (原版分段公式)。 */
    private fun pointsToReach(level: Int): Int = when {
        level <= 16 -> level * level + 6 * level
        level <= 31 -> (2.5 * level * level - 40.5 * level + 360).roundToInt()
        else -> (4.5 * level * level - 162.5 * level + 2220).roundToInt()
    }

    /** [level] 级升到下一级所需经验点 (原版分段公式)。 */
    private fun pointsToNext(level: Int): Int = when {
        level <= 15 -> 2 * level + 7
        level <= 30 -> 5 * level - 38
        else -> 9 * level - 158
    }

    /** [pointsToReach] 的 Long 版本, 供大额经验换算避免 Int 溢出。 */
    private fun pointsToReachLong(level: Int): Long = when {
        level <= 16 -> level.toLong() * level + 6L * level
        level <= 31 -> (2.5 * level * level - 40.5 * level + 360).roundToLong()
        else -> (4.5 * level * level - 162.5 * level + 2220).roundToLong()
    }

    /**
     * 把玩家从当前等级再抬升 [levels] 级所需的经验点。
     *
     * 只取决于起始等级与级差 (两级阈值之差), 与当前进度无关; 结果封顶到 Int 上限。
     */
    fun pointsForLevels(fromLevel: Int, levels: Int): Int {
        if (levels <= 0) return 0
        val delta = pointsToReachLong(fromLevel + levels) - pointsToReachLong(fromLevel)
        return delta.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    /** 给定总经验点可换算出的**完整等级数** (从 0 级起, 用于展示)。 */
    fun levelsFromPoints(points: Long): Int {
        if (points <= 0) return 0
        var lo = 0; var hi = 1_000_000
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (pointsToReachLong(mid) <= points) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** 玩家当前持有的总经验点。 */
    fun total(player: Player): Int =
        pointsToReach(player.level) + (player.exp * pointsToNext(player.level)).roundToInt()

    /** 清空玩家经验 (存入抽屉时调用)。 */
    fun clear(player: Player) {
        player.exp = 0f
        player.level = 0
    }

    /** 给予玩家 [points] 点经验 (从抽屉取出时调用; giveExp 内部自动重算等级进度)。 */
    fun give(player: Player, points: Int) {
        if (points > 0) player.giveExp(points)
    }
}
