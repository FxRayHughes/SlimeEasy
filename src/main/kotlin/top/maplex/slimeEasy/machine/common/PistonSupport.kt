package top.maplex.slimeEasy.machine.common

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional

/**
 * 活塞相关的中立工具方法, 供各类"机器 + 活塞"装置复用。
 *
 * 本类不关心机器具体行为 (破坏 / 放置等), 仅负责定位活塞与解析其朝向目标。
 * 所有方法均需在主线程调用。
 */
object PistonSupport {

    /** 机器本体的相邻六面。 */
    private val ADJACENT_FACES = arrayOf(
        BlockFace.UP, BlockFace.DOWN,
        BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST
    )

    /**
     * 检测机器相邻六面, 返回第一个类型匹配的活塞方块; 无则返回 null。
     *
     * @param machine 机器本体方块
     * @param pistonTypes 视为有效活塞的方块类型集合 (由调用方按机器语义指定,
     *   如破坏机接受普通与粘性活塞, 放置机仅接受粘性活塞)
     */
    fun findAdjacentPiston(machine: Block, pistonTypes: Set<Material>): Block? {
        for (face in ADJACENT_FACES) {
            val block = machine.getRelative(face)
            if (block.type in pistonTypes) return block
        }
        return null
    }

    /** 活塞推杆朝向的目标方块 (活塞正前方一格); 非活塞类方块返回 null。 */
    fun resolveTarget(piston: Block): Block? {
        val data = piston.blockData
        if (data !is Directional) return null
        return piston.getRelative(data.facing)
    }
}
