package top.maplex.slimeEasy.machine.clicker

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.craftbukkit.block.CraftBlock
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import top.maplex.slimeEasy.config.I18n

/**
 * 以虚拟玩家真正“点击”方块的强类型 NMS 交互器，签名由 Paper userdev 锁定到目标服务端。
 *
 * Bukkit 事件仅负责通知监听器，不能自行执行原版方块逻辑；右键必须进入
 * `ServerPlayerGameMode.useItemOn`，左键必须进入 `destroyBlock`，才能让按钮、工具消耗、
 * 方块掉落与 Slimefun 的破坏监听保持原版语义。所有入口只允许在服务端主线程调用。
 */
object BlockInteractor {
    private var warningLogged = false

    /** 虚拟玩家右键 [block] 的 [face] 面；仅在完整执行 NMS 调用后返回 true。 */
    fun useItemOn(fake: Player, block: Block, face: BlockFace): Boolean = runCatching {
        val serverPlayer = (fake as CraftPlayer).handle
        val position = BlockPos(block.x, block.y, block.z)
        val direction = requireNotNull(CraftBlock.blockFaceToNotch(face)) {
            "Unsupported block face: $face"
        }
        val hitResult = BlockHitResult(Vec3.atCenterOf(position), direction, position, false)
        serverPlayer.gameMode.useItemOn(
            serverPlayer,
            serverPlayer.level(),
            serverPlayer.getItemInHand(InteractionHand.MAIN_HAND),
            InteractionHand.MAIN_HAND,
            hitResult
        )
        true
    }.getOrElse {
        warnOnce(it)
        false
    }

    /** 虚拟玩家左键破坏 [block]；返回原版 `destroyBlock` 的实际结果。 */
    fun destroyBlock(fake: Player, block: Block): Boolean = runCatching {
        val serverPlayer = (fake as CraftPlayer).handle
        serverPlayer.gameMode.destroyBlock(BlockPos(block.x, block.y, block.z))
    }.getOrElse {
        warnOnce(it)
        false
    }

    /** 直接 NMS 调用若失配只记录一次，避免自动点击器按 tick 重复刷屏。 */
    private fun warnOnce(cause: Throwable) {
        if (warningLogged) return
        warningLogged = true
        Bukkit.getLogger().warning(I18n.text("logs.auto-clicker.nms-interaction-failed", "error" to cause.message))
    }
}
