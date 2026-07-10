package top.maplex.slimeEasy.machine.butcher

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.SlimeEasy
import java.util.UUID

/**
 * 屠夫机器的两侧武器展示层。
 *
 * 在观察者攻击方向 (facing) 的左右两侧各生成一个 [ItemDisplay], 显示当前使用的
 * 第一把武器。攻击周期到来时调用 [sweep] 触发挥砍动画 (transformation 插值)。
 *
 * 两个展示实体的 UUID 与生成时的 facing 持久化在 BlockData, 以便服务器重载后
 * 定位并按需重建 ([ensureCurrent])。所有方法须在主线程调用。
 */
object ButcherDisplay {

    private const val KEY_LEFT = "se_butcher_disp_l"
    private const val KEY_RIGHT = "se_butcher_disp_r"
    private const val KEY_FACE = "se_butcher_disp_face"
    private const val KEY_VERSION = "se_butcher_disp_ver"

    /** 展示实体参数版本; 提升即令存量机器下次运行时按新参数重建一次。 */
    private const val CURRENT_VERSION = "1"

    /** facing 的左右垂直面 (水平朝向); 垂直朝向回退用 EAST/WEST。 */
    private fun sideFaces(face: BlockFace): Pair<BlockFace, BlockFace> = when (face) {
        BlockFace.NORTH -> BlockFace.WEST to BlockFace.EAST
        BlockFace.SOUTH -> BlockFace.EAST to BlockFace.WEST
        BlockFace.EAST -> BlockFace.NORTH to BlockFace.SOUTH
        BlockFace.WEST -> BlockFace.SOUTH to BlockFace.NORTH
        else -> BlockFace.EAST to BlockFace.WEST
    }

    /** 在攻击方向两侧生成展示实体 (若已存在先清除), 记录到 BlockData。 */
    fun spawn(block: Block, face: BlockFace) {
        remove(block)
        val (left, right) = sideFaces(face)
        val world = block.world
        val leftEntity = world.spawn(block.getRelative(left).location.toCenterLocation(), ItemDisplay::class.java) {
            it.isPersistent = true
        }
        val rightEntity = world.spawn(block.getRelative(right).location.toCenterLocation(), ItemDisplay::class.java) {
            it.isPersistent = true
        }
        StorageCacheUtils.setData(block.location, KEY_LEFT, leftEntity.uniqueId.toString())
        StorageCacheUtils.setData(block.location, KEY_RIGHT, rightEntity.uniqueId.toString())
        StorageCacheUtils.setData(block.location, KEY_FACE, face.name)
        StorageCacheUtils.setData(block.location, KEY_VERSION, CURRENT_VERSION)
    }

    /** 刷新两侧展示的武器图标 (null 表示无武器, 清空显示)。 */
    fun update(block: Block, weapon: ItemStack?) {
        val icon = weapon?.clone()?.apply { amount = 1 }
        (entity(block, KEY_LEFT) as? ItemDisplay)?.setItemStack(icon)
        (entity(block, KEY_RIGHT) as? ItemDisplay)?.setItemStack(icon)
    }

    /**
     * 触发一次挥砍动画: 两侧展示实体先绕 Y 轴摆出一个角度, 隔 [SWEEP_TICKS] tick 再
     * 插值**复位到中位**, 形成"摆过去-收回来"的完整横扫。
     *
     * 原实现只摆出、从不复位, 首刀之后 rightRotation 恒为同值 → 后续攻击无可见变化。
     * 现每次攻击都摆出并调度复位, 使每刀都可见。
     */
    fun sweep(block: Block) {
        for (key in listOf(KEY_LEFT, KEY_RIGHT)) {
            val disp = entity(block, key) as? ItemDisplay ?: continue
            val t = disp.transformation
            // 1. 摆出
            disp.interpolationDelay = 0
            disp.interpolationDuration = SWEEP_TICKS
            val yaw = org.joml.Quaternionf().rotateY(if (key == KEY_LEFT) SWEEP_ANGLE else -SWEEP_ANGLE)
            disp.setTransformation(org.bukkit.util.Transformation(t.translation, t.leftRotation, t.scale, yaw))
        }
        // 2. 调度复位到中位 (无旋转), 使下一刀可再次摆出
        Bukkit.getScheduler().runTaskLater(SlimeEasy.instance, Runnable {
            for (key in listOf(KEY_LEFT, KEY_RIGHT)) {
                val disp = entity(block, key) as? ItemDisplay ?: continue
                val t = disp.transformation
                disp.interpolationDelay = 0
                disp.interpolationDuration = SWEEP_TICKS
                disp.setTransformation(
                    org.bukkit.util.Transformation(t.translation, t.leftRotation, t.scale, org.joml.Quaternionf())
                )
            }
        }, SWEEP_TICKS.toLong())
    }

    /** 移除两侧展示实体并清除 BlockData 记录 (破坏 / 重建前调用)。 */
    fun remove(block: Block) {
        entity(block, KEY_LEFT)?.remove()
        entity(block, KEY_RIGHT)?.remove()
        StorageCacheUtils.setData(block.location, KEY_LEFT, "")
        StorageCacheUtils.setData(block.location, KEY_RIGHT, "")
    }

    /**
     * 确保展示实体为当前版本参数; 版本过旧则按记录的 facing 重建一次。
     *
     * @return true 表示本次进行了重建 (调用方随后应刷新武器图标)
     */
    fun ensureCurrent(block: Block): Boolean {
        if (StorageCacheUtils.getData(block.location, KEY_VERSION) == CURRENT_VERSION) return false
        val faceName = StorageCacheUtils.getData(block.location, KEY_FACE) ?: return false
        val face = runCatching { BlockFace.valueOf(faceName) }.getOrNull() ?: return false
        spawn(block, face)
        return true
    }

    /** 依 BlockData 记录的 UUID 解析实体; 缺失或已卸载返回 null。 */
    private fun entity(block: Block, key: String): org.bukkit.entity.Entity? {
        val raw = StorageCacheUtils.getData(block.location, key)
        if (raw.isNullOrEmpty()) return null
        val uuid = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
        return Bukkit.getEntity(uuid)
    }

    /** 横扫动画插值时长 (tick)。 */
    private const val SWEEP_TICKS = 4

    /** 横扫摆动角度 (弧度)。 */
    private const val SWEEP_ANGLE = 1.2f
}
