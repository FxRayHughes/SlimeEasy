package top.maplex.slimeEasy.storage.network

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy

/**
 * 网络终端的排序偏好 (按玩家持久化于其 PDC)。
 *
 * 存两项: 排序字段 (名称 / 存量) 与方向 (正序 / 倒序)。持久化在**玩家** PDC 而非
 * 控制器方块, 使同一玩家无论从网络控制器方块还是远程终端物品打开, 都沿用其个人
 * 偏好, 且不干扰其他玩家。默认按名称正序。
 */
object TerminalSortState {

    /** 排序字段。 */
    enum class Field { NAME, AMOUNT }

    private val fieldKey: NamespacedKey by lazy { NamespacedKey(SlimeEasy.instance, "term_sort_field") }
    private val descKey: NamespacedKey by lazy { NamespacedKey(SlimeEasy.instance, "term_sort_desc") }

    /** 读取排序字段; 无记录默认 [Field.NAME]。 */
    fun field(player: Player): Field {
        val ord = player.persistentDataContainer.getOrDefault(fieldKey, PersistentDataType.INTEGER, 0)
        return Field.values().getOrElse(ord) { Field.NAME }
    }

    /** 是否倒序; 无记录默认 false (正序)。 */
    fun descending(player: Player): Boolean =
        player.persistentDataContainer.getOrDefault(descKey, PersistentDataType.BYTE, 0.toByte()).toInt() != 0

    /** 在名称 / 存量间切换字段并写回, 返回切换后的字段。 */
    fun cycleField(player: Player): Field {
        val next = if (field(player) == Field.NAME) Field.AMOUNT else Field.NAME
        player.persistentDataContainer.set(fieldKey, PersistentDataType.INTEGER, next.ordinal)
        return next
    }

    /** 翻转正序 / 倒序并写回, 返回切换后是否倒序。 */
    fun toggleDir(player: Player): Boolean {
        val next = !descending(player)
        player.persistentDataContainer.set(descKey, PersistentDataType.BYTE, (if (next) 1 else 0).toByte())
        return next
    }
}
