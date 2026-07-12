package top.maplex.slimeEasy.storage.disk

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.core.QuantityFormat
import top.maplex.slimeEasy.storage.core.StorageChangeBus
import top.maplex.slimeEasy.util.locationKey

/**
 * 磁盘管理器独立界面。
 *
 * 六个协议槽与雕纹书架物理槽一一对应：手持磁盘点击空槽安装，空光标点击已安装磁盘
 * 拆下。界面物品只是状态镜像，真实磁盘始终保存在书架方块库存中。
 */
object DiskManagerMenu {

    /** UI 槽位顺序必须与雕纹书架原生 0..5 书槽保持一致。 */
    private val DISK_SLOTS = intArrayOf(10, 11, 12, 14, 15, 16)
    private const val STATUS_SLOT = 22
    private val openViews = java.util.concurrent.ConcurrentHashMap<String, MutableSet<View>>()

    init {
        StorageChangeBus.subscribe { block ->
            openViews[block.locationKey()]?.toList()?.forEach { it.render() }
        }
    }

    fun open(manager: DiskManager, block: Block, player: Player) {
        View(manager, block, player).apply { menu.open(player) }
    }

    private class View(
        val manager: DiskManager,
        val block: Block,
        val player: Player
    ) {
        val menu = ChestMenu(I18n.text("menus.disk-manager.title"))

        init {
            menu.setEmptySlotsClickable(false)
            menu.setPlayerInventoryClickable(true)
            for (slot in 0 until 27) menu.addItem(slot, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            openViews.computeIfAbsent(block.locationKey()) {
                java.util.concurrent.ConcurrentHashMap.newKeySet()
            }.add(this)
            menu.addMenuCloseHandler { openViews[block.locationKey()]?.remove(this) }
            render()
        }

        fun render() {
            val disks = DiskManagerStorage.mounted(block).associateBy { it.slot }
            for ((shelfSlot, menuSlot) in DISK_SLOTS.withIndex()) {
                val disk = disks[shelfSlot]
                if (disk == null) {
                    menu.replaceExistingItem(menuSlot, GuiItems.localized(
                        Material.BOOK, "menus.disk-manager.empty-slot", "slot" to shelfSlot + 1
                    ))
                } else {
                    menu.replaceExistingItem(menuSlot, DiskStore.display(disk.item, disk.tier, disk.storage))
                }
                menu.addMenuClickHandler(menuSlot) { p, _, _, _ ->
                    handleSlot(p, shelfSlot)
                    false
                }
            }

            val mounted = disks.values
            val usedEighthBytes = mounted.sumOf { DiskStore.usedEighthBytes(it.tier, it.storage) }
            val capacity = mounted.sumOf { it.tier.capacityBytes }
            val types = mounted.sumOf { it.storage.typeCount }
            val items = mounted.sumOf { DiskStore.totalItems(it.storage) }
            menu.replaceExistingItem(STATUS_SLOT, GuiItems.localized(
                Material.COMPARATOR,
                "menus.disk-manager.status",
                "disks" to mounted.size,
                "maxDisks" to DiskManagerStorage.SLOT_COUNT,
                "types" to types,
                "items" to QuantityFormat.grouped(items),
                "used" to DiskStore.formatBytes(usedEighthBytes),
                "capacity" to QuantityFormat.grouped(capacity)
            ))
            menu.addMenuClickHandler(STATUS_SLOT) { _, _, _, _ -> false }
        }

        private fun handleSlot(player: Player, shelfSlot: Int) {
            // 已打开界面可能跨越方块被破坏的时刻；先校验 Slimefun 方块身份，避免把磁盘写进空气位置。
            if (StorageCacheUtils.getBlock(block.location)?.sfId != manager.id) {
                player.closeInventory()
                return
            }
            val current = DiskManagerStorage.mounted(block).firstOrNull { it.slot == shelfSlot }
            val cursor = player.itemOnCursor
            if (current != null) {
                if (cursor != null && !cursor.type.isAir) {
                    player.sendMessage(I18n.text("messages.disk-manager.clear-cursor"))
                    return
                }
                DiskManagerStorage.setSlot(block, shelfSlot, null)
                player.setItemOnCursor(DiskStore.write(current.item, current.tier, current.storage))
                manager.rebuildFromDisks(block)
                render()
                return
            }

            val tier = DiskTier.of(cursor)
            if (tier == null) {
                player.sendMessage(I18n.text("messages.disk-manager.disk-required"))
                return
            }
            val installed = cursor.clone().apply { amount = 1 }
            val normalized = DiskStore.write(installed, tier, DiskStore.read(installed))
            DiskManagerStorage.setSlot(block, shelfSlot, normalized)
            if (cursor.amount <= 1) player.setItemOnCursor(null)
            else player.setItemOnCursor(cursor.clone().apply { amount -= 1 })
            manager.rebuildFromDisks(block)
            render()
        }
    }
}
