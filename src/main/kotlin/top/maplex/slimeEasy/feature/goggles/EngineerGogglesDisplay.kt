package top.maplex.slimeEasy.feature.goggles

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlock
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.Action
import org.bukkit.event.block.CauldronLevelChangeEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import top.maplex.slimeEasy.api.goggles.DefaultEngineerGogglesDisplayContent
import top.maplex.slimeEasy.api.goggles.DefaultEngineerGogglesDisplayContext
import top.maplex.slimeEasy.api.goggles.EngineerGogglesApi
import top.maplex.slimeEasy.api.goggles.EngineerGogglesDisplayEvent
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.registry.Items
import java.util.UUID
import java.util.logging.Level

/**
 * 工程师护目镜的全服共享扫描与显示服务。
 *
 * 所有世界、方块、Slimefun 数据和全息图操作都在 Bukkit 主线程执行。普通 Slimefun 方块使用每轮区块快照，
 * 原版方块组成的多方块与扩展注册结构按 16³ 空间单元建立全服共享索引；所有层次都不会主动加载区块。
 */
internal object EngineerGogglesDisplay : Listener {

    /** 普通 Slimefun 方块的刷新轮次共享键；世界 UUID 防止不同世界同区块坐标串用结果。 */
    private data class ChunkKey(val world: UUID, val x: Int, val z: Int)

    /** 原生多方块和扩展结构中心的持久空间索引键；每个坐标轴均以 16 格为一个单元。 */
    private data class SpatialCell(val world: UUID, val x: Int, val y: Int, val z: Int)

    /** 单轮允许建立的新单元数量；使用可变包装让同轮所有佩戴者共享同一主线程预算。 */
    private data class CellScanBudget(var remaining: Int)

    /**
     * 单次共享任务的结果层；生命周期严格限制在一轮刷新内，既让重叠玩家复用读取，又不会持有动态机器状态。
     */
    private data class RefreshContext(
        val now: Long,
        val storedBlocksByChunk: MutableMap<ChunkKey, List<EngineerGogglesTarget>> = HashMap(),
        val details: MutableMap<String, List<String>> = HashMap(),
        val workStates: MutableMap<String, EngineerGogglesWorkState> = HashMap(),
        val seenEnergy: MutableSet<String> = HashSet(),
        val scanBudget: CellScanBudget = CellScanBudget(SEConfig.engineerGogglesMaxNewCellsPerRefresh)
    )

    private data class PlayerState(
        val holograms: MutableMap<String, PrivateHologramBackend.Handle> = HashMap(),
        val lastLines: MutableMap<String, List<String>> = HashMap()
    )

    /** 同一轮空间扫描使用的原生多方块与通用扩展目标快照。 */
    private data class StructuredTargetSources(
        val multiblocks: List<MultiBlock>,
        val customProviders: List<EngineerGogglesApi.TargetRegistration>
    )

    private val states = HashMap<UUID, PlayerState>()
    private val missingDependencyWarned = HashSet<UUID>()
    /**
     * 插入顺序同时作为轻量 LRU 队列；命中时通过 remove + put 移到末尾，防止玩家长期探索无限增长。
     * Bukkit 世界对象不放入键中，避免世界卸载后被缓存强引用。
     */
    private val structuredTargetCells = LinkedHashMap<SpatialCell, List<EngineerGogglesTarget>>()
    private val multiblocksByCenterMaterial = HashMap<Material, List<MultiBlock>>()
    private val customProvidersByCenterMaterial =
        HashMap<Material, List<EngineerGogglesApi.TargetRegistration>>()
    private var multiblockRegistrySize = -1
    private var customTargetProviderRevision = -1L
    private val energy = EngineerGogglesEnergy()
    private var wearerCursor = 0
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
        structuredTargetCells.clear()
        multiblocksByCenterMaterial.clear()
        customProvidersByCenterMaterial.clear()
        multiblockRegistrySize = -1
        customTargetProviderRevision = -1L
        wearerCursor = 0
        energy.retain(emptySet())
        EngineerGogglesApi.shutdown()
        backend = null
    }

    private fun tick() {
        val activePlayers = HashSet<UUID>()
        val context = RefreshContext(System.currentTimeMillis())
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        val orderedPlayers = if (onlinePlayers.isEmpty()) {
            emptyList()
        } else {
            val start = wearerCursor.mod(onlinePlayers.size)
            wearerCursor = (start + 1).mod(onlinePlayers.size)
            onlinePlayers.drop(start) + onlinePlayers.take(start)
        }

        for (player in orderedPlayers) {
            if (!isWearing(player)) {
                remove(player.uniqueId)
                missingDependencyWarned.remove(player.uniqueId)
                continue
            }

            activePlayers += player.uniqueId
            // 独立于 Slimefun 的异步护甲缓存补齐登录后夜视，同时复用现有 10 tick 主线程任务。
            EngineerGogglesNightVision.refreshIfWearing(player)
            val activeBackend = backend
            if (activeBackend == null) {
                if (missingDependencyWarned.add(player.uniqueId)) {
                    player.sendActionBar(I18n.component("messages.engineer-goggles.decent-holograms-missing"))
                }
                continue
            }

            updatePlayer(player, activeBackend, context)
        }

        (states.keys - activePlayers).forEach(::remove)
        energy.retain(context.seenEnergy)
    }

    private fun updatePlayer(
        player: Player,
        activeBackend: PrivateHologramBackend,
        context: RefreshContext
    ) {
        val state = states.getOrPut(player.uniqueId, ::PlayerState)
        val targets = LinkedHashMap<String, EngineerGogglesTarget>()
        discoverStoredBlocks(player, context.storedBlocksByChunk).forEach { targets[it.key] = it }
        discoverStructuredTargets(player, context.scanBudget).forEach { targets.putIfAbsent(it.key, it) }
        val filters = player.inventory.helmet?.let(EngineerGogglesFilter::read) ?: return
        if (filters.displayMode == EngineerGogglesDisplayMode.AIMED) {
            val aimedBlock = player.getTargetBlockExact(SEConfig.engineerGogglesRadius, FluidCollisionMode.NEVER)
            targets.entries.removeIf { aimedBlock == null || !it.value.contains(aimedBlock) }
        }
        targets.entries.removeIf { entry ->
            val workState = context.workStates.getOrPut(entry.key) { EngineerGogglesWorkState.of(entry.value) }
            !filters.allows(entry.value, workState)
        }

        val visibleTargets = HashSet<String>()
        for (target in targets.values) {
            val details = buildList {
                val workState = context.workStates.getOrPut(target.key) { EngineerGogglesWorkState.of(target) }
                if (workState != EngineerGogglesWorkState.UNKNOWN) {
                    // UNKNOWN 仍用于筛选归类，但不向玩家重复展示没有诊断价值的“状态未知”行。
                    add(
                        I18n.text(
                            "holograms.engineer-goggles.work-state",
                            "state" to I18n.raw("names.engineer-goggles.work-state.${workState.filterKey}")
                        )
                    )
                }
                addAll(
                    context.details.getOrPut(target.key) {
                        energy.lines(target, context.now, context.seenEnergy)
                    }
                )
            }
            val displayContext = DefaultEngineerGogglesDisplayContext(
                player,
                target.blockLocation.block,
                target.item,
                target.blockData == null
            )
            val content = DefaultEngineerGogglesDisplayContent(target.item.itemName, details)
            EngineerGogglesApi.applyProviders(displayContext, content)
            val cancelled = if (EngineerGogglesDisplayEvent.hasListeners()) {
                EngineerGogglesDisplayEvent(displayContext, content)
                    .also(Bukkit.getPluginManager()::callEvent)
                    .isCancelled()
            } else {
                false
            }
            if (cancelled || !content.visible) continue
            val lines = content.toLines()
            visibleTargets += target.key
            val existing = state.holograms[target.key]
            if (existing != null) {
                if (state.lastLines[target.key] != lines) {
                    runCatching { existing.update(lines) }
                        .onSuccess { state.lastLines[target.key] = lines }
                        .onFailure { logBackendFailure(it) }
                }
            } else {
                runCatching { activeBackend.create(player, target.lastLineLocation, lines) }
                    .onSuccess {
                        state.holograms[target.key] = it
                        state.lastLines[target.key] = lines
                    }
                    .onFailure { logBackendFailure(it) }
            }
        }

        val stale = state.holograms.keys - visibleTargets
        stale.forEach { key ->
            state.holograms.remove(key)?.let(::destroy)
            state.lastLines.remove(key)
        }
    }

    /**
     * 遍历已加载区块的 Slimefun 缓存，不调用会触发区块加载的数据入口。
     * 同一刷新轮次内相邻佩戴者共用 [chunkSnapshots]，每个区块最多读取一次 Slimefun 数据列表。
     */
    private fun discoverStoredBlocks(
        player: Player,
        chunkSnapshots: MutableMap<ChunkKey, List<EngineerGogglesTarget>>
    ): List<EngineerGogglesTarget> {
        val radius = SEConfig.engineerGogglesRadius
        val radiusSquared = radius.toDouble() * radius
        val center = player.location
        val world = player.world
        val result = ArrayList<EngineerGogglesTarget>()
        val minimumChunkX = (center.blockX - radius) shr 4
        val maximumChunkX = (center.blockX + radius) shr 4
        val minimumChunkZ = (center.blockZ - radius) shr 4
        val maximumChunkZ = (center.blockZ + radius) shr 4

        for (chunkX in minimumChunkX..maximumChunkX) {
            for (chunkZ in minimumChunkZ..maximumChunkZ) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue
                val key = ChunkKey(world.uid, chunkX, chunkZ)
                val targets = chunkSnapshots.getOrPut(key) { storedBlocksInChunk(world, chunkX, chunkZ) }
                for (target in targets) {
                    if (target.centerDistanceSquared(center) <= radiusSquared) result += target
                }
            }
        }
        return result
    }

    private fun storedBlocksInChunk(world: World, chunkX: Int, chunkZ: Int): List<EngineerGogglesTarget> {
        val controller = Slimefun.getDatabaseManager().blockDataController
        val chunkData = controller.getChunkDataFromCache(world.getChunkAt(chunkX, chunkZ)) ?: return emptyList()
        return chunkData.allBlockData.mapNotNull { data ->
            if (data.isPendingRemove || data.location.world?.uid != world.uid) return@mapNotNull null
            SlimefunItem.getById(data.sfId)?.let { EngineerGogglesTarget.forBlock(data, it) }
        }
    }

    /** 查询玩家球形范围覆盖的结构单元；原生多方块和扩展目标共享缓存与扫描预算。 */
    private fun discoverStructuredTargets(
        player: Player,
        scanBudget: CellScanBudget
    ): List<EngineerGogglesTarget> {
        val world = player.world
        val origin = player.location
        val radius = SEConfig.engineerGogglesRadius
        val radiusSquared = radius.toDouble() * radius
        val sources = registeredTargetSources()
        val minimumCellX = (origin.blockX - radius) shr CELL_SHIFT
        val maximumCellX = (origin.blockX + radius) shr CELL_SHIFT
        val minimumCellY = (origin.blockY - radius).coerceAtLeast(world.minHeight) shr CELL_SHIFT
        val maximumCellY = (origin.blockY + radius).coerceAtMost(world.maxHeight - 1) shr CELL_SHIFT
        val minimumCellZ = (origin.blockZ - radius) shr CELL_SHIFT
        val maximumCellZ = (origin.blockZ + radius) shr CELL_SHIFT
        val originCellX = origin.blockX shr CELL_SHIFT
        val originCellY = origin.blockY shr CELL_SHIFT
        val originCellZ = origin.blockZ shr CELL_SHIFT
        val cells = ArrayList<SpatialCell>()
        val result = LinkedHashMap<String, EngineerGogglesTarget>()

        for (cellX in minimumCellX..maximumCellX) {
            for (cellZ in minimumCellZ..maximumCellZ) {
                // 空间单元与区块在 X/Z 上同为 16 格，对未加载区块直接跳过且不写入空缓存。
                if (!world.isChunkLoaded(cellX, cellZ)) continue
                for (cellY in minimumCellY..maximumCellY) {
                    cells += SpatialCell(world.uid, cellX, cellY, cellZ)
                }
            }
        }
        // 新单元预算有限时优先建立玩家所在及最近单元，让近处结构先出现而不是按坐标从角落预热。
        cells.sortBy { cell ->
            val deltaX = cell.x - originCellX
            val deltaY = cell.y - originCellY
            val deltaZ = cell.z - originCellZ
            deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
        }
        for (cell in cells) {
            for (target in structuredTargetsInCell(world, cell, sources, scanBudget)) {
                if (target.centerDistanceSquared(origin) <= radiusSquared) {
                    result.putIfAbsent(target.key, target)
                }
            }
        }
        return result.values.toList()
    }

    /** 任一结构来源变化时整体清空派生索引，不能让已注销插件的目标继续留在空间缓存中。 */
    private fun registeredTargetSources(): StructuredTargetSources {
        val multiblocks = Slimefun.getRegistry().multiBlocks.toList()
        val providerRevision = EngineerGogglesApi.targetProviderRevision()
        val customProviders = EngineerGogglesApi.targetProviderSnapshot()
        if (multiblocks.size != multiblockRegistrySize || providerRevision != customTargetProviderRevision) {
            multiblocksByCenterMaterial.clear()
            customProvidersByCenterMaterial.clear()
            structuredTargetCells.clear()
            multiblockRegistrySize = multiblocks.size
            customTargetProviderRevision = providerRevision
        }
        return StructuredTargetSources(multiblocks, customProviders)
    }

    private fun structuredTargetsInCell(
        world: World,
        cell: SpatialCell,
        sources: StructuredTargetSources,
        scanBudget: CellScanBudget
    ): List<EngineerGogglesTarget> {
        structuredTargetCells.remove(cell)?.let { cached ->
            // 命中项移到插入顺序末尾，淘汰时优先移除长期未访问区域。
            structuredTargetCells[cell] = cached
            return cached
        }
        // 未命中但本轮预算耗尽时暂不扫描；后续刷新会继续，避免首次佩戴集中扫描全部 27 个单元。
        if (scanBudget.remaining <= 0) return emptyList()
        scanBudget.remaining--
        val scanned = scanCell(world, cell, sources)
        structuredTargetCells[cell] = scanned
        while (structuredTargetCells.size > MAX_CACHED_CELLS) {
            val eldest = structuredTargetCells.entries.iterator()
            if (eldest.hasNext()) {
                eldest.next()
                eldest.remove()
            }
        }
        return scanned
    }

    /** 首次访问单元时扫描其中 4096 个潜在中心；后续移动和其它玩家直接复用匹配结果。 */
    private fun scanCell(
        world: World,
        cell: SpatialCell,
        sources: StructuredTargetSources
    ): List<EngineerGogglesTarget> {
        val minimumX = cell.x shl CELL_SHIFT
        val minimumY = (cell.y shl CELL_SHIFT).coerceAtLeast(world.minHeight)
        val minimumZ = cell.z shl CELL_SHIFT
        val maximumX = minimumX + CELL_SIZE - 1
        val maximumY = (minimumY + CELL_SIZE - 1).coerceAtMost(world.maxHeight - 1)
        val maximumZ = minimumZ + CELL_SIZE - 1
        val result = LinkedHashMap<String, EngineerGogglesTarget>()

        for (x in minimumX..maximumX) {
            for (z in minimumZ..maximumZ) {
                for (y in minimumY..maximumY) {
                    val center = world.getBlockAt(x, y, z)
                    val multiblockCandidates = multiblocksByCenterMaterial.getOrPut(center.type) {
                        sources.multiblocks.filter { materialMatches(center.type, it.structure[4]) }
                    }
                    for (multiblock in multiblockCandidates) {
                        val structure = multiblock.structure
                        val directions = if (multiblock.isSymmetric) SYMMETRIC_DIRECTIONS else ALL_DIRECTIONS
                        for (direction in directions) {
                            if (!structureChunksLoaded(center, direction) ||
                                !matches(center, direction, structure)
                            ) continue
                            val target = EngineerGogglesTarget.forMultiblock(center, direction, multiblock)
                            result.putIfAbsent(target.key, target)
                        }
                    }
                    val customCandidates = customProvidersByCenterMaterial.getOrPut(center.type) {
                        sources.customProviders.filter { center.type in it.centerMaterials }
                    }
                    for (registration in customCandidates) {
                        val members = EngineerGogglesApi.resolveTargetMembers(registration, center)
                        if (members.isEmpty()) continue
                        val target = EngineerGogglesTarget.forCustomStructure(
                            center,
                            registration.id,
                            registration.slimefunItem,
                            members
                        )
                        result.putIfAbsent(target.key, target)
                    }
                }
            }
        }
        return result.values.toList()
    }

    /** 跨区块结构只在两侧区块均已加载时匹配，避免 getRelative 隐式触发新区块加载。 */
    private fun structureChunksLoaded(center: Block, direction: BlockFace): Boolean {
        val world = center.world
        val firstX = center.x + direction.modX
        val firstZ = center.z + direction.modZ
        val secondX = center.x - direction.modX
        val secondZ = center.z - direction.modZ
        return world.isChunkLoaded(firstX shr CELL_SHIFT, firstZ shr CELL_SHIFT) &&
            world.isChunkLoaded(secondX shr CELL_SHIFT, secondZ shr CELL_SHIFT)
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
        Items.isEngineerGogglesId(SlimefunItem.getByItem(player.inventory.helmet)?.id)

    /**
     * 解析玩家点击的普通 Slimefun 方块或多方块触发块。
     *
     * 多方块顺序与 Slimefun 交互监听器一致，重叠结构选择注册表中最后匹配的一项。
     */
    internal fun resolveItem(block: Block): SlimefunItem? {
        StorageCacheUtils.getBlock(block.location)?.sfId
            ?.let(SlimefunItem::getById)
            ?.let { return it }

        val matches = ArrayList<SlimefunItem>()
        for (multiblock in Slimefun.getRegistry().multiBlocks) {
            val center = block.getRelative(multiblock.triggerBlock)
            val directions = if (multiblock.isSymmetric) SYMMETRIC_DIRECTIONS else ALL_DIRECTIONS
            if (directions.any { structureChunksLoaded(center, it) && matches(center, it, multiblock.structure) }) {
                matches += multiblock.slimefunItem
            }
        }
        matches += resolveCustomStructureItems(block)
        return matches.lastOrNull()
    }

    /**
     * 从被点击成员反向枚举提供器声明半径内的候选中心，并用实际成员结果完成最终确认。
     * 此路径只在玩家主动切换单项过滤时运行；空间持续显示仍使用按材质建立的共享单元索引。
     */
    private fun resolveCustomStructureItems(member: Block): List<SlimefunItem> {
        val world = member.world
        val matches = ArrayList<SlimefunItem>()
        for (registration in EngineerGogglesApi.targetProviderSnapshot()) {
            val reach = registration.structureReach
            val minimumY = (member.y - reach).coerceAtLeast(world.minHeight)
            val maximumY = (member.y + reach).coerceAtMost(world.maxHeight - 1)
            for (x in member.x - reach..member.x + reach) {
                for (z in member.z - reach..member.z + reach) {
                    if (!world.isChunkLoaded(x shr CELL_SHIFT, z shr CELL_SHIFT)) continue
                    for (y in minimumY..maximumY) {
                        val center = world.getBlockAt(x, y, z)
                        if (center.type !in registration.centerMaterials) continue
                        val members = EngineerGogglesApi.resolveTargetMembers(registration, center)
                        if (members.any { it.x == member.x && it.y == member.y && it.z == member.z }) {
                            matches += registration.slimefunItem
                        }
                    }
                }
            }
        }
        return matches
    }

    private fun remove(playerId: UUID) {
        states.remove(playerId)?.let(::destroy)
    }

    private fun destroy(state: PlayerState) {
        state.holograms.values.forEach(::destroy)
        state.holograms.clear()
        state.lastLines.clear()
    }

    private fun destroy(handle: PrivateHologramBackend.Handle) {
        runCatching(handle::destroy).onFailure(::logBackendFailure)
    }

    private fun logBackendFailure(error: Throwable) {
        plugin.logger.log(Level.WARNING, "Engineer goggles hologram operation failed", error)
    }

    /**
     * 只删除可能以该成员为组成部分的结构中心单元；扩展提供器声明的最大半径参与边界计算，
     * 避免局部变化让全服索引失效，也不能沿用原生三乘三结构的一格假设漏掉大型扩展结构。
     */
    private fun invalidate(block: Block) {
        val world = block.world.uid
        val reach = maxOf(
            NATIVE_STRUCTURE_REACH,
            EngineerGogglesApi.maximumTargetStructureReach()
        )
        val minimumCellX = (block.x - reach) shr CELL_SHIFT
        val maximumCellX = (block.x + reach) shr CELL_SHIFT
        val minimumCellY = (block.y - reach) shr CELL_SHIFT
        val maximumCellY = (block.y + reach) shr CELL_SHIFT
        val minimumCellZ = (block.z - reach) shr CELL_SHIFT
        val maximumCellZ = (block.z + reach) shr CELL_SHIFT
        for (cellX in minimumCellX..maximumCellX) {
            for (cellY in minimumCellY..maximumCellY) {
                for (cellZ in minimumCellZ..maximumCellZ) {
                    structuredTargetCells.remove(SpatialCell(world, cellX, cellY, cellZ))
                }
            }
        }
    }

    /**
     * 主手持护目镜时的快捷过滤协议：潜行左键打开多选界面，潜行右键目标切换该物品类型。
     * LOWEST 阶段先取消事件，避免右键过滤同时打开或操作被点击的机器。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onGogglesInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!player.isSneaking || event.hand != EquipmentSlot.HAND) return
        val goggles = player.inventory.itemInMainHand
        if (!Items.isEngineerGogglesId(SlimefunItem.getByItem(goggles)?.id)) return

        when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                event.isCancelled = true
                EngineerGogglesFilterMenu.open(player)
            }
            Action.RIGHT_CLICK_BLOCK -> {
                event.isCancelled = true
                val target = event.clickedBlock?.let(::resolveItem)
                if (target == null) {
                    player.sendActionBar(I18n.component("messages.engineer-goggles.not-slimefun-target"))
                    return
                }
                val shown = EngineerGogglesFilter.toggleItem(goggles, target.id)
                player.inventory.setItemInMainHand(goggles)
                player.sendActionBar(
                    I18n.component(
                        "messages.engineer-goggles.item-${if (shown) "shown" else "hidden"}",
                        "item" to (ChatColor.stripColor(target.itemName) ?: target.id)
                    )
                )
            }
            else -> Unit
        }
    }

    /** 玩家离线时立即释放 DH 对象，并清理可选依赖提示会话。 */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        remove(event.player.uniqueId)
        // 可选依赖缺失时也不能永久保存离线玩家 UUID；重新登录视为新的穿戴会话。
        missingDependencyWarned.remove(event.player.uniqueId)
    }

    /** 依赖方关闭时立即释放其内容与目标提供器，避免缓存继续引用插件类加载器。 */
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        EngineerGogglesApi.unregisterProviders(event.plugin)
        // PluginDisableEvent 后不能等下一轮 revision 检查，立即释放缓存中的 SlimefunItem 类引用。
        structuredTargetCells.clear()
        customProvidersByCenterMaterial.clear()
        customTargetProviderRevision = -1L
    }

    /** 死亡可能卸下或掉落头盔，先清理显示，复活后由共享任务重新识别。 */
    @EventHandler
    fun onDeath(event: PlayerDeathEvent) = remove(event.player.uniqueId)

    /** 全息图不能跨世界复用，切换世界时立即销毁旧世界显示。 */
    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) = remove(event.player.uniqueId)

    /** 放置方块只失效可能以该成员组成结构的相邻空间单元。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) = invalidate(event.block)

    /** 破坏方块使用与放置相同的局部失效边界。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) = invalidate(event.block)

    /** 坩埚装入或取出流体会原地改变结构中心材质，必须像放置/破坏一样清除结构结果。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCauldronLevelChange(event: CauldronLevelChangeEvent) = invalidate(event.block)

    /** 活塞伸出同时失效活塞、移动前位置与移动后位置，防止结构残影。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        invalidate(event.block)
        event.blocks.forEach { block ->
            invalidate(block)
            invalidate(block.getRelative(event.direction))
        }
    }

    /** 活塞收回同样覆盖移动源与目标；事件方向由 Paper 表示本次实际移动方向。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        invalidate(event.block)
        event.blocks.forEach { block ->
            invalidate(block)
            invalidate(block.getRelative(event.direction))
        }
    }

    /** 爆炸只按实际受影响方块逐个失效空间单元。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) = event.blockList().forEach(::invalidate)

    /** 实体爆炸与方块爆炸使用相同的实际方块列表。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) = event.blockList().forEach(::invalidate)

    /**
     * 区块加载会让边界另一侧的结构首次变得可匹配，必须清除周围列中可能缓存的“不完整结构”结果。
     */
    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        invalidateChunkNeighborhood(event.world.uid, event.chunk.x, event.chunk.z)
    }

    /** 区块卸载时同步清除相邻列，防止仍加载的一侧继续显示跨边界结构。 */
    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        invalidateChunkNeighborhood(event.world.uid, event.chunk.x, event.chunk.z)
    }

    private fun invalidateChunkNeighborhood(world: UUID, chunkX: Int, chunkZ: Int) {
        structuredTargetCells.keys.removeIf { cell ->
            cell.world == world && cell.x in chunkX - 1..chunkX + 1 && cell.z in chunkZ - 1..chunkZ + 1
        }
    }

    /** 世界卸载必须删除全部空间结果，避免 UUID 对应世界重新加载后复用旧结构。 */
    @EventHandler
    fun onWorldUnload(event: WorldUnloadEvent) {
        val world = event.world.uid
        structuredTargetCells.keys.removeIf { it.world == world }
    }

    /** 空间单元与区块对齐为 16 格；修改会整体改变缓存键协议。 */
    private const val CELL_SHIFT = 4
    private const val CELL_SIZE = 16

    /** Slimefun 标准多方块从中心向任一轴最多延伸一格，局部失效至少覆盖该距离。 */
    private const val NATIVE_STRUCTURE_REACH = 1

    /** 全服最多保存 4096 个已访问单元，硬上限防止玩家长期探索导致缓存无界增长。 */
    private const val MAX_CACHED_CELLS = 4096

    private val SYMMETRIC_DIRECTIONS = arrayOf(BlockFace.NORTH, BlockFace.EAST)
    private val ALL_DIRECTIONS = arrayOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
}

/** 扩展结构成员相对逻辑中心的不可变坐标，不跨 tick 保留外部插件返回的 Block 引用。 */
internal data class EngineerGogglesMemberOffset(val x: Int, val y: Int, val z: Int)

/** 单个可展示目标；结构目标没有 [blockData]，因此只展示名称而不伪造能源数据。 */
internal data class EngineerGogglesTarget(
    val key: String,
    val lastLineLocation: Location,
    val blockLocation: Location,
    val item: SlimefunItem,
    val blockData: SlimefunBlockData?,
    val multiblockDirection: BlockFace?,
    val multiblockStructure: Array<out Material?>?,
    val customMemberOffsets: Set<EngineerGogglesMemberOffset>?
) {
    /** 以方块中心计算三维距离，普通机器与多方块查询共享同一精确半径口径。 */
    fun centerDistanceSquared(origin: Location): Double {
        val deltaX = blockLocation.blockX + 0.5 - origin.x
        val deltaY = blockLocation.blockY + 0.5 - origin.y
        val deltaZ = blockLocation.blockZ + 0.5 - origin.z
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
    }

    /**
     * 判断视线命中的方块是否属于本目标。普通机器只匹配自身；原生多方块按已确认朝向的九格结构成员匹配；
     * 扩展结构使用注册回调本次返回的真实成员快照，不能把声明半径内的其它方块误判成目标。
     */
    fun contains(block: Block): Boolean {
        if (block.world.uid != blockLocation.world?.uid) return false
        if (blockData != null) {
            return block.x == blockLocation.blockX &&
                block.y == blockLocation.blockY &&
                block.z == blockLocation.blockZ
        }
        customMemberOffsets?.let { members ->
            return EngineerGogglesMemberOffset(
                block.x - blockLocation.blockX,
                block.y - blockLocation.blockY,
                block.z - blockLocation.blockZ
            ) in members
        }
        val direction = multiblockDirection ?: return false
        val structure = multiblockStructure ?: return false
        for (index in structure.indices) {
            if (structure[index] == null) continue
            val horizontal = when (index % 3) {
                0 -> direction
                2 -> direction.oppositeFace
                else -> null
            }
            val vertical = when (index / 3) {
                0 -> 1
                2 -> -1
                else -> 0
            }
            if (block.x == blockLocation.blockX + (horizontal?.modX ?: 0) &&
                block.y == blockLocation.blockY + vertical &&
                block.z == blockLocation.blockZ + (horizontal?.modZ ?: 0)
            ) return true
        }
        return false
    }

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
                data,
                null,
                null,
                null
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
                null,
                direction,
                multiblock.structure,
                null
            )
        }

        /**
         * 把扩展提供器的瞬时 Block 集合转换成中心相对坐标；缓存只保留 SlimeEasy 自有值对象，
         * 插件关闭并递增注册 revision 后即可释放提供器及其类加载器。
         */
        fun forCustomStructure(
            center: Block,
            sourceId: Long,
            item: SlimefunItem,
            members: Collection<Block>
        ): EngineerGogglesTarget {
            val location = center.location
            val offsets = members.mapTo(LinkedHashSet()) { member ->
                EngineerGogglesMemberOffset(member.x - center.x, member.y - center.y, member.z - center.z)
            }
            val highestOffset = offsets.maxOf { it.y }
            return EngineerGogglesTarget(
                "${locationKey("custom-$sourceId", location)}:${item.id}",
                location.clone().add(0.5, highestOffset + 1.65, 0.5),
                location,
                item,
                null,
                null,
                null,
                offsets
            )
        }

        /** 世界 UUID 与整数坐标组成稳定键，避免跨世界同坐标共享能源采样或全息图。 */
        private fun locationKey(prefix: String, location: Location): String =
            "$prefix:${location.world?.uid}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }
}
