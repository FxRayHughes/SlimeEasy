package top.maplex.slimeEasy.territory

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.I18n
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 所有权转让的单次玩家名输入；聊天线程只消费文本，所有 Bukkit 状态访问切回主线程。 */
internal class TerritoryInputListener : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    private fun onChat(event: AsyncPlayerChatEvent) {
        val playerId = event.player.uniqueId
        val request = requests[playerId] ?: return
        if (request.deadline < System.currentTimeMillis()) {
            if (requests.remove(playerId, request)) notifyExpired(playerId)
            return
        }
        if (!requests.remove(playerId, request)) return
        event.isCancelled = true
        val name = event.message.trim()
        Bukkit.getScheduler().runTask(SlimeEasy.instance, Runnable {
            if (name.equals("cancel", true) || name == "取消") {
                event.player.sendMessage(I18n.text("messages.territory.input-cancelled"))
                return@Runnable
            }
            val territory = TerritoryService.byId(request.territory)
            if (territory == null) {
                event.player.sendMessage(I18n.text("messages.territory.result.not-found"))
                return@Runnable
            }
            val target = Bukkit.getPlayerExact(name)
            if (target == null) {
                event.player.sendMessage(I18n.text("messages.territory.player-not-online"))
                return@Runnable
            }
            val result = TerritoryInvitations.requestTransfer(event.player, territory, target)
            TerritoryMessages.send(event.player, result)
        })
    }

    companion object {
        private const val INPUT_TTL_MILLIS = 60_000L
        private const val INPUT_TTL_TICKS = 20L * 60L
        /** 写入发生在主线程、消费发生在聊天线程，因此必须使用并发映射。 */
        private val requests = ConcurrentHashMap<UUID, Request>()

        /** 转让仍需精确输入当前成员名；新增成员邀请已改为在线玩家头颅列表。 */
        fun promptTransfer(player: org.bukkit.entity.Player, territory: Territory) {
            val request = Request(territory.id, System.currentTimeMillis() + INPUT_TTL_MILLIS)
            requests[player.uniqueId] = request
            player.closeInventory()
            player.sendMessage(I18n.text("messages.territory.input-transfer"))
            Bukkit.getScheduler().runTaskLater(SlimeEasy.instance, Runnable {
                if (requests.remove(player.uniqueId, request)) {
                    Bukkit.getPlayer(player.uniqueId)?.sendMessage(I18n.text("messages.territory.input-expired"))
                }
            }, INPUT_TTL_TICKS)
        }

        /** 超时判定来自聊天线程，实际消息发送必须切回服务器主线程。 */
        private fun notifyExpired(player: UUID) {
            Bukkit.getScheduler().runTask(SlimeEasy.instance, Runnable {
                Bukkit.getPlayer(player)?.sendMessage(I18n.text("messages.territory.input-expired"))
            })
        }

        private data class Request(val territory: UUID, val deadline: Long)
    }
}
