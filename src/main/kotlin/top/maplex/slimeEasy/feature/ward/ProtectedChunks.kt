package top.maplex.slimeEasy.feature.ward

import org.bukkit.Chunk
import top.maplex.slimeEasy.config.SEConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * 苦力怕驱逐方块的受保护区块登记表 (内存 TTL, 自愈式)。
 *
 * 设计要点: 驱逐方块的 ticker 每次运行都调用 [refresh] 续期其周围区块的保护期;
 * 生成拦截监听器通过 [isProtected] 判定生成点所在区块。方块被破坏后不再续期,
 * TTL 到期即自动失效; 服务器重启后 ticker 重新运行会重建全部登记 ——
 * 因此无需持久化, 也无需破坏事件钩子。
 *
 * 全部访问预期在主线程 (Slimefun 同步 ticker 与 CreatureSpawnEvent 均在主线程),
 * 仍用 [ConcurrentHashMap] 以防御性隔离潜在的异步生成来源。
 */
object ProtectedChunks {

    /** 区块保护有效期 (毫秒)。远大于 ticker 周期 (默认约 0.5s), 避免续期间隙抖动。实时读取配置。 */
    private val ttlMillis: Long get() = SEConfig.creeperWardProtectionTtlMillis

    /** [exitDirection] 单方向最大外探区块数, 防止大片相连保护区导致过度扫描。实时读取配置。 */
    private val maxScanChunks: Int get() = SEConfig.creeperWardMaxScanChunks

    /** 区块唯一键 -> 保护到期时间戳 (System.currentTimeMillis)。 */
    private val expiry = ConcurrentHashMap<Long, Long>()

    /**
     * 续期以 [center] 为中心、切比雪夫半径 [radius] 内的全部区块保护期。
     *
     * radius = 1 即中心区块 + 周围一圈, 共 3x3 个区块。
     */
    fun refresh(center: Chunk, radius: Int) {
        val worldId = center.world.uid
        val deadline = System.currentTimeMillis() + ttlMillis
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                expiry[chunkKey(worldId.mostSignificantBits, center.x + dx, center.z + dz)] = deadline
            }
        }
    }

    /**
     * 判定区块当前是否受保护; 惰性清除已过期条目。
     */
    fun isProtected(chunk: Chunk): Boolean =
        isProtectedAt(chunk.world.uid.mostSignificantBits, chunk.x, chunk.z)

    /**
     * 计算把某受保护区块内的实体推出"全部受保护区块并集"的最近水平方向。
     *
     * 从该区块沿 ±X / ±Z 四个方向逐格外探, 找到各方向上最近的非保护区块,
     * 返回所需步数最少的方向增量 (dx, dz)。因判定只依赖全局并集、与调用方是
     * 哪一个驱逐方块无关, 多个方块对同一位置得到一致方向 —— 从根本上消除
     * 重叠区域内相互对推、把苦力怕挤在中间的问题。
     *
     * @return 出口方向的区块增量; 若该区块本身未受保护则返回 null (无需推)
     */
    fun exitDirection(worldHigh: Long, chunkX: Int, chunkZ: Int): Pair<Int, Int>? {
        if (!isProtectedAt(worldHigh, chunkX, chunkZ)) return null

        // 固定顺序保证平局时所有方块选取一致方向 (+X, -X, +Z, -Z)
        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        var best: Pair<Int, Int>? = null
        var bestSteps = Int.MAX_VALUE
        for ((dx, dz) in dirs) {
            for (step in 1..maxScanChunks) {
                if (!isProtectedAt(worldHigh, chunkX + dx * step, chunkZ + dz * step)) {
                    if (step < bestSteps) {
                        bestSteps = step
                        best = dx to dz
                    }
                    break
                }
            }
        }
        // 极端情形 (半径内全被保护) 兜底给一个默认方向
        return best ?: (1 to 0)
    }

    /** 按世界高位与区块坐标查询保护状态, 惰性清除过期条目。 */
    private fun isProtectedAt(worldHigh: Long, x: Int, z: Int): Boolean {
        val key = chunkKey(worldHigh, x, z)
        val deadline = expiry[key] ?: return false
        if (System.currentTimeMillis() >= deadline) {
            expiry.remove(key)
            return false
        }
        return true
    }

    /**
     * 将世界标识高位与区块 XZ 坐标打包为单个 long 键。
     *
     * 组合世界高位 (取 UUID 最高 32 位) 与 x、z 各 16 位, 在单机多世界场景下
     * 冲突概率可忽略, 且避免了每次生成事件的字符串拼接开销。
     */
    private fun chunkKey(worldHigh: Long, x: Int, z: Int): Long {
        val world = (worldHigh and 0xFFFFFFFFL) shl 32
        val xz = ((x.toLong() and 0xFFFF) shl 16) or (z.toLong() and 0xFFFF)
        return world or xz
    }
}
