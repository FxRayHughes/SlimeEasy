package top.maplex.slimeEasy.storage.core

import top.maplex.slimeEasy.config.I18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * 存储 GUI 通用图标工厂 (抽屉 / 箱子共用)。
 *
 * 统一背景、按钮、占位图标的外观, 避免各 GUI 重复构造。
 */
object GuiItems {

    /** 灰色玻璃板背景。 */
    val BACKGROUND: ItemStack = named(Material.GRAY_STAINED_GLASS_PANE, " ")

    /** 升级槽占位图标 (空槽提示)。 */
    val UPGRADE_PLACEHOLDER: ItemStack =
        named(Material.LIME_STAINED_GLASS_PANE, I18n.text("menus.gui-items-001"), I18n.text("menus.gui-items-002"))

    /** 虚空配置按钮。 */
    val VOID_CONFIG: ItemStack =
        named(Material.BLACK_CONCRETE, I18n.text("menus.gui-items-003"), I18n.text("menus.gui-items-004"))

    /** 抽取过滤配置按钮。 */
    val EXTRACT_CONFIG: ItemStack =
        named(Material.HOPPER, I18n.text("menus.gui-items-005"), I18n.text("menus.gui-items-006"))

    /** 输出过滤配置按钮。 */
    val OUTPUT_CONFIG: ItemStack =
        named(Material.DROPPER, I18n.text("menus.gui-items-007"), I18n.text("menus.gui-items-008"))

    /** 上一页按钮。 */
    val PREV_PAGE: ItemStack = named(Material.ARROW, I18n.text("menus.gui-items-009"))

    /** 下一页按钮。 */
    val NEXT_PAGE: ItemStack = named(Material.ARROW, I18n.text("menus.gui-items-010"))

    /** 升级插件入口按钮。 */
    val UPGRADE_ENTRY: ItemStack = named(Material.ANVIL, I18n.text("menus.gui-items-011"), I18n.text("menus.gui-items-012"))

    /** 构造带名称与 lore 的图标。 */
    fun named(material: Material, name: String, vararg lore: String): ItemStack =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name).color(NamedTextColor.WHITE))
                if (lore.isNotEmpty()) meta.lore(lore.map { Component.text(it) })
            }
        }
}
