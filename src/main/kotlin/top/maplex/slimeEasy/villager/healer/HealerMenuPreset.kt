package top.maplex.slimeEasy.villager.healer

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
 * 村民治愈机的操作界面 (Slimefun 原生 [BlockMenu] 预设)。
 *
 * 输入槽放入僵尸村民 (满捕捉器)、金苹果槽放入普通金苹果, 计时到点后治愈为普通村民放入输出槽
 * (消耗一个金苹果)。输入 / 金苹果 / 输出为自由放置且自动持久化的功能槽; 背景与标签为受保护预设槽。
 */
class HealerMenuPreset(id: String, title: String) : BlockMenuPreset(id, title) {

    override fun init() {
        drawBackground(GuiItems.BACKGROUND, backgroundSlots())
        protectedItem(INFO_SLOT, infoTemplate())
        protectedItem(LABEL_INPUT, label(Material.BROWN_STAINED_GLASS_PANE, "§6输入 ↓", "§7放入僵尸村民 (满捕捉器)"))
        protectedItem(LABEL_APPLE, label(Material.YELLOW_STAINED_GLASS_PANE, "§e金苹果 ↓", "§7放入普通金苹果, 每次治愈消耗一个"))
        protectedItem(LABEL_OUTPUT, label(Material.GREEN_STAINED_GLASS_PANE, "§a输出 ↓", "§7治愈后的普通村民"))
    }

    private fun protectedItem(slot: Int, item: ItemStack) = addItem(slot, item, ChestMenuUtils.getEmptyClickHandler())

    override fun canOpen(block: Block, player: Player): Boolean = true

    override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray =
        if (flow == ItemTransportFlow.WITHDRAW) intArrayOf(OUTPUT_SLOT) else intArrayOf(INPUT_SLOT, APPLE_SLOT)

    override fun getSlotsAccessedByItemTransport(
        menu: DirtyChestMenu, flow: ItemTransportFlow, item: ItemStack
    ): IntArray = when (flow) {
        ItemTransportFlow.WITHDRAW -> intArrayOf(OUTPUT_SLOT)
        ItemTransportFlow.INSERT ->
            if (item.type == Material.GOLDEN_APPLE) intArrayOf(APPLE_SLOT) else intArrayOf(INPUT_SLOT)
    }

    companion object {
        const val INFO_SLOT = 4
        const val INPUT_SLOT = 20
        const val APPLE_SLOT = 22
        const val OUTPUT_SLOT = 24

        private const val LABEL_INPUT = 11
        private const val LABEL_APPLE = 13
        private const val LABEL_OUTPUT = 15

        val FUNCTIONAL_SLOTS = intArrayOf(INPUT_SLOT, APPLE_SLOT, OUTPUT_SLOT)

        private fun backgroundSlots(): IntArray {
            val occupied = FUNCTIONAL_SLOTS.toHashSet().apply {
                add(INFO_SLOT); add(LABEL_INPUT); add(LABEL_APPLE); add(LABEL_OUTPUT)
            }
            return (0 until 54).filter { it !in occupied }.toIntArray()
        }

        /** 金苹果槽是否有普通金苹果 (非附魔金苹果)。 */
        fun hasApple(menu: BlockMenu): Boolean =
            menu.getItemInSlot(APPLE_SLOT)?.let { it.type == Material.GOLDEN_APPLE && it.amount > 0 } ?: false

        /** 刷新信息面板。 */
        fun updateInfo(menu: BlockMenu, line: String) {
            menu.toInventory().setItem(
                INFO_SLOT,
                GuiItems.named(
                    Material.GOLDEN_APPLE, "§6村民治愈机",
                    "§7$line",
                    "",
                    "§7放入僵尸村民 (满捕捉器) + 普通金苹果,",
                    "§7到点后治愈为普通村民 (保留职业)。"
                )
            )
        }

        private fun infoTemplate(): ItemStack = GuiItems.named(
            Material.GOLDEN_APPLE, "§6村民治愈机",
            "§7放入僵尸村民 (满捕捉器) 与普通金苹果开始治愈。"
        )

        private fun label(material: Material, name: String, vararg lore: String): ItemStack =
            GuiItems.named(material, name, *lore)
    }
}
