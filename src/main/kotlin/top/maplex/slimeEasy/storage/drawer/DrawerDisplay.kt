package top.maplex.slimeEasy.storage.drawer

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemFrame
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.QuantityFormat
import java.util.UUID

/**
 * 抽屉的面向玩家展示层。
 *
 * 在抽屉方块朝向玩家的一面生成一个隐形、固定的 [ItemFrame], 展示所存物品图标
 * (无边框, 玩家不可取)。存量数字**写入该图标的自定义名**: 玩家准星对准展示框
 * 时浮现数量 (原版 item frame 的 on-look 名牌行为), 无需额外常显实体。
 *
 * 展示实体的 UUID 与展示朝向持久化在 BlockData, 以便服务器重载后仍能定位并更新。
 * 所有方法须在主线程调用 (涉及实体生成 / 修改)。
 */
object DrawerDisplay {

    private const val KEY_FRAME = "se_frame_uuid"
    /** 旧版本残留的浮空文字实体键; 保留仅用于重建时清理历史 TextDisplay。 */
    private const val KEY_TEXT = "se_text_uuid"
    private const val KEY_FACE = "se_face"
    private const val KEY_VERSION = "se_display_ver"

    /** 展示实体参数版本; 提升此值即令存量抽屉在下次运行时按新参数重建一次。 */
    private const val CURRENT_VERSION = "4"

    /**
     * 确保展示实体为当前版本参数; 版本过旧 (旧抽屉) 则按新参数重建一次。
     *
     * 从版本 3 起改为"数量写入图标名 + 移除浮空文字实体", 旧抽屉重建时其历史
     * [KEY_TEXT] TextDisplay 会被 [remove] 一并清除。
     *
     * @return true 表示本次进行了重建 (调用方需随后刷新展示内容)
     */
    fun ensureCurrent(block: Block): Boolean {
        if (StorageCacheUtils.getData(block.location, KEY_VERSION) == CURRENT_VERSION) return false
        val faceName = StorageCacheUtils.getData(block.location, KEY_FACE)
        val face = faceName?.let { runCatching { BlockFace.valueOf(it) }.getOrNull() } ?: return false
        spawn(block, face)
        StorageCacheUtils.setData(block.location, KEY_VERSION, CURRENT_VERSION)
        return true
    }

    /**
     * 在指定面生成展示实体 (若已存在则先清除), 并记录到 BlockData。
     *
     * @param face 面向玩家的方块面 (由放置时玩家朝向反推)
     */
    fun spawn(block: Block, face: BlockFace) {
        remove(block)
        val world = block.world
        // 悬挂实体须生成在"被挂方块朝向面前方的相邻方块", 挂在抽屉该面上朝外显示;
        // 若生成在抽屉自身坐标, 框会尝试挂到身后方块而嵌入实心木桶, 导致不可见/无法点中。
        val frameLoc = block.getRelative(face).location
        val frame = world.spawn(frameLoc, ItemFrame::class.java) { f ->
            f.setFacingDirection(face, true)
            f.isVisible = false
            // 不设 isFixed: 固定展示框会忽略左键攻击, 导致 EntityDamageByEntityEvent 不触发
            // (取出无效)。改由 [DrawerListener] 取消左右键事件来防止取下 / 破坏。
            f.isFixed = false
            f.isSilent = true
            f.setGravity(false)
            f.isPersistent = true
        }
        StorageCacheUtils.setData(block.location, KEY_FRAME, frame.uniqueId.toString())
        StorageCacheUtils.setData(block.location, KEY_FACE, face.name)
        StorageCacheUtils.setData(block.location, KEY_VERSION, CURRENT_VERSION)
    }

    /**
     * 刷新展示内容。
     *
     * 数量以紧凑单位 (如 47.2K) 写入图标的自定义名; 玩家准星对准展示框时浮现。
     *
     * @param key 当前所存物品身份; null 表示空抽屉 (清空图标)
     * @param count 当前存量
     */
    fun update(block: Block, key: ItemKey?, count: Long) {
        val frame = entity(block, KEY_FRAME) as? ItemFrame ?: return
        if (key == null || count <= 0) {
            frame.setItem(null, false)
            return
        }
        val icon = key.toDisplay(1).apply {
            editMeta { it.displayName(Component.text("§f${QuantityFormat.compact(count)}")) }
        }
        frame.setItem(icon, false)
    }

    /** 移除展示实体并清除 BlockData 记录 (方块破坏 / 重建前调用; 一并清理旧版文字实体)。 */
    fun remove(block: Block) {
        entity(block, KEY_FRAME)?.remove()
        entity(block, KEY_TEXT)?.remove() // 清理旧版本残留的浮空文字实体
        StorageCacheUtils.setData(block.location, KEY_FRAME, "")
        StorageCacheUtils.setData(block.location, KEY_TEXT, "")
    }

    /** 判断某 ItemFrame 是否属于某抽屉方块 (交互事件中用于归属校验)。 */
    fun isDrawerFrame(block: Block, frameId: UUID): Boolean =
        StorageCacheUtils.getData(block.location, KEY_FRAME) == frameId.toString()

    /** 依 BlockData 记录的 UUID 解析实体; 缺失或已卸载返回 null。 */
    private fun entity(block: Block, key: String): org.bukkit.entity.Entity? {
        val raw = StorageCacheUtils.getData(block.location, key)
        if (raw.isNullOrEmpty()) return null
        val uuid = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
        return Bukkit.getEntity(uuid)
    }
}
