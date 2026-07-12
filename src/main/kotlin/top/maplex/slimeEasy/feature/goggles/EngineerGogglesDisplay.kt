package top.maplex.slimeEasy.feature.goggles

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlock
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.registry.Items
import java.util.UUID
import java.util.logging.Level

/**
 * 工程师护目镜的全服共享扫描与显示服务。
 *
 * 所有世界、方块、Slimefun 数据和全息图操作都在 Bukkit 主线程执行。普通 Slimefun 方块直接遍历
 * 已加载区块缓存；原版方块组成的多方块仅在佩戴者跨方块移动或世界结构发生变化时重新匹配。
 */
internal object EngineerGogglesDisplay : Listener {

    private data class ScanAnchor(
        val world: UUID,
        val x: Int,
        val y: Int,
        val z: Int,
        val revision: Long
    )

    private data class PlayerState(
        val holograms: MutableMap<String, PrivateHologramBackend.Handle> = HashMap(),
        var anchor: ScanAnchor? = null,
        var multiblocks: List<EngineerGogglesTarget> = emptyList()
    )

    private val states = HashMap<UUID, PlayerState>()
    private val missingDependencyWarned = HashSet<UUID>()
    private val worldRevisions = HashMap<UUID, Long>()
    private val multiblocksByCenterMaterial = HashMap<Material, List<MultiBlock>>()
    private var multiblockRegistrySize = -1
    private val energy = EngineerGogglesEnergy()
    private var backend: PrivateHologramBackend? = null
    private var task: BukkitTask? = null
    private lateinit var plugin: JavaPlugin

    /** 启动唯一共享任务并注册清理/结构失效监听器；注册阶段只允许调用一次。 */
    fun start(plugin: JavaPlugin) {
        if (task != null) return
        this.plugin = plugin
        backend = PrivateHologramBackendFactory.create(plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
        task = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable(::tick),
            SEConfig.engineerGogglesRefreshTicks,
            SEConfig.engineerGogglesRefreshTicks
        )
    }

    /** 插件禁用前显式销毁 DH 内存对象，不能只依赖 Bukkit 自动取消调度任务。 */
    fun stop() {
        task?.cancel()
        task = null
        states.values.forEach(::destroy)
        states.clear()
        missingDependencyWarned.clear()
        worldRevisions.clear()
        multiblocksByCenterMaterial.clear()
        multiblockRegistrySize = -1
        energy.retain(emptySet())
        backend = null
    }

    private fun tick() {
        val activePlayers = HashSet<UUID>()
        val details = HashMap<String, List<String>>()
        val seenEnergy = HashSet<String>()
        val now = System.currentTimeMillis()

        for (player in Bukkit.getOnlinePlayers()) {
            if (!isWearing(player)) {
                remove(player.uniqueId)
                missingDependencyWarned.remove(player.uniqueId)
                continue
            }

            activePlayers += player.uniqueId
            val activeBackend = backend
            if (activeBackend == null) {
                if (missingDependencyWarned.add(player.uniqueId)) {
                    player.sendActionBar(I18n.component("messages.engineer-goggles.decent-holograms-missing"))
                }
                continue
            }

            updatePlayer(player, activeBackend, now, details, seenEnergy)
        }

        (states.keys - activePlayers).forEach(::remove)
        energy.retain(seenEnergy)
    }

    private fun updatePlayer(
        player: Player,
        activeBackend: PrivateHologramBackend,
        now: Long,
        details: MutableMap<String, List<String>>,
        seenEnergy: MutableSet<String>
    ) {
        val state = states.getOrPut(player.uniqueId, ::PlayerState)
        val targets = LinkedHashMap<String, EngineerGogglesTarget>()
        discoverStoredBlocks(player).forEach { targets[it.key] = it }
        discoverMultiblocks(player, state).forEach { targets.putIfAbsent(it.key, it) }

        for (target in targets.values) {
            val lines = buildList {
                add(target.item.itemName)
                addAll(details.getOrPut(target.key) { energy.lines(target, now, seenEnergy) })
            }
            val existing = state.holograms[target.key]
            if (existing != null) {
                runCatching { existing.update(lines) }.onFailure { logBackendFailure(it) }
            } else {
                runCatching { activeBackend.create(player, target.displayLocation, lines) }
                    .onSuccess { state.holograms[target.key] = it }
                    .onFailure { logBackendFailure(it) }
            }
        }

        val stale = state.holograms.keys - targets.keys
        stale.forEach { key -> state.holograms.remove(key)?.let(::destroy) }
    }

    /** 遍历已加载区块的 Slimefun 缓存，不调用会触发区块加载的数据入口。 */
    private fun discoverStoredBlocks(player: Player): List<EngineerGogglesTarget> {
        val radius = SEConfig.engineerGogglesRadius
        val radiusSquared = radius.toDouble() * radius
        val center = player.location
        val world = player.world
        val controller = Slimefun.getDatabaseManager().blockDataController
        val result = ArrayList<EngineerGogglesTarget>()
        val minimumChunkX = (center.blockX - radius) shr 4
        val maximumChunkX = (center.blockX + radius) shr 4
        val minimumChunkZ = (center.blockZ - radius) shr 4
        val maximumChunkZ = (center.blockZ + radius) shr 4

        for (chunkX in minimumChunkX..maximumChunkX) {
            for (chunkZ in minimumChunkZ..maximumChunkZ) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue
                val chunkData = controller.getChunkDataFromCache(world.getChunkAt(chunkX, chunkZ)) ?: continue
                for (data in chunkData.allBlockData) {
                    if (data.isPendingRemove || data.location.world?.uid != world.uid) continue
                    val blockCenter = data.location.clone().add(0.5, 0.5, 0.5)
                    if (blockCenter.distanceSquared(center) > radiusSquared) continue
                    val item = SlimefunItem.getById(data.sfId) ?: continue
                    result += EngineerGogglesTarget.forBlock(data, item)
                }
            }
        }
        return result
    }

    /** 多方块没有持久化控制器，需按 Slimefun 注册结构在玩家附近的已加载方块中实时识别。 */
    private fun discoverMultiblocks(player: Player, state: PlayerState): List<EngineerGogglesTarget> {
        val location = player.location
        val revision = worldRevisions[player.world.uid] ?: 0L
        val anchor = ScanAnchor(player.world.uid, location.blockX, location.blockY, location.blockZ, revision)
        if (anchor == state.anchor) return state.multiblocks

        state.anchor = anchor
        state.multiblocks = scanMultiblocks(player)
        return state.multiblocks
    }

    private fun scanMultiblocks(player: Player): List<EngineerGogglesTarget> {
        val world = player.world
        val origin = player.location
        val radius = SEConfig.engineerGogglesRadius
        val radiusSquared = radius.toDouble() * radius
        val minimumY = (origin.blockY - radius).coerceAtLeast(world.minHeight)
        val maximumY = (origin.blockY + radius).coerceAtMost(world.maxHeight - 1)
        val registered = Slimefun.getRegistry().multiBlocks.toList()
        if (registered.size != multiblockRegistrySize) {
            // 附属注册表只在启动阶段增长；数量变化时清空材质索引以纳入后注册的多方块。
            multiblocksByCenterMaterial.clear()
            multiblockRegistrySize = registered.size
        }
        val result = LinkedHashMap<String, EngineerGogglesTarget>()

        for (x in origin.blockX - radius..origin.blockX + radius) {
            for (z in origin.blockZ - radius..origin.blockZ + radius) {
                if (!world.isChunkLoaded(x shr 4, z shr 4)) continue
                for (y in minimumY..maximumY) {
                    val centerLocation = Location(world, x + 0.5, y + 0.5, z + 0.5)
                    if (centerLocation.distanceSquared(origin) > radiusSquared) continue
                    val center = world.getBlockAt(x, y, z)
                    val candidates = multiblocksByCenterMaterial.getOrPut(center.type) {
                        registered.filter { materialMatches(center.type, it.structure[4]) }
                    }
                    for (multiblock in candidates) {
                        val structure = multiblock.structure
                        val directions = if (multiblock.isSymmetric) SYMMETRIC_DIRECTIONS else ALL_DIRECTIONS
                        for (direction in directions) {
                            if (!matches(center, direction, structure)) continue
                            val target = EngineerGogglesTarget.forMultiblock(center, direction, multiblock)
                            result.putIfAbsent(target.key, target)
                        }
                    }
                }
            }
        }
        return result.values.toList()
    }

    /** 索引布局与 Slimefun MultiBlockListener 一致：1/4/7 为中心竖列，两侧分别为 0/3/6 与 2/5/8。 */
    private fun matches(center: Block, direction: BlockFace, structure: Array<out Material?>): Boolean =
        matchesVertical(center, structure[1], structure[4], structure[7]) &&
            matchesVertical(center.getRelative(direction), structure[0], structure[3], structure[6]) &&
            matchesVertical(center.getRelative(direction.oppositeFace), structure[2], structure[5], structure[8])

    private fun matchesVertical(block: Block, top: Material?, middle: Material?, bottom: Material?): Boolean =
        materialMatches(block.type, middle) &&
            materialMatches(block.getRelative(BlockFace.UP).type, top) &&
            materialMatches(block.getRelative(BlockFace.DOWN).type, bottom)

    /** 标签兼容木材变体；活塞工作时允许 MOVING_PISTON，避免工业矿机运行中短暂消失。 */
    private fun materialMatches(actual: Material, expected: Material?): Boolean {
        if (expected == null || actual == expected) return true
        if ((actual == Material.PISTON && expected == Material.MOVING_PISTON) ||
            (actual == Material.MOVING_PISTON && expected == Material.PISTON)
        ) return true
        return MultiBlock.getSupportedTags().any { it.isTagged(actual) && it.isTagged(expected) }
    }

    private fun isWearing(player: Player): Boolean =
        SlimefunItem.getByItem(player.inventory.helmet)?.id == Items.ENGINEER_GOGGLES_ID

    private fun remove(playerId: UUID) {
        states.remove(playerId)?.let(::destroy)
    }

    private fun destroy(state: PlayerState) {
        state.holograms.values.forEach(::destroy)
        state.holograms.clear()
    }

    private fun destroy(handle: PrivateHologramBackend.Handle) {
        runCatching(handle::destroy).onFailure(::logBackendFailure)
    }

    private fun logBackendFailure(error: Throwable) {
        plugin.logger.log(Level.WARNING, "Engineer goggles hologram operation failed", error)
    }

    private fun invalidate(world: UUID) {
        worldRevisions.compute(world) { _, old -> (old ?: 0L) + 1L }
    }

    /** 玩家离线时立即释放 DH 对象，并清理可选依赖提示会话。 */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        remove(event.player.uniqueId)
        // 可选依赖缺失时也不能永久保存离线玩家 UUID；重新登录视为新的穿戴会话。
        missingDependencyWarned.remove(event.player.uniqueId)
    }

    /** 死亡可能卸下或掉落头盔，先清理显示，复活后由共享任务重新识别。 */
    @EventHandler
    fun onDeath(event: PlayerDeathEvent) = remove(event.player.uniqueId)

    /** 全息图不能跨世界复用，切换世界时立即销毁旧世界显示。 */
    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) = remove(event.player.uniqueId)

    /** 放置任意结构方块都会使当前世界的多方块缓存失效。 */
    @EventHandler
    fun onPlace(event: BlockPlaceEvent) = invalidate(event.block.world.uid)

    /** 破坏任意结构方块都会使当前世界的多方块缓存失效。 */
    @EventHandler
    fun onBreak(event: BlockBreakEvent) = invalidate(event.block.world.uid)

    /** 活塞伸出可能移动工业矿机结构，下一轮必须重新匹配。 */
    @EventHandler
    fun onPistonExtend(event: BlockPistonExtendEvent) = invalidate(event.block.world.uid)

    /** 活塞收回同样会改变结构，不能继续复用旧中心与朝向。 */
    @EventHandler
    fun onPistonRetract(event: BlockPistonRetractEvent) = invalidate(event.block.world.uid)

    /** 方块爆炸可一次移除多个结构成员，统一递增世界修订号。 */
    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) = invalidate(event.block.world.uid)

    /** 实体爆炸与方块爆炸遵循相同的保守失效策略。 */
    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.location.world?.uid?.let(::invalidate)
    }

    private val SYMMETRIC_DIRECTIONS = arrayOf(BlockFace.NORTH, BlockFace.EAST)
    private val ALL_DIRECTIONS = arrayOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
}

/** 单个可展示目标；多方块没有 [blockData]，因此只展示名称而不伪造能源数据。 */
internal data class EngineerGogglesTarget(
    val key: String,
    val displayLocation: Location,
    val blockLocation: Location,
    val item: SlimefunItem,
    val blockData: SlimefunBlockData?
) {
    companion object {
        /** 从持久化 Slimefun 方块构造目标，并把全息图放到单方块顶部。 */
        fun forBlock(data: SlimefunBlockData, item: SlimefunItem): EngineerGogglesTarget {
            val location = data.location
            val key = locationKey("block", location)
            return EngineerGogglesTarget(
                key,
                location.clone().add(0.5, 1.65, 0.5),
                location,
                item,
                data
            )
        }

        /** 从已匹配结构构造带朝向的去重目标，多方块本身不附带能源数据容器。 */
        fun forMultiblock(center: Block, direction: BlockFace, multiblock: MultiBlock): EngineerGogglesTarget {
            val highestOffset = when {
                multiblock.structure.sliceArray(0..2).any { it != null } -> 1
                multiblock.structure.sliceArray(3..5).any { it != null } -> 0
                else -> -1
            }
            val location = center.location
            val key = "${locationKey("multiblock", location)}:${multiblock.slimefunItem.id}:${direction.name}"
            return EngineerGogglesTarget(
                key,
                location.clone().add(0.5, highestOffset + 1.65, 0.5),
                location,
                multiblock.slimefunItem,
                null
            )
        }

        /** 世界 UUID 与整数坐标组成稳定键，避免跨世界同坐标共享能源采样或全息图。 */
        private fun locationKey(prefix: String, location: Location): String =
            "$prefix:${location.world?.uid}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }
}
