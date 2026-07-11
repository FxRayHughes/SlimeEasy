package top.maplex.slimeEasy.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import top.maplex.slimeEasy.SlimeEasy
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** SlimeEasy 的独立语言文件服务。 */
object I18n {

    data class Display<T>(val name: T, val lore: List<T>)

    private val ampersandSerializer = LegacyComponentSerializer.legacyAmpersand()
    private val sectionSerializer = LegacyComponentSerializer.legacySection()
    private lateinit var language: YamlConfiguration

    /** 加载配置指定的语言文件；缺失文件或键均回退到插件内置简体中文。 */
    fun load() {
        val plugin = SlimeEasy.instance
        val locale = SEConfig.language
        val fallbackPath = "lang/$DEFAULT_LANGUAGE.yml"
        val fallbackFile = File(plugin.dataFolder, fallbackPath)
        if (!fallbackFile.exists()) {
            plugin.saveResource(fallbackPath, false)
        }

        val bundledFallback = plugin.getResource(fallbackPath)?.use { stream ->
            InputStreamReader(stream, StandardCharsets.UTF_8).use(YamlConfiguration::loadConfiguration)
        } ?: YamlConfiguration.loadConfiguration(fallbackFile)

        val relativePath = "lang/$locale.yml"
        val selectedFile = File(plugin.dataFolder, relativePath)
        if (!selectedFile.exists() && locale != DEFAULT_LANGUAGE) {
            plugin.logger.warning("Language file $relativePath does not exist, falling back to $DEFAULT_LANGUAGE")
        }
        language = YamlConfiguration.loadConfiguration(if (selectedFile.exists()) selectedFile else fallbackFile).apply {
            setDefaults(bundledFallback)
        }
    }

    /** 读取保留 `&` 颜色码的原始文本，供 SlimefunItemStack 等 API 使用。 */
    fun raw(key: String, vararg placeholders: Pair<String, Any?>): String =
        format(language.getString(key) ?: missing(key), placeholders)

    /** 读取列表或 YAML 多行块，保留 `&` 颜色码。 */
    fun rawList(key: String, vararg placeholders: Pair<String, Any?>): List<String> =
        values(key).map { format(it, placeholders) }

    /** 读取并转换为 `§` Legacy 颜色码的 Bukkit 文本。 */
    fun text(key: String, vararg placeholders: Pair<String, Any?>): String =
        sectionSerializer.serialize(ampersandSerializer.deserialize(raw(key, *placeholders)))

    /** 读取为 Adventure Component。 */
    fun component(key: String, vararg placeholders: Pair<String, Any?>): Component =
        ampersandSerializer.deserialize(raw(key, *placeholders))

    /** 把已有 `§` Legacy 字符串转换为 Adventure 组件。 */
    fun legacyComponent(value: String): Component = sectionSerializer.deserialize(value)

    /** 读取列表或 YAML 多行块为 Adventure 组件。 */
    fun components(key: String, vararg placeholders: Pair<String, Any?>): List<Component> =
        rawList(key, *placeholders).map(ampersandSerializer::deserialize)

    /** 读取 `{base}.name` 与多行 `{base}.lore`，供 Slimefun 物品使用。 */
    fun rawDisplay(base: String, vararg placeholders: Pair<String, Any?>): Display<String> =
        Display(raw("$base.name", *placeholders), rawList("$base.lore", *placeholders))

    /** 读取 `{base}.name` 与多行 `{base}.lore` 为 Adventure 组件，供 UI 图标使用。 */
    fun componentDisplay(base: String, vararg placeholders: Pair<String, Any?>): Display<Component> =
        Display(
            component("$base.name", *placeholders),
            components("$base.lore", *placeholders)
        )

    private fun values(key: String): List<String> = when {
        language.isList(key) -> language.getStringList(key)
        language.isString(key) -> language.getString(key).orEmpty().split('\n')
        else -> {
            missing(key)
            emptyList()
        }
    }

    private fun format(value: String, placeholders: Array<out Pair<String, Any?>>): String {
        var result = value
        for ((name, replacement) in placeholders) {
            result = result.replace("{$name}", replacement?.toString().orEmpty())
        }
        return result
    }

    private fun missing(key: String): String {
        SlimeEasy.instance.logger.warning("Missing i18n key: $key")
        return key
    }

    private const val DEFAULT_LANGUAGE = "zh_CN"
}
