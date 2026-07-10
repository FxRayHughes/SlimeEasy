package top.maplex.slimeEasy

import org.bukkit.plugin.java.JavaPlugin
import top.maplex.slimeEasy.command.SECommand
import top.maplex.slimeEasy.config.SEText
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
        // 释放默认配置 (含全部开关 / 研究等级 / 运行时数值), 供 SEConfig / SEText 读取
        saveDefaultConfig()
        // 注册全部内容: 期间各物品模板经 SEText 解析名称 / Lore, 缺失项以默认值写入内存态配置
        Registration.registerAll(addon)
        // 把本轮 SEText 自动补全的默认物品文本落盘 (仅首次或新增物品时写入)
        SEText.flush()
        // 注册 /se reload 管理指令
        SECommand.register()
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
