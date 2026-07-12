package top.maplex.slimeEasy.feature.survey

import top.maplex.slimeEasy.config.I18n
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
 * 该切换对普通版与进阶版一致适用 (与层级数无关)。点击方块时必须尊重领地与其它保护插件
 * 已取消的事件，不能借由本监听器继续修改手持物品状态。
 */
class SurveyDisplayListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInteract(e: PlayerInteractEvent) {
        if (!e.player.isSneaking) return
        if (e.action != Action.LEFT_CLICK_AIR && e.action != Action.LEFT_CLICK_BLOCK) return

        val item = e.item ?: return
        if (SlimefunItem.getByItem(item) !is SurveyRuler) return

        // 取消原版左键 (破坏 / 攻击), 仅执行展示形式切换
        e.isCancelled = true
        val next = SurveyState.toggleDisplay(item)
        val label = if (next == SurveyDisplay.GUI) I18n.text("names.survey-display.gui") else I18n.text("names.survey-display.chat")
        e.player.sendMessage(I18n.text("messages.survey.display-changed", "display" to label))
    }
}
