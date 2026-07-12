package top.maplex.slimeEasy.territory

import org.bukkit.command.CommandSender
import top.maplex.slimeEasy.config.I18n

/** 把内部结果码统一映射到本地化消息，避免业务类散落玩家可见文本。 */
internal object TerritoryMessages {
    /** 将非成功结果发送为对应的本地化提示。 */
    fun send(sender: CommandSender, result: TerritoryService.Result) {
        if (result == TerritoryService.Result.SUCCESS) return
        sender.sendMessage(I18n.text("messages.territory.result.${result.name.lowercase().replace('_', '-')}"))
    }
}

/** 写盘失败表示业务状态已提交到内存，调用方仍应刷新界面或发送业务成功通知。 */
internal fun TerritoryService.Result.wasApplied(): Boolean =
    this == TerritoryService.Result.SUCCESS || this == TerritoryService.Result.PERSISTENCE_FAILED
