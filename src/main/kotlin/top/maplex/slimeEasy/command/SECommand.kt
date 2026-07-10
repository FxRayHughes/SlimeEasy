package top.maplex.slimeEasy.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.SEConfig

/**
 * `/se` 指令入口。
 *
 * 目前提供:
 * - `/se reload` —— 重新加载 config.yml。运行时数值 (伤害 / 范围 / 冷却 / 间隔 / 上限) 立即生效;
 *   物品文本 / 功能开关 / 研究等级因 Slimefun 注册后冻结, 需**重启服务端**方可生效。
 */
class SECommand : Command("se") {

    init {
        description = "SlimeEasy 管理指令"
        usageMessage = "/se reload"
        permission = "slimeeasy.admin"
        aliases = listOf("slimeeasy")
    }

    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || !args[0].equals("reload", ignoreCase = true)) {
            sender.sendMessage("§e用法: §f/se reload §7- 重新加载配置文件")
            return true
        }
        if (!sender.hasPermission("slimeeasy.admin")) {
            sender.sendMessage("§c你没有权限执行该指令。")
            return true
        }
        runCatching { SEConfig.reload() }
            .onSuccess {
                sender.sendMessage("§aSlimeEasy 配置已重载。")
                sender.sendMessage("§7运行时数值 (伤害/范围/冷却/间隔/上限) §a已即时生效§7。")
                sender.sendMessage("§7物品文本 / 功能开关 / 研究等级 §e需重启服务端§7方可生效。")
            }
            .onFailure {
                sender.sendMessage("§c配置重载失败: ${it.message}")
                SlimeEasy.instance.logger.warning("配置重载失败: ${it.message}")
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
