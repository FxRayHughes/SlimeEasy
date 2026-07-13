package top.maplex.slimeEasy.machine.butcher

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent

/**
 * 隐藏机器假玩家触发的原版进度公告。
 *
 * 假玩家必须保留 [net.minecraft.server.level.ServerPlayer] 身份，原版攻击和 Slimefun 物品交互才会
 * 走完整玩家路径；因此它也会满足“怪物猎人”等进度条件。这里只清空公告消息，不取消真实玩家事件，
 * 也不修改全服 `announceAdvancements` 游戏规则。
 */
class FakePlayerAdvancementListener : Listener {

    /** 在广播前清空本工厂假玩家的公告消息，真实玩家的进度展示保持不变。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAdvancementDone(event: PlayerAdvancementDoneEvent) {
        if (FakePlayerFactory.isFake(event.player)) event.message(null)
    }
}
