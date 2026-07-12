package top.maplex.slimeEasy.territory

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.I18n
import java.util.IdentityHashMap
import java.util.UUID

/** 放下后创建唯一领地，拆除采用10秒二次确认。 */
internal class TerritoryCore(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /** 注册玩家放置创建协议；破坏预检由原始 Bukkit 事件监听器统一处理。 */
    override fun preRegister() {
        addItemHandler(object : BlockPlaceHandler(false) {
            /** 高优先级阶段只预检；最终提交由 MONITOR 监听器确认没有其它插件拒绝。 */
            override fun onPlayerPlace(event: BlockPlaceEvent) {
                if (!passesProtection(event, Interaction.PLACE_BLOCK)) return
                val result = TerritoryService.checkCreate(event.player, event.block.location)
                if (result == TerritoryService.Result.SUCCESS) {
                    TerritoryBlockTransactions.prepareCorePlace(event)
                } else {
                    event.isCancelled = true
                    TerritoryMessages.send(event.player, result)
                }
            }
        })
    }
}

/**
 * 每面旗帜是一个3×3区块覆盖锚点；世界方块可显示领地底色与花纹，但 Slimefun 掉落始终使用
 * 注册时的无花纹白旗模板，避免把领地样式复制到玩家物品中。
 */
internal class TerritoryFlag(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /** 注册旗帜扩区协议；拆除预检由原始 Bukkit 事件监听器统一处理。 */
    override fun preRegister() {
        addItemHandler(object : BlockPlaceHandler(false) {
            /** 预检扩区拓扑并保留一次性选择，直到最终事件真正提交。 */
            override fun onPlayerPlace(event: BlockPlaceEvent) {
                if (!passesProtection(event, Interaction.PLACE_BLOCK)) return
                val selected = TerritorySessions.expansionSelection(event.player.uniqueId)
                val result = TerritoryService.checkAddFlag(event.player, event.block.location, selected)
                if (result == TerritoryService.Result.SUCCESS) {
                    TerritoryBlockTransactions.prepareFlagPlace(event, selected)
                } else {
                    event.isCancelled = true
                    TerritoryMessages.send(event.player, result)
                }
            }
        })
    }
}

/**
 * 放置预检来自 Slimefun 的 HIGHEST handler，拆除预检直接监听原始事件的 HIGH 阶段；两者都可能被
 * 更晚的保护监听器拒绝，因此用事件对象身份暂存操作，只在 MONITOR 确认未取消后提交内存与存档。
 */
internal class TerritoryBlockTransactionListener : Listener {
    /**
     * 必须监听原始 Bukkit 事件，而不能依赖 Slimefun 的 BlockBreakHandler：当 Slimefun 方块数据尚未加载时，
     * 该 handler 会在事件 MONITOR 阶段结束后才回调，届时无法再把释放区块操作提交到同一事件事务。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private fun prepareBreak(event: BlockBreakEvent) {
        val territory = TerritoryService.byBlock(event.block.location) ?: return
        if (!passesProtection(event, Interaction.BREAK_BLOCK)) return
        val block = TerritoryBlock.of(event.block.location)
        if (territory.core == block) {
            prepareCoreBreak(event, territory, block)
        } else {
            prepareFlagBreak(event)
        }
    }

    /** 核心仍要求主人在10秒内二次破坏，确认通过后才进入最终提交阶段。 */
    private fun prepareCoreBreak(event: BlockBreakEvent, territory: Territory, block: TerritoryBlock) {
        val result = TerritoryService.checkDisband(event.player, territory)
        if (result != TerritoryService.Result.SUCCESS) {
            event.isCancelled = true
            TerritoryMessages.send(event.player, result)
            return
        }
        if (!TerritorySessions.confirmCoreBreak(event.player.uniqueId, block)) {
            event.isCancelled = true
            event.player.sendMessage(I18n.text("messages.territory.confirm-core-break"))
            return
        }
        TerritoryBlockTransactions.prepareCoreBreak(event, territory.id)
    }

    /** 已绑定旗帜只有在拆除后仍保持核心连通时才允许进入最终提交阶段。 */
    private fun prepareFlagBreak(event: BlockBreakEvent) {
        val result = TerritoryService.checkRemoveFlag(event.player, event.block.location)
        if (result == TerritoryService.Result.SUCCESS) {
            TerritoryBlockTransactions.prepareFlagBreak(event)
        } else {
            event.isCancelled = true
            TerritoryMessages.send(event.player, result)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    private fun onPlace(event: BlockPlaceEvent) = TerritoryBlockTransactions.commit(event)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    private fun onBreak(event: BlockBreakEvent) = TerritoryBlockTransactions.commit(event)
}

/** 同步事件生命周期内的短期事务表；键使用事件身份，避免坐标相同的后续事件误消费。 */
private object TerritoryBlockTransactions {
    private val pending = IdentityHashMap<Event, Operation>()

    fun prepareCorePlace(event: BlockPlaceEvent) { pending[event] = Operation.CorePlace }
    fun prepareFlagPlace(event: BlockPlaceEvent, selected: UUID?) { pending[event] = Operation.FlagPlace(selected) }
    fun prepareCoreBreak(event: BlockBreakEvent, territory: UUID) { pending[event] = Operation.CoreBreak(territory) }
    fun prepareFlagBreak(event: BlockBreakEvent) { pending[event] = Operation.FlagBreak }

    /** 提交后若仅写盘失败则保留已发生的方块事件，避免内存状态与世界方块反向分裂。 */
    fun commit(event: Event) {
        val operation = pending.remove(event) ?: return
        if ((event as org.bukkit.event.Cancellable).isCancelled) return
        val player = when (event) {
            is BlockPlaceEvent -> event.player
            is BlockBreakEvent -> event.player
            else -> return
        }
        val location = when (event) {
            is BlockPlaceEvent -> event.block.location
            is BlockBreakEvent -> event.block.location
            else -> return
        }
        val result = when (operation) {
            Operation.CorePlace -> TerritoryService.create(player, location)
            is Operation.FlagPlace -> TerritoryService.addFlag(player, location, operation.selected)
            is Operation.CoreBreak -> TerritoryService.byId(operation.territory)
                ?.let(TerritoryService::disband) ?: TerritoryService.Result.NO_PERMISSION
            Operation.FlagBreak -> TerritoryService.removeFlag(player, location)
        }
        if (result != TerritoryService.Result.SUCCESS && result != TerritoryService.Result.PERSISTENCE_FAILED) {
            (event as org.bukkit.event.Cancellable).isCancelled = true
            TerritoryMessages.send(player, result)
            return
        }
        if (operation is Operation.FlagPlace) {
            TerritorySessions.consumeExpansion(player.uniqueId, operation.selected)
        }
        if (result == TerritoryService.Result.PERSISTENCE_FAILED) TerritoryMessages.send(player, result)
        val message = when (operation) {
            Operation.CorePlace -> "messages.territory.created"
            is Operation.FlagPlace -> "messages.territory.chunk-added"
            is Operation.CoreBreak -> "messages.territory.disbanded"
            Operation.FlagBreak -> "messages.territory.chunk-removed"
        }
        player.sendMessage(I18n.text(message))
    }

    private sealed interface Operation {
        data object CorePlace : Operation
        data class FlagPlace(val selected: UUID?) : Operation
        data class CoreBreak(val territory: UUID) : Operation
        data object FlagBreak : Operation
    }
}

/** 显式询问完整 Slimefun 保护链；拒绝时中止锚点 handler，且不创建 pending 事务。 */
private fun passesProtection(event: Event, interaction: Interaction): Boolean {
    val player = when (event) {
        is BlockPlaceEvent -> event.player
        is BlockBreakEvent -> event.player
        else -> return false
    }
    val location = when (event) {
        is BlockPlaceEvent -> event.block.location
        is BlockBreakEvent -> event.block.location
        else -> return false
    }
    if (TerritoryProtectionBridge.hasPermission(player, location, interaction)) return true
    (event as org.bukkit.event.Cancellable).isCancelled = true
    player.sendMessage(I18n.text("messages.territory.action-denied"))
    return false
}
