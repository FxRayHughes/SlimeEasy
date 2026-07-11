package top.maplex.slimeEasy.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.config.SEConfig

/**
 * `/se` 指令入口。
 *
 * 目前提供:
 * - `/se reload` —— 重新加载 config.yml 与当前语言文件。运行时数值和动态界面 / 消息文本立即生效;
 *   物品、分类、研究文本及功能开关因 Slimefun 注册后冻结, 需**重启服务端**方可生效。
 */
class SECommand : Command("se") {

    init {
        description = I18n.text("messages.command.description")
        usageMessage = "/se reload"
        permission = "slimeeasy.admin"
        aliases = listOf("slimeeasy")
    }

    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || !args[0].equals("reload", ignoreCase = true)) {
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

    companion object {
        /** 通过 Bukkit CommandMap 动态注册 (onEnable 调用)。 */
        fun register() {
            Bukkit.getCommandMap().register(SlimeEasy.instance.name.lowercase(), SECommand())
        }
    }
}
