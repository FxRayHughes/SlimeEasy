package top.maplex.slimeEasy.storage.drawer

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.StorageDrops
import top.maplex.slimeEasy.storage.network.NetworkRegistry
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore

/**
 * 抽屉的放置 / 破坏 / 右键处理器工厂。
 *
 * 放置时面向玩家生成展示框。破坏时把库存内容与已装升级作为真实物品散落
 * ([StorageDrops.spill]), 方块本体由 Slimefun 掉落 —— handler **不再**手动加入
 * 方块物品 (Slimefun 会在 handler 之后追加 `getDrops()`, 否则掉两个), 内容与升级
 * 直接散落而非随抽屉物品搬走。右键木桶本体打开操作界面 (与点击展示框等效)。
 */
object DrawerHandlers {

    /** 放置: 面向玩家生成展示框。 */
    fun place(drawer: Drawer): BlockPlaceHandler = object : BlockPlaceHandler(false) {
        override fun onPlayerPlace(e: BlockPlaceEvent) {
            val block = e.block
            val face = e.player.facing.oppositeFace
            DrawerDisplay.spawn(block, face)
            val storage = drawer.storageAt(block)
            drawer.refreshCapacity(block, storage, null)
            val first = storage.entries().firstOrNull()
            DrawerDisplay.update(block, first?.first, first?.second ?: 0L)
            NetworkRegistry.invalidateAll() // 拓扑变更: 让相邻网络纳入本抽屉
        }
    }

    /** 破坏: 移除展示实体, 内容 + 升级散落, 清理缓存。 */
    fun break_(drawer: Drawer): BlockBreakHandler = object : BlockBreakHandler(false, false) {
        override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
            val block = e.block
            StorageDrops.spill(block, drawer.storageAt(block), drops)
            DrawerDisplay.remove(block)
            MagnetRegistry.unmark(block)
            drawer.clearCache(block)
            NetworkRegistry.invalidateAll() // 拓扑变更: 移除本抽屉后重建网络
        }
    }

    /**
     * 右键木桶本体: 打开操作界面。
     *
     * 展示框只覆盖朝外一面的图标区域, 点到木桶其它部位原本无反应。本 handler
     * 让整块木桶都可右键开界面, 与点击展示框行为对齐。经验模式开 [ExpMenu],
     * 否则开 [DrawerMenu]。
     */
    fun use(drawer: Drawer): BlockUseHandler = BlockUseHandler { e: PlayerRightClickEvent ->
        val block = e.clickedBlock.orElse(null) ?: return@BlockUseHandler
        e.cancel() // 阻止原版木桶界面
        if (UpgradeStore.resolve(block.location).hasExpStorage) {
            ExpMenu.open(block, e.player)
        } else {
            DrawerMenu.open(drawer, block, e.player)
        }
    }
}
