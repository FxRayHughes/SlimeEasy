package top.maplex.slimeEasy.machine.quarry

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
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.storage.core.GuiItems

/**
 * 采石场的升级界面 (Slimefun 原生 [BlockMenu] 预设)。
 *
 * 采石场无产物缓冲, 界面提供效率升级槽 ([UPGRADE_SLOT])、产物升级槽
 * ([OUTPUT_UPGRADE_SLOT]) 与信息面板 ([INFO_SLOT])。两类升级可同时安装并自动持久化;
 * 破坏机器时由 [Quarry] 的破坏处理器散落槽内物品。
 *
 * 3 行 (27 格) 布局: 顶行居中信息面板, 中行居中升级槽 (左侧一格为文字标签), 余为背景。
 */
class QuarryMenuPreset(id: String, title: String) : BlockMenuPreset(id, title) {

    override fun init() {
        // 背景铺满除信息面板 / 标签 / 升级槽外的全部槽 (这些成为受保护预设槽);
        // 升级槽不在 init 占用, 自动成为可自由放置且持久化的 inventory 槽。
        val reserved = intArrayOf(INFO_SLOT, LABEL_UPGRADE, UPGRADE_SLOT, LABEL_OUTPUT, OUTPUT_UPGRADE_SLOT)
        drawBackground(GuiItems.BACKGROUND, (0 until 27).filter { it !in reserved }.toIntArray())
        protectedItem(INFO_SLOT, infoTemplate())
        protectedItem(
            LABEL_UPGRADE,
            GuiItems.named(
                Material.YELLOW_STAINED_GLASS_PANE, I18n.text("menus.quarry-menu-preset-001"),
                I18n.text("menus.quarry-menu-preset-002"),
                I18n.text("menus.quarry-menu-preset-003"),
                I18n.text("menus.quarry-menu-preset-004")
            )
        )
        protectedItem(
            LABEL_OUTPUT,
            GuiItems.named(
                Material.ORANGE_STAINED_GLASS_PANE, I18n.text("menus.quarry-menu-preset-005"),
                I18n.text("menus.quarry-menu-preset-006"),
                I18n.text("menus.quarry-menu-preset-007")
            )
        )
    }

    /** 放置一个受保护 (不可被玩家拿走 / 放入) 的预设图标。 */
    private fun protectedItem(slot: Int, item: ItemStack) =
        addItem(slot, item, ChestMenuUtils.getEmptyClickHandler())

    /** 任何玩家均可打开 (领地无关; 采石场不改动世界)。 */
    override fun canOpen(block: Block, player: Player): Boolean = true

    /** 货运接入: INSERT 可及两个升级槽, 无 WITHDRAW。 */
    override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray =
        if (flow == ItemTransportFlow.INSERT) FUNCTIONAL_SLOTS else IntArray(0)

    /** 货运精细路由: 效率与产物升级分别进入对应槽位, 其余拒收。 */
    override fun getSlotsAccessedByItemTransport(
        menu: DirtyChestMenu, flow: ItemTransportFlow, item: ItemStack
    ): IntArray = if (flow != ItemTransportFlow.INSERT) IntArray(0) else when {
        QuarryTier.fromItem(item) != null -> intArrayOf(UPGRADE_SLOT)
        QuarryOutput.isUpgrade(item) -> intArrayOf(OUTPUT_UPGRADE_SLOT)
        else -> IntArray(0)
    }

    companion object {
        /** 信息面板槽 (顶行居中)。 */
        const val INFO_SLOT = 4

        /** 效率升级槽 (中行居中, 自由放置 + 持久化)。 */
        const val UPGRADE_SLOT = 13

        /** 产物升级槽 (中行右侧, 自由放置 + 持久化)。 */
        const val OUTPUT_UPGRADE_SLOT = 15

        /** 升级槽左侧的文字标签槽。 */
        private const val LABEL_UPGRADE = 12

        /** 产物升级槽左侧的文字标签槽。 */
        private const val LABEL_OUTPUT = 14

        /** 全部功能槽 (破坏时据此散落)。 */
        val FUNCTIONAL_SLOTS: IntArray = intArrayOf(UPGRADE_SLOT, OUTPUT_UPGRADE_SLOT)

        /** 升级槽内的效率档位 (空 / 非升级组件返回 null, 即基础速率)。 */
        fun tierOf(menu: BlockMenu): QuarryTier? = QuarryTier.fromItem(menu.getItemInSlot(UPGRADE_SLOT))

        /** 当前产物类型; 空槽或非法物品回落为圆石。 */
        fun outputOf(menu: BlockMenu): QuarryOutput = QuarryOutput.fromItem(menu.getItemInSlot(OUTPUT_UPGRADE_SLOT))

        /**
         * 刷新信息面板为当前状态。
         *
         * 直更底层库存 (`setItem`) 而非 `replaceExistingItem`: 后者恒 `markDirty` 会使
         * 信息槽每 tick 存盘; 信息面板是预设槽 (不持久化, 重载由 [init] 重绘), 无需落盘。
         */
        fun updateInfo(
            menu: BlockMenu,
            attached: Boolean,
            producing: Boolean,
            tier: QuarryTier?,
            output: QuarryOutput
        ) {
            val rate = if (tier != null) I18n.text("menus.quarry-menu-preset-008", "value0" to (tier.perOperation), "value1" to (tier.name))
            else I18n.text("menus.quarry-menu-preset-009", "value0" to (SEConfig.quarryBaseOutput), "value1" to (SEConfig.quarryBaseIntervalTicks))
            val status = when {
                !attached -> I18n.text("menus.quarry-menu-preset-010")
                !producing -> I18n.text("menus.quarry-menu-preset-011")
                else -> I18n.text("menus.quarry-menu-preset-012")
            }
            menu.toInventory().setItem(
                INFO_SLOT,
                GuiItems.named(
                    Material.OBSERVER, I18n.text("menus.quarry-menu-preset-013"),
                    I18n.text("menus.quarry-menu-preset-014", "value0" to (status)),
                    I18n.text("menus.quarry-menu-preset-015", "value0" to (rate)),
                    I18n.text("menus.quarry-menu-preset-016", "value0" to (output.displayName)),
                    "",
                    I18n.text("menus.quarry-menu-preset-017"),
                    I18n.text("menus.quarry-menu-preset-018"),
                    I18n.text("menus.quarry-menu-preset-019")
                )
            )
        }

        /** 信息面板静态模板 (无 block 上下文时的初始占位, 打开 / tick 后由 [updateInfo] 刷新)。 */
        private fun infoTemplate(): ItemStack = GuiItems.named(
            Material.OBSERVER, I18n.text("menus.quarry-menu-preset-020"),
            I18n.text("menus.quarry-menu-preset-021"),
            I18n.text("menus.quarry-menu-preset-022"),
            I18n.text("menus.quarry-menu-preset-023")
        )
    }
}
