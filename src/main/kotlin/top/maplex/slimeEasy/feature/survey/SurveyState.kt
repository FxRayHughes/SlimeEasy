package top.maplex.slimeEasy.feature.survey

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy

/** 勘察结果的展示形式。 */
enum class SurveyDisplay { CHAT, GUI }

/**
 * 勘察尺物品状态中心 (持久化于物品 PDC)。
 *
 * 集中管理两项随物品保存的状态:
 * - 选中层级索引 (哪一型矿机的范围);
 * - 展示形式 (聊天栏 / 箱子界面)。
 *
 * 由右键勘察 ([SurveyRuler]) 与潜行左键切换 ([SurveyDisplayListener]) 共用,
 * 避免键名与读写逻辑在两处重复。
 */
object SurveyState {

    /** 选中层级索引的 PDC 键名。 */
    private const val TIER_KEY = "survey_tier_index"

    /** 展示形式的 PDC 键名 (存 [SurveyDisplay.ordinal])。 */
    private const val DISPLAY_KEY = "survey_display"

    private val tierKey: NamespacedKey by lazy { NamespacedKey(SlimeEasy.instance, TIER_KEY) }
    private val displayKey: NamespacedKey by lazy { NamespacedKey(SlimeEasy.instance, DISPLAY_KEY) }

    /** 读取选中层级索引; 无记录时默认 0 (首个层级)。 */
    fun readTierIndex(item: ItemStack): Int {
        val meta = item.itemMeta ?: return 0
        return meta.persistentDataContainer.getOrDefault(tierKey, PersistentDataType.INTEGER, 0)
    }

    /** 写入选中层级索引。 */
    fun writeTierIndex(item: ItemStack, index: Int) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(tierKey, PersistentDataType.INTEGER, index)
        item.itemMeta = meta
    }

    /** 读取展示形式; 无记录时默认 [SurveyDisplay.CHAT]。 */
    fun readDisplay(item: ItemStack): SurveyDisplay {
        val meta = item.itemMeta ?: return SurveyDisplay.CHAT
        val ordinal = meta.persistentDataContainer.getOrDefault(displayKey, PersistentDataType.INTEGER, 0)
        return SurveyDisplay.values().getOrElse(ordinal) { SurveyDisplay.CHAT }
    }

    /** 翻转展示形式并写回, 返回切换后的形式。 */
    fun toggleDisplay(item: ItemStack): SurveyDisplay {
        val next = if (readDisplay(item) == SurveyDisplay.CHAT) SurveyDisplay.GUI else SurveyDisplay.CHAT
        val meta = item.itemMeta ?: return next
        meta.persistentDataContainer.set(displayKey, PersistentDataType.INTEGER, next.ordinal)
        item.itemMeta = meta
        return next
    }
}
