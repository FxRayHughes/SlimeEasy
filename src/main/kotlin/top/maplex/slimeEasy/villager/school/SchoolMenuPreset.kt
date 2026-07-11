package top.maplex.slimeEasy.villager.school

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.GuiItems

/**
 * 村民小学的操作界面 (Slimefun 原生 [BlockMenu] 预设)。
 *
 * 输入槽放入傻子村民 (满捕捉器), 计时到点后转化为无职业村民放入输出槽。输入 / 输出为
 * 自由放置且自动持久化的功能槽; 背景与标签为受保护预设槽。
 */
class SchoolMenuPreset(id: String, title: String) : BlockMenuPreset(id, title) {

    override fun init() {
        drawBackground(GuiItems.BACKGROUND, backgroundSlots())
        protectedItem(INFO_SLOT, infoTemplate())
        protectedItem(LABEL_INPUT, label(Material.BROWN_STAINED_GLASS_PANE, I18n.text("menus.school-menu-preset-001"), I18n.text("menus.school-menu-preset-002")))
        protectedItem(LABEL_OUTPUT, label(Material.GREEN_STAINED_GLASS_PANE, I18n.text("menus.school-menu-preset-003"), I18n.text("menus.school-menu-preset-004")))
    }

    private fun protectedItem(slot: Int, item: ItemStack) = addItem(slot, item, ChestMenuUtils.getEmptyClickHandler())

    override fun canOpen(block: Block, player: Player): Boolean = true

    override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray =
        if (flow == ItemTransportFlow.WITHDRAW) intArrayOf(OUTPUT_SLOT) else intArrayOf(INPUT_SLOT)

    override fun getSlotsAccessedByItemTransport(
        menu: DirtyChestMenu, flow: ItemTransportFlow, item: ItemStack
    ): IntArray = when (flow) {
        ItemTransportFlow.WITHDRAW -> intArrayOf(OUTPUT_SLOT)
        ItemTransportFlow.INSERT -> intArrayOf(INPUT_SLOT)
    }

    companion object {
        const val INFO_SLOT = 4
        const val INPUT_SLOT = 20
        const val OUTPUT_SLOT = 24

        private const val LABEL_INPUT = 11
        private const val LABEL_OUTPUT = 15

        val FUNCTIONAL_SLOTS = intArrayOf(INPUT_SLOT, OUTPUT_SLOT)

        private fun backgroundSlots(): IntArray {
            val occupied = FUNCTIONAL_SLOTS.toHashSet().apply {
                add(INFO_SLOT); add(LABEL_INPUT); add(LABEL_OUTPUT)
            }
            return (0 until 54).filter { it !in occupied }.toIntArray()
        }

        /** 刷新信息面板。 */
        fun updateInfo(menu: BlockMenu, line: String) {
            menu.toInventory().setItem(
                INFO_SLOT,
                GuiItems.named(
                    Material.LECTERN, I18n.text("menus.school-menu-preset-005"),
                    "§7$line",
                    "",
                    I18n.text("menus.school-menu-preset-006"),
                    I18n.text("menus.school-menu-preset-007")
                )
            )
        }

        private fun infoTemplate(): ItemStack = GuiItems.named(
            Material.LECTERN, I18n.text("menus.school-menu-preset-008"),
            I18n.text("menus.school-menu-preset-009")
        )

        private fun label(material: Material, name: String, vararg lore: String): ItemStack =
            GuiItems.named(material, name, *lore)
    }
}
