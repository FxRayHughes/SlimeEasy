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

    /** 读取字符串列表，保留 `&` 颜色码。 */
    fun rawList(key: String, vararg placeholders: Pair<String, Any?>): List<String> =
        language.getStringList(key).map { format(it, placeholders) }

    /** 读取并转换为 `§` Legacy 颜色码的 Bukkit 文本。 */
    fun text(key: String, vararg placeholders: Pair<String, Any?>): String =
        sectionSerializer.serialize(ampersandSerializer.deserialize(raw(key, *placeholders)))

    /** 读取为 Adventure Component。 */
    fun component(key: String, vararg placeholders: Pair<String, Any?>): Component =
        ampersandSerializer.deserialize(raw(key, *placeholders))

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
