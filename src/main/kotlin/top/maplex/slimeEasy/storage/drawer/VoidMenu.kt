package top.maplex.slimeEasy.storage.drawer

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 虚空升级的销毁列表配置 GUI (带保留数量)。
 *
 * - 手持物品点击底部"标记"按钮, 或直接点击背包内的物品 → 把该物品加入 / 移出销毁列表
 *   (加入时默认保留 0);
 * - 点击已列出的物品图标调整其**保留数量**:
 *   左键 +64 / 右键 -64 / 潜行左键 +1 / 潜行右键 -1; 保留为 0 时潜行右键移除。
 *
 * 命中列表的物品在容器内封顶到其保留数量, 超出的入库部分被湮灭 —— 保留 0 即全毁
 * (清除采矿垃圾), 保留 N 即把该物品限量囤积。
 */
object VoidMenu {

    /** 展示销毁列表的槽位 (前三行)。 */
    private val LIST_SLOTS = (0 until 27).toList()
    private const val MARK_SLOT = 31

    fun open(block: Block, player: Player) {
        val menu = ChestMenu("§8虚空销毁列表")
        menu.setEmptySlotsClickable(false)
        // 点击背包内的物品也可加入 / 移出销毁列表 (不必手持)
        menu.addPlayerInventoryClickHandler { _, _, item, _ ->
            if (!item.type.isAir) { VoidFilter.toggle(block.location, item); render(menu, block) }
            false
        }
        render(menu, block)
        menu.open(player)
    }

    private fun render(menu: ChestMenu, block: Block) {
        val entries = VoidFilter.read(block.location).entries.toList()
        for (slot in LIST_SLOTS) {
            val entry = entries.getOrNull(slot)
            if (entry != null) {
                val (key, keep) = entry
                menu.addItem(slot, icon(key, keep)) { _, _, _, action ->
                    adjust(menu, block, key.template, action.isRightClicked, action.isShiftClicked); false
                }
            } else {
                menu.addItem(slot, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            }
        }
        menu.addItem(MARK_SLOT, GuiItems.named(Material.LAVA_BUCKET, "§c标记物品",
            "§7手持物品点击, 或直接点击背包内物品", "§7加入 / 移出销毁列表 (默认保留 §e0 §7全毁)")) { p, _, _, _ ->
            val hand = p.inventory.itemInMainHand
            if (!hand.type.isAir) { VoidFilter.toggle(block.location, hand); render(menu, block) }
            false
        }
    }

    /** 构造列表项图标: 显示保留数量与调整提示。 */
    private fun icon(key: ItemKey, keep: Int): ItemStack =
        key.toDisplay(1).apply {
            editMeta {
                it.lore(listOf(
                    Component.text("§7保留: §e$keep §7个 §8(超出湮灭)"),
                    Component.text("§8左键 +64 / 右键 -64"),
                    Component.text("§8潜行左键 +1 / 潜行右键 -1"),
                    Component.text("§8保留为 0 时潜行右键移除")
                ))
            }
        }

    /** 依点击调整某物品保留数量; 保留为 0 时潜行右键移除。 */
    private fun adjust(menu: ChestMenu, block: Block, item: ItemStack, right: Boolean, shift: Boolean) {
        val loc = block.location
        // 保留已为 0 且潜行右键 → 移出列表
        if (shift && right && (VoidFilter.keep(loc, item) ?: return) <= 0) {
            VoidFilter.remove(loc, item)
        } else {
            val delta = when {
                shift && right -> -1
                shift -> 1
                right -> -64
                else -> 64
            }
            VoidFilter.addKeep(loc, item, delta)
        }
        render(menu, block)
    }
}
