package top.maplex.slimeEasy.storage.drawer

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.block.Block
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID

/**
 * 抽屉展示框的交互监听。
 *
 * - 左键 ([EntityDamageByEntityEvent]): 取出一个; Shift+左键取出一组;
 * - 右键 ([PlayerInteractEntityEvent], 仅主手): 存入手中该组; 双击 (≤[DOUBLE_MS])
 *   存入背包全部同类; 空手 Shift+右键打开升级 GUI;
 * - 经验模式: 右键存入全部经验, 左键取出一级经验。
 *
 * 通过展示框 UUID 与抽屉 BlockData 记录比对来确认归属, 避免误伤普通展示框。
 */
class DrawerListener : Listener {

    /** 玩家上次右键抽屉展示框的时间戳, 用于双击判定。 */
    private val lastRightClick = HashMap<UUID, Long>()

    @EventHandler
    fun onLeftClick(e: EntityDamageByEntityEvent) {
        val frame = e.entity as? ItemFrame ?: return
        val player = e.damager as? Player ?: return
        val (drawer, block) = resolve(frame) ?: return
        e.isCancelled = true // 防止把展示框打掉
        if (isExp(block)) { ExpMenu.open(block, player); return }
        if (player.isSneaking) DrawerInteract.withdrawStack(drawer, block, player)
        else DrawerInteract.withdrawOne(drawer, block, player)
    }

    @EventHandler
    fun onRightClick(e: PlayerInteractEntityEvent) {
        if (e.hand != EquipmentSlot.HAND) return // 去重: 只处理主手
        val frame = e.rightClicked as? ItemFrame ?: return
        val (drawer, block) = resolve(frame) ?: return
        e.isCancelled = true
        val player = e.player
        // 空手 Shift+右键: 打开抽屉主界面 (当前物品 + 升级按钮)
        val hand = player.inventory.itemInMainHand
        if (hand.type.isAir && player.isSneaking) {
            if (isExp(block)) ExpMenu.open(block, player) else DrawerMenu.open(drawer, block, player)
            return
        }
        // 经验模式: 任意右键打开经验操作 GUI
        if (isExp(block)) { ExpMenu.open(block, player); return }
        // 双击判定
        val now = System.currentTimeMillis()
        val last = lastRightClick.put(player.uniqueId, now) ?: 0L
        if (now - last <= DOUBLE_MS) {
            lastRightClick.remove(player.uniqueId)
            DrawerInteract.depositAll(drawer, block, player, hand.takeIf { !it.type.isAir })
        } else {
            DrawerInteract.depositHand(drawer, block, player, hand)
        }
    }

    /** 保护抽屉展示框不被爆炸 / 实体 / 环境破坏 (不固定后需自行拦截)。 */
    @EventHandler
    fun onHangingBreak(e: HangingBreakEvent) {
        val frame = e.entity as? ItemFrame ?: return
        if (resolve(frame) != null) e.isCancelled = true
    }

    private fun isExp(block: Block): Boolean =
        top.maplex.slimeEasy.storage.upgrade.UpgradeStore.resolve(block.location).hasExpStorage

    /** 玩家离线时清理其双击时间戳, 防止 map 累积。 */
    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        lastRightClick.remove(e.player.uniqueId)
    }

    /**
     * 解析展示框所属的抽屉方块。
     *
     * 展示框生成于抽屉本体所在坐标, 故优先检查该坐标; 再退而检查六邻
     * (容错朝向偏移)。最终以抽屉 BlockData 记录的展示框 UUID 精确校验归属。
     */
    private fun resolve(frame: ItemFrame): Pair<Drawer, Block>? {
        val self = frame.location.block
        val candidates = listOf(self) + org.bukkit.block.BlockFace.entries
            .filter { it.isCartesian }.map { self.getRelative(it) }
        for (block in candidates) {
            if (!StorageCacheUtils.hasBlock(block.location)) continue
            if (!DrawerDisplay.isDrawerFrame(block, frame.uniqueId)) continue
            val id = StorageCacheUtils.getBlock(block.location)?.sfId ?: continue
            val drawer = SlimefunItem.getById(id) as? Drawer ?: continue
            return drawer to block
        }
        return null
    }

    companion object {
        private const val DOUBLE_MS = 350L
    }
}
