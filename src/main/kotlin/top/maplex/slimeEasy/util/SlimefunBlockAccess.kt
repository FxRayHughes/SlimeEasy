package top.maplex.slimeEasy.util

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction
import org.bukkit.block.Block
import org.bukkit.entity.Player

/**
 * 玩家访问现有 Slimefun 方块的统一门禁。
 *
 * Slimefun 的 `BlockUseHandler` 只保证物品使用权限，不会自动询问 `ProtectionManager`；
 * 展示实体重定向与远程终端又可能完全绕过方块事件，因此这些入口必须显式复用本门禁。
 */
object SlimefunBlockAccess {

    /** 解析目标 Slimefun 方块后，检查物品权限与完整方块交互保护链。 */
    fun canUse(player: Player, block: Block, message: Boolean = true): Boolean {
        val item = StorageCacheUtils.getSlimefunItem(block.location) ?: return false
        return canUse(player, block, item, message)
    }

    /**
     * 使用已解析的 [item] 检查访问权，避免控制器等调用方重复判断方块类型。
     * OP 在确认目标确为 Slimefun 方块后直接放行；非 OP 必须同时通过两层权限。
     */
    fun canUse(player: Player, block: Block, item: SlimefunItem, message: Boolean = true): Boolean {
        if (player.isOp) return true
        if (!Slimefun.getPermissionsService().hasPermission(player, item)) return denied(player, message)
        val manager = runCatching { Slimefun.getProtectionManager() }.getOrNull()
        if (manager?.hasPermission(player, block, Interaction.INTERACT_BLOCK) == true) return true
        return denied(player, message)
    }

    /** 使用 Slimefun 自身的通用权限提示，避免把其它保护模块的拒绝误报成某一种领地错误。 */
    private fun denied(player: Player, message: Boolean): Boolean {
        if (message) Slimefun.getLocalization().sendMessage(player, "messages.no-permission", true)
        return false
    }
}
