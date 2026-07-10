package top.maplex.slimeEasy.machine.clicker

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.block.Block
import kotlin.math.roundToInt

/**
 * 自动点击器的按块设置 (存于 Slimefun BlockData)。
 *
 * 三项均可在界面内调节: 左键开关、右键开关 (默认**均关闭 → 不点击**)、点击间隔 (以 Slimefun tick 计, 支持小数)。
 * 间隔可低至 [MIN_INTERVAL] (0.25 tick): 小于 1 时由机器在单个 tick 内**连点多次**达成高频。
 */
object AutoClickerState {

    private const val KEY_LEFT = "se_click_left"
    private const val KEY_RIGHT = "se_click_right"
    private const val KEY_INTERVAL = "se_click_interval"

    /** 间隔下限 (tick): 0.05 → 每 tick 连点 20 次 (受机器单 tick 连点上限约束)。 */
    const val MIN_INTERVAL = 0.05

    /** 间隔上限 (tick)。 */
    const val MAX_INTERVAL = 40.0

    /** 默认间隔 (tick; 原生每 tick 约 0.5 秒)。 */
    const val DEFAULT_INTERVAL = 4.0

    /** 对齐网格 / 微调步进 (tick)。 */
    const val STEP = 0.05

    /** 粗调步进 (tick, 普通点击)。 */
    const val COARSE_STEP = 0.25

    /** 左键是否开启 (默认否)。 */
    fun leftEnabled(block: Block): Boolean = StorageCacheUtils.getData(block.location, KEY_LEFT) == "1"

    /** 右键是否开启 (默认否)。 */
    fun rightEnabled(block: Block): Boolean = StorageCacheUtils.getData(block.location, KEY_RIGHT) == "1"

    /** 点击间隔 (tick, 夹取到合法区间)。 */
    fun interval(block: Block): Double =
        (StorageCacheUtils.getData(block.location, KEY_INTERVAL)?.toDoubleOrNull() ?: DEFAULT_INTERVAL)
            .coerceIn(MIN_INTERVAL, MAX_INTERVAL)

    /** 切换左键开关。 */
    fun toggleLeft(block: Block) {
        StorageCacheUtils.setData(block.location, KEY_LEFT, if (leftEnabled(block)) "0" else "1")
    }

    /** 切换右键开关。 */
    fun toggleRight(block: Block) {
        StorageCacheUtils.setData(block.location, KEY_RIGHT, if (rightEnabled(block)) "0" else "1")
    }

    /** 调整间隔 (对齐到步进并夹取到区间)。 */
    fun addInterval(block: Block, delta: Double) {
        val raw = interval(block) + delta
        val snapped = (raw / STEP).roundToInt() * STEP // 对齐步进, 避免浮点漂移
        StorageCacheUtils.setData(block.location, KEY_INTERVAL, snapped.coerceIn(MIN_INTERVAL, MAX_INTERVAL).toString())
    }
}
