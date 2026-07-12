package top.maplex.slimeEasy.feature.goggles

import eu.decentsoftware.holograms.api.DHAPI
import eu.decentsoftware.holograms.api.holograms.Hologram
import eu.decentsoftware.holograms.api.holograms.enums.EnumFlag
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** DecentHolograms 2.10.1 的临时、单玩家可见发包实现。 */
internal class DecentHologramsBackend : PrivateHologramBackend {

    private val instanceId = UUID.randomUUID().toString().replace("-", "").take(8)
    private val sequence = AtomicLong()

    override fun create(
        viewer: Player,
        lastLineLocation: Location,
        lines: List<String>
    ): PrivateHologramBackend.Handle {
        val name = "se_g_${instanceId}_${sequence.incrementAndGet()}"

        // DHAPI.createHologram 会在返回前 showAll；直接构造才能在首次发包前关闭默认可见性。
        val hologram = Hologram(name, lastLineLocation, false).apply {
            setDefaultVisibleState(false)
            // 底部原点先让 DH 按每行真实高度向上布局，再以实际末行坐标完成精确校准。
            setDownOrigin(true)
            addFlags(EnumFlag.DISABLE_ACTIONS)
        }
        DHAPI.setHologramLines(hologram, lines)
        alignLastLine(hologram, lastLineLocation)
        hologram.setShowPlayer(viewer)
        hologram.show(viewer, 0)

        return DecentHandle(hologram, lastLineLocation.clone())
    }

    /** 临时全息图只销毁内存对象，不产生或删除持久化配置文件。 */
    private class DecentHandle(
        private val hologram: Hologram,
        private val lastLineLocation: Location
    ) : PrivateHologramBackend.Handle {
        override fun update(lines: List<String>) {
            DHAPI.setHologramLines(hologram, lines)
            alignLastLine(hologram, lastLineLocation)
        }

        override fun destroy() {
            hologram.destroy()
        }
    }

    companion object {
        /**
         * 使用 DH 已计算的末行真实坐标校准整个页面；不同内容类型、行高或行数都不会影响末行锚点。
         * 仅在坐标确有偏差时重排，避免每次能源数值刷新都额外发送无变化的移动包。
         */
        private fun alignLastLine(hologram: Hologram, target: Location) {
            val page = hologram.getPage(0) ?: return
            if (page.size() == 0) return
            val current = page.getLine(page.size() - 1)?.location ?: return
            val deltaX = target.x - current.x
            val deltaY = target.y - current.y
            val deltaZ = target.z - current.z
            if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= LOCATION_EPSILON_SQUARED) return
            hologram.location = hologram.location.clone().add(deltaX, deltaY, deltaZ)
            hologram.realignLines()
        }

        /** 小于微米级方块距离的浮点误差不应触发实体移动包。 */
        private const val LOCATION_EPSILON_SQUARED = 1.0E-12
    }
}
