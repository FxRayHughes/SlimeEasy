package top.maplex.slimeEasy.machine.butcher

import com.mojang.authlib.GameProfile
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionSet
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.I18n
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 屠夫机器与自动点击器共用的强类型 NMS 假玩家工厂。
 *
 * 每个世界构造并缓存一个未加入在线列表的 [ServerPlayer]，使机器攻击仍被原版识别为玩家伤害，
 * 同时避免生成网络连接和真实玩家存档。构造失败只记录一次并永久降级，调用方可继续使用普通伤害路径。
 *
 * 假玩家需要同时通过 Bukkit 与 NMS 两套权限判断，但公开 OP 名单 API 会立即写入 `ops.json`。
 * 因此 Bukkit 侧只附加 Slimefun 物品实际使用的权限节点，NMS 侧由 [FakeServerPlayer.permissions]
 * 所有者权限；两者都不修改服务器 OP 名单。屠夫伤害的领地与外部保护权限会在事件层还原为
 * 真实机器主人单独校验，不能让所有玩家借用这个内部 OP 身份绕过保护。
 * 所有入口只允许在服务端主线程调用。
 */
object FakePlayerFactory {
    /** 固定离线风格 UUID 保证重启后 Slimefun 研究档案稳定，同时避免与真实玩家冲突。 */
    private val FAKE_UUID = UUID.nameUUIDFromBytes("SlimeEasyButcherFakePlayer".toByteArray())

    /** 名称只用于事件来源识别，不会加入服务器在线玩家列表。 */
    private const val FAKE_NAME = "SE_Butcher"

    /** 假玩家未加入世界且 `isValid` 恒为 false，因此只能按世界 UUID 显式缓存。 */
    private val cache = ConcurrentHashMap<UUID, Player>()

    /** 首次 NMS 构造失败后停止重试，避免每个机器 tick 重复抛错和刷日志。 */
    @Volatile
    private var disabled = false

    /**
     * 取得指定世界的假玩家；同一世界始终复用同一实例。
     *
     * @return 假玩家，或在当前 Paper NMS 构造失败时返回 null 让调用方执行降级逻辑
     */
    fun get(world: World): Player? {
        if (disabled) return null
        cache[world.uid]?.let { return it }
        return try {
            build(world).also { cache[world.uid] = it }
        } catch (cause: Throwable) {
            disabled = true
            Bukkit.getLogger().warning(I18n.text("logs.butcher.fake-player-failed", "error" to cause.message))
            null
        }
    }

    /**
     * 判断事件来源是否为本工厂缓存的假玩家。
     *
     * 只比较对象身份而不信任名称或固定 UUID，避免其它实体伪装后把行为错误归属给机器主人。
     */
    fun isFake(player: Player): Boolean = cache.values.any { it === player }

    /** 构造与目标世界绑定的 ServerPlayer，并完成无敌、权限、NPC 标记和 Slimefun 研究初始化。 */
    private fun build(world: World): Player {
        val craftServer = Bukkit.getServer() as CraftServer
        val serverPlayer = FakeServerPlayer(
            craftServer.server,
            (world as CraftWorld).handle,
            GameProfile(FAKE_UUID, FAKE_NAME),
            ClientInformation.createDefault(),
            craftServer
        )
        val bukkitPlayer = serverPlayer.bukkitEntity
        bukkitPlayer.isInvulnerable = true
        grantSlimefunPermissions(bukkitPlayer)
        markAsNpc(bukkitPlayer)
        grantAllResearches(bukkitPlayer)
        return bukkitPlayer
    }

    /**
     * 只授予 Slimefun 物品配置中真实存在的权限节点。
     *
     * 使用标准 [CraftPlayer] 可避免 Essentials 将自定义包装识别为非 Bukkit 玩家；逐项附加权限则
     * 保留机器与 Slimefun 物品交互所需的权限，同时不把假玩家写进 `ops.json`，也不授予领地绕过权限。
     */
    private fun grantSlimefunPermissions(player: Player) {
        val attachment = player.addAttachment(SlimeEasy.instance)
        for (item in Slimefun.getRegistry().enabledSlimefunItems) {
            Slimefun.getPermissionsService().getPermission(item).ifPresent { permission ->
                attachment.setPermission(permission, true)
            }
        }
    }

    /**
     * 授予全部 Slimefun 研究，使机器行为不因假玩家没有真实研究进度而被 `canUse` 拒绝。
     * 档案异步加载后只补齐缺失项；失败不影响原版攻击这一核心路径。
     */
    private fun grantAllResearches(player: Player) {
        runCatching {
            PlayerProfile.get(player) { profile ->
                for (research in Slimefun.getRegistry().researches) {
                    if (!profile.hasUnlocked(research)) profile.setResearched(research, true)
                }
            }
        }
    }

    /**
     * Citizens 的 `NPC` 元数据是常见插件跳过用户档案、AFK 与在线状态处理的兼容协议。
     * 这里只使用 Bukkit 元数据，不引入 Citizens 或 Essentials 硬依赖。
     */
    private fun markAsNpc(player: Player) {
        runCatching {
            player.setMetadata("NPC", FixedMetadataValue(SlimeEasy.instance, true))
        }
    }

    /**
     * 直接修改未联网 ServerPlayer 的坐标，只为让原版击退方向以机器为起点。
     * Bukkit teleport 依赖连接状态，不适用于这个从未加入世界的伤害来源实体。
     */
    fun positionAt(fake: Player, location: Location) {
        runCatching {
            (fake as CraftPlayer).handle.setPos(location.x, location.y, location.z)
        }
    }

    /**
     * 为假玩家固定 NMS 所有者权限，并确保所有 Bukkit 包装入口都返回同一个标准包装。
     * 该类与 CraftBukkit 二进制签名由 paperweight dev bundle 锁定，升级 Paper 时必须重新编译检查。
     */
    private class FakeServerPlayer(
        server: MinecraftServer,
        level: ServerLevel,
        profile: GameProfile,
        clientInformation: ClientInformation,
        craftServer: CraftServer
    ) : ServerPlayer(server, level, profile, clientInformation) {
        private val fakeBukkitEntity = CraftPlayer(craftServer, this)

        override fun getBukkitEntity(): CraftPlayer = fakeBukkitEntity

        override fun getBukkitEntityRaw(): CraftPlayer = fakeBukkitEntity

        override fun permissions(): PermissionSet = LevelBasedPermissionSet.OWNER
    }

}
