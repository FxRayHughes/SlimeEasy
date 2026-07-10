package top.maplex.slimeEasy.machine.common

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.ItemFrame
import top.maplex.slimeEasy.config.SEConfig

/**
 * 破坏频率解析器。
 *
 * 规则 (以 Slimefun 原生 tick 为单位, 每档约 0.5 秒):
 * - 活塞上无展示框, 或展示框内不是拉杆 → 默认 [DEFAULT_INTERVAL] 档 (最快)。
 * - 展示框内为拉杆 → 间隔 = (旋转角序号 + 1) × [STEP_PER_ANGLE]。
 *   [org.bukkit.Rotation] 共 8 个角度 (序号 0~7), 因此间隔为 3、6 … 24 档。
 * 两档数值实时读取 [SEConfig], /se reload 后即时生效。
 */
object FrequencyResolver {

    /** 无调速装置时的默认间隔档位。实时读取配置。 */
    val DEFAULT_INTERVAL: Int get() = SEConfig.machineDefaultInterval

    /** 拉杆每旋转一个角度增加的间隔档位。实时读取配置。 */
    val STEP_PER_ANGLE: Int get() = SEConfig.machineStepPerAngle

    /**
     * 解析给定活塞方块当前应使用的破坏间隔 (单位: Slimefun tick)。
     *
     * @param piston 活塞方块
     * @return 间隔档位, 范围 [DEFAULT_INTERVAL] 或 [STEP_PER_ANGLE]~([STEP_PER_ANGLE]×8)
     */
    fun resolveInterval(piston: Block): Int {
        val frame = findLeverFrame(piston) ?: return DEFAULT_INTERVAL
        // Rotation.ordinal 范围 0~7, 对应八个角度
        return (frame.rotation.ordinal + 1) * STEP_PER_ANGLE
    }

    /**
     * 在活塞六个面上寻找 "展示框且框内为拉杆" 的实体。
     *
     * @return 命中的展示框, 未找到返回 null
     */
    private fun findLeverFrame(piston: Block): ItemFrame? {
        val world = piston.world
        // 展示框实体坐标位于其所贴附方块的相邻单元格, 在活塞包围盒稍作外扩即可覆盖六个面
        val nearby = world.getNearbyEntities(piston.location.toCenterLocation(), 1.0, 1.0, 1.0)
        return nearby.asSequence()
            .filterIsInstance<ItemFrame>()
            .filter { it.item.type == Material.LEVER }
            .firstOrNull()
    }
}
