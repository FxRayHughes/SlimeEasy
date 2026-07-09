package top.maplex.slimeEasy.storage.drawer

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.block.Block
import org.bukkit.entity.Player
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 虚空升级的销毁列表配置 GUI。
 *
 * - 手持物品点击底部"标记"按钮 → 把该物品加入 / 移出销毁列表 (仅取物品身份, 不消耗);
 * - 点击已列出的物品图标 → 取消其标记。
 *
 * 命中列表的物品在入库前被湮灭, 用于自动清除采矿垃圾等。
 */
object VoidMenu {

    /** 展示销毁列表的槽位 (前三行)。 */
    private val LIST_SLOTS = (0 until 27).toList()
    private const val MARK_SLOT = 31

    fun open(block: Block, player: Player) {
        val menu = ChestMenu("§8虚空销毁列表")
        menu.setEmptySlotsClickable(false)
        render(menu, block)
        menu.open(player)
    }

    private fun render(menu: ChestMenu, block: Block) {
        val marked = VoidFilter.read(block.location).toList()
        for (slot in LIST_SLOTS) {
            val key = marked.getOrNull(slot)
            if (key != null) {
                menu.addItem(slot, key.toDisplay(1)) { p, _, _, _ -> toggleAndRefresh(menu, block, p, key.template); false }
            } else {
                menu.addItem(slot, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            }
        }
        menu.addItem(MARK_SLOT, GuiItems.named(org.bukkit.Material.LAVA_BUCKET, "§c标记手持物品", "§7手持物品点击加入/移出销毁列表")) { p, _, _, _ ->
            val hand = p.inventory.itemInMainHand
            if (!hand.type.isAir) toggleAndRefresh(menu, block, p, hand)
            false
        }
    }

    private fun toggleAndRefresh(menu: ChestMenu, block: Block, player: Player, item: org.bukkit.inventory.ItemStack) {
        VoidFilter.toggle(block.location, item)
        render(menu, block)
    }
}
