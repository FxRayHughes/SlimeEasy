package top.maplex.slimeEasy

import org.bukkit.plugin.java.JavaPlugin
import top.maplex.slimeEasy.command.SECommand
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.feature.goggles.EngineerGogglesDisplay
import top.maplex.slimeEasy.machine.sieve.SieveRuntime
import top.maplex.slimeEasy.registry.Registration
import top.maplex.slimeEasy.territory.TerritoryService

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
        // 释放默认配置并加载独立语言文件，确保注册物品前文本已就绪
        saveDefaultConfig()
        I18n.load()
        // 先恢复领地索引，随后注册的 Slimefun 保护模块才能从第一次查询起得到完整结果。
        TerritoryService.initialize(this)
        // 注册全部内容；Slimefun 物品文本在注册阶段冻结
        Registration.registerAll(addon)
        // 注册 /se reload 管理指令
        SECommand.register()
        logger.info("Successfully running SlimeEasy!")
    }

    override fun onDisable() {
        // 筛子在首击时已托管一份原料，必须趁世界与容器仍可访问时先返还，再停止其它插件服务。
        SieveRuntime.shutdown()
        // DH 临时全息图属于外部插件内存对象，必须在本插件失效前主动注销。
        EngineerGogglesDisplay.stop()
        // 领地属于插件自有动态存档；卸载前执行最终原子保存。
        TerritoryService.shutdown()
        // Slimefun 会在正常关服时随后清理注册表；不支持 PlugMan 或 /reload 式热卸载。
    }

    companion object {
        /** 全局单例, 供构建 NamespacedKey 等场景使用。 */
        lateinit var instance: SlimeEasy
            private set
    }
}
