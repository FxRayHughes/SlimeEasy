package top.maplex.slimeEasy.config

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material

/**
 * Slimefun 物品模板构造器。
 *
 * 名称与 Lore 由调用方通过 [I18n.raw] 从语言文件读取；本类只统一封装构造签名，
 * 不再把语言文本写入 `config.yml`。
 */
object SEText {

    fun stack(id: String, material: Material, name: String, vararg lore: String): SlimefunItemStack =
        SlimefunItemStack(id, material, name, *lore)
}
