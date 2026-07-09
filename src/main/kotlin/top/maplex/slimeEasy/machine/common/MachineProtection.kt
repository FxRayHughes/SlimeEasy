package top.maplex.slimeEasy.machine.common

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.UUID

/**
 * 机器的领地保护校验 (自动破坏机 / 自动放置机共用)。
 *
 * 机器 tick 时没有玩家上下文, 无法直接判断"谁在操作"。参照官方 BlockPlacer /
 * MinerAndroid 的做法: 放置机器时把放置者 UUID 存入方块数据 (键 [OWNER_KEY]),
 * 操作 (破坏 / 放置) 前以该玩家身份询问 [Slimefun.getProtectionManager], 从而尊重
 * WorldGuard / GriefPrevention 等保护插件的领地规则, 避免机器成为绕过保护的后门。
 *
 * 兼容策略: 未记录 owner 的机器 (本功能上线前放置的旧机器) 一律放行, 与官方一致,
 * 避免既有布局因升级而失效; 新放置的机器则受保护约束。
 */
object MachineProtection {

    /** 方块数据中存储放置者 UUID 的键 (沿用官方约定, 便于跨插件语义一致)。 */
    private const val OWNER_KEY = "owner"

    /** 记录机器放置者: 在 BlockPlaceHandler.onPlayerPlace 中调用。 */
    fun recordOwner(machine: Block, player: Player) {
        StorageCacheUtils.setData(machine.location, OWNER_KEY, player.uniqueId.toString())
    }

    /** 机器所有者是否有权破坏 [target]。 */
    fun canBreak(machine: Block, target: Block): Boolean =
        hasPermission(machine, target, Interaction.BREAK_BLOCK)

    /** 机器所有者是否有权在 [target] 放置方块。 */
    fun canPlace(machine: Block, target: Block): Boolean =
        hasPermission(machine, target, Interaction.PLACE_BLOCK)

    /**
     * 以机器所有者身份校验对 [target] 的某类操作。
     *
     * owner 缺失 (旧机器) 时放行; 否则委托保护管理器裁决。
     */
    private fun hasPermission(machine: Block, target: Block, interaction: Interaction): Boolean {
        val owner = StorageCacheUtils.getData(machine.location, OWNER_KEY) ?: return true
        val player = Bukkit.getOfflinePlayer(UUID.fromString(owner))
        return Slimefun.getProtectionManager().hasPermission(player, target, interaction)
    }
}
