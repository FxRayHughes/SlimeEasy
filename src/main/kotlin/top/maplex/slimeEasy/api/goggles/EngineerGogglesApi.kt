package top.maplex.slimeEasy.api.goggles

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level

/** 护目镜内容扩展的稳定注册入口，不暴露内部扫描缓存或全息图后端。 */
object EngineerGogglesApi {

    /** 当前公开协议版本；只在发生二进制不兼容变更时递增。 */
    const val API_VERSION = 1

    private data class Registration(
        val owner: Plugin,
        val provider: EngineerGogglesContentProvider
    )

    private val providers = CopyOnWriteArrayList<Registration>()
    private val failedProviders = ConcurrentHashMap.newKeySet<EngineerGogglesContentProvider>()

    /**
     * 按调用顺序注册内容提供器。
     *
     * 同一插件和提供器对象不会重复注册；返回 true 表示本次实际加入。
     */
    @JvmStatic
    fun registerProvider(owner: Plugin, provider: EngineerGogglesContentProvider): Boolean {
        if (providers.any { it.owner === owner && it.provider === provider }) return false
        providers += Registration(owner, provider)
        return true
    }

    /** 注销指定插件拥有的单个提供器对象；返回 true 表示找到并删除。 */
    @JvmStatic
    fun unregisterProvider(owner: Plugin, provider: EngineerGogglesContentProvider): Boolean {
        val removed = providers.removeIf { it.owner === owner && it.provider === provider }
        if (removed) failedProviders.remove(provider)
        return removed
    }

    /** 注销插件注册的全部提供器，供依赖方主动清理或响应插件关闭。 */
    @JvmStatic
    fun unregisterProviders(owner: Plugin) {
        val removed = providers.filter { it.owner === owner }
        providers.removeAll(removed.toSet())
        removed.forEach { failedProviders.remove(it.provider) }
    }

    /** 在主线程依次执行仍处于启用状态的提供器，并隔离第三方异常。 */
    internal fun applyProviders(
        context: EngineerGogglesDisplayContext,
        content: EngineerGogglesDisplayContent
    ) {
        for ((owner, provider) in providers) {
            if (!owner.isEnabled) continue
            runCatching { provider.provide(context, content) }.onFailure { error ->
                if (failedProviders.add(provider)) {
                    owner.logger.log(Level.SEVERE, "Engineer goggles content provider failed", error)
                }
            }
        }
    }

    /** SlimeEasy 关闭时释放全部外部插件和提供器引用。 */
    internal fun shutdown() {
        providers.clear()
        failedProviders.clear()
    }
}

/** 只在单轮渲染中创建的上下文实现，避免把内部目标对象纳入公共 ABI。 */
internal data class DefaultEngineerGogglesDisplayContext(
    override val viewer: Player,
    override val block: Block,
    override val slimefunItem: SlimefunItem,
    override val isMultiblock: Boolean
) : EngineerGogglesDisplayContext

/** 可变内容的内部实现；最终列表始终保持标题为第一行的固定协议。 */
internal class DefaultEngineerGogglesDisplayContent(
    override var title: String,
    details: Collection<String>
) : EngineerGogglesDisplayContent {
    override val details: MutableList<String> = details.toMutableList()
    override var visible: Boolean = true

    /** 生成交给私有全息图后端的不可变行快照。 */
    fun toLines(): List<String> = buildList {
        add(title)
        addAll(details)
    }
}
