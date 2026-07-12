package top.maplex.slimeEasy.territory

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import top.maplex.slimeEasy.config.I18n
import java.util.UUID

/**
 * 五分钟成员邀请与所有权转让确认。
 * 会话不持久化，服务端重启或领地解散会自然撤销，避免过期授权跨重启生效。
 */
internal object TerritoryInvitations {
    private const val TTL_MILLIS = 5 * 60 * 1000L
    private val memberInvites = mutableListOf<Pending>()
    private val transferInvites = mutableListOf<Pending>()

    fun inviteMember(actor: Player, territory: Territory, target: Player): TerritoryService.Result {
        if (!TerritoryService.canManage(actor, territory, TerritoryManagement.MEMBERS)) {
            return TerritoryService.Result.NO_PERMISSION
        }
        if (target.uniqueId == territory.owner || target.uniqueId in territory.members) {
            return TerritoryService.Result.ALREADY_MEMBER
        }
        memberInvites.removeIf { it.territory == territory.id && it.target == target.uniqueId }
        memberInvites += Pending(
            territory.id, territory.owner, actor.uniqueId, target.uniqueId, System.currentTimeMillis() + TTL_MILLIS
        )
        sendButtons(
            target,
            actor.name,
            Bukkit.getOfflinePlayer(territory.owner).name ?: actor.name,
            false
        )
        actor.sendMessage(I18n.text("messages.territory.invite-sent", "player" to target.name))
        return TerritoryService.Result.SUCCESS
    }

    fun requestTransfer(owner: Player, territory: Territory, target: Player): TerritoryService.Result {
        if (owner.uniqueId != territory.owner && !TerritoryService.isAdmin(owner)) {
            return TerritoryService.Result.NO_PERMISSION
        }
        if (target.uniqueId !in territory.members) return TerritoryService.Result.NOT_MEMBER
        if (TerritoryService.ownedBy(target.uniqueId) != null) return TerritoryService.Result.TARGET_ALREADY_OWNS
        transferInvites.removeIf { it.territory == territory.id }
        transferInvites += Pending(
            territory.id, territory.owner, owner.uniqueId, target.uniqueId, System.currentTimeMillis() + TTL_MILLIS
        )
        sendButtons(target, owner.name, Bukkit.getOfflinePlayer(territory.owner).name ?: owner.name, true)
        owner.sendMessage(I18n.text("messages.territory.transfer-requested", "player" to target.name))
        return TerritoryService.Result.SUCCESS
    }

    fun respondMember(target: Player, ownerName: String, accept: Boolean): Boolean {
        val pending = find(memberInvites, target, ownerName) ?: return false
        memberInvites.remove(pending)
        if (!accept) {
            target.sendMessage(I18n.text("messages.territory.invite-denied"))
            Bukkit.getPlayer(pending.actor)?.sendMessage(I18n.text(
                "messages.territory.invite-declined-owner", "player" to target.name
            ))
            return true
        }
        val territory = TerritoryService.byId(pending.territory) ?: return false
        // 邀请发出后仍可能发生转让或撤权；接受时必须重验，不能让旧会话绕过即时权限收回。
        val inviter = Bukkit.getOfflinePlayer(pending.actor)
        if (territory.owner != pending.owner ||
            !TerritoryService.canManage(inviter, territory, TerritoryManagement.MEMBERS)) {
            return false
        }
        val result = TerritoryService.addMember(territory, target.uniqueId)
        TerritoryMessages.send(target, result)
        if (result.wasApplied()) {
            target.sendMessage(I18n.text("messages.territory.invite-accepted"))
            notifyDistinct(pending.actor, territory.owner,
                I18n.text("messages.territory.member-joined", "player" to target.name))
        }
        return true
    }

    fun respondTransfer(target: Player, ownerName: String, accept: Boolean): Boolean {
        val pending = find(transferInvites, target, ownerName) ?: return false
        transferInvites.remove(pending)
        if (!accept) {
            target.sendMessage(I18n.text("messages.territory.transfer-denied"))
            Bukkit.getPlayer(pending.actor)?.sendMessage(I18n.text(
                "messages.territory.transfer-declined-owner", "player" to target.name
            ))
            return true
        }
        val territory = TerritoryService.byId(pending.territory) ?: return false
        val result = TerritoryService.transfer(territory, target.uniqueId)
        TerritoryMessages.send(target, result)
        if (result.wasApplied()) {
            target.sendMessage(I18n.text("messages.territory.transfer-accepted"))
            notifyDistinct(pending.actor, pending.owner,
                I18n.text("messages.territory.transfer-complete", "player" to target.name))
        }
        return true
    }

    fun cancelFor(territory: UUID) {
        memberInvites.removeIf { it.territory == territory }
        transferInvites.removeIf { it.territory == territory }
    }

    private fun find(entries: MutableList<Pending>, target: Player, ownerName: String): Pending? {
        val now = System.currentTimeMillis()
        entries.removeIf { it.deadline < now }
        return entries.firstOrNull { pending ->
            pending.target == target.uniqueId && Bukkit.getOfflinePlayer(pending.owner).name.equals(ownerName, true)
        }
    }

    private fun sendButtons(target: Player, inviter: String, owner: String, transfer: Boolean) {
        val prefix = if (transfer) "transfer-" else ""
        val prompt = if (transfer) {
            I18n.component("messages.territory.transfer-invite", "owner" to owner)
        } else {
            I18n.component("messages.territory.invite", "inviter" to inviter, "owner" to owner)
        }
        target.sendMessage(
            prompt
                .append(I18n.component("messages.territory.accept-button").clickEvent(
                    ClickEvent.runCommand("/se territory ${prefix}accept $owner")
                ).hoverEvent(HoverEvent.showText(I18n.component("messages.territory.accept-hover"))))
                .append(I18n.component("messages.territory.deny-button").clickEvent(
                    ClickEvent.runCommand("/se territory ${prefix}deny $owner")
                ).hoverEvent(HoverEvent.showText(I18n.component("messages.territory.deny-hover"))))
        )
    }

    /** 邀请者可能是受委派成员；邀请者与主人不同时双方都应收到结果，但不能重复发送。 */
    private fun notifyDistinct(first: UUID, second: UUID, message: String) {
        Bukkit.getPlayer(first)?.sendMessage(message)
        if (second != first) Bukkit.getPlayer(second)?.sendMessage(message)
    }

    private data class Pending(
        val territory: UUID,
        val owner: UUID,
        val actor: UUID,
        val target: UUID,
        val deadline: Long
    )
}
