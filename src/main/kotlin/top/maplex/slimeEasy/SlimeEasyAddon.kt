package top.maplex.slimeEasy

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import org.bukkit.plugin.java.JavaPlugin

/**
 * SlimeEasy 的 Slimefun 附属身份。
 *
 * 独立于 [SlimeEasy] 主类实现 [SlimefunAddon], 从而规避 JavaPlugin 的 final
 * `getName()` 与接口默认方法在 Kotlin 中的签名冲突。
 * `getName()` / `getLogger()` 等直接采用接口默认实现 (内部委托 [getJavaPlugin])。
 *
 * @param plugin 本附属所属的插件实例
 */
class SlimeEasyAddon(private val plugin: JavaPlugin) : SlimefunAddon {

    /** 返回真实的 JavaPlugin 实例。 */
    override fun getJavaPlugin(): JavaPlugin = plugin

    /** bug 反馈地址, 无则返回 null。 */
    override fun getBugTrackerURL(): String? = null
}
