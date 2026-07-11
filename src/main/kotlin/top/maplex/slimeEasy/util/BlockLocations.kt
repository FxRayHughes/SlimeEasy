package top.maplex.slimeEasy.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block

/** 生成仅用于进程内 Map 的稳定方块位置键。 */
fun Block.locationKey(): String = location.locationKey()

/** 生成仅用于进程内 Map 的稳定方块位置键。 */
fun Location.locationKey(): String = "${world?.name}:$blockX:$blockY:$blockZ"

/**
 * 方块位置的持久化编解码器。
 *
 * 该格式已用于远程终端、远程升级和远程成员索引，必须保持向后兼容。
 */
object BlockLocationCodec {

    fun encode(block: Block): String =
        "${block.world.name};${block.x};${block.y};${block.z}"

    fun decode(raw: String): Block? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != PART_COUNT) return null
        val world = Bukkit.getWorld(parts[0]) ?: return null
        val x = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        val z = parts[3].toIntOrNull() ?: return null
        return world.getBlockAt(x, y, z)
    }

    private const val SEPARATOR = ";"
    private const val PART_COUNT = 4
}
