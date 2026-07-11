package top.maplex.slimeEasy.storage.drawer

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.core.QuantityFormat
import top.maplex.slimeEasy.storage.core.StorageChangeBus
import top.maplex.slimeEasy.storage.core.UpgradeMenu
import top.maplex.slimeEasy.util.locationKey

/**
 * 抽屉主界面 (空手潜行右键打开)。
 *
 * 布局: 中央展示**当前所存物品** (数量写入 lore), 点击取出 (左键取一组 / 右键取一);
 * 侧边一个**升级组件按钮**打开 [UpgradeMenu] 管理升级。经验模式走 [ExpMenu], 不用本页。
 *
 * 打开中的界面订阅 [StorageChangeBus] 实时刷新 (货运 / 磁铁 / 别处改动即时反映)。
 */
object DrawerMenu {

    private const val ITEM_SLOT = 11
    private const val UPGRADE_SLOT = 15

    /** 位置键 → 打开中的界面 (实时刷新用)。 */
    private val openViews = java.util.concurrent.ConcurrentHashMap<String, MutableSet<View>>()

    init {
        StorageChangeBus.subscribe { block ->
            openViews[block.locationKey()]?.toList()?.forEach { it.render() }
        }
    }

    fun open(drawer: Drawer, block: Block, player: Player) {
        View(drawer, block).apply { menu.open(player) }
    }

    private class View(val drawer: Drawer, val block: Block) {
        val menu = ChestMenu("§9抽屉")

        init {
            menu.setEmptySlotsClickable(false)
            for (i in 0 until 27) menu.addItem(i, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            openViews.computeIfAbsent(block.locationKey()) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(this)
            menu.addMenuCloseHandler { openViews[block.locationKey()]?.remove(this) }
            render()
        }

        fun render() {
            val storage = drawer.storageAt(block)
            val stored = storage.entries().firstOrNull()
            if (stored == null) {
                menu.replaceExistingItem(ITEM_SLOT,
                    GuiItems.named(Material.BARREL, "§7抽屉为空", "§7右键抽屉前的展示框放入物品"))
                menu.addMenuClickHandler(ITEM_SLOT) { _, _, _, _ -> false }
            } else {
                val (k, count) = stored
                // 总容量 = 抽屉槽数 × 单槽容量 (原版堆叠 × 堆叠升级倍率)
                val total = storage.maxSlots.toLong() * storage.cellCapacity(k)
                val icon = k.toDisplay(minOf(count, k.vanillaMaxStack.toLong()).toInt()).apply {
                    editMeta {
                        it.lore(listOf(
                            Component.text("§7数量: §e${QuantityFormat.grouped(count)} §7/ §f${QuantityFormat.grouped(total)}"),
                            Component.text("§8[${QuantityFormat.bar(count, total)}§8] §f${QuantityFormat.percent(count, total)}"),
                            Component.text("§7左键取一组 / 右键取一个")
                        ))
                    }
                }
                menu.replaceExistingItem(ITEM_SLOT, icon)
                menu.addMenuClickHandler(ITEM_SLOT) { p, _, _, action ->
                    if (action.isRightClicked) DrawerInteract.withdrawOne(drawer, block, p)
                    else DrawerInteract.withdrawStack(drawer, block, p)
                    render(); false
                }
            }
            menu.replaceExistingItem(UPGRADE_SLOT, GuiItems.UPGRADE_ENTRY)
            menu.addMenuClickHandler(UPGRADE_SLOT) { p, _, _, _ ->
                UpgradeMenu.open(drawer, block, p, "§9抽屉升级"); false
            }
        }
    }
}
