package top.maplex.slimeEasy.storage.drawer

import org.bukkit.entity.ExperienceOrb
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import top.maplex.slimeEasy.storage.drawer.Drawer.Companion.MAGNET_RADIUS

/**
 * 经验球生成拦截监听器。
 *
 * 经验球一生成即检查附近是否有经验磁铁抽屉 ([MagnetRegistry]); 命中则把经验直接
 * 存入抽屉并取消该球的生成 —— 抢在原版把球吸向玩家之前完成吸取。
 *
 * 仅拦截确实落入某抽屉吸附范围内的球; 其余球照常生成, 不影响正常拾取。经验容器主动
 * 取出并发放的球带有 [ExperiencePayout] 标记，必须跳过，否则会在玩家拾取前被吸回。
 */
class MagnetOrbListener : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onOrbSpawn(e: EntitySpawnEvent) {
        val orb = e.entity as? ExperienceOrb ?: return
        if (ExperiencePayout.isPayoutOrb(orb)) return
        val block = MagnetRegistry.nearest(orb.location, MAGNET_RADIUS.toInt()) ?: return
        DrawerExp.addAbsorbed(block, orb.experience.toLong())
        e.isCancelled = true
    }
}
