package top.maplex.slimeEasy.machine.common

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import top.maplex.slimeEasy.territory.TerritoryService
import java.util.UUID

/**
 * SlimeEasy 自动机器共用的放置者身份与保护校验。
 *
 * 机器 tick 时没有玩家上下文, 无法直接判断"谁在操作"。参照官方 BlockPlacer /
 * MinerAndroid 的做法: 放置机器时把放置者 UUID 存入方块数据 (键 [OWNER_KEY]),
 * 操作 (破坏 / 放置) 前以该玩家身份询问 [Slimefun.getProtectionManager], 从而尊重
 * WorldGuard / GriefPrevention 等保护插件的领地规则, 避免机器成为绕过保护的后门。
 * 真实放置者是 OP 时按服务器管理员语义直接放行，不让自动行为再次被保护链拦截。
 *
 * 兼容策略: 没有绑定版本标记的旧机器即使残留 owner，也不会直接采用该历史身份；机器若位于
 * SlimeEasy 领地内，会在首次查询时绑定当时的领地主人，领地外则继续放行。
 */
object MachineProtection {

    /** 方块数据中存储放置者 UUID 的键 (沿用官方约定, 便于跨插件语义一致)。 */
    private const val OWNER_KEY = "owner"
    /**
     * 标记 [OWNER_KEY] 已由当前绑定协议写入；缺少此键的非空 owner 仍属于旧数据，不能直接信任。
     * 键名和版本值属于持久化协议，升级时只能新增迁移分支，不能直接复用为其它含义。
     */
    private const val OWNER_BINDING_VERSION_KEY = "slimeeasy-owner-binding-version"
    private const val OWNER_BINDING_VERSION = "1"

    /** 记录机器放置者: 在 BlockPlaceHandler.onPlayerPlace 中调用。 */
    fun recordOwner(machine: Block, player: Player) {
        bindOwner(machine, player.uniqueId)
    }

    /**
     * 返回当前协议确认过的机器所有者 UUID；旧机器会按 [resolveOwner] 的规则惰性迁移。
     *
     * 未绑定旧机器返回 null；调用方不得把历史 `owner` 原始值直接作为权限身份。
     */
    fun ownerOf(machine: Block): String? =
        (resolveOwner(machine) as? OwnerResolution.Bound)?.playerId?.toString()

    /** 机器所有者是否有权破坏 [target]。 */
    fun canBreak(machine: Block, target: Block): Boolean =
        hasPermission(machine, target, Interaction.BREAK_BLOCK)

    /** 机器所有者是否有权在 [target] 放置方块。 */
    fun canPlace(machine: Block, target: Block): Boolean =
        hasPermission(machine, target, Interaction.PLACE_BLOCK)

    /**
     * 机器所有者是否有权与 [target] 容器交互。
     *
     * 主动输入/输出端口会在没有在线玩家上下文的 tick 中读写相邻库存，因此必须使用
     * `INTERACT_BLOCK` 询问保护插件，不能把“只搬物品”当作无需领地权限的内部操作。
     */
    fun canInteract(machine: Block, target: Block): Boolean =
        hasPermission(machine, target, Interaction.INTERACT_BLOCK)

    /**
     * 机器所有者是否有权攻击位于 [target] 位置的实体。
     *
     * 供屠夫机等主动攻击装置调用: 以 owner 身份询问保护管理器, 避免机器在他人
     * 领地内刷怪 (绕过保护)。owner 缺失 (旧机器) 时放行。校验以实体所在方块近似
     * 其位置——protection API 以坐标为粒度, 对领地判断足够精确。
     */
    fun canAttack(machine: Block, target: Block): Boolean =
        hasPermission(machine, target, Interaction.ATTACK_ENTITY)

    /**
     * 以机器所有者身份校验对 [target] 的某类操作。
     *
     * 旧机器未绑定时放行；OP owner 在进入完整保护链前直接放行；已标记 owner 损坏或
     * ProtectionManager 尚未完成延迟初始化时失败关闭。
     * 后一种情况只存在于启动窗口，拒绝本 tick 比让机器短暂绕过外部领地插件更安全。
     */
    private fun hasPermission(machine: Block, target: Block, interaction: Interaction): Boolean {
        val playerId = when (val owner = resolveOwner(machine)) {
            is OwnerResolution.Bound -> owner.playerId
            OwnerResolution.Unbound -> return true
            OwnerResolution.Invalid -> return false
        }
        val player = Bukkit.getOfflinePlayer(playerId)
        // OP 是服务器级受信任身份，必须在聚合保护链前短路，否则其它模块仍可能否决自动行为。
        if (player.isOp) return true
        val manager = runCatching { Slimefun.getProtectionManager() }.getOrNull() ?: return false
        return manager.hasPermission(player, target, interaction)
    }

    /**
     * 解析或迁移机器身份。
     *
     * - 有非空绑定版本标记：严格解析已绑定 UUID，损坏数据失败关闭；未知新版本保留相同 UUID 语义；
     * - 无版本标记：无论历史 owner 是否非空都视为旧机器，领地外保持未绑定并放行；
     * - 旧机器位于领地内：覆盖历史 owner，写入当时的领地主人与版本标记，之后永久使用该 UUID。
     */
    private fun resolveOwner(machine: Block): OwnerResolution {
        val bindingVersion = StorageCacheUtils.getData(machine.location, OWNER_BINDING_VERSION_KEY)
            ?.takeIf { it.isNotBlank() }
        if (bindingVersion != null) {
            val rawOwner = StorageCacheUtils.getData(machine.location, OWNER_KEY)?.takeIf { it.isNotBlank() }
                ?: return OwnerResolution.Invalid
            val playerId = runCatching { UUID.fromString(rawOwner) }.getOrNull()
                ?: return OwnerResolution.Invalid
            return OwnerResolution.Bound(playerId)
        }

        val territoryOwner = TerritoryService.at(machine.location)?.owner ?: return OwnerResolution.Unbound
        return bindOwner(machine, territoryOwner)
    }

    /** 先写 owner 再写版本标记，避免中途失败把不完整记录误判成已完成绑定。 */
    private fun bindOwner(machine: Block, playerId: UUID): OwnerResolution.Bound {
        StorageCacheUtils.setData(machine.location, OWNER_KEY, playerId.toString())
        StorageCacheUtils.setData(machine.location, OWNER_BINDING_VERSION_KEY, OWNER_BINDING_VERSION)
        return OwnerResolution.Bound(playerId)
    }

    /** 区分可放行的旧未绑定机器与必须失败关闭的已标记损坏数据。 */
    private sealed interface OwnerResolution {
        data class Bound(val playerId: UUID) : OwnerResolution
        data object Unbound : OwnerResolution
        data object Invalid : OwnerResolution
    }
}
