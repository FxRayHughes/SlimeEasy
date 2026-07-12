package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.block.Block
import org.bukkit.entity.Player
import top.maplex.slimeEasy.util.SlimefunBlockAccess

/** 网络控制器目标识别、Slimefun 物品权限与目标位置保护的统一入口。 */
object NetworkControllerAccess {

    fun controllerAt(block: Block): NetworkController? {
        val id = StorageCacheUtils.getBlock(block.location)?.sfId ?: return null
        return SlimefunItem.getById(id) as? NetworkController
    }

    fun isController(block: Block): Boolean = controllerAt(block) != null

    fun canUse(player: Player, block: Block, message: Boolean = true): Boolean {
        val controller = controllerAt(block) ?: return false
        return SlimefunBlockAccess.canUse(player, block, controller, message)
    }
}
