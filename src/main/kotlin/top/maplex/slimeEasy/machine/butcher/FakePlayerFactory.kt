package top.maplex.slimeEasy.machine.butcher

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 屠夫机器的假玩家工厂 (纯反射 NMS)。
 *
 * 为让机器击杀被原版认定为"玩家击杀"(从而正常掉经验、稀有掉落, 并按抢夺等级
 * 缩放掉落), 通过反射构造一个 NMS `ServerPlayer` 并取其 Bukkit [Player] 包装。
 * 每个世界缓存一个假玩家复用, 攻击时把武器塞进其主手, 以其作为伤害来源实体。
 *
 * 设计要点:
 * - **反射面最小化**: 只用反射做"构造玩家 + 取 Bukkit 实体"一处; 后续攻击、
 *   装备、伤害全走 Bukkit API, 降低跨版本崩溃风险。
 * - **失败即降级**: 任一反射步骤失败返回 null; 调用方 (攻击逻辑) 应回退为无
 *   killer 的普通伤害 + 死亡监听器补偿, 保证 NMS 变动时机器仍可用。
 * - **无敌**: 假玩家仅作伤害来源, 设为 invulnerable, 避免被怪反击致死 / 触发
 *   死亡事件 / 被其他插件误伤。
 * - **OP 恒 true 且不落盘**: 使 [Player.isOp] 恒返回 true。不用 `setOp` (它经
 *   `ServerOpList.add()` 必触发 `save()` 写入 ops.json); 改为反射把 OP 条目直接
 *   塞进 `StoredUserList` 的内存 `map`, 跳过 `add()`/`save()` —— 全局 `isOp()` 恒
 *   true, 但 ops.json 不被污染 (进程内有效, 重启后随假玩家重建自然重来)。此注入
 *   独立降级: 失败仅记警告, 不影响假玩家作为 killer 的核心功能。
 *   机器可攻击与否仍由 [top.maplex.slimeEasy.machine.common.MachineProtection] 基于
 *   机器所有者单独判定, 不依赖此 OP 身份。
 *
 * 26.2 运行时为 Mojang 映射且 craftbukkit 无版本包名后缀, 类名可直接 forName。
 * 所有方法须在主线程调用。
 */
object FakePlayerFactory {

    /** 假玩家的固定 UUID (离线风格; 全服唯一, 避免与真实玩家冲突)。 */
    private val FAKE_UUID = UUID.nameUUIDFromBytes("SlimeEasyButcherFakePlayer".toByteArray())

    /** 假玩家显示名。 */
    private const val FAKE_NAME = "SE_Butcher"

    /** 每世界缓存的假玩家 (键为世界 UID)。 */
    private val cache = ConcurrentHashMap<UUID, Player>()

    /** 反射构造整体失败的标记: 一旦失败不再重试, 直接走降级路径 (避免每 tick 反射抛异常刷屏)。 */
    @Volatile
    private var disabled = false

    /**
     * 取得指定世界的假玩家 (在线包装), 首次调用时反射构造并缓存。
     *
     * @return 假玩家; 反射失败或已降级时返回 null (调用方应走无 killer 降级路径)
     */
    fun get(world: World): Player? {
        if (disabled) return null
        // 假玩家从不 addEntity 进世界 (isValid 恒 false), 故按 world.uid 直接缓存复用,
        // 不用 isValid 判定 (否则每次都重建)。
        cache[world.uid]?.let { return it }
        return try {
            val player = build(world)
            cache[world.uid] = player
            player
        } catch (e: Throwable) {
            // 首次失败即永久降级: 记一次日志, 之后静默走 Bukkit 普通伤害 + 死亡监听补偿
            disabled = true
            Bukkit.getLogger().warning(I18n.text("messages.fake-player-factory-001", "value0" to (e.message)))
            null
        }
    }

    /** 反射构造 ServerPlayer 并返回其 Bukkit Player 包装 (无敌 + OP)。 */
    private fun build(world: World): Player {
        // 1. MinecraftServer: ((CraftServer) Bukkit.getServer()).getServer()
        val craftServer = Bukkit.getServer()
        val nmsServer = craftServer.javaClass.getMethod("getServer").invoke(craftServer)

        // 2. ServerLevel: 从 CraftWorld.getHandle() 众重载中筛返回 ServerLevel 的那个
        val handleMethod = world.javaClass.methods.first {
            it.name == "getHandle" && it.parameterCount == 0 &&
                it.returnType.name == "net.minecraft.server.level.ServerLevel"
        }
        val serverLevel = handleMethod.invoke(world)

        // 3. GameProfile(uuid, name)
        val profileClass = Class.forName("com.mojang.authlib.GameProfile")
        val gameProfile = profileClass
            .getConstructor(UUID::class.java, String::class.java)
            .newInstance(FAKE_UUID, FAKE_NAME)

        // 4. ClientInformation.createDefault()
        val clientInfoClass = Class.forName("net.minecraft.server.level.ClientInformation")
        val clientInfo = clientInfoClass.getMethod("createDefault").invoke(null)

        // 5. new ServerPlayer(server, level, profile, clientInfo)
        val serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer")
        val serverPlayer = serverPlayerClass.getConstructor(
            Class.forName("net.minecraft.server.MinecraftServer"),
            Class.forName("net.minecraft.server.level.ServerLevel"),
            profileClass,
            clientInfoClass
        ).newInstance(nmsServer, serverLevel, gameProfile, clientInfo)

        // 6. getBukkitEntity() → CraftPlayer (Bukkit Player)
        val bukkitPlayer = serverPlayerClass.getMethod("getBukkitEntity").invoke(serverPlayer) as Player
        // 无敌: 仅作伤害来源, 不参与被伤害 / 死亡逻辑
        bukkitPlayer.isInvulnerable = true
        // 兼容性: 打上 Citizens 约定的 "NPC" 元数据, 使 Essentials 等插件识别其为
        // 非真实玩家并跳过创建用户存档 / AFK / 在线列表等处理 (见 [markAsNpc])。
        markAsNpc(bukkitPlayer)
        // OP 恒 true 且不落盘: 反射注入内存 OP 名单 (跳过 add()/save())
        injectOpInMemory(nmsServer)
        // 授予全部 Slimefun 研究: 使假玩家通过任何 canUse 研究门槛 (点击 / 掉落 / 交互均不受"未解锁"限制)
        grantAllResearches(bukkitPlayer)
        return bukkitPlayer
    }

    /**
     * 给假玩家授予 Slimefun 的**全部研究**, 使其对任何 [io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.canUse]
     * 研究门槛恒通过 —— 即"允许不解锁就使用"。
     *
     * 走公开 API [PlayerProfile.get] 异步加载存档后逐项 [PlayerProfile.setResearched]; 已解锁的跳过。
     * 结果随 Slimefun 存档持久化 (重启后仍有效), 新增研究在下次构建假玩家时补授。失败静默 (非核心功能)。
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
     * 给假玩家打上 `"NPC"` 元数据 (Citizens 约定, Essentials 等广泛遵循的事实标准)。
     *
     * 假玩家作为伤害来源出现在 [org.bukkit.event.entity.EntityDamageByEntityEvent] 的
     * damager 中时, Essentials 等插件会为其懒创建 User 存档 (日志刷 "Created a User for
     * SE_Butcher …")。这些插件在处理玩家前普遍检查 `hasMetadata("NPC")`, 命中即视为
     * 非真实玩家而跳过。故在首次使用前标记, 避免污染其它插件的玩家数据。
     *
     * 用 Bukkit 元数据 API, 不引入对 Essentials 的硬依赖; 失败静默 (兼容性增强非必需)。
     */
    private fun markAsNpc(player: Player) {
        runCatching {
            player.setMetadata(
                "NPC",
                org.bukkit.metadata.FixedMetadataValue(top.maplex.slimeEasy.SlimeEasy.instance, true)
            )
        }
    }

    /**
     * 反射把假玩家的 OP 条目塞进服务器 OP 名单的**内存 map**, 绕过会触发 `save()`
     * 的 `ServerOpList.add()` —— 使 `isOp()` 全局恒 true 但不写 ops.json。
     *
     * 链路 (26.2, Mojang 映射): MinecraftServer.getPlayerList().getOps() → 其父类
     * StoredUserList 的 private `map` → put(uuid.toString(), ServerOpListEntry)。
     * key 即 `getKeyForUser` 的实现 (`NameAndId.id().toString()`)。
     *
     * 独立 try/catch: 注入失败不影响假玩家作为 killer 的核心功能, 仅少了 OP 身份。
     */
    private fun injectOpInMemory(nmsServer: Any) {
        try {
            val playerList = nmsServer.javaClass.getMethod("getPlayerList").invoke(nmsServer)
            val opList = playerList.javaClass.getMethod("getOps").invoke(playerList) // ServerOpList

            // NameAndId(UUID, String)
            val nameAndIdClass = Class.forName("net.minecraft.server.players.NameAndId")
            val nameAndId = nameAndIdClass
                .getConstructor(UUID::class.java, String::class.java)
                .newInstance(FAKE_UUID, FAKE_NAME)

            // operatorUserPermissions(): LevelBasedPermissionSet
            val permSet = nmsServer.javaClass.getMethod("operatorUserPermissions").invoke(nmsServer)
            val permSetClass = Class.forName("net.minecraft.server.permissions.LevelBasedPermissionSet")

            // new ServerOpListEntry(NameAndId, LevelBasedPermissionSet, false)
            val entryClass = Class.forName("net.minecraft.server.players.ServerOpListEntry")
            val entry = entryClass
                .getConstructor(nameAndIdClass, permSetClass, Boolean::class.javaPrimitiveType)
                .newInstance(nameAndId, permSet, false)

            // StoredUserList.map (private, 在 ServerOpList 的父类) → put, 不经 add()/save()
            val mapField = findMapField(opList.javaClass)
                ?: error(I18n.text("messages.fake-player-factory-002"))
            mapField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = mapField.get(opList) as MutableMap<String, Any>
            map[FAKE_UUID.toString()] = entry
        } catch (e: Throwable) {
            Bukkit.getLogger().warning(I18n.text("messages.fake-player-factory-003", "value0" to (e.message)))
        }
    }

    /**
     * 把假玩家瞬移到指定坐标 (仅为让原版击退方向从机器算起)。
     *
     * 假玩家未加入世界, 用 Bukkit `teleport` 可能因无连接而异常, 故走 NMS 反射
     * `ServerPlayer.setPos(x,y,z)` 直接改坐标; 失败静默 (击退方向退化, 不影响击杀)。
     */
    fun positionAt(fake: Player, loc: org.bukkit.Location) {
        runCatching {
            val handle = fake.javaClass.getMethod("getHandle").invoke(fake) // ServerPlayer
            handle.javaClass.getMethod(
                "setPos", Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType, Double::class.javaPrimitiveType
            ).invoke(handle, loc.x, loc.y, loc.z)
        }
    }

    /** 沿类层级向上找 StoredUserList 的 `Map` 字段 (字段名经混淆, 按类型匹配)。 */
    private fun findMapField(start: Class<*>): java.lang.reflect.Field? {
        var c: Class<*>? = start
        while (c != null) {
            c.declaredFields.firstOrNull { Map::class.java.isAssignableFrom(it.type) }?.let { return it }
            c = c.superclass
        }
        return null
    }
}
