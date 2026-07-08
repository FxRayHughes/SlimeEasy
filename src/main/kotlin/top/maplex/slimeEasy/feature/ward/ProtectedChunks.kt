package top.maplex.slimeEasy.feature.ward

import org.bukkit.Chunk
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

    /** 区块保护有效期 (毫秒)。远大于 ticker 周期 (默认约 0.5s), 避免续期间隙抖动。 */
    private const val TTL_MILLIS = 8_000L

    /** 区块唯一键 -> 保护到期时间戳 (System.currentTimeMillis)。 */
    private val expiry = ConcurrentHashMap<Long, Long>()

    /**
     * 续期以 [center] 为中心、切比雪夫半径 [radius] 内的全部区块保护期。
     *
     * radius = 1 即中心区块 + 周围一圈, 共 3x3 个区块。
     */
    fun refresh(center: Chunk, radius: Int) {
        val worldId = center.world.uid
        val deadline = System.currentTimeMillis() + TTL_MILLIS
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                expiry[chunkKey(worldId.mostSignificantBits, center.x + dx, center.z + dz)] = deadline
            }
        }
    }

    /**
     * 判定区块当前是否受保护; 惰性清除已过期条目。
     */
    fun isProtected(chunk: Chunk): Boolean {
        val key = chunkKey(chunk.world.uid.mostSignificantBits, chunk.x, chunk.z)
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
