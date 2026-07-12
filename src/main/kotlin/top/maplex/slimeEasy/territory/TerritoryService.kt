package top.maplex.slimeEasy.territory

import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.block.Banner
import org.bukkit.block.banner.Pattern
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Rotatable
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.ArrayDeque
import java.util.UUID

/**
 * 领地内存索引与所有状态变更的唯一入口。
 * Bukkit 事件均在主线程调用，因此索引刻意使用普通集合以保持复合修改的原子性。
 */
internal object TerritoryService {

    private lateinit var plugin: JavaPlugin
    private lateinit var repository: TerritoryRepository
    private val territories = mutableMapOf<UUID, Territory>()
    private val ownerIndex = mutableMapOf<UUID, UUID>()
    private val chunkIndex = mutableMapOf<TerritoryChunk, UUID>()
    private val blockIndex = mutableMapOf<TerritoryBlock, UUID>()
    private var persistenceReady = false
    private var dirty = false

    /** 插件启用时恢复存档并重建主人、区块和方块位置索引。 */
    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
        repository = TerritoryRepository(plugin)
        persistenceReady = false
        dirty = false
        territories.clear()
        ownerIndex.clear()
        chunkIndex.clear()
        blockIndex.clear()
        val loaded = runCatching { repository.load() }.getOrElse {
            plugin.logger.severe("Unable to load territories.yml; persistence is locked to protect the original file: ${it.message}")
            return
        }
        dirty = repository.requiresMigration
        loaded.forEach { territory ->
            validate(territory)
            territories[territory.id] = territory
            val previousOwner = ownerIndex.putIfAbsent(territory.owner, territory.id)
            if (previousOwner != null) {
                lock(territory, "duplicate owner")
                territories[previousOwner]?.let { lock(it, "duplicate owner") }
            }
            territory.chunks.forEach { chunk ->
                val previous = chunkIndex.putIfAbsent(chunk, territory.id)
                if (previous != null) {
                    lock(territory, "overlapping chunk")
                    territories[previous]?.let { lock(it, "overlapping chunk") }
                }
            }
            blockIndex[territory.core] = territory.id
            territory.flags.values.forEach { blockIndex[it] = territory.id }
        }
        persistenceReady = true
    }

    /** 插件卸载前重试仍未落盘的最终快照；干净状态无需重写同一文件。 */
    fun shutdown(): Boolean = if (dirty) save() else true

    /** 返回只供管理查询使用的当前聚合集合。 */
    fun all(): Collection<Territory> = territories.values
    /** 按主人唯一索引查询领地。 */
    fun ownedBy(player: UUID): Territory? = ownerIndex[player]?.let(territories::get)
    /** 按稳定领地 UUID 查询。 */
    fun byId(id: UUID): Territory? = territories[id]
    /** 按世界坐标查询覆盖该位置的领地。 */
    fun at(location: Location): Territory? = chunkIndex[TerritoryChunk.of(location)]?.let(territories::get)
    /** 按无损区块键查询领地。 */
    fun at(chunk: TerritoryChunk): Territory? = chunkIndex[chunk]?.let(territories::get)
    /** 查询核心或旗帜锚点绑定的领地。 */
    fun byBlock(location: Location): Territory? = blockIndex[TerritoryBlock.of(location)]?.let(territories::get)
    /** 判断坐标是否为仍绑定的核心。 */
    fun isCore(location: Location): Boolean = byBlock(location)?.core == TerritoryBlock.of(location)
    /** 判断坐标是否为仍绑定的扩展旗帜。 */
    fun isFlag(location: Location): Boolean =
        byBlock(location)?.flags?.containsValue(TerritoryBlock.of(location)) == true

    /** 创建玩家唯一领地并立即占用核心所在区块周围的3×3区域。 */
    fun create(owner: Player, location: Location): Result {
        val check = checkCreate(owner, location)
        if (check != Result.SUCCESS) return check
        val center = TerritoryChunk.of(location)
        val chunks = center.square(Territory.CORE_CHUNK_RADIUS).toMutableSet()
        val core = TerritoryBlock.of(location)
        val territory = Territory(UUID.randomUUID(), owner.uniqueId, core, chunks, mutableMapOf())
        territories[territory.id] = territory
        ownerIndex[owner.uniqueId] = territory.id
        chunks.forEach { chunkIndex[it] = territory.id }
        blockIndex[core] = territory.id
        dirty = true
        return persistedResult()
    }

    /** 只检查核心创建条件，不修改任何索引，供 Slimefun 高优先级事件阶段使用。 */
    fun checkCreate(owner: Player, location: Location): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (ownedBy(owner.uniqueId) != null) return Result.ALREADY_OWNS
        val center = TerritoryChunk.of(location)
        if (center.square(Territory.CORE_CHUNK_RADIUS).any(chunkIndex::containsKey)) return Result.CHUNK_OCCUPIED
        return Result.SUCCESS
    }

    /** 为明确且可管理的相邻领地增加一面旗帜，并认领其3×3覆盖中尚未属于本领地的区块。 */
    fun addFlag(player: Player, location: Location, selected: UUID? = null): Result {
        val check = checkAddFlag(player, location, selected)
        if (check != Result.SUCCESS) return check
        val center = TerritoryChunk.of(location)
        val territory = flagCandidates(player, center, selected).single()
        val flag = TerritoryBlock.of(location)
        territory.flags[center] = flag
        val addedChunks = center.square(Territory.FLAG_CHUNK_RADIUS) - territory.chunks
        territory.chunks += addedChunks
        addedChunks.forEach { chunkIndex[it] = territory.id }
        blockIndex[flag] = territory.id
        synchronizeFlagBlock(flag, territory.flagBaseColor, territory.flagPatterns)
        schedulePlacedFlagSynchronization(territory.id, flag)
        dirty = true
        return persistedResult()
    }

    /** 只验证3×3覆盖归属、连通关系与旗帜上限；同一领地内部的覆盖重叠合法。 */
    fun checkAddFlag(player: Player, location: Location, selected: UUID? = null): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        val center = TerritoryChunk.of(location)
        val candidates = flagCandidates(player, center, selected)
        if (candidates.isEmpty()) return Result.NOT_ADJACENT
        if (candidates.size > 1) return Result.AMBIGUOUS
        val territory = candidates.single()
        if (center in territory.flags) return Result.FLAG_CENTER_OCCUPIED
        if (territory.flags.size >= Territory.MAX_FLAGS) return Result.LIMIT_REACHED
        val overlapsAnotherTerritory = center.square(Territory.FLAG_CHUNK_RADIUS).any {
            val owner = chunkIndex[it]
            owner != null && owner != territory.id
        }
        if (overlapsAnotherTerritory) return Result.CHUNK_OCCUPIED
        return Result.SUCCESS
    }

    /** 拆除旗帜后重算覆盖并集，只释放不再被核心或其它旗帜覆盖的区块。 */
    fun removeFlag(player: Player, location: Location): Result {
        val check = checkRemoveFlag(player, location)
        if (check != Result.SUCCESS) return check
        val block = TerritoryBlock.of(location)
        val territory = requireNotNull(byBlock(location))
        val center = requireNotNull(territory.flags.entries.firstOrNull { it.value == block }?.key)
        val previousChunks = territory.chunks.toSet()
        territory.flags.remove(center)
        val remainingChunks = territory.expectedChunks()
        (previousChunks - remainingChunks).forEach { chunk ->
            if (chunkIndex[chunk] == territory.id) chunkIndex.remove(chunk)
        }
        territory.chunks.clear()
        territory.chunks += remainingChunks
        blockIndex.remove(block)
        dirty = true
        return persistedResult()
    }

    /** 只验证旗帜绑定、区块管理权与拆除后的连通性。 */
    fun checkRemoveFlag(player: Player, location: Location): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        val block = TerritoryBlock.of(location)
        val territory = byBlock(location) ?: return Result.SUCCESS
        if (territory.locked) return Result.LOCKED
        if (!canManage(player, territory, TerritoryManagement.CHUNKS)) return Result.NO_PERMISSION
        val center = territory.flags.entries.firstOrNull { it.value == block }?.key ?: return Result.NO_PERMISSION
        val remainingChunks = claimedChunks(territory.core.chunk, territory.flags.keys - center)
        if (!connected(remainingChunks, territory.core.chunk)) return Result.WOULD_DISCONNECT
        return Result.SUCCESS
    }

    /** 释放全部索引并撤销与该领地有关的短期邀请。 */
    fun disband(territory: Territory): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        territories.remove(territory.id)
        ownerIndex.remove(territory.owner)
        territory.chunks.forEach(chunkIndex::remove)
        blockIndex.remove(territory.core)
        territory.flags.values.forEach(blockIndex::remove)
        TerritoryInvitations.cancelFor(territory.id)
        dirty = true
        return persistedResult()
    }

    /** 只验证核心解散权限；真实释放必须等所有保护监听器完成后再提交。 */
    fun checkDisband(player: Player, territory: Territory): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (territory.locked && !isAdmin(player)) return Result.LOCKED
        if (territory.owner != player.uniqueId && !isAdmin(player)) return Result.NO_PERMISSION
        return Result.SUCCESS
    }

    /** 经玩家确认后转让，原主人保留完整成员与管理能力。 */
    fun transfer(territory: Territory, target: UUID): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (territory.locked) return Result.LOCKED
        if (target !in territory.members) return Result.NOT_MEMBER
        if (ownedBy(target) != null) return Result.TARGET_ALREADY_OWNS
        val oldOwner = territory.owner
        territory.members.remove(target)
        territory.members[oldOwner] = TerritoryMember.formerOwner()
        territory.owner = target
        ownerIndex.remove(oldOwner)
        ownerIndex[target] = territory.id
        TerritoryInvitations.cancelFor(territory.id)
        dirty = true
        return persistedResult()
    }

    /** 管理员救援转让绕过锁定状态，但仍维护主人唯一索引。 */
    fun forceTransfer(territory: Territory, target: UUID): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (ownedBy(target)?.id == territory.id) return Result.SUCCESS
        if (ownedBy(target) != null) return Result.TARGET_ALREADY_OWNS
        val oldOwner = territory.owner
        territory.members.remove(target)
        territory.members[oldOwner] = TerritoryMember.formerOwner()
        territory.owner = target
        ownerIndex.remove(oldOwner)
        ownerIndex[target] = territory.id
        TerritoryInvitations.cancelFor(territory.id)
        dirty = true
        return persistedResult()
    }

    /** 接受邀请后按安全默认权限加入成员。 */
    fun addMember(territory: Territory, player: UUID): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (player == territory.owner || territory.members.containsKey(player)) return Result.ALREADY_MEMBER
        territory.members[player] = TerritoryMember(
            territory.defaultMemberActions.toMutableSet(),
            territory.defaultMemberManagement.toMutableSet()
        )
        dirty = true
        return persistedResult()
    }

    /** 移除成员并在陌生人禁入时立即驱离。 */
    fun removeMember(actor: Player, territory: Territory, player: UUID): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (!canManage(actor, territory, TerritoryManagement.MEMBERS)) return Result.NO_PERMISSION
        if (territory.members.remove(player) == null) return Result.NOT_MEMBER
        dirty = true
        val result = persistedResult()
        evictUnauthorized(territory)
        return result
    }

    /** 切换成员或访客的一项行为权限。 */
    fun toggleAction(actor: Player, territory: Territory, target: UUID?, action: TerritoryAction): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (!canManage(actor, territory, TerritoryManagement.PERMISSIONS)) return Result.NO_PERMISSION
        val set = if (target == null) {
            territory.visitorActions
        } else {
            territory.members[target]?.actions ?: return Result.NOT_MEMBER
        }
        if (!set.add(action)) set.remove(action)
        dirty = true
        return persistedResult()
    }

    /** 仅主人或管理员可委派管理能力，防止权限管理员自我升级。 */
    fun toggleManagement(actor: Player, territory: Territory, target: UUID, permission: TerritoryManagement): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (actor.uniqueId != territory.owner && !isAdmin(actor)) return Result.NO_PERMISSION
        val set = territory.members[target]?.management ?: return Result.NOT_MEMBER
        if (!set.add(permission)) set.remove(permission)
        dirty = true
        return persistedResult()
    }

    /** 修改新成员默认行为权限；只影响之后加入的成员，不回写现有成员。 */
    fun toggleDefaultAction(actor: Player, territory: Territory, action: TerritoryAction): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (!canManage(actor, territory, TerritoryManagement.PERMISSIONS)) return Result.NO_PERMISSION
        if (!territory.defaultMemberActions.add(action)) territory.defaultMemberActions.remove(action)
        dirty = true
        return persistedResult()
    }

    /** 只有主人或管理员能设置新成员默认管理权限，避免权限管理者为未来账号间接提权。 */
    fun toggleDefaultManagement(
        actor: Player,
        territory: Territory,
        permission: TerritoryManagement
    ): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (actor.uniqueId != territory.owner && !isAdmin(actor)) return Result.NO_PERMISSION
        if (!territory.defaultMemberManagement.add(permission)) {
            territory.defaultMemberManagement.remove(permission)
        }
        dirty = true
        return persistedResult()
    }

    /**
     * 保存领地旗帜的底色和图案模板并同步全部已绑定旗帜。
     * 底色必须同步到实际旗帜材质，图案写入 Banner TileState；位置索引不变，因此 Slimefun 身份保持不变。
     */
    fun updateFlagDesign(
        actor: Player,
        territory: Territory,
        baseColor: DyeColor,
        patterns: List<Pattern>
    ): FlagDesignUpdate {
        if (!persistenceReady) return FlagDesignUpdate(Result.PERSISTENCE_UNAVAILABLE, 0, territory.flags.size)
        if (!isActive(territory)) return FlagDesignUpdate(Result.NOT_FOUND, 0, territory.flags.size)
        if (!canManage(actor, territory, TerritoryManagement.FLAGS)) {
            return FlagDesignUpdate(Result.NO_PERMISSION, 0, territory.flags.size)
        }
        if (patterns.size > Territory.MAX_FLAG_PATTERNS) {
            return FlagDesignUpdate(Result.FLAG_PATTERN_LIMIT, 0, territory.flags.size)
        }
        territory.flagBaseColor = baseColor
        territory.flagPatterns.clear()
        territory.flagPatterns.addAll(patterns)
        val updated = territory.flags.values.count {
            synchronizeFlagBlock(it, territory.flagBaseColor, territory.flagPatterns)
        }
        dirty = true
        return FlagDesignUpdate(persistedResult(), updated, territory.flags.size)
    }

    /** 切换陌生人进入设置并立即执行滞留检查。 */
    fun toggleEntry(actor: Player, territory: Territory): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (!canManage(actor, territory, TerritoryManagement.SETTINGS)) return Result.NO_PERMISSION
        territory.allowStrangerEntry = !territory.allowStrangerEntry
        dirty = true
        val result = persistedResult()
        evictUnauthorized(territory)
        return result
    }

    /** 切换已有飞行能力的使用权；关闭时不撤销外部授予的 allowFlight。 */
    fun toggleFlight(actor: Player, territory: Territory): Result {
        if (!persistenceReady) return Result.PERSISTENCE_UNAVAILABLE
        if (!isActive(territory)) return Result.NOT_FOUND
        if (!canManage(actor, territory, TerritoryManagement.SETTINGS)) return Result.NO_PERMISSION
        territory.allowFlight = !territory.allowFlight
        dirty = true
        val result = persistedResult()
        if (!territory.allowFlight) Bukkit.getOnlinePlayers()
            .filter { at(it.location)?.id == territory.id && it.uniqueId != territory.owner && !isAdmin(it) }
            .forEach { it.isFlying = false }
        return result
    }

    /** 为 Slimefun 与原版事件返回同一份六类行为判定。 */
    fun hasPermission(player: OfflinePlayer, location: Location, action: TerritoryAction): Boolean {
        // 全局存档无法解析时无法可靠重建区块索引，因此保护模块必须失败关闭而不是放行未知区域。
        if (!persistenceReady) return isAdmin(player)
        val territory = at(location) ?: return true
        if (territory.locked) return isAdmin(player)
        // 核心与旗帜自身必须允许打开身份感知的只读界面；真实管理按钮仍逐项鉴权。
        if (action == TerritoryAction.INTERACT_BLOCK && byBlock(location)?.id == territory.id) return true
        if (isAdmin(player) || territory.owner == player.uniqueId) return true
        return territory.members[player.uniqueId]?.actions?.contains(action)
            ?: territory.visitorActions.contains(action)
    }

    /** 成员恒可进入，访客遵循领地设置，锁定领地仅管理员可进入。 */
    fun canEnter(player: OfflinePlayer, territory: Territory): Boolean =
        isAdmin(player) || (!territory.locked &&
            (territory.owner == player.uniqueId ||
                territory.members.containsKey(player.uniqueId) || territory.allowStrangerEntry))

    /** 查询独立管理能力；锁定状态下只允许管理员救援。 */
    fun canManage(player: OfflinePlayer, territory: Territory, permission: TerritoryManagement): Boolean =
        isAdmin(player) || (!territory.locked &&
            (territory.owner == player.uniqueId ||
                territory.members[player.uniqueId]?.management?.contains(permission) == true))

    /** 管理员绕过只作用于本领地模块，不改变其它保护插件结果。 */
    fun isAdmin(player: OfflinePlayer): Boolean =
        player.isOp || player.player?.hasPermission(ADMIN_PERMISSION) == true

    /**
     * 保存当前完整快照并报告真实结果；加载未完成时绝不触碰原文件。
     * 失败会保留 dirty 标记，使下一次变更或插件关闭能够重试。
     */
    fun save(): Boolean {
        if (!::repository.isInitialized || !persistenceReady) return false
        return runCatching { repository.save(territories.values) }
            .onSuccess { dirty = false }
            .onFailure {
                dirty = true
                plugin.logger.severe("Unable to save territories.yml: ${it.message}")
            }
            .isSuccess
    }

    /** 把设置变化后仍滞留的未授权在线玩家移出领地。 */
    fun evictUnauthorized(territory: Territory) {
        Bukkit.getOnlinePlayers().filter { at(it.location)?.id == territory.id && !canEnter(it, territory) }
            .forEach { player -> player.teleport(findExit(player.location, territory, player)) }
    }

    private fun findExit(origin: Location, territory: Territory, player: Player): Location {
        val world = requireNotNull(origin.world)
        val center = TerritoryChunk.of(origin)
        for (radius in 1..8) {
            for (x in center.x - radius..center.x + radius) {
                for (z in center.z - radius..center.z + radius) {
                    val chunk = TerritoryChunk(world.uid, x, z)
                    if (chunk in territory.chunks) continue
                    val destinationTerritory = at(chunk)
                    if (destinationTerritory != null && !canEnter(player, destinationTerritory)) continue
                    val blockX = (x shl 4) + 8
                    val blockZ = (z shl 4) + 8
                    return world.getHighestBlockAt(blockX, blockZ).location.add(0.5, 1.0, 0.5)
                }
            }
        }
        return world.spawnLocation
    }

    private fun validate(territory: Territory) {
        val valid = territory.flags.size <= Territory.MAX_FLAGS &&
            territory.flags.all { (center, block) -> center == block.chunk } &&
            territory.flagPatterns.size <= Territory.MAX_FLAG_PATTERNS &&
            territory.flags.keys.all { it.world == territory.core.world } &&
            territory.chunks.all { it.world == territory.core.world } &&
            territory.chunks == territory.expectedChunks() &&
            connected(territory.chunks, territory.core.chunk)
        if (!valid) lock(territory, "invalid topology")
    }

    private fun connected(chunks: Collection<TerritoryChunk>, root: TerritoryChunk): Boolean {
        if (root !in chunks) return false
        val remaining = chunks.toMutableSet()
        val queue = ArrayDeque<TerritoryChunk>()
        queue += root
        remaining.remove(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            remaining.filter(current::adjacentTo).toList().forEach {
                remaining.remove(it)
                queue += it
            }
        }
        return remaining.isEmpty()
    }

    private fun lock(territory: Territory, reason: String) {
        territory.locked = true
        dirty = true
        if (::plugin.isInitialized) plugin.logger.warning("Territory ${territory.id} locked: $reason")
    }

    enum class Result {
        SUCCESS,
        ALREADY_OWNS,
        CHUNK_OCCUPIED,
        FLAG_CENTER_OCCUPIED,
        NOT_ADJACENT,
        AMBIGUOUS,
        LIMIT_REACHED,
        WOULD_DISCONNECT,
        NO_PERMISSION,
        LOCKED,
        NOT_MEMBER,
        ALREADY_MEMBER,
        TARGET_ALREADY_OWNS,
        NOT_FOUND,
        FLAG_PATTERN_LIMIT,
        PERSISTENCE_FAILED,
        PERSISTENCE_UNAVAILABLE
    }

    /** 管理员救援与本模块绕过权限节点；名称属于服务端权限配置协议。 */
    const val ADMIN_PERMISSION = "slimeeasy.territory.admin"

    /** 候选领地只判断3×3覆盖能否与现有并集相接；跨领地占用冲突由最终校验单独报告。 */
    private fun flagCandidates(player: Player, center: TerritoryChunk, selected: UUID?): List<Territory> {
        val coverage = center.square(Territory.FLAG_CHUNK_RADIUS)
        return territories.values.filter { territory ->
            !territory.locked && canManage(player, territory, TerritoryManagement.CHUNKS) &&
                (selected == null || territory.id == selected) && coverage.any { candidate ->
                    candidate in territory.chunks || territory.chunks.any(candidate::adjacentTo)
                }
        }
    }

    /** 核心3×3与各旗帜3×3覆盖的并集是权限索引的唯一真相源。 */
    private fun claimedChunks(core: TerritoryChunk, flagCenters: Collection<TerritoryChunk>): Set<TerritoryChunk> =
        buildSet {
            addAll(core.square(Territory.CORE_CHUNK_RADIUS))
            flagCenters.forEach { addAll(it.square(Territory.FLAG_CHUNK_RADIUS)) }
        }

    /**
     * Slimefun 会在放置事件结束阶段完成自有方块初始化，可能覆盖同一 tick 写入的 Banner 图案。
     * 下一 tick 仅在原旗帜仍绑定到同一领地时补写最终模板，避免对已拆除或替换的方块误操作。
     */
    private fun schedulePlacedFlagSynchronization(territoryId: UUID, flag: TerritoryBlock) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val territory = territories[territoryId] ?: return@Runnable
            if (territory.flags[flag.chunk] != flag) return@Runnable
            synchronizeFlagBlock(flag, territory.flagBaseColor, territory.flagPatterns)
        })
    }

    /** 菜单和邀请可能持有旧聚合引用，只有仍由主索引持有的同一对象才允许继续修改。 */
    private fun isActive(territory: Territory): Boolean = territories[territory.id] === territory

    /**
     * 未加载世界或锚点已缺失时返回 false，模板仍会持久化供之后新增旗帜继承。
     * 原版用不同 Material 表示旗帜底色，因此必须替换材质并显式复制墙旗朝向或立旗旋转。
     */
    private fun synchronizeFlagBlock(
        block: TerritoryBlock,
        baseColor: DyeColor,
        patterns: List<Pattern>
    ): Boolean {
        val world = Bukkit.getWorld(block.world) ?: return false
        val bannerBlock = world.getBlockAt(block.x, block.y, block.z)
        if (bannerBlock.state !is Banner) return false
        val wallMounted = bannerBlock.type.name.endsWith("_WALL_BANNER")
        val materialSuffix = if (wallMounted) "WALL_BANNER" else "BANNER"
        val targetMaterial = Material.matchMaterial("${baseColor.name}_$materialSuffix") ?: return false
        if (bannerBlock.type != targetMaterial) {
            val previousData = bannerBlock.blockData
            val targetData = targetMaterial.createBlockData()
            when {
                previousData is Directional && targetData is Directional -> targetData.facing = previousData.facing
                previousData is Rotatable && targetData is Rotatable -> targetData.rotation = previousData.rotation
            }
            // applyPhysics=false 防止墙旗在材质切换的中间状态脱落；Slimefun 数据按坐标存储，不随材质清除。
            bannerBlock.setBlockData(targetData, false)
        }
        val desiredPatterns = patterns.map { Pattern(it.color, it.pattern) }
        val templateMaterial = Material.matchMaterial("${baseColor.name}_BANNER") ?: return false
        val componentTemplate = ItemStack(templateMaterial).apply {
            editMeta(BannerMeta::class.java) { it.patterns = desiredPatterns }
        }
        if (BannerNmsBridge.apply(plugin, bannerBlock, componentTemplate, desiredPatterns.size)) return true

        // 非 Paper 或映射发生变化时保留 Bukkit 兼容回退；正常26.2/26.3路径应由上方原版组件桥完成。
        val state = bannerBlock.state as? Banner ?: return false
        state.baseColor = baseColor
        state.patterns = desiredPatterns
        if (!state.update(true, false)) return false
        var applied = bannerBlock.state as? Banner ?: return false
        if (!samePatterns(applied.patterns, desiredPatterns)) {
            // 某些服务端实现会在材质切换后的第一次 TileState 更新中丢弃图案，重新获取快照后补写一次。
            applied.baseColor = baseColor
            applied.patterns = desiredPatterns
            if (!applied.update(true, false)) return false
            applied = bannerBlock.state as? Banner ?: return false
        }
        if (!samePatterns(applied.patterns, desiredPatterns)) return false
        sendFlagBlockUpdate(bannerBlock.location, applied)
        return true
    }

    /**
     * Paper 的 TileState 更新并不保证已经追踪该区块的客户端立即收到方块实体数据。
     * 先发送真实颜色材质，再显式发送 Banner TileState，确保花纹层在客户端立刻刷新。
     */
    private fun sendFlagBlockUpdate(location: Location, state: Banner) {
        val world = requireNotNull(location.world)
        val block = location.block
        world.getPlayersSeeingChunk(block.chunk).forEach { player ->
            player.sendBlockChange(location, block.blockData)
            player.sendBlockUpdate(location, state)
        }
    }

    /** 不依赖 Pattern 实现的 equals，按稳定的颜色与注册表类型逐层核对实际写入结果。 */
    private fun samePatterns(first: List<Pattern>, second: List<Pattern>): Boolean =
        first.size == second.size && first.zip(second).all { (left, right) ->
            left.color == right.color && left.pattern == right.pattern
        }

    /** 状态已在内存提交；此结果只区分磁盘快照是否同步成功。 */
    private fun persistedResult(): Result = if (save()) Result.SUCCESS else Result.PERSISTENCE_FAILED

    /** 旗帜样式提交结果同时报告实际找到并刷新的锚点数量。 */
    data class FlagDesignUpdate(val result: Result, val updated: Int, val total: Int)
}
