package top.maplex.slimeEasy.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.territory.TerritoryCommands

/**
 * `/se` 指令入口。
 *
 * 子命令按职责独立鉴权，不能在根命令设置管理员权限，否则普通玩家无法接受领地邀请。
 * 目前提供:
 * - `/se reload` —— 重新加载 config.yml 与当前语言文件。运行时数值和动态界面 / 消息文本立即生效;
 *   物品、分类、研究文本及功能开关因 Slimefun 注册后冻结, 需**重启服务端**方可生效。
 * - `/se territory ...` —— 玩家邀请确认与管理员领地救援入口。
 */
class SECommand : Command("se") {

    init {
        description = I18n.text("messages.command.description")
        usageMessage = "/se reload | /se territory ..."
        aliases = listOf("slimeeasy")
    }

    /** 按首级子命令路由，并在各分支内部执行对应权限检查。 */
    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(I18n.text("messages.command.usage"))
            return true
        }
        if (args[0].equals("territory", ignoreCase = true)) {
            return TerritoryCommands.execute(sender, args.drop(1))
        }
        if (!args[0].equals("reload", ignoreCase = true)) {
            sender.sendMessage(I18n.text("messages.command.usage"))
            return true
        }
        if (!sender.hasPermission("slimeeasy.admin")) {
            sender.sendMessage(I18n.text("messages.command.no-permission"))
            return true
        }
        runCatching {
            SEConfig.reload()
            I18n.load()
        }
            .onSuccess {
                sender.sendMessage(I18n.text("messages.command.reload.success"))
                sender.sendMessage(I18n.text("messages.command.reload.runtime-applied"))
                sender.sendMessage(I18n.text("messages.command.reload.restart-required"))
            }
            .onFailure {
                sender.sendMessage(I18n.text("messages.command.reload.failed", "error" to it.message))
                SlimeEasy.instance.logger.warning(I18n.text("logs.command.reload-failed", "error" to it.message))
            }
        return true
    }

    /** 只向具备管理员权限的发送者展示领地救援子命令。 */
    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> = when {
        args.size == 1 -> listOf("reload", "territory").filter { it.startsWith(args[0], true) }
        args.firstOrNull()?.equals("territory", true) == true -> TerritoryCommands.complete(sender, args.drop(1))
        else -> emptyList()
    }

    companion object {
        /** 通过 Bukkit CommandMap 动态注册 (onEnable 调用)。 */
        fun register() {
            Bukkit.getCommandMap().register(SlimeEasy.instance.name.lowercase(), SECommand())
        }
    }
}
