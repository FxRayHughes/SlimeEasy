package top.maplex.slimeEasy.storage.core

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
        named(Material.LIME_STAINED_GLASS_PANE, "§a放入升级组件", "§7手持或点击背包内的升级组件安装")

    /** 虚空配置按钮。 */
    val VOID_CONFIG: ItemStack =
        named(Material.BLACK_CONCRETE, "§8虚空过滤配置", "§7点击管理销毁列表")

    /** 抽取过滤配置按钮。 */
    val EXTRACT_CONFIG: ItemStack =
        named(Material.HOPPER, "§e抽取过滤配置", "§7点击管理抽取黑 / 白名单")

    /** 输出过滤配置按钮。 */
    val OUTPUT_CONFIG: ItemStack =
        named(Material.DROPPER, "§e输出过滤配置", "§7点击管理输出黑 / 白名单")

    /** 上一页按钮。 */
    val PREV_PAGE: ItemStack = named(Material.ARROW, "§e上一页")

    /** 下一页按钮。 */
    val NEXT_PAGE: ItemStack = named(Material.ARROW, "§e下一页")

    /** 升级插件入口按钮。 */
    val UPGRADE_ENTRY: ItemStack = named(Material.ANVIL, "§b升级插件", "§7点击管理升级组件")

    /** 构造带名称与 lore 的图标。 */
    fun named(material: Material, name: String, vararg lore: String): ItemStack =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name).color(NamedTextColor.WHITE))
                if (lore.isNotEmpty()) meta.lore(lore.map { Component.text(it) })
            }
        }
}
