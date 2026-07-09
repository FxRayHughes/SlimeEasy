package top.maplex.slimeEasy.storage.box

import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.StorageDrops
import top.maplex.slimeEasy.storage.network.NetworkRegistry

/**
 * 翻页箱的放置 / 破坏 / 右键处理器工厂。
 *
 * 放置 / 破坏时使网络缓存失效, 以便相邻控制器的网络即时纳入 / 移除本箱 (相邻免
 * 连接器自动组网)。破坏时把库存内容与已装升级作为真实物品散落 (由 [StorageDrops.spill]),
 * 方块本体由 Slimefun 掉落 —— handler **不再**手动加入方块物品 (Slimefun 破坏流程会在
 * handler 之后追加 `getDrops()`, 若这里再加会掉两个)。右键打开 [PagedBoxMenu]。
 */
object BoxHandlers {

    /** 放置: 使网络缓存失效, 让相邻网络纳入本箱。 */
    fun place(): BlockPlaceHandler = object : BlockPlaceHandler(false) {
        override fun onPlayerPlace(e: BlockPlaceEvent) = NetworkRegistry.invalidateAll()
    }

    fun break_(box: PagedBox): BlockBreakHandler = object : BlockBreakHandler(false, false) {
        override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
            StorageDrops.spill(e.block, box.storageAt(e.block), drops)
            box.clearCache(e.block)
            NetworkRegistry.invalidateAll() // 拓扑变更: 移除本箱后重建网络
        }
    }

    fun use(box: PagedBox): BlockUseHandler = BlockUseHandler { e: PlayerRightClickEvent ->
        val block = e.clickedBlock.orElse(null) ?: return@BlockUseHandler
        e.cancel() // 阻止原版容器界面 / 放置行为
        // 装了经验升级: 打开经验操作页 (存入 / 按等级取出), 而非普通分页
        if (top.maplex.slimeEasy.storage.upgrade.UpgradeStore.resolve(block.location).hasExpStorage) {
            top.maplex.slimeEasy.storage.drawer.ExpMenu.open(block, e.player)
        } else {
            PagedBoxMenu.open(box, block, e.player)
        }
    }
}
