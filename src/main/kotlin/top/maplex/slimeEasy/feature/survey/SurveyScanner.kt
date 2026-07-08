package top.maplex.slimeEasy.feature.survey

import org.bukkit.Material
import org.bukkit.block.Block

/**
 * 区域矿石扫描器。
 *
 * 模拟工业矿机的采掘几何: 以给定方块为中心, 覆盖边长 (2·range+1) 的正方形水平区域,
 * 自该方块所在 Y 向下直到世界最低高度, 统计其中各类可挖矿石 (由 [MineableOres] 判定) 数量。
 */
object SurveyScanner {

    /**
     * 扫描 [center] 下方的矿石分布。
     *
     * @param center 玩家点击的方块 (扫描中心与起始高度)
     * @param range  水平半径; 采掘区为 (2·range+1)²
     * @return 各矿石材质到数量的映射 (仅含存在的矿石)
     */
    fun scan(center: Block, range: Int): Map<Material, Int> {
        val world = center.world
        val minY = world.minHeight
        val baseX = center.x
        val baseZ = center.z
        val topY = center.y

        val counts = HashMap<Material, Int>()
        for (x in (baseX - range)..(baseX + range)) {
            for (z in (baseZ - range)..(baseZ + range)) {
                for (y in minY..topY) {
                    val type = world.getBlockAt(x, y, z).type
                    if (MineableOres.isMineable(type)) {
                        counts.merge(type, 1, Int::plus)
                    }
                }
            }
        }
        return counts
    }
}
