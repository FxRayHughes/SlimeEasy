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
        location: Location,
        lines: List<String>
    ): PrivateHologramBackend.Handle {
        val name = "se_g_${instanceId}_${sequence.incrementAndGet()}"

        // DHAPI.createHologram 会在返回前 showAll；直接构造才能在首次发包前关闭默认可见性。
        val hologram = Hologram(name, location, false).apply {
            setDefaultVisibleState(false)
            addFlags(EnumFlag.DISABLE_ACTIONS)
        }
        DHAPI.setHologramLines(hologram, lines)
        hologram.setShowPlayer(viewer)
        hologram.show(viewer, 0)

        return DecentHandle(hologram)
    }

    /** 临时全息图只销毁内存对象，不产生或删除持久化配置文件。 */
    private class DecentHandle(private val hologram: Hologram) : PrivateHologramBackend.Handle {
        override fun update(lines: List<String>) {
            DHAPI.setHologramLines(hologram, lines)
        }

        override fun destroy() {
            hologram.destroy()
        }
    }
}
