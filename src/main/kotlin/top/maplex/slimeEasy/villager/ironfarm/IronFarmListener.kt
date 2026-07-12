package top.maplex.slimeEasy.villager.ironfarm

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import top.maplex.slimeEasy.registry.VillagerItems
import top.maplex.slimeEasy.util.SlimefunBlockAccess
import top.maplex.slimeEasy.villager.core.VillagerDisplay

/**
 * 胶囊刷铁机的内嵌展示实体点击重定向。
 *
 * 展示村民 / 僵尸 / 铁傀儡站在方块中心, 玩家右键往往先命中实体而非方块, 导致 Slimefun
 * 的"右键方块开菜单"不触发。此处把"点到本刷铁机的展示实体"等同于"右键刷铁机方块": 打开其操作菜单。
 */
class IronFarmListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onDisplayInteract(e: PlayerInteractEntityEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        val entity = e.rightClicked
        if (!VillagerDisplay.isDisplay(entity)) return
        val block = entity.location.block
        if (StorageCacheUtils.getBlock(block.location)?.sfId != VillagerItems.IRON_FARM_ID) return
        e.isCancelled = true
        // 展示实体点击不会经过 Slimefun 方块监听，必须按刷铁机本体位置补做完整访问鉴权。
        if (!SlimefunBlockAccess.canUse(e.player, block)) return
        StorageCacheUtils.getMenu(block.location)?.open(e.player)
    }
}
