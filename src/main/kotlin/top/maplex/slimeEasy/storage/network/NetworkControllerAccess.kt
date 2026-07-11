package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import org.bukkit.block.Block
import org.bukkit.entity.Player

/** 网络控制器目标识别与 Slimefun 使用权限的统一入口。 */
object NetworkControllerAccess {

    fun controllerAt(block: Block): NetworkController? {
        val id = StorageCacheUtils.getBlock(block.location)?.sfId ?: return null
        return SlimefunItem.getById(id) as? NetworkController
    }

    fun isController(block: Block): Boolean = controllerAt(block) != null

    fun canUse(player: Player, block: Block, message: Boolean = true): Boolean {
        val controller = controllerAt(block) ?: return false
        if (Slimefun.getPermissionsService().hasPermission(player, controller)) return true
        if (message) Slimefun.getLocalization().sendMessage(player, "messages.no-permission", true)
        return false
    }
}
