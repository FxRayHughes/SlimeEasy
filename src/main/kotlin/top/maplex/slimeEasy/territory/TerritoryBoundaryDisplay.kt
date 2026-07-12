package top.maplex.slimeEasy.territory

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import top.maplex.slimeEasy.registry.Items
import kotlin.math.abs

/**
 * 手持领地旗帜时，仅向该玩家绘制其可管理领地的附近外边界。
 * 只画覆盖并集的外沿可避免重叠旗帜产生内部重复线，也能把粒子数量限制在可控范围内。
 */
internal object TerritoryBoundaryDisplay {
    /** 每10 tick刷新可兼顾移动跟随与网络开销；任务由 Bukkit 在插件禁用时自动取消。 */
    private const val REFRESH_TICKS = 10L
    /** 只显示玩家周围5区块内的边界，防止大型领地一次发送数千个粒子。 */
    private const val DISPLAY_RADIUS_CHUNKS = 5
    /** 边界每0.5格一个粒子，使轮廓在远距离和高速移动时仍保持连续。 */
    private const val PARTICLE_STEP = 0.5
    private val PARTICLE_STYLE = Particle.DustOptions(Color.fromRGB(255, 196, 48), 1.1f)

    /** 启动全服共享刷新任务；注册阶段只能调用一次。 */
    fun start(plugin: JavaPlugin) {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            plugin.server.onlinePlayers.filter(::isHoldingTerritoryFlag).forEach(::drawFor)
        }, REFRESH_TICKS, REFRESH_TICKS)
    }

    private fun isHoldingTerritoryFlag(player: Player): Boolean =
        isTerritoryFlag(player.inventory.itemInMainHand) || isTerritoryFlag(player.inventory.itemInOffHand)

    /** 按 Slimefun ID 识别旗帜，避免普通白旗或伪造显示名触发边界显示。 */
    private fun isTerritoryFlag(item: ItemStack?): Boolean =
        item != null && !item.type.isAir && SlimefunItem.getByItem(item)?.id == Items.TERRITORY_FLAG_ID

    private fun drawFor(player: Player) {
        val playerChunk = TerritoryChunk.of(player.location)
        val worldId = player.world.uid
        val height = player.location.y + 0.25
        TerritoryService.all().asSequence()
            .filter { it.core.world == worldId && TerritoryService.canManage(player, it, TerritoryManagement.CHUNKS) }
            .forEach { territory ->
                territory.chunks.asSequence()
                    .filter { abs(it.x - playerChunk.x) <= DISPLAY_RADIUS_CHUNKS }
                    .filter { abs(it.z - playerChunk.z) <= DISPLAY_RADIUS_CHUNKS }
                    .forEach { drawChunkEdges(player, territory.chunks, it, height) }
            }
    }

    /** 只有相邻区块不在同一认领并集时才绘制该边，重叠覆盖因此不会产生额外描边。 */
    private fun drawChunkEdges(
        player: Player,
        claimed: Set<TerritoryChunk>,
        chunk: TerritoryChunk,
        height: Double
    ) {
        val minimumX = chunk.x shl 4
        val minimumZ = chunk.z shl 4
        if (TerritoryChunk(chunk.world, chunk.x, chunk.z - 1) !in claimed) {
            drawLine(player, minimumX, minimumZ, true, height)
        }
        if (TerritoryChunk(chunk.world, chunk.x, chunk.z + 1) !in claimed) {
            drawLine(player, minimumX, minimumZ + 16, true, height)
        }
        if (TerritoryChunk(chunk.world, chunk.x - 1, chunk.z) !in claimed) {
            drawLine(player, minimumX, minimumZ, false, height)
        }
        if (TerritoryChunk(chunk.world, chunk.x + 1, chunk.z) !in claimed) {
            drawLine(player, minimumX + 16, minimumZ, false, height)
        }
    }

    /** horizontal=true 沿X轴绘制，否则沿Z轴绘制；粒子只发送给当前手持旗帜的玩家。 */
    private fun drawLine(player: Player, originX: Int, originZ: Int, horizontal: Boolean, height: Double) {
        var offset = 0.0
        while (offset <= 16.0) {
            val x = originX + if (horizontal) offset else 0.0
            val z = originZ + if (horizontal) 0.0 else offset
            player.spawnParticle(Particle.DUST, x, height, z, 1, PARTICLE_STYLE)
            offset += PARTICLE_STEP
        }
    }
}
