package top.maplex.slimeEasy.storage.drawer

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Location
import org.bukkit.block.Block
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import java.util.concurrent.ConcurrentHashMap

/**
 * 经验磁铁抽屉登记处。
 *
 * 同时装有"经验存储 + 磁铁"的抽屉每 tick 把自身位置登记于此; [MagnetOrbListener]
 * 在经验球生成瞬间据此就近查找可吸取的抽屉, 直接拦截入库 —— 抢在原版把球吸向
 * 玩家之前, 从根上解决"磁铁吸不到经验球"。
 *
 * 登记项含时间戳, 过期 (对应抽屉已卸载 / 拆除, 不再刷新) 自动失效。
 */
object MagnetRegistry {

    /** 位置键 → 最近一次登记的时刻 (ms)。 */
    private val active = ConcurrentHashMap<String, Long>()

    /**
     * 登记项有效期 (ms)。
     *
     * 必须显著长于 Slimefun ticker 间隔 (默认约 1 秒), 否则登记会在两次 tick 之间
     * 过期, 导致经验球生成时 [nearest] 查不到而漏吸。取 3 秒留足余量。
     */
    private const val TTL_MS = 3000L

    private fun key(l: Location) = "${l.world?.name}:${l.blockX}:${l.blockY}:${l.blockZ}"

    /** 抽屉 tick 时登记 (仅经验+磁铁双升级时调用)。 */
    fun mark(block: Block) { active[key(block.location)] = System.currentTimeMillis() }

    /** 移除登记 (抽屉拆除时)。 */
    fun unmark(block: Block) { active.remove(key(block.location)) }

    /**
     * 就近查找一个可吸取经验球的经验磁铁抽屉方块。
     *
     * @param at 经验球所在位置
     * @param radius 搜索切比雪夫半径 (格)
     * @return 命中的抽屉方块; 无则 null
     */
    fun nearest(at: Location, radius: Int): Block? {
        val world = at.world ?: return null
        val now = System.currentTimeMillis()
        val bx = at.blockX; val by = at.blockY; val bz = at.blockZ
        var best: Block? = null
        var bestDist = Int.MAX_VALUE
        val it = active.entries.iterator()
        while (it.hasNext()) {
            val (k, ts) = it.next()
            if (now - ts > TTL_MS) { it.remove(); continue } // 过期清理
            val parts = k.split(":")
            if (parts.size < 4 || parts[0] != world.name) continue
            val x = parts[1].toIntOrNull() ?: continue
            val y = parts[2].toIntOrNull() ?: continue
            val z = parts[3].toIntOrNull() ?: continue
            val d = maxOf(abs(x - bx), abs(y - by), abs(z - bz))
            if (d > radius || d >= bestDist) continue
            // 二次校验: 方块仍存在且仍是经验磁铁抽屉
            val block = world.getBlockAt(x, y, z)
            if (!StorageCacheUtils.hasBlock(block.location)) continue
            val up = UpgradeStore.resolve(block.location)
            if (!up.hasExpStorage || !up.hasMagnet) continue
            best = block; bestDist = d
        }
        return best
    }

    private fun abs(v: Int) = if (v < 0) -v else v
}
