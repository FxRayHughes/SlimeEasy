package top.maplex.slimeEasy.feature.survey

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/**
 * 勘察尺"潜行 + 左键"切换展示形式的监听器。
 *
 * 左键交互不属于 Slimefun 的 [io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler]
 * (仅右键), 故以原版 [PlayerInteractEvent] 承接: 玩家潜行左键 (空气或方块) 手持勘察尺时,
 * 翻转其展示形式 (聊天栏 / 箱子界面) 并取消事件, 避免生存模式下顺带破坏方块。
 *
 * 该切换对普通版与进阶版一致适用 (与层级数无关)。
 */
class SurveyDisplayListener : Listener {

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        if (!e.player.isSneaking) return
        if (e.action != Action.LEFT_CLICK_AIR && e.action != Action.LEFT_CLICK_BLOCK) return

        val item = e.item ?: return
        if (SlimefunItem.getByItem(item) !is SurveyRuler) return

        // 取消原版左键 (破坏 / 攻击), 仅执行展示形式切换
        e.isCancelled = true
        val next = SurveyState.toggleDisplay(item)
        val label = if (next == SurveyDisplay.GUI) "箱子界面" else "聊天栏"
        e.player.sendMessage("§6[勘察尺] §7已切换展示形式: §f$label")
    }
}
