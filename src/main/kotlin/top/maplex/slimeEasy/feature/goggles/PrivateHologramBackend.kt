package top.maplex.slimeEasy.feature.goggles

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * 护目镜使用的最小私有全息图协议。
 *
 * 接口本身不得暴露 DecentHolograms 类型，否则可选依赖缺失时 JVM 可能在加载护目镜服务时
 * 提前解析外部类并导致整个插件启用失败。
 */
internal interface PrivateHologramBackend {

    /** 创建只向 [viewer] 发包、不会持久化且不带点击命中箱的全息图。 */
    fun create(viewer: Player, location: Location, lines: List<String>): Handle

    /** 单个临时全息图的更新与销毁句柄。 */
    interface Handle {
        /** 原位替换全部文本行，保持既有单玩家可见范围。 */
        fun update(lines: List<String>)

        /** 隐藏并注销临时全息图；实现必须允许在清理阶段安全调用。 */
        fun destroy()
    }
}

/** 可选依赖工厂通过反射加载实现类，确保类验证阶段不直接链接 DecentHolograms。 */
internal object PrivateHologramBackendFactory {

    private const val PLUGIN_NAME = "DecentHolograms"
    private const val IMPLEMENTATION = "top.maplex.slimeEasy.feature.goggles.DecentHologramsBackend"

    /** 仅在 DH 已启用时反射构造后端，任何链接错误都降级为无显示模式。 */
    fun create(plugin: JavaPlugin): PrivateHologramBackend? {
        if (!plugin.server.pluginManager.isPluginEnabled(PLUGIN_NAME)) {
            plugin.logger.warning("DecentHolograms is not installed; engineer goggles display is disabled.")
            return null
        }

        return runCatching {
            Class.forName(IMPLEMENTATION, true, plugin.javaClass.classLoader)
                .getDeclaredConstructor()
                .newInstance() as PrivateHologramBackend
        }.onFailure {
            plugin.logger.warning("Unable to initialize DecentHolograms backend: ${it.message}")
        }.getOrNull()
    }
}
