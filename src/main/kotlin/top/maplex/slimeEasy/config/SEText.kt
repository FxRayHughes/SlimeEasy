package top.maplex.slimeEasy.config

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material

/**
 * Slimefun 物品模板构造器。
 *
 * 调用方只提供层级语言基础键；本类通过 [I18n.rawDisplay] 读取 `name/lore` 并统一构造，
 * 不在业务代码逐行拼装文本，也不把语言文本写入 `config.yml`。
 */
object SEText {

    /** 以层级语言节点 `{key}.name/lore` 构造物品。 */
    fun localized(
        id: String,
        material: Material,
        key: String,
        vararg placeholders: Pair<String, Any?>
    ): SlimefunItemStack {
        val display = I18n.rawDisplay(key, *placeholders)
        return SlimefunItemStack(id, material, display.name, *display.lore.toTypedArray())
    }

}
