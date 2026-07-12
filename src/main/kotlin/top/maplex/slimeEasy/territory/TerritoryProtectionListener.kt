package top.maplex.slimeEasy.territory

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.Tameable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.event.vehicle.VehicleDamageEvent
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.projectiles.ProjectileSource
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.machine.butcher.ButcherLogic
import top.maplex.slimeEasy.machine.butcher.FakePlayerFactory
import java.util.UUID

/**
 * 原版事件保护层。
 * 所有玩家行为仍询问 Slimefun ProtectionManager，使其它保护模块与本领地按“全部允许”合并。
 */
internal class TerritoryProtectionListener : Listener {
    private val entryDenialNotices = mutableMapOf<UUID, Long>()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onBreak(event: BlockBreakEvent) {
        deny(event.player, event.block, Interaction.BREAK_BLOCK) { event.isCancelled = true }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onPlace(event: BlockPlaceEvent) {
        deny(event.player, event.block, Interaction.PLACE_BLOCK) { event.isCancelled = true }
    }

    /**
     * 必须早于 Slimefun 默认 NORMAL 监听器执行；否则 BlockUseHandler 已打开菜单后再取消事件没有作用。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        val territory = TerritoryService.byBlock(block.location)
        // 菜单只响应主手，避免同一次右键产生两次界面；副手仍继续经过普通方块交互鉴权。
        if (territory != null && event.action.isRightClick && event.hand == EquipmentSlot.HAND) {
            event.isCancelled = true
            val item = StorageCacheUtils.getSlimefunItem(block.location) ?: return
            if (!Slimefun.getPermissionsService().hasPermission(event.player, item)) {
                Slimefun.getLocalization().sendMessage(event.player, "messages.no-permission", true)
                return
            }
            if (!allowed(event.player, block.location, Interaction.INTERACT_BLOCK)) {
                event.player.sendMessage(I18n.text("messages.territory.action-denied"))
                return
            }
            TerritoryMenu.open(event.player, territory)
            return
        }
        deny(event.player, block, Interaction.INTERACT_BLOCK) { event.isCancelled = true }
    }

    /**
     * 实体交互同样必须在本插件的捕捉器、药剂与展示实体监听器之前拒绝；这些监听器统一忽略
     * 已取消事件，确保领地权限是发生业务副作用前的第一道门禁。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onEntityInteract(event: PlayerInteractEntityEvent) =
        deny(event.player, event.rightClicked.location, Interaction.INTERACT_ENTITY) { event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onDamage(event: EntityDamageByEntityEvent) {
        // Paper DamageSource 能保留 TNT 等间接伤害的真正触发者，旧事件 damager 仅作为兼容回退。
        val player = attackingPlayer(event.damageSource.causingEntity) ?: attackingPlayer(event.damager) ?: return
        val interaction = if (event.entity is Player) Interaction.ATTACK_PLAYER else Interaction.ATTACK_ENTITY
        val machineOwner = butcherOwner(player, event.entity)
        if (machineOwner == null) {
            deny(player, event.entity.location, interaction) { event.isCancelled = true }
        } else if (
            // 与机器前置校验保持一致：真实放置者是 OP 时，伤害阶段也不能重新进入保护链。
            !machineOwner.isOp && TerritoryService.at(event.entity.location) != null &&
            !allowed(machineOwner, event.entity.location, interaction)
        ) {
            // 自动攻击没有可接收提示的真实操作者，只取消本次命中，避免向在线 owner 每 tick 刷屏。
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onBucketEmpty(event: PlayerBucketEmptyEvent) =
        deny(
            event.player,
            event.blockClicked.getRelative(event.blockFace),
            Interaction.PLACE_BLOCK
        ) { event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onBucketFill(event: PlayerBucketFillEvent) =
        deny(event.player, event.blockClicked, Interaction.BREAK_BLOCK) { event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onIgnite(event: BlockIgniteEvent) {
        val player = event.player
        if (TerritoryService.at(event.block.location) != null &&
            (player == null || !allowed(player, event.block.location, Interaction.PLACE_BLOCK))
        ) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onHangingPlace(event: HangingPlaceEvent) {
        val player = event.player ?: return
        deny(player, event.entity.location, Interaction.PLACE_BLOCK) { event.isCancelled = true }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onHangingBreak(event: HangingBreakByEntityEvent) {
        val player = attackingPlayer(event.remover) ?: return
        deny(player, event.entity.location, Interaction.BREAK_BLOCK) { event.isCancelled = true }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onVehicleEnter(event: VehicleEnterEvent) {
        val player = event.entered as? Player ?: return
        deny(player, event.vehicle.location, Interaction.INTERACT_ENTITY) { event.isCancelled = true }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onVehicleDamage(event: VehicleDamageEvent) {
        val player = attackingPlayer(event.attacker) ?: return
        deny(player, event.vehicle.location, Interaction.ATTACK_ENTITY) { event.isCancelled = true }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private fun onVehicleMove(event: VehicleMoveEvent) {
        if (sameChunk(event.from, event.to)) return
        val source = TerritoryService.at(event.from)
        val destination = TerritoryService.at(event.to)
        val players = event.vehicle.passengers.filterIsInstance<Player>()
        val denied = destination?.let { territory ->
            players.filterNot { TerritoryService.canEnter(it, territory) }
        }.orEmpty()
        if (denied.isNotEmpty()) {
            event.vehicle.teleport(event.from)
            denied.forEach(::sendEntryDenied)
            return
        }
        players.forEach { notifyTransition(it, source, destination) }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { TerritoryService.at(it.location) != null }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { TerritoryService.at(it.location) != null }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onBurn(event: BlockBurnEvent) {
        if (TerritoryService.at(event.block.location) != null) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (TerritoryService.at(event.block.location) != null) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (crossesBoundary(event.block, event.blocks, event.direction)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (crossesBoundary(event.block, event.blocks, event.direction)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onFlow(event: BlockFromToEvent) {
        val source = territoryId(event.block.location)
        val target = territoryId(event.toBlock.location)
        if (source != target && (source != null || target != null)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onSpread(event: BlockSpreadEvent) {
        val source = territoryId(event.source.location)
        val target = territoryId(event.block.location)
        if (source != target && (source != null || target != null)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onMove(event: PlayerMoveEvent) {
        val destination = event.to
        if (sameChunk(event.from, destination)) return
        val territory = TerritoryService.at(destination)
        if (territory != null && !TerritoryService.canEnter(event.player, territory)) {
            event.isCancelled = true
            sendEntryDenied(event.player)
            return
        }
        if (territory != null && !territory.allowFlight &&
            !TerritoryService.isAdmin(event.player) && territory.owner != event.player.uniqueId
        ) {
            event.player.isFlying = false
        }
    }

    /**
     * 只在事件最终未取消且领地身份确实变化后提示，避免同一领地内部跨区块刷屏，
     * 也避免其它保护插件在更晚阶段取消移动时提前发送错误提示。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun onMoveCompleted(event: PlayerMoveEvent) {
        if (sameChunk(event.from, event.to)) return
        notifyTransition(event.player, TerritoryService.at(event.from), TerritoryService.at(event.to))
    }

    /** PlayerTeleportEvent 使用独立 HandlerList，必须单独在最终阶段发送边界提示。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun onTeleportCompleted(event: PlayerTeleportEvent) {
        notifyTransition(event.player, TerritoryService.at(event.from), TerritoryService.at(event.to))
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onTeleport(event: PlayerTeleportEvent) {
        val territory = TerritoryService.at(event.to)
        if (territory != null && !TerritoryService.canEnter(event.player, territory)) {
            event.isCancelled = true
            sendEntryDenied(event.player)
        } else if (territory != null && !territory.allowFlight &&
            territory.owner != event.player.uniqueId && !TerritoryService.isAdmin(event.player)
        ) {
            event.player.isFlying = false
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private fun onToggleFlight(event: PlayerToggleFlightEvent) {
        if (!event.isFlying) return
        val territory = TerritoryService.at(event.player.location) ?: return
        if (!territory.allowFlight && territory.owner != event.player.uniqueId &&
            !TerritoryService.isAdmin(event.player)
        ) {
            event.isCancelled = true
            event.player.isFlying = false
            event.player.sendMessage(I18n.text("messages.territory.flight-denied"))
        }
    }

    @EventHandler
    private fun onJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTask(SlimeEasy.instance, Runnable {
            val territory = TerritoryService.at(event.player.location) ?: return@Runnable
            if (!TerritoryService.canEnter(event.player, territory)) {
                TerritoryService.evictUnauthorized(territory)
            } else if (!territory.allowFlight && territory.owner != event.player.uniqueId &&
                !TerritoryService.isAdmin(event.player)
            ) {
                event.player.isFlying = false
            }
        })
    }

    /** 玩家离线后清理仅用于提示限频的瞬时状态，避免长期保存无效 UUID。 */
    @EventHandler
    private fun onQuit(event: PlayerQuitEvent) {
        entryDenialNotices.remove(event.player.uniqueId)
    }

    private fun crossesBoundary(piston: Block, blocks: List<Block>, direction: org.bukkit.block.BlockFace): Boolean {
        val pistonTerritory = territoryId(piston.location)
        return blocks.any { block ->
            val source = territoryId(block.location)
            val target = territoryId(block.getRelative(direction).location)
            source != target || (source != null && source != pistonTerritory)
        }
    }

    private fun allowed(player: OfflinePlayer, location: Location, interaction: Interaction): Boolean =
        TerritoryProtectionBridge.hasPermission(player, location, interaction)

    private fun deny(player: Player, block: Block, interaction: Interaction, cancel: () -> Unit) =
        deny(player, block.location, interaction, cancel)

    private fun deny(player: Player, location: Location, interaction: Interaction, cancel: () -> Unit) {
        if (TerritoryService.at(location) != null && !allowed(player, location, interaction)) {
            cancel()
            player.sendMessage(I18n.text("messages.territory.action-denied"))
        }
    }

    /**
     * 屠夫用固定 UUID 假玩家制造原版玩家伤害，但权限必须属于机器放置者。
     * [ButcherLogic.KEY_KILLER] 只在调用 `damage` 前写入当前目标，且仅接受本工厂对象身份，
     * 因此不能被普通玩家通过伪造 PDC 借用其他玩家的领地权限。
     */
    private fun butcherOwner(attacker: Player, target: Entity): OfflinePlayer? {
        if (!FakePlayerFactory.isFake(attacker)) return null
        val rawOwner = target.persistentDataContainer.get(ButcherLogic.KEY_KILLER, PersistentDataType.STRING)
            ?: return null
        val ownerId = runCatching { UUID.fromString(rawOwner) }.getOrNull() ?: return null
        return Bukkit.getOfflinePlayer(ownerId)
    }

    /** 递归追溯投射物、玩家点燃的 TNT 与驯服实体，封堵间接伤害绕过。 */
    private fun attackingPlayer(entity: Entity?): Player? = when (entity) {
        is Player -> entity
        is Projectile -> playerSource(entity.shooter)
        is TNTPrimed -> attackingPlayer(entity.source)
        is Tameable -> entity.owner as? Player
        else -> null
    }

    private fun playerSource(source: ProjectileSource?): Player? = when (source) {
        is Player -> source
        is Entity -> attackingPlayer(source)
        else -> null
    }

    /** 使用动作栏显示一次边界变化；领地直达领地时合并为单条，避免聊天栏刷屏。 */
    private fun notifyTransition(player: Player, source: Territory?, destination: Territory?) {
        if (source?.id == destination?.id) return
        val sourceOwner = source?.let(::ownerName)
        val destinationOwner = destination?.let(::ownerName)
        val message = when {
            sourceOwner != null && destinationOwner != null -> I18n.component(
                "messages.territory.switched", "from" to sourceOwner, "to" to destinationOwner
            )
            sourceOwner != null -> I18n.component("messages.territory.left", "owner" to sourceOwner)
            destinationOwner != null -> I18n.component("messages.territory.entered", "owner" to destinationOwner)
            else -> return
        }
        player.sendActionBar(message)
    }

    /** 边界处连续移动会重复触发取消事件，提示按玩家限频以免刷屏。 */
    private fun sendEntryDenied(player: Player) {
        val now = System.currentTimeMillis()
        if ((entryDenialNotices[player.uniqueId] ?: 0L) > now) return
        entryDenialNotices[player.uniqueId] = now + ENTRY_DENIAL_NOTICE_INTERVAL
        player.sendMessage(I18n.text("messages.territory.entry-denied"))
    }

    private fun ownerName(territory: Territory): String =
        Bukkit.getOfflinePlayer(territory.owner).name ?: territory.owner.toString()

    private fun territoryId(location: Location): java.util.UUID? = TerritoryService.at(location)?.id
    private fun sameChunk(first: Location, second: Location): Boolean =
        first.world === second.world &&
            first.blockX shr 4 == second.blockX shr 4 &&
            first.blockZ shr 4 == second.blockZ shr 4

    companion object {
        /** 禁入边界提示最多每 1.5 秒一次，不影响每个移动事件本身的权限拦截。 */
        private const val ENTRY_DENIAL_NOTICE_INTERVAL = 1_500L
    }
}
