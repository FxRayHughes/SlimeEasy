package top.maplex.slimeEasy.storage.core

import top.maplex.slimeEasy.config.I18n
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * 存储 GUI 通用图标工厂 (抽屉 / 箱子共用)。
 *
 * 统一背景、按钮、占位图标的外观, 避免各 GUI 重复构造。
 */
object GuiItems {

    private val legacySerializer = LegacyComponentSerializer.legacySection()

    /** 灰色玻璃板背景。 */
    val BACKGROUND: ItemStack = named(Material.GRAY_STAINED_GLASS_PANE, " ")

    /** 升级槽占位图标 (空槽提示)。 */
    val UPGRADE_PLACEHOLDER: ItemStack =
        localized(Material.LIME_STAINED_GLASS_PANE, "menus.common.upgrade-placeholder")

    /** 虚空配置按钮。 */
    val VOID_CONFIG: ItemStack =
        localized(Material.BLACK_CONCRETE, "menus.common.void-config")

    /** 抽取过滤配置按钮。 */
    val EXTRACT_CONFIG: ItemStack =
        localized(Material.HOPPER, "menus.common.extract-config")

    /** 输出过滤配置按钮。 */
    val OUTPUT_CONFIG: ItemStack =
        localized(Material.DROPPER, "menus.common.output-config")

    /** 压制过滤与不可逆配方配置按钮。 */
    val COMPRESSION_CONFIG: ItemStack =
        localized(Material.PISTON, "menus.common.compression-config")

    /** 上一页按钮。 */
    val PREV_PAGE: ItemStack = localized(Material.ARROW, "menus.common.previous-page")

    /** 下一页按钮。 */
    val NEXT_PAGE: ItemStack = localized(Material.ARROW, "menus.common.next-page")

    /** 升级插件入口按钮。 */
    val UPGRADE_ENTRY: ItemStack = localized(Material.ANVIL, "menus.common.upgrade-entry")

    /** 构造带名称与 lore 的图标，并关闭 ItemMeta 默认继承的斜体样式。 */
    fun named(material: Material, name: String, vararg lore: String): ItemStack =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(legacySerializer.deserialize(name).decoration(TextDecoration.ITALIC, false))
                if (lore.isNotEmpty()) {
                    meta.lore(lore.map { legacySerializer.deserialize(it).decoration(TextDecoration.ITALIC, false) })
                }
            }
        }

    /** 以层级语言节点 `{key}.name/lore` 构造 UI 图标。 */
    fun localized(material: Material, key: String, vararg placeholders: Pair<String, Any?>): ItemStack =
        ItemStack(material).apply {
            val display = I18n.componentDisplay(key, *placeholders)
            editMeta { meta ->
                meta.displayName(display.name)
                if (display.lore.isNotEmpty()) meta.lore(display.lore)
            }
        }
}
