package top.maplex.slimeEasy.storage.disk

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.network.NetworkRegistry

/** 磁盘管理器的放置、破坏与访问处理器。 */
object DiskManagerHandlers {

    /** 新方块必须从空书架状态开始，防止方块物品携带的容器数据污染磁盘槽。 */
    fun place(manager: DiskManager): BlockPlaceHandler = object : BlockPlaceHandler(false) {
        override fun onPlayerPlace(e: BlockPlaceEvent) {
            DiskManagerStorage.removeAll(e.block)
            manager.rebuildFromDisks(e.block)
            NetworkRegistry.invalidateAll()
        }
    }

    /** 破坏时只掉落磁盘书本，不把盘内虚拟物品散落，从而保留可移动磁盘语义。 */
    fun break_(manager: DiskManager): BlockBreakHandler = object : BlockBreakHandler(false, false) {
        override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
            drops.addAll(DiskManagerStorage.removeAll(e.block))
            manager.clearCache(e.block)
            NetworkRegistry.invalidateAll()
        }
    }

    /** 右键始终接管原版放书行为，所有安装和拆卸必须经过带校验的独立 UI。 */
    fun use(manager: DiskManager): BlockUseHandler = BlockUseHandler { e: PlayerRightClickEvent ->
        val block = e.clickedBlock.orElse(null) ?: return@BlockUseHandler
        e.cancel()
        if (!Slimefun.getPermissionsService().hasPermission(e.player, manager)) {
            Slimefun.getLocalization().sendMessage(e.player, "messages.no-permission", true)
            return@BlockUseHandler
        }
        DiskManagerMenu.open(manager, block, e.player)
    }
}
