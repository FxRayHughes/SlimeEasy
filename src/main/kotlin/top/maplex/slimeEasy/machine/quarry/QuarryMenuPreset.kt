package top.maplex.slimeEasy.machine.quarry

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
 * 采石场的升级界面 (Slimefun 原生 [BlockMenu] 预设)。
 *
 * 采石场无圆石缓冲, 界面仅一个**效率升级槽** ([UPGRADE_SLOT]) 与信息面板 ([INFO_SLOT])。
 * 升级槽为自由放置、自动持久化的 inventory 槽: 放入档位组件即提速, 取出即回落基础速率;
 * 破坏机器时由 [Quarry] 的破坏处理器散落该槽物品。档位仅由槽内物品身份决定 (见 [QuarryTier])。
 *
 * 3 行 (27 格) 布局: 顶行居中信息面板, 中行居中升级槽 (左侧一格为文字标签), 余为背景。
 */
class QuarryMenuPreset(id: String, title: String) : BlockMenuPreset(id, title) {

    override fun init() {
        // 背景铺满除信息面板 / 标签 / 升级槽外的全部槽 (这些成为受保护预设槽);
        // 升级槽不在 init 占用, 自动成为可自由放置且持久化的 inventory 槽。
        val reserved = intArrayOf(INFO_SLOT, LABEL_UPGRADE, UPGRADE_SLOT)
        drawBackground(GuiItems.BACKGROUND, (0 until 27).filter { it !in reserved }.toIntArray())
        protectedItem(INFO_SLOT, infoTemplate())
        protectedItem(
            LABEL_UPGRADE,
            GuiItems.named(
                Material.YELLOW_STAINED_GLASS_PANE, "§e效率升级 →",
                "§7放入 §f采石场效率升级 I~V§7:",
                "§7I=1 · II=6 · III=12 · IV=32 · V=64 §7个/0.5s",
                "§7空槽时基础速率 §f1 个/秒§7。"
            )
        )
    }

    /** 放置一个受保护 (不可被玩家拿走 / 放入) 的预设图标。 */
    private fun protectedItem(slot: Int, item: ItemStack) =
        addItem(slot, item, ChestMenuUtils.getEmptyClickHandler())

    /** 任何玩家均可打开 (领地无关; 采石场不改动世界)。 */
    override fun canOpen(block: Block, player: Player): Boolean = true

    /** 货运接入: 仅 INSERT 可及升级槽 (供物流 / 漏斗自动送入升级组件), 无 WITHDRAW。 */
    override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray =
        if (flow == ItemTransportFlow.INSERT) intArrayOf(UPGRADE_SLOT) else IntArray(0)

    /** 货运精细路由: 仅效率升级组件放行进入升级槽, 其余拒收。 */
    override fun getSlotsAccessedByItemTransport(
        menu: DirtyChestMenu, flow: ItemTransportFlow, item: ItemStack
    ): IntArray =
        if (flow == ItemTransportFlow.INSERT && QuarryTier.fromItem(item) != null)
            intArrayOf(UPGRADE_SLOT) else IntArray(0)

    companion object {
        /** 信息面板槽 (顶行居中)。 */
        const val INFO_SLOT = 4

        /** 效率升级槽 (中行居中, 自由放置 + 持久化)。 */
        const val UPGRADE_SLOT = 13

        /** 升级槽左侧的文字标签槽。 */
        private const val LABEL_UPGRADE = 12

        /** 全部功能槽 (破坏时据此散落)。 */
        val FUNCTIONAL_SLOTS: IntArray = intArrayOf(UPGRADE_SLOT)

        /** 升级槽内的效率档位 (空 / 非升级组件返回 null, 即基础速率)。 */
        fun tierOf(menu: BlockMenu): QuarryTier? = QuarryTier.fromItem(menu.getItemInSlot(UPGRADE_SLOT))

        /**
         * 刷新信息面板为当前状态。
         *
         * 直更底层库存 (`setItem`) 而非 `replaceExistingItem`: 后者恒 `markDirty` 会使
         * 信息槽每 tick 存盘; 信息面板是预设槽 (不持久化, 重载由 [init] 重绘), 无需落盘。
         */
        fun updateInfo(menu: BlockMenu, attached: Boolean, producing: Boolean, tier: QuarryTier?) {
            val rate = if (tier != null) "§f${tier.perOperation} §7个/0.5s (效率 §f${tier.name}§7)"
            else "§f1 §7个/秒 (无升级)"
            val status = when {
                !attached -> "§c未附着圆石"
                !producing -> "§c圆石未同时相邻岩浆与水"
                else -> "§a生产中"
            }
            menu.toInventory().setItem(
                INFO_SLOT,
                GuiItems.named(
                    Material.OBSERVER, "§b采石场",
                    "§7状态: $status",
                    "§7速率: $rate",
                    "",
                    "§7脸朝向的 §f圆石 §7需同时相邻 §c岩浆 §7与 §9水§7,",
                    "§7满足即持续产出圆石 (不破坏该圆石)。",
                    "§7产物输出到本机周围的 §f容器 / 抽屉 / 翻页箱§7。"
                )
            )
        }

        /** 信息面板静态模板 (无 block 上下文时的初始占位, 打开 / tick 后由 [updateInfo] 刷新)。 */
        private fun infoTemplate(): ItemStack = GuiItems.named(
            Material.OBSERVER, "§b采石场",
            "§7脸朝向 §f圆石§7, 且该圆石同时相邻 §c岩浆 §7与 §9水 §7时产出圆石。",
            "§7产物输出到周围的 §f容器 / 抽屉 / 翻页箱§7。"
        )
    }
}
