package top.maplex.slimeEasy.territory

import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.ProtectionModule
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * 将 SlimeEasy 领地接入 Slimefun 的统一保护链。
 * 返回 true 只代表本模块允许；ProtectionManager 仍会继续询问 WorldGuard 等其它模块。
 */
internal class TerritoryProtectionModule(private val plugin: Plugin) : ProtectionModule {
    /** 模块不需要额外外部插件初始化。 */
    override fun load() = Unit
    /** 返回承载本保护模块的 SlimeEasy 插件实例。 */
    override fun getPlugin(): Plugin = plugin

    /** 将 Slimefun Interaction 映射到领地的稳定行为权限。 */
    override fun hasPermission(player: OfflinePlayer, location: Location, interaction: Interaction): Boolean =
        TerritoryService.hasPermission(player, location, TerritoryAction.of(interaction))
}

/**
 * 协调 Slimefun ProtectionManager 的延迟生命周期。
 * Slimefun 2026.x 在自身 `onEnable` 末尾安排下一 tick 才创建 ProtectionManager，附属插件不能在
 * `onEnable` 中直接解引用它；否则插件会半注册后启用失败，并把旧 handler 留在 Slimefun 注册表中。
 */
internal object TerritoryProtectionBridge {
    private const val MAX_INITIALIZATION_ATTEMPTS = 200
    private var registered = false

    /** 在 ProtectionManager 可用后注册一次领地模块，正常启动时通常在第一次尝试即完成。 */
    fun initialize(plugin: JavaPlugin) {
        if (registered) return
        object : BukkitRunnable() {
            private var attempts = 0

            override fun run() {
                if (!plugin.isEnabled) {
                    cancel()
                    return
                }
                val manager = protectionManager()
                if (manager == null) {
                    attempts++
                    if (attempts >= MAX_INITIALIZATION_ATTEMPTS) {
                        plugin.logger.severe(
                            "Slimefun ProtectionManager was not initialized; territory protection remains fail-closed"
                        )
                        cancel()
                    }
                    return
                }
                manager.registerModule(Bukkit.getPluginManager(), plugin.name) { TerritoryProtectionModule(it) }
                registered = true
                cancel()
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    /**
     * 查询完整 Slimefun 保护链；管理器尚未就绪时拒绝操作。
     * 失败关闭能避免启动窗口内绕过 WorldGuard 等外部保护模块。
     */
    fun hasPermission(player: OfflinePlayer, location: Location, interaction: Interaction): Boolean =
        protectionManager()?.hasPermission(player, location, interaction) ?: false

    private fun protectionManager() = runCatching { Slimefun.getProtectionManager() }.getOrNull()
}
