package top.maplex.slimeEasy.storage.core

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import top.maplex.slimeEasy.storage.upgrade.FaceConfig
import top.maplex.slimeEasy.storage.upgrade.FilterMode
import top.maplex.slimeEasy.storage.upgrade.ItemFilter

/**
 * 通用"物品名单 + 生效面"配置 GUI (抽取升级 / 输出升级共用)。
 *
 * - 前三行: 名单物品图标 (点击移出); 手持点"标记"按钮或点背包物品加入 / 移出名单;
 * - 底部行: 六个**生效面**开关 (上/下/北/南/东/西, 点击切换该方向是否参与抽取/输出) +
 *   标记按钮 + 黑 / 白名单模式切换按钮。
 *
 * 交互即时持久化 (无保存按钮), 与具体过滤器 ([ItemFilter]) / 面配置 ([FaceConfig]) 解耦。
 */
object FilterMenu {

    /** 展示名单的槽位 (前三行)。 */
    private val LIST_SLOTS = (0 until 27).toList()

    /** 底部行: 六向开关 27..32, 标记 33, 模式 34。 */
    private const val FACE_SLOT_START = 27
    private const val MARK_SLOT = 33
    private const val MODE_SLOT = 34

    fun open(block: Block, player: Player, filter: ItemFilter, faceConfig: FaceConfig, title: String) {
        val menu = ChestMenu(title)
        menu.setEmptySlotsClickable(false)
        // 点击背包内的物品也可加入 / 移出名单 (不必手持)
        menu.addPlayerInventoryClickHandler { _, _, item, _ ->
            if (!item.type.isAir) { filter.toggle(block.location, item); render(menu, block, filter, faceConfig) }
            false
        }
        render(menu, block, filter, faceConfig)
        menu.open(player)
    }

    private fun render(menu: ChestMenu, block: Block, filter: ItemFilter, faceConfig: FaceConfig) {
        val entries = filter.read(block.location).toList()
        for (slot in LIST_SLOTS) {
            val key = entries.getOrNull(slot)
            if (key != null) {
                menu.addItem(slot, icon(key)) { _, _, _, _ ->
                    filter.remove(block.location, key.template); render(menu, block, filter, faceConfig); false
                }
            } else {
                menu.addItem(slot, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            }
        }
        // 六向生效面开关
        for ((i, face) in FaceConfig.ALL.withIndex()) {
            val enabled = faceConfig.isEnabled(block.location, face)
            menu.addItem(FACE_SLOT_START + i, faceIcon(face, enabled)) { _, _, _, _ ->
                faceConfig.toggle(block.location, face); render(menu, block, filter, faceConfig); false
            }
        }
        menu.addItem(MARK_SLOT, GuiItems.named(Material.NAME_TAG, "§e标记物品",
            "§7手持物品点击, 或直接点击背包内物品", "§7加入 / 移出名单")) { p, _, _, _ ->
            val hand = p.inventory.itemInMainHand
            if (!hand.type.isAir) { filter.toggle(block.location, hand); render(menu, block, filter, faceConfig) }
            false
        }
        menu.addItem(MODE_SLOT, modeIcon(filter.mode(block.location))) { _, _, _, _ ->
            val next = if (filter.mode(block.location) == FilterMode.BLACKLIST) FilterMode.WHITELIST else FilterMode.BLACKLIST
            filter.setMode(block.location, next); render(menu, block, filter, faceConfig); false
        }
    }

    /** 名单项图标: 点击移出提示。 */
    private fun icon(key: ItemKey) = key.toDisplay(1).apply {
        editMeta { it.lore(listOf(Component.text("§8点击移出名单"))) }
    }

    /** 生效面开关图标。 */
    private fun faceIcon(face: BlockFace, enabled: Boolean) =
        if (enabled) GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a${faceName(face)} 面: 启用", "§8点击禁用")
        else GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, "§7${faceName(face)} 面: 禁用", "§8点击启用")

    private fun faceName(face: BlockFace) = when (face) {
        BlockFace.UP -> "上"; BlockFace.DOWN -> "下"
        BlockFace.NORTH -> "北"; BlockFace.SOUTH -> "南"
        BlockFace.EAST -> "东"; BlockFace.WEST -> "西"
        else -> face.name
    }

    /** 模式切换按钮图标。 */
    private fun modeIcon(mode: FilterMode) = when (mode) {
        FilterMode.BLACKLIST -> GuiItems.named(Material.RED_CONCRETE, "§c黑名单模式",
            "§7名单内的物品 §c不处理§7, 其余放行", "§8点击切换为白名单")
        FilterMode.WHITELIST -> GuiItems.named(Material.LIME_CONCRETE, "§a白名单模式",
            "§7仅处理名单内的物品, 其余拦截", "§8点击切换为黑名单")
    }
}
