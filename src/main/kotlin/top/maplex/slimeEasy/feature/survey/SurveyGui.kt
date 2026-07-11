package top.maplex.slimeEasy.feature.survey

import top.maplex.slimeEasy.config.I18n
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 勘察结果的箱子界面展示。
 *
 * 将某一层级的扫描结果以只读 [ChestMenu] 呈现: 每种矿石一个图标 (原矿方块外观),
 * 名称为中文矿名, 数量写入堆叠数 (封顶 64) 与 lore (精确值)。
 *
 * 只读保证: [ChestMenu.setEmptySlotsClickable] 置 false 拦截空槽, 禁用玩家背包联动点击,
 * 且**每个图标都注册返回 false 的点击处理器**取消物品移动 —— 缺此处理器时 ChestMenu 默认
 * 允许取走有物品的槽位, 故图标必须显式拦截, 玩家方无法从界面取出任何矿石。
 */
object SurveyGui {

    /** 单页最大槽位 (6 行); 矿石种类通常十余种, 不会超出。 */
    private const val MAX_SLOTS = 54

    /** 每行槽位数。 */
    private const val ROW = 9

    /**
     * 构建并向 [player] 打开某层级的勘察结果界面。
     *
     * @param block 勘察中心方块 (标题展示坐标)
     * @param tier  当前选中层级
     */
    fun open(player: Player, block: Block, tier: SurveyTier) {
        val side = tier.range * 2 + 1
        val counts = SurveyScanner.scan(block, tier.range)
        val total = counts.values.sum()
        // 标题披露总量与三种燃料用量估算, 方便对照材料规划
        val title = "§b${tier.label} §7(${side}×${side}) ${SurveyFormat.fuelSummary(total)}"

        val menu = ChestMenu(title)
        menu.setEmptySlotsClickable(false)
        menu.setPlayerInventoryClickable(false)

        if (counts.isEmpty()) {
            menu.setSize(ROW)
            // 返回 false 取消一切点击, 防止取走占位图标
            menu.addItem(ROW / 2, emptyIcon()) { _, _, _, _ -> false }
        } else {
            fillOres(menu, counts)
        }
        menu.open(player)
    }

    /** 按数量降序把矿石图标铺入菜单 (超出容量的种类丢弃, 现实不会触发)。 */
    private fun fillOres(menu: ChestMenu, counts: Map<Material, Int>) {
        val sorted = counts.entries.sortedByDescending { it.value }.take(MAX_SLOTS)
        val rows = (sorted.size + ROW - 1) / ROW
        menu.setSize(rows * ROW)
        sorted.forEachIndexed { slot, (material, count) ->
            // 返回 false 取消点击, 图标只读, 玩家无法取走矿石
            menu.addItem(slot, oreIcon(material, count)) { _, _, _, _ -> false }
        }
    }

    /** 构建单个矿石图标: 原矿外观 + 中文名 + 数量。 */
    private fun oreIcon(material: Material, count: Int): ItemStack {
        val icon = ItemStack(material, count.coerceIn(1, 64))
        val meta = icon.itemMeta
        if (meta != null) {
            meta.setDisplayName("§f${OreNames.of(material)}")
            meta.lore = listOf(I18n.text("menus.survey.result.count", "count" to count))
            icon.itemMeta = meta
        }
        return icon
    }

    /** 空结果占位图标。 */
    private fun emptyIcon(): ItemStack {
        val icon = ItemStack(Material.BARRIER)
        val meta = icon.itemMeta
        if (meta != null) {
            meta.setDisplayName(I18n.text("menus.survey.result.empty"))
            icon.itemMeta = meta
        }
        return icon
    }
}
