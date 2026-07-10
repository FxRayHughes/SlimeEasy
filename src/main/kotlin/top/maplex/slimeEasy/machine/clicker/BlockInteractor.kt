package top.maplex.slimeEasy.machine.clicker

import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 以虚拟玩家真正"点击"方块的 NMS 反射交互器 (Mojang 映射, Paper 26.2)。
 *
 * Bukkit 事件仅是通知, `callEvent` 不会执行原版方块交互; 要让点击真正生效 (按按钮 / 拉杆、
 * 骨粉施肥、破坏方块等), 必须调用服务端 `ServerPlayerGameMode`:
 * - **右键**: [useItemOn] → `useItemOn(player, level, stack, hand, hitResult)`, 执行原版交互
 *   (并在内部派发 Bukkit [org.bukkit.event.player.PlayerInteractEvent], 故 Slimefun 等监听器亦会响应);
 * - **左键**: [destroyBlock] → `destroyBlock(pos)`, 直接破坏方块 (触发 BlockBreakEvent, Slimefun 方块正常掉落)。
 *
 * 全反射、失败即永久降级 (仅记一次日志), 与 [top.maplex.slimeEasy.machine.butcher.FakePlayerFactory] 一致。
 * 所有方法须在主线程调用。
 */
object BlockInteractor {

    /** 反射句柄集合 (一次解析, 失败为 null → 交互降级为无操作)。 */
    private class Refl(
        val gameModeField: Field,
        val levelMethod: Method,
        val getItemInHand: Method,
        val mainHand: Any,
        val blockPosCtor: Constructor<*>,
        val directionValueOf: Method,
        val vec3Ctor: Constructor<*>,
        val hitResultCtor: Constructor<*>,
        val useItemOn: Method,
        val destroyBlock: Method
    )

    private val refl: Refl? by lazy {
        runCatching { build() }.getOrElse {
            Bukkit.getLogger().warning("[SlimeEasy] 自动点击器 NMS 交互反射初始化失败, 点击降级为无效: ${it.message}")
            null
        }
    }

    /** 虚拟玩家右键 [block] 的 [face] 面 (原版 useItemOn); 成功返回 true。 */
    fun useItemOn(fake: Player, block: Block, face: BlockFace): Boolean {
        val r = refl ?: return false
        return runCatching {
            val handle = fake.javaClass.getMethod("getHandle").invoke(fake)
            val gameMode = r.gameModeField.get(handle)
            val level = r.levelMethod.invoke(handle)
            val stack = r.getItemInHand.invoke(handle, r.mainHand)
            val pos = r.blockPosCtor.newInstance(block.x, block.y, block.z)
            val dir = r.directionValueOf.invoke(null, face.name)
            val vec = r.vec3Ctor.newInstance(block.x + 0.5, block.y + 0.5, block.z + 0.5)
            val hit = r.hitResultCtor.newInstance(vec, dir, pos, false)
            r.useItemOn.invoke(gameMode, handle, level, stack, r.mainHand, hit)
            true
        }.getOrDefault(false)
    }

    /** 虚拟玩家左键破坏 [block] (原版 destroyBlock); 成功返回 true。 */
    fun destroyBlock(fake: Player, block: Block): Boolean {
        val r = refl ?: return false
        return runCatching {
            val handle = fake.javaClass.getMethod("getHandle").invoke(fake)
            val gameMode = r.gameModeField.get(handle)
            val pos = r.blockPosCtor.newInstance(block.x, block.y, block.z)
            r.destroyBlock.invoke(gameMode, pos) as? Boolean ?: false
        }.getOrDefault(false)
    }

    /** 解析全部反射句柄 (类名为 Mojang 映射, 直接 forName)。 */
    private fun build(): Refl {
        val serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer")
        val gameModeCls = Class.forName("net.minecraft.server.level.ServerPlayerGameMode")
        val levelCls = Class.forName("net.minecraft.world.level.Level")
        val itemCls = Class.forName("net.minecraft.world.item.ItemStack")
        val handCls = Class.forName("net.minecraft.world.InteractionHand")
        val hitCls = Class.forName("net.minecraft.world.phys.BlockHitResult")
        val posCls = Class.forName("net.minecraft.core.BlockPos")
        val dirCls = Class.forName("net.minecraft.core.Direction")
        val vec3Cls = Class.forName("net.minecraft.world.phys.Vec3")
        val int = Int::class.javaPrimitiveType
        val dbl = Double::class.javaPrimitiveType
        val bool = Boolean::class.javaPrimitiveType
        return Refl(
            gameModeField = serverPlayer.getField("gameMode"),
            levelMethod = serverPlayer.getMethod("level"),
            getItemInHand = serverPlayer.getMethod("getItemInHand", handCls),
            mainHand = handCls.getField("MAIN_HAND").get(null),
            blockPosCtor = posCls.getConstructor(int, int, int),
            directionValueOf = dirCls.getMethod("valueOf", String::class.java),
            vec3Ctor = vec3Cls.getConstructor(dbl, dbl, dbl),
            hitResultCtor = hitCls.getConstructor(vec3Cls, dirCls, posCls, bool),
            useItemOn = gameModeCls.getMethod("useItemOn", serverPlayer, levelCls, itemCls, handCls, hitCls),
            destroyBlock = gameModeCls.getMethod("destroyBlock", posCls)
        )
    }
}
