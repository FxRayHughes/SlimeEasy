package top.maplex.slimeEasy.villager.trader

import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import top.maplex.slimeEasy.villager.core.VillagerData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 村民交易器的"临时代理村民"会话管理。
 *
 * 弃用虚拟 [org.bukkit.inventory.Merchant] —— 它既不累加村民经验、也无法解锁新交易, 导致交易
 * 内容与等级被永久冻结。改为交易时在**玩家身后一格**生成一只**隐形**真实 [Villager] 作代理:
 * 由 [VillagerData.applyTo] 还原职业 / 类型 / 等级 / 经验 / 交易, 关 AI / 无敌 / 无重力 / 不持久,
 * 玩家直接与之交易 —— 原版逻辑接管, 自然累加 uses 与交易经验。
 *
 * 生成在玩家身旁 (而非世界底部): 真实村民交易界面每 tick 校验实体有效性 / 与玩家的距离,
 * 代理离玩家太远会导致界面**打开即闪退关闭**; 贴身生成距离恒近, 隐形则玩家不可见。
 *
 * 关闭时按原版经验阈值驱动 [Villager.increaseLevel] 升级并解锁新交易, 再 [VillagerData.capture]
 * 重抓快照回存方块, 移除代理并刷新展示实体外观。所有方法须在主线程调用。
 */
object TraderMerchant {

    /**
     * 升到 (index+1)+1 级所需的**累计**交易经验 (原版值): 1→2=10, 2→3=70, 3→4=150, 4→5=250。
     * 数组下标为"当前等级-1", 取值即"从当前等级升到下一级所需的累计经验门槛"。
     */
    private val XP_FOR_NEXT = intArrayOf(10, 70, 150, 250)

    /**
     * 玩家当前打开的代理会话。
     *
     * 保留 [origin] 原始快照: 回存时以它的职业 / 变种类型兜底 —— 交易器内村民的身份本就不该改变
     * (升级只追加交易), 且原版对"等级 1 无经验 / 失去职业方块 POI"的村民有掉职业风险, 故不信任
     * 代理实体回读的职业, 一律锁回原始值, 仅采纳升级得来的等级 / 经验 / 交易。
     */
    private data class Session(val block: Block, val villagerId: UUID, val origin: VillagerData)

    private val sessions = ConcurrentHashMap<UUID, Session>()

    /**
     * 打开交易: 在玩家身后生成隐形代理村民并让玩家与之交易。
     *
     * 若玩家已有会话 (异常并发) 先幂等关闭。[block] 仅用于回存 (见 [Session]); 代理生成位置取
     * 玩家身后, 与 [block] 无关。
     *
     * [Player.openMerchant] 在当前 Paper 标为 deprecated 但为打开交易界面的标准 API, 显式抑制告警。
     */
    @Suppress("DEPRECATION")
    fun open(player: Player, block: Block, data: VillagerData) {
        close(player) // 幂等: 清理可能残留的上一个会话
        // 玩家身后一格 (水平反朝向): 贴身以规避真实村民交易的距离/有效性闪退; 垂直俯仰不计入
        val back = player.location.direction.setY(0.0)
        val spawnAt = if (back.lengthSquared() > 1e-6) player.location.clone().subtract(back.normalize())
        else player.location.clone()
        val villager = player.world.spawn(spawnAt, Villager::class.java) { v ->
            v.setAI(false)
            v.isAware = false
            v.setGravity(false)
            v.isInvulnerable = true
            v.isSilent = true
            v.isCollidable = false
            v.isInvisible = true       // 隐形: 玩家不可见 (贴身生成, 不隐形会露馅)
            v.isPersistent = false     // 重启 / 卸载自动清除, 不落存档
            data.applyTo(v)            // 还原职业 / 类型 / 等级 / 经验 / 成年 / 交易
        }
        sessions[player.uniqueId] = Session(block, villager.uniqueId, data)
        player.openMerchant(villager, true)
    }

    /**
     * 关闭交易 (界面关闭 / 玩家退出时调用, 幂等)。
     *
     * 从代理村民驱动升级 → 重抓快照回存方块 → 刷新展示外观 → 移除代理。
     * 代理实体已卸载 (区块问题) 时跳过回存, 仅清理会话, 避免覆盖丢失数据。
     */
    fun close(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        val villager = org.bukkit.Bukkit.getEntity(session.villagerId) as? Villager ?: return
        driveLevelUp(villager)
        // 采纳升级后的等级 / 经验 / 交易; 职业与变种类型锁回原始快照, 防代理村民意外掉职业
        val captured = VillagerData.capture(villager)
        val updated = captured.copy(
            professionKey = session.origin.professionKey,
            typeKey = session.origin.typeKey
        )
        TraderStore.setVillager(session.block, updated)
        // 立即按新等级刷新展示实体外观 (等级徽章 / 职业)
        VillagerTrader.spawnDisplay(session.block, updated)
        villager.remove()
    }

    /**
     * 按累计交易经验驱动升级: 代理村民 AI 关闭不会自行 tick 升级, 故手动补足。
     *
     * 逐级调用 [Villager.increaseLevel] (它会解锁新交易, 区别于 [Villager.setVillagerLevel]),
     * 直到等级达到经验对应的上限或封顶 5 级。以等级推进为循环条件, 天然防止死循环。
     */
    private fun driveLevelUp(villager: Villager) {
        val xp = villager.villagerExperience
        while (villager.villagerLevel < 5 && xp >= XP_FOR_NEXT[villager.villagerLevel - 1]) {
            val before = villager.villagerLevel
            if (!villager.increaseLevel(1) || villager.villagerLevel == before) break
        }
    }
}
