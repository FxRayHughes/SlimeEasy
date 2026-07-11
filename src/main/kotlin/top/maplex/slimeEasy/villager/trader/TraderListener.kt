package top.maplex.slimeEasy.villager.trader

import top.maplex.slimeEasy.config.I18n
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantInventory
import top.maplex.slimeEasy.registry.VillagerItems
import top.maplex.slimeEasy.villager.catcher.VillagerCatcher
import top.maplex.slimeEasy.villager.core.VillagerConfig
import top.maplex.slimeEasy.villager.core.VillagerDisplay
import top.maplex.slimeEasy.villager.core.WorkstationMap

/**
 * 村民交易器的方块交互监听。
 *
 * 右键本方块 (原版 [PlayerInteractEvent]):
 * - **潜行 + 右键**: 取出 —— 有村民则退还满捕捉器并移除展示; 否则有工作站方块则退还该方块。
 * - **右键 (非潜行)**: 手持满捕捉器且未装村民 → 放入村民; 手持工作站方块且未装工作站 → 放入工作站;
 *   已装村民 → 打开虚拟交易界面。
 *
 * 交易界面关闭 ([InventoryCloseEvent]) 时把交易进度 (uses) 回存到方块。
 */
class TraderListener : Listener {

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        if (StorageCacheUtils.getBlock(block.location)?.sfId != VillagerItems.VILLAGER_TRADER_ID) return

        e.isCancelled = true // 接管交互, 阻止手持物品放置等原版行为
        route(block, e.player)
    }

    /**
     * 右键内嵌展示村民的重定向。
     *
     * 展示村民站在方块中心, 玩家右键往往先命中实体而非方块 (触发 [PlayerInteractEntityEvent]),
     * 导致方块交互失效。此处把"点到本交易器展示村民"等同于"右键交易器方块"。
     */
    @EventHandler
    fun onDisplayInteract(e: PlayerInteractEntityEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        val villager = e.rightClicked as? Villager ?: return
        if (!VillagerDisplay.isDisplay(villager)) return
        val block = villager.location.block
        if (StorageCacheUtils.getBlock(block.location)?.sfId != VillagerItems.VILLAGER_TRADER_ID) return
        e.isCancelled = true
        route(block, e.player)
    }

    /** 交互路由: 潜行取出, 否则放入 / 交易。 */
    private fun route(block: Block, player: Player) {
        if (player.isSneaking) extract(block, player) else insertOrTrade(block, player)
    }

    /** 潜行取出: 村民优先, 其次工作站。 */
    private fun extract(block: Block, player: Player) {
        val villager = TraderStore.getVillager(block)
        if (villager != null) {
            give(player, VillagerCatcher.fill(villager))
            TraderStore.setVillager(block, null)
            VillagerTrader.removeDisplay(block)
            player.playSound(block.location, Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f)
            player.sendMessage(I18n.text("messages.trader.removed-villager", "villager" to villager.professionLabel))
            return
        }
        val workstation = TraderStore.getWorkstation(block)
        if (workstation != null) {
            give(player, ItemStack(workstation))
            TraderStore.setWorkstation(block, null)
            player.sendMessage(I18n.text("messages.trader.removed-workstation", "workstation" to workstation.name))
            return
        }
        player.sendMessage(I18n.text("messages.trader.empty"))
    }

    /** 右键: 放入村民 / 工作站, 或打开交易。 */
    private fun insertOrTrade(block: Block, player: Player) {
        val hand = player.inventory.itemInMainHand
        val stored = TraderStore.getVillager(block)
        when {
            VillagerCatcher.isFilled(hand) && stored == null -> insertVillager(block, player, hand)
            WorkstationMap.isWorkstation(hand.type) && TraderStore.getWorkstation(block) == null ->
                insertWorkstation(block, player, hand)
            stored != null -> TraderMerchant.open(player, block, stored)
            else -> player.sendMessage(I18n.text("messages.trader.usage"))
        }
    }

    private fun insertVillager(block: Block, player: Player, hand: ItemStack) {
        val data = VillagerCatcher.dataOf(hand) ?: return
        TraderStore.setVillager(block, data)
        TraderStore.setLastRestock(block, System.currentTimeMillis()) // 起算补货计时
        VillagerTrader.spawnDisplay(block, data)
        consumeOne(player)
        player.playSound(block.location, Sound.ENTITY_VILLAGER_AMBIENT, 1f, 1f)
        player.sendMessage(I18n.text("messages.trader.inserted-villager", "villager" to data.professionLabel))
    }

    private fun insertWorkstation(block: Block, player: Player, hand: ItemStack) {
        TraderStore.setWorkstation(block, hand.type)
        consumeOne(player)
        player.sendMessage(I18n.text(
            "messages.trader.inserted-workstation",
            "workstation" to hand.type.name,
            "seconds" to VillagerConfig.traderRestockMillis / 1000
        ))
    }

    /** 交易界面关闭: 驱动升级、回存快照并移除代理村民。 */
    @EventHandler
    fun onClose(e: InventoryCloseEvent) {
        val player = e.player as? Player ?: return
        if (e.inventory !is MerchantInventory) return
        TraderMerchant.close(player)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        TraderMerchant.close(e.player)
    }

    /** 主手当前物品 -1。 */
    private fun consumeOne(player: Player) {
        val hand = player.inventory.itemInMainHand
        if (hand.amount <= 1) player.inventory.setItemInMainHand(null)
        else { hand.amount -= 1; player.inventory.setItemInMainHand(hand) }
    }

    /** 给予物品, 背包满则地面掉落。 */
    private fun give(player: Player, item: ItemStack) {
        player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
    }
}
