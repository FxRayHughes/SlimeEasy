package top.maplex.slimeEasy.api.goggles

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import java.util.logging.Level
import kotlin.math.abs

/** 护目镜内容与自定义结构目标的稳定注册入口，不暴露内部扫描缓存或全息图后端。 */
object EngineerGogglesApi {

    /** 当前公开协议能力版本；版本 2 新增不进入 Slimefun 原生注册表的自定义结构目标。 */
    const val API_VERSION = 2

    /** 自定义结构中心到任一成员的最大切比雪夫距离，限制点击解析的同步搜索上界。 */
    const val MAX_TARGET_STRUCTURE_REACH = 16

    private data class Registration(
        val owner: Plugin,
        val provider: EngineerGogglesContentProvider
    )

    /**
     * 自定义世界结构的注册快照。
     *
     * 中心材质和结构半径在注册时固定，防止外部可变集合让空间缓存协议在没有 revision 的情况下漂移。
     */
    internal data class TargetRegistration(
        val id: Long,
        val owner: Plugin,
        val slimefunItem: SlimefunItem,
        val centerMaterials: Set<Material>,
        val structureReach: Int,
        val provider: Function<Block, Collection<Block>>
    )

    private val providers = CopyOnWriteArrayList<Registration>()
    private val failedProviders = ConcurrentHashMap.newKeySet<EngineerGogglesContentProvider>()
    private val targetProviders = CopyOnWriteArrayList<TargetRegistration>()
    private val failedTargetProviders = ConcurrentHashMap.newKeySet<Function<Block, Collection<Block>>>()
    private val targetProviderSequence = AtomicLong()
    private val targetRevision = AtomicLong()

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

    /** 注销插件注册的全部内容和目标提供器，供依赖方主动清理或响应插件关闭。 */
    @JvmStatic
    fun unregisterProviders(owner: Plugin) {
        val removed = providers.filter { it.owner === owner }
        providers.removeAll(removed.toSet())
        removed.forEach { failedProviders.remove(it.provider) }
        unregisterTargetProviders(owner)
    }

    /**
     * 注册一个不在 Slimefun 原生方块或多方块注册表中的结构目标。
     *
     * SlimeEasy 只会对 [centerMaterials] 对应的已加载中心调用 [provider]；返回集合必须包含中心块及本次
     * 实际结构成员，空集合表示该中心不匹配。全部成员必须与中心同世界，且各轴距离不超过
     * [structureReach]。解析在主线程的空间扫描路径执行，不得加载区块、执行 I/O 或保存传入的方块引用。
     *
     * 同一插件和函数对象不会重复注册；返回 true 表示本次实际加入。
     */
    @JvmStatic
    fun registerTargetProvider(
        owner: Plugin,
        slimefunItem: SlimefunItem,
        centerMaterials: Set<Material>,
        structureReach: Int,
        provider: Function<Block, Collection<Block>>
    ): Boolean {
        require(centerMaterials.isNotEmpty()) { "Engineer goggles target center materials cannot be empty" }
        require(centerMaterials.all { it.isBlock && !it.isAir }) {
            "Engineer goggles target centers must use non-air block materials"
        }
        require(structureReach in 0..MAX_TARGET_STRUCTURE_REACH) {
            "Engineer goggles target structure reach must be between 0 and $MAX_TARGET_STRUCTURE_REACH"
        }
        if (targetProviders.any { it.owner === owner && it.provider === provider }) return false
        targetProviders += TargetRegistration(
            targetProviderSequence.incrementAndGet(),
            owner,
            slimefunItem,
            centerMaterials.toSet(),
            structureReach,
            provider
        )
        targetRevision.incrementAndGet()
        return true
    }

    /** 注销指定插件拥有的单个自定义目标提供器；返回 true 表示找到并删除。 */
    @JvmStatic
    fun unregisterTargetProvider(
        owner: Plugin,
        provider: Function<Block, Collection<Block>>
    ): Boolean {
        val removed = targetProviders.removeIf { it.owner === owner && it.provider === provider }
        if (removed) {
            failedTargetProviders.remove(provider)
            targetRevision.incrementAndGet()
        }
        return removed
    }

    /** 注销插件注册的全部自定义目标提供器，并让空间索引在下一轮刷新时整体失效。 */
    @JvmStatic
    fun unregisterTargetProviders(owner: Plugin) {
        val removed = targetProviders.filter { it.owner === owner }
        if (removed.isEmpty()) return
        targetProviders.removeAll(removed.toSet())
        removed.forEach { failedTargetProviders.remove(it.provider) }
        targetRevision.incrementAndGet()
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

    /** 返回当前启用插件的不可变注册快照；内部扫描器不得持有外部解析结果中的 Block。 */
    internal fun targetProviderSnapshot(): List<TargetRegistration> =
        targetProviders.filter { it.owner.isEnabled }

    /** 注册变化版本用于整体失效空间缓存，避免旧目标继续引用已经关闭的插件类加载器。 */
    internal fun targetProviderRevision(): Long = targetRevision.get()

    /** 局部方块事件只需要所有启用提供器中的最大结构半径，无需为每个受影响方块分配注册快照。 */
    internal fun maximumTargetStructureReach(): Int =
        targetProviders.asSequence()
            .filter { it.owner.isEnabled }
            .maxOfOrNull { it.structureReach }
            ?: 0

    /**
     * 同步解析并验证一个候选中心。
     *
     * 外部异常和违反成员边界的结果按提供器只记录一次；错误目标不会进入持久空间缓存。
     */
    internal fun resolveTargetMembers(registration: TargetRegistration, center: Block): List<Block> {
        if (!registration.owner.isEnabled || center.type !in registration.centerMaterials) return emptyList()
        return runCatching {
            val supplied = registration.provider.apply(center) ?: return@runCatching emptyList()
            if (supplied.isEmpty()) return@runCatching emptyList()
            val members = LinkedHashMap<String, Block>()
            for (member in supplied) {
                require(member.world.uid == center.world.uid) {
                    "Engineer goggles target members must be in the center world"
                }
                require(center.world.isChunkLoaded(member.x shr 4, member.z shr 4)) {
                    "Engineer goggles target members must stay in loaded chunks"
                }
                require(
                    abs(member.x - center.x) <= registration.structureReach &&
                        abs(member.y - center.y) <= registration.structureReach &&
                        abs(member.z - center.z) <= registration.structureReach
                ) { "Engineer goggles target member exceeds registered structure reach" }
                members["${member.x}:${member.y}:${member.z}"] = member
            }
            require(members.values.any { it.x == center.x && it.y == center.y && it.z == center.z }) {
                "Engineer goggles target members must include the center block"
            }
            members.values.toList()
        }.getOrElse { error ->
            if (failedTargetProviders.add(registration.provider)) {
                registration.owner.logger.log(Level.SEVERE, "Engineer goggles target provider failed", error)
            }
            emptyList()
        }
    }

    /** SlimeEasy 关闭时释放全部外部插件和提供器引用。 */
    internal fun shutdown() {
        providers.clear()
        failedProviders.clear()
        targetProviders.clear()
        failedTargetProviders.clear()
        targetRevision.incrementAndGet()
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
