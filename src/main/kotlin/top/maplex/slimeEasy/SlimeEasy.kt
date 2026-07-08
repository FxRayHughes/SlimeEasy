package top.maplex.slimeEasy

import org.bukkit.plugin.java.JavaPlugin
import top.maplex.slimeEasy.registry.Registration

/**
 * SlimeEasy 主类 (Paper 插件入口)。
 *
 * 依赖顺序由 paper-plugin.yml 声明为 Slimefun load: BEFORE, 因此 onEnable
 * 时 Slimefun 必然已就绪。
 *
 * 注意: 本类不直接实现 SlimefunAddon —— JavaPlugin 继承的 final `getName()`
 * 会与 SlimefunAddon 的同名默认方法在 Kotlin 中产生无法消解的冲突。附属身份
 * 由独立的 [SlimeEasyAddon] 承担。
 */
class SlimeEasy : JavaPlugin() {

    /** 本插件对应的 Slimefun 附属实例。 */
    val addon: SlimeEasyAddon by lazy { SlimeEasyAddon(this) }

    override fun onEnable() {
        instance = this
        Registration.registerAll(addon)
        logger.info("Successfully running SlimeEasy!")
    }

    override fun onDisable() {
        // 无需手动注销: Slimefun 会在自身卸载时清理附属注册的内容
    }

    companion object {
        /** 全局单例, 供构建 NamespacedKey 等场景使用。 */
        lateinit var instance: SlimeEasy
            private set
    }
}
