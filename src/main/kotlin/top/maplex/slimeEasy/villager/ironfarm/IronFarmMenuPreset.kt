package top.maplex.slimeEasy.villager.ironfarm

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.machine.butcher.ButcherLogic
import top.maplex.slimeEasy.registry.VillagerItems
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.villager.catcher.VillagerCatcher
import top.maplex.slimeEasy.villager.core.VillagerConfig

/**
 * 胶囊刷铁机的操作界面 (Slimefun 原生 [BlockMenu] 预设)。
 *
 * 功能槽 (自由放置、自动持久化): 村民 (满捕捉器) / 僵尸信号 / 食物 / 速度升级 / 输出区。
 * 背景与标签为受保护预设槽 (带 [ChestMenuUtils.getEmptyClickHandler], 防止被玩家取走)。
 * 食物 / 僵尸信号支持货运插入, 输出支持货运抽取。
 */
class IronFarmMenuPreset(id: String, title: String) : BlockMenuPreset(id, title) {

    override fun init() {
        drawBackground(GuiItems.BACKGROUND, backgroundSlots())
        protectedItem(INFO_SLOT, infoTemplate())
        protectedItem(LABEL_VILLAGER, label(Material.GREEN_STAINED_GLASS_PANE, I18n.text("menus.iron-farm-menu-preset-001"), I18n.text("menus.iron-farm-menu-preset-002")))
        protectedItem(LABEL_SIGNAL, label(Material.LIME_STAINED_GLASS_PANE, I18n.text("menus.iron-farm-menu-preset-003"), I18n.text("menus.iron-farm-menu-preset-004")))
        protectedItem(LABEL_FOOD, label(Material.ORANGE_STAINED_GLASS_PANE, I18n.text("menus.iron-farm-menu-preset-005"), I18n.text("menus.iron-farm-menu-preset-006")))
        protectedItem(LABEL_SPEED, label(Material.YELLOW_STAINED_GLASS_PANE, I18n.text("menus.iron-farm-menu-preset-007"), I18n.text("menus.iron-farm-menu-preset-008")))
        protectedItem(LABEL_OUTPUT, label(Material.BLUE_STAINED_GLASS_PANE, I18n.text("menus.iron-farm-menu-preset-009"), I18n.text("menus.iron-farm-menu-preset-010")))
    }

    private fun protectedItem(slot: Int, item: ItemStack) = addItem(slot, item, ChestMenuUtils.getEmptyClickHandler())

    override fun canOpen(block: Block, player: Player): Boolean = true

    override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray =
        if (flow == ItemTransportFlow.WITHDRAW) OUTPUT_SLOTS else intArrayOf(SIGNAL_SLOT, FOOD_SLOT)

    override fun getSlotsAccessedByItemTransport(
        menu: DirtyChestMenu, flow: ItemTransportFlow, item: ItemStack
    ): IntArray = when (flow) {
        ItemTransportFlow.WITHDRAW -> OUTPUT_SLOTS
        ItemTransportFlow.INSERT -> when {
            isSignal(item) -> intArrayOf(SIGNAL_SLOT)
            ButcherLogic.nutritionOf(item.type) > 0 -> intArrayOf(FOOD_SLOT)
            else -> IntArray(0)
        }
    }

    companion object {
        const val INFO_SLOT = 4
        const val VILLAGER_SLOT = 19
        const val SIGNAL_SLOT = 21
        const val FOOD_SLOT = 23
        const val SPEED_SLOT = 25
        val OUTPUT_SLOTS = intArrayOf(38, 39, 40, 41, 42)

        private const val LABEL_VILLAGER = 10
        private const val LABEL_SIGNAL = 12
        private const val LABEL_FOOD = 14
        private const val LABEL_SPEED = 16
        private const val LABEL_OUTPUT = 31

        /** 全部功能槽 (破坏时据此掉落)。 */
        val FUNCTIONAL_SLOTS: IntArray =
            intArrayOf(VILLAGER_SLOT, SIGNAL_SLOT, FOOD_SLOT, SPEED_SLOT) + OUTPUT_SLOTS

        private fun backgroundSlots(): IntArray {
            val occupied = FUNCTIONAL_SLOTS.toHashSet().apply {
                add(INFO_SLOT); add(LABEL_VILLAGER); add(LABEL_SIGNAL)
                add(LABEL_FOOD); add(LABEL_SPEED); add(LABEL_OUTPUT)
            }
            return (0 until 54).filter { it !in occupied }.toIntArray()
        }

        /** 村民槽的满捕捉器 (无 / 非满返回 null)。 */
        fun villagerItem(menu: BlockMenu): ItemStack? =
            menu.getItemInSlot(VILLAGER_SLOT)?.takeIf { VillagerCatcher.isFilled(it) }

        /** 僵尸信号槽是否有僵尸信号。 */
        fun hasSignal(menu: BlockMenu): Boolean = isSignal(menu.getItemInSlot(SIGNAL_SLOT))

        /** 食物槽物品 (仅当为可食用物品且有数量)。 */
        fun foodItem(menu: BlockMenu): ItemStack? =
            menu.getItemInSlot(FOOD_SLOT)?.takeIf { !it.type.isAir && ButcherLogic.nutritionOf(it.type) > 0 && it.amount > 0 }

        /** 速度升级级数 (= 组件堆叠数, 封顶配置上限; 非该组件为 0)。 */
        fun speedLevel(menu: BlockMenu): Int =
            (menu.getItemInSlot(SPEED_SLOT)?.takeIf { isSpeed(it) }?.amount ?: 0)
                .coerceAtMost(VillagerConfig.ironSpeedMaxLevel)

        /** 是否为僵尸信号。 */
        fun isSignal(item: ItemStack?): Boolean =
            item != null && !item.type.isAir && SlimefunItem.getByItem(item)?.id == VillagerItems.ZOMBIE_SIGNAL_ID

        /** 是否为刷铁机速度升级。 */
        fun isSpeed(item: ItemStack?): Boolean =
            item != null && !item.type.isAir && SlimefunItem.getByItem(item)?.id == VillagerItems.IRON_FARM_SPEED_UPGRADE_ID

        /** 刷新信息面板为当前状态。 */
        fun updateInfo(menu: BlockMenu, running: Boolean, reason: String) {
            menu.toInventory().setItem(
                INFO_SLOT,
                GuiItems.named(
                    Material.IRON_INGOT, I18n.text("menus.iron-farm-menu-preset-011"),
                    I18n.text("menus.iron-farm-menu-preset-014", "value0" to (if (running) I18n.text("menus.iron-farm-menu-preset-012") else I18n.text("menus.iron-farm-menu-preset-013"))),
                    "§7$reason",
                    "",
                    I18n.text("menus.iron-farm-menu-preset-015"),
                    I18n.text("menus.iron-farm-menu-preset-016", "value0" to (speedLevel(menu)))
                )
            )
        }

        private fun infoTemplate(): ItemStack = GuiItems.named(
            Material.IRON_INGOT, I18n.text("menus.iron-farm-menu-preset-017"),
            I18n.text("menus.iron-farm-menu-preset-018")
        )

        private fun label(material: Material, name: String, vararg lore: String): ItemStack =
            GuiItems.named(material, name, *lore)
    }
}
