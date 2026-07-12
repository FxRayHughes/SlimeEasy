package top.maplex.slimeEasy.territory

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import top.maplex.slimeEasy.config.I18n

/** `/se territory` 玩家确认与管理员救援命令。 */
internal object TerritoryCommands {
    fun execute(sender: CommandSender, args: List<String>): Boolean {
        if (args.isEmpty()) return usage(sender)
        return when (args[0].lowercase()) {
            "accept", "deny", "transfer-accept", "transfer-deny" -> respond(sender, args)
            "admin" -> admin(sender, args.drop(1))
            else -> usage(sender)
        }
    }

    fun complete(sender: CommandSender, args: List<String>): List<String> = when {
        args.size == 1 -> buildList {
            addAll(listOf("accept", "deny", "transfer-accept", "transfer-deny"))
            if (sender.isOp || sender.hasPermission(TerritoryService.ADMIN_PERMISSION)) add("admin")
        }.filter { it.startsWith(args[0], true) }
        args.size == 2 && args[0].equals("admin", true) &&
            (sender.isOp || sender.hasPermission(TerritoryService.ADMIN_PERMISSION)) ->
            listOf("here", "info", "disband", "transfer").filter { it.startsWith(args[1], true) }
        else -> emptyList()
    }

    private fun respond(sender: CommandSender, args: List<String>): Boolean {
        val player = sender as? Player ?: return usage(sender)
        if (args.size != 2) return usage(sender)
        val handled = when (args[0].lowercase()) {
            "accept" -> TerritoryInvitations.respondMember(player, args[1], true)
            "deny" -> TerritoryInvitations.respondMember(player, args[1], false)
            "transfer-accept" -> TerritoryInvitations.respondTransfer(player, args[1], true)
            else -> TerritoryInvitations.respondTransfer(player, args[1], false)
        }
        if (!handled) player.sendMessage(I18n.text("messages.territory.invite-not-found"))
        return true
    }

    private fun admin(sender: CommandSender, args: List<String>): Boolean {
        if (!sender.isOp && !sender.hasPermission(TerritoryService.ADMIN_PERMISSION)) {
            sender.sendMessage(I18n.text("messages.command.no-permission"))
            return true
        }
        if (args.isEmpty()) return usage(sender)
        val territory = when (args[0].lowercase()) {
            "here" -> (sender as? Player)?.let { TerritoryService.at(it.location) }
            "info", "disband", "transfer" -> args.getOrNull(1)
                ?.let(::offline)?.let { TerritoryService.ownedBy(it.uniqueId) }
            else -> null
        }
        if (territory == null) {
            sender.sendMessage(I18n.text("messages.territory.not-found"))
            return true
        }
        when (args[0].lowercase()) {
            "here", "info" -> sender.sendMessage(I18n.text("messages.territory.admin-info",
                "id" to territory.id, "owner" to Bukkit.getOfflinePlayer(territory.owner).name,
                "chunks" to territory.chunks.size, "locked" to territory.locked))
            "disband" -> {
                val result = TerritoryService.disband(territory)
                TerritoryMessages.send(sender, result)
                if (result.wasApplied()) sender.sendMessage(I18n.text("messages.territory.disbanded"))
            }
            "transfer" -> {
                val target = args.getOrNull(2)?.let(::offline)
                if (target == null || TerritoryService.ownedBy(target.uniqueId) != null) {
                    sender.sendMessage(I18n.text("messages.territory.admin-transfer-failed"))
                } else {
                    TerritoryMessages.send(sender, TerritoryService.forceTransfer(territory, target.uniqueId))
                }
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun offline(name: String) = Bukkit.getOfflinePlayer(name)

    private fun usage(sender: CommandSender): Boolean {
        sender.sendMessage(I18n.text("messages.territory.command-usage"))
        return true
    }
}
