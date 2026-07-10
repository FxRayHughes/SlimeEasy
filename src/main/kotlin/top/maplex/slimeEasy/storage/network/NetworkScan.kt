package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import kotlin.math.abs

/**
 * 存储网络拓扑扫描器。
 *
 * 自控制器出发, 沿"控制器 / 连接器"构成的导通图做 6 邻广度优先洪泛 (BFS),
 * 在切比雪夫半径 [RADIUS] 内收集节点: 成员容器 ([CargoBufferBlock]) 与
 * 输入 / 输出端口 ([NetworkPort])。**连接器与成员容器**都导通并继续扩散 —— 故相邻
 * 的箱子 / 抽屉免连接器即自动组网; 端口为叶子不扩散; 遇到另一控制器视为边界不并网。
 *
 * 注意: "组网"仅让成员在终端**合并显示、可被路由存取**, 各成员物品始终留在各自
 * 容器的独立存储中, 控制器只读写、不吞并、不搬运。
 *
 * BFS 仅沿方块图行走 (与网络规模成正比), 不扫描整个立方体, 故开销可控。
 */
object NetworkScan {

    /** 网络覆盖的切比雪夫半径 (格)。实时读取配置。 */
    val RADIUS: Int get() = SEConfig.storageNetworkScanRadius

    private val FACES = listOf(
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    )

    fun build(controller: Block): StorageNetwork {
        val ox = controller.x; val oy = controller.y; val oz = controller.z
        val visited = HashSet<Long>()
        val queue = ArrayDeque<Block>()
        val members = ArrayList<Pair<Block, CargoBufferBlock>>()
        val inputs = ArrayList<Block>()
        val outputs = ArrayList<Block>()

        visited.add(key(controller, ox, oy, oz)); queue.add(controller)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (face in FACES) {
                val nb = cur.getRelative(face)
                if (abs(nb.x - ox) > RADIUS || abs(nb.y - oy) > RADIUS || abs(nb.z - oz) > RADIUS) continue
                if (!visited.add(key(nb, ox, oy, oz))) continue
                if (!StorageCacheUtils.hasBlock(nb.location)) continue
                val sf = SlimefunItem.getById(StorageCacheUtils.getBlock(nb.location)?.sfId ?: continue)
                when (sf) {
                    is NetworkConnector -> queue.add(nb)                     // 导线: 导通继续扩散
                    is CargoBufferBlock -> { members.add(nb to sf); queue.add(nb) } // 成员: 入网并继续扩散 (相邻箱子/抽屉免连接器自动组网; 各自独立存储, 仅显示合并, 不吞并)
                    is NetworkPort -> if (sf.isInput) inputs.add(nb) else outputs.add(nb) // 端口: 叶子, 不扩散
                    // 其它 (含另一控制器) 视为边界, 不扩散
                }
            }
        }
        // 追加远程成员 (远程升级挂靠的容器, 不在物理 BFS 范围内): 作叶子加入, 去重物理成员
        for ((rb, rlogic) in RemoteBind.remoteMembersOf(controller)) {
            if (members.none { it.first == rb }) members.add(rb to rlogic)
        }
        return StorageNetwork(controller, members, inputs, outputs)
    }

    /**
     * 以"相对控制器的偏移"打包为 long 键。
     *
     * 偏移被 [RADIUS] 约束在 [-RADIUS, RADIUS], 加 [RADIUS] 归正后每轴仅需 7 位,
     * 三轴共 21 位, 绝不溢出或碰撞 —— 从根上规避绝对坐标取低位在负数下的碰撞。
     */
    private fun key(b: Block, ox: Int, oy: Int, oz: Int): Long {
        val dx = (b.x - ox + RADIUS).toLong()
        val dy = (b.y - oy + RADIUS).toLong()
        val dz = (b.z - oz + RADIUS).toLong()
        return (dx shl 14) or (dy shl 7) or dz
    }
}
