package top.maplex.slimeEasy.machine.clicker

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.machine.butcher.FakePlayerFactory

/**
 * 自动点击器的点击编排: 以屠夫机的 OP 假玩家手持 [handItem] 对正前方方块执行右键 / 左键。
 *
 * - **右键 Slimefun (粘液) 方块**: 直接调用其 [BlockUseHandler], 绕过 Slimefun 中央监听的研究解锁 gate;
 * - **右键原版方块**: 经 [BlockInteractor.useItemOn] 真正执行原版交互 (按钮 / 拉杆 / 骨粉等);
 * - **左键**: 经 [BlockInteractor.destroyBlock] 真正破坏方块。
 *
 * 点击后返回假玩家主手剩余物品 (反映消耗), 供机器回写物品槽。必须在主线程调用。
 */
object AutoClickerLogic {

    /**
     * 手持 [handItem] 点击 [machine] 正前方方块。
     *
     * @param doLeft 是否执行左键 (破坏)
     * @param doRight 是否执行右键 (交互)
     * @return 点击后主手剩余物品 (空则 null)
     */
    fun click(machine: Block, facing: BlockFace, handItem: ItemStack?, doLeft: Boolean, doRight: Boolean): ItemStack? {
        if (!doLeft && !doRight) return handItem
        val fake = FakePlayerFactory.get(machine.world) ?: return handItem
        val front = machine.getRelative(facing)

        FakePlayerFactory.positionAt(fake, machine.location.toCenterLocation())
        fake.inventory.setItemInMainHand(handItem)
        val face = facing.oppositeFace
        if (doRight) runCatching { rightClick(fake, front, face) }
        if (doLeft) runCatching { BlockInteractor.destroyBlock(fake, front) }
        val after = fake.inventory.itemInMainHand.takeIf { !it.type.isAir }
        fake.inventory.setItemInMainHand(null) // 清空, 避免残留影响下次 / 屠夫机
        return after
    }

    /**
     * 右键点击正前方方块。
     *
     * - **Slimefun 单方块**: 直派 [BlockUseHandler], 绕过研究解锁 gate;
     * - **其余方块**: 先派发 Bukkit [PlayerInteractEvent] —— 触发 Slimefun **多方块结构** (磨石 / 增强工作台等,
     *   由 `MultiBlockListener` 监听该事件) 与其他插件; 若未被拦截, 再走 NMS [BlockInteractor.useItemOn]
     *   执行原版交互 (按钮 / 拉杆 / 骨粉等)。`useItemOn` 自身不派发 PlayerInteractEvent, 故多方块必须靠此处补发。
     */
    private fun rightClick(fake: Player, front: Block, face: BlockFace) {
        if (front.type.isAir) return
        val hand = fake.inventory.itemInMainHand.takeIf { !it.type.isAir }
        val interact = PlayerInteractEvent(fake, Action.RIGHT_CLICK_BLOCK, hand, front, face, EquipmentSlot.HAND)

        val sfItem = StorageCacheUtils.getBlock(front.location)?.sfId?.let { SlimefunItem.getById(it) }
        if (sfItem != null) {
            sfItem.callItemHandler(BlockUseHandler::class.java) { it.onRightClick(PlayerRightClickEvent(interact)) }
            return
        }

        Bukkit.getPluginManager().callEvent(interact)
        if (interact.useInteractedBlock() != Event.Result.DENY) {
            BlockInteractor.useItemOn(fake, front, face)
        }
    }
}
