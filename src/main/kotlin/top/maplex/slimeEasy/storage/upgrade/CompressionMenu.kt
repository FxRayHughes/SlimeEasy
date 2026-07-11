package top.maplex.slimeEasy.storage.upgrade

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.core.ItemKey

/** 17 格压制名单、黑白名单模式与不可逆配方开关的配置界面。 */
object CompressionMenu {

    /** 槽位协议：前 17 格固定为过滤名单，不随全局过滤器上限变化。 */
    private val LIST_SLOTS = (0 until 17).toList()
    private const val MARK_SLOT = 18
    private const val MODE_SLOT = 20
    private const val IRREVERSIBLE_SLOT = 22

    /** 打开配置界面；点击玩家背包物品也可直接切换其名单状态。 */
    fun open(block: Block, player: Player) {
        val menu = ChestMenu(I18n.text("menus.compression.title"))
        menu.setEmptySlotsClickable(false)
        menu.addPlayerInventoryClickHandler { _, _, item, _ ->
            if (!item.type.isAir) {
                ItemFilter.COMPRESSION.toggle(block.location, item)
                render(menu, block)
            }
            false
        }
        render(menu, block)
        menu.open(player)
    }

    /** 按容器最新持久化状态重绘全部按钮与名单槽。 */
    private fun render(menu: ChestMenu, block: Block) {
        val filter = ItemFilter.COMPRESSION
        val entries = filter.read(block.location).toList()
        for (slot in 0 until 27) menu.addItem(slot, GuiItems.BACKGROUND) { _, _, _, _ -> false }
        for (slot in LIST_SLOTS) {
            val key = entries.getOrNull(slot)
            if (key != null) {
                menu.addItem(slot, listedIcon(key)) { _, _, _, _ ->
                    filter.remove(block.location, key.template)
                    render(menu, block)
                    false
                }
            }
        }
        menu.addItem(MARK_SLOT, GuiItems.localized(Material.NAME_TAG, "menus.compression.marker")) { player, _, _, _ ->
            val hand = player.inventory.itemInMainHand
            if (!hand.type.isAir) {
                filter.toggle(block.location, hand)
                render(menu, block)
            }
            false
        }
        menu.addItem(MODE_SLOT, modeIcon(filter.mode(block.location))) { _, _, _, _ ->
            val next = if (filter.mode(block.location) == FilterMode.BLACKLIST) FilterMode.WHITELIST else FilterMode.BLACKLIST
            filter.setMode(block.location, next)
            render(menu, block)
            false
        }
        val irreversible = CompressionState.allowsIrreversible(block.location)
        menu.addItem(
            IRREVERSIBLE_SLOT,
            GuiItems.localized(
                if (irreversible) Material.LIME_CONCRETE else Material.RED_CONCRETE,
                if (irreversible) "menus.compression.irreversible.enabled" else "menus.compression.irreversible.disabled"
            )
        ) { _, _, _, _ -> CompressionState.toggleIrreversible(block.location); render(menu, block); false }
    }

    /** 名单物品使用真实物品外观，并覆盖为统一的移除提示 Lore。 */
    private fun listedIcon(key: ItemKey) = key.toDisplay(1).apply {
        editMeta { it.lore(I18n.components("menus.filter.listed-item-lore")) }
    }

    /** 黑白名单模式按钮。 */
    private fun modeIcon(mode: FilterMode) = when (mode) {
        FilterMode.BLACKLIST -> GuiItems.localized(Material.RED_CONCRETE, "menus.filter.mode.blacklist")
        FilterMode.WHITELIST -> GuiItems.localized(Material.LIME_CONCRETE, "menus.filter.mode.whitelist")
    }
}
