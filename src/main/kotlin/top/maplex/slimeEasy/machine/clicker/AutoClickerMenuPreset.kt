package top.maplex.slimeEasy.machine.clicker

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.core.UpgradeHost
import top.maplex.slimeEasy.storage.core.UpgradeMenu

/**
 * 自动点击器的操作界面 (Slimefun 原生 [BlockMenu] 预设)。
 *
 * 一格物品槽 ([ITEM_SLOT]) + 三项按块设置按钮 (左键开关 / 右键开关 / 间隔 −·显示·+),
 * 设置存于 BlockData ([AutoClickerState])。
 *
 * 按钮的图标与点击处理**必须在 [newInstance] 内**用 `replaceExistingItem` + `addMenuClickHandler` 设置,
 * 且这些槽**不能**在 [init] 里被 `drawBackground` 占用 —— 否则 `BlockMenuPreset.clone` 会在 newInstance 之后
 * 用预设的空点击处理器把按钮 handler 覆盖掉 (点击失效)。参见 Slimefun `AbstractCargoNode` 信道按钮。
 */
class AutoClickerMenuPreset(id: String, title: String, private val host: UpgradeHost) : BlockMenuPreset(id, title) {

    override fun init() {
        // 只铺装饰性背景: 排除物品槽、静态标签与全部按钮槽 (按钮在 newInstance 内绘制/挂钩)
        val reserved = intArrayOf(ITEM_SLOT, LABEL_ITEM, LEFT_TOGGLE, RIGHT_TOGGLE, INTERVAL_DOWN, INTERVAL_INFO, INTERVAL_UP, UPGRADE_ENTRY)
        drawBackground(GuiItems.BACKGROUND, (0 until 27).filter { it !in reserved }.toIntArray())
        addItem(
            LABEL_ITEM,
            GuiItems.localized(Material.NAME_TAG, "menus.auto-clicker.held-item"),
            ChestMenuUtils.getEmptyClickHandler()
        )
    }

    override fun canOpen(block: Block, player: Player): Boolean = true

    override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray = intArrayOf(ITEM_SLOT)

    override fun getSlotsAccessedByItemTransport(
        menu: DirtyChestMenu, flow: ItemTransportFlow, item: ItemStack
    ): IntArray = intArrayOf(ITEM_SLOT)

    // clone() 会以 Location 重载调用 newInstance; 两个重载都指向 setup 以防万一
    override fun newInstance(menu: BlockMenu, b: Block) = setup(menu, b)
    override fun newInstance(menu: BlockMenu, l: Location) = setup(menu, l.block)

    // ChestMenu.MenuClickHandler 为 Slimefun 遗留但唯一的点击回调 API, 显式抑制弃用告警
    @Suppress("DEPRECATION")
    private fun setup(menu: BlockMenu, block: Block) {
        paint(menu, block)
        menu.addMenuClickHandler(LEFT_TOGGLE, ChestMenu.MenuClickHandler { _, _, _, _ ->
            AutoClickerState.toggleLeft(block); paint(menu, block); false
        })
        menu.addMenuClickHandler(RIGHT_TOGGLE, ChestMenu.MenuClickHandler { _, _, _, _ ->
            AutoClickerState.toggleRight(block); paint(menu, block); false
        })
        menu.addMenuClickHandler(INTERVAL_DOWN, ChestMenu.MenuClickHandler { _, _, _, action ->
            AutoClickerState.addInterval(block, -stepOf(action)); paint(menu, block); false
        })
        menu.addMenuClickHandler(INTERVAL_UP, ChestMenu.MenuClickHandler { _, _, _, action ->
            AutoClickerState.addInterval(block, stepOf(action)); paint(menu, block); false
        })
        menu.addMenuClickHandler(INTERVAL_INFO, ChestMenuUtils.getEmptyClickHandler())
        // 升级入口: 打开通用升级 GUI (点击器仅接受抽取升级, 由 host.rejectUpgradeChange 约束)
        menu.replaceExistingItem(UPGRADE_ENTRY, GuiItems.UPGRADE_ENTRY)
        menu.addMenuClickHandler(UPGRADE_ENTRY, ChestMenu.MenuClickHandler { p, _, _, _ ->
            UpgradeMenu.open(host, block, p, I18n.text("menus.auto-clicker.upgrade-title")); false
        })
    }

    /** 按块状态绘制按钮图标 (replaceExistingItem, 与官方交互按钮一致)。 */
    private fun paint(menu: BlockMenu, block: Block) {
        val left = AutoClickerState.leftEnabled(block)
        val right = AutoClickerState.rightEnabled(block)
        val interval = AutoClickerState.interval(block)
        menu.replaceExistingItem(LEFT_TOGGLE, toggleIcon(I18n.text("names.click-mode.left"), left))
        menu.replaceExistingItem(RIGHT_TOGGLE, toggleIcon(I18n.text("names.click-mode.right"), right))
        menu.replaceExistingItem(INTERVAL_DOWN, GuiItems.localized(
            Material.RED_STAINED_GLASS_PANE, "menus.auto-clicker.interval.decrease",
            "coarseStep" to AutoClickerState.COARSE_STEP, "fineStep" to AutoClickerState.STEP
        ))
        menu.replaceExistingItem(INTERVAL_INFO, GuiItems.localized(
            Material.CLOCK, "menus.auto-clicker.interval.info",
            "interval" to "%.2f".format(interval),
            "seconds" to "%.2f".format(interval * 0.5),
            "min" to AutoClickerState.MIN_INTERVAL,
            "max" to AutoClickerState.MAX_INTERVAL.toInt()
        ))
        menu.replaceExistingItem(INTERVAL_UP, GuiItems.localized(
            Material.LIME_STAINED_GLASS_PANE, "menus.auto-clicker.interval.increase",
            "coarseStep" to AutoClickerState.COARSE_STEP, "fineStep" to AutoClickerState.STEP
        ))
    }

    /** Shift 点击用微调步进 (0.05), 普通点击用粗调步进 (0.25)。 */
    @Suppress("DEPRECATION")
    private fun stepOf(action: ClickAction): Double =
        if (action.isShiftClicked) AutoClickerState.STEP else AutoClickerState.COARSE_STEP

    private fun toggleIcon(name: String, on: Boolean): ItemStack =
        if (on) GuiItems.localized(Material.LIME_DYE, "menus.auto-clicker.toggle.enabled", "mode" to name)
        else GuiItems.localized(Material.GRAY_DYE, "menus.auto-clicker.toggle.disabled", "mode" to name)

    companion object {
        /** 唯一功能槽: 点击时手持的物品 (一格容积)。 */
        const val ITEM_SLOT = 13

        private const val LABEL_ITEM = 4
        const val LEFT_TOGGLE = 10
        const val RIGHT_TOGGLE = 16
        const val INTERVAL_DOWN = 21
        const val INTERVAL_INFO = 22
        const val INTERVAL_UP = 23
        const val UPGRADE_ENTRY = 8
    }
}
