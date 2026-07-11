package top.maplex.slimeEasy.storage.upgrade

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.inventory.ItemStack

/**
 * 升级组件类型。
 *
 * 分两类:
 * - **容量类** ([STACK_I]/[STACK_II]/[STACK_III]): 提升每格逻辑容量, 倍率**连乘**
 *   (同时装 I+III = ×4×64 = ×256)。因组件"各不相同", 每档至多一枚。
 * - **能力类** ([EXP_STORAGE]/[MAGNET]/[VOID]/[PAGE_EXPANSION]): 改变容器行为,
 *   无容量倍率。其中 [PAGE_EXPANSION] 可**叠装多枚** (每枚 +1 页), 为唯一例外。
 *
 * @property itemId 对应 Slimefun 物品 ID (与 Items.kt 定义一致), 作为解析主键
 * @property multiplier 容量倍率 (能力类恒为 1.0)
 */
enum class UpgradeType(val itemId: String, val multiplier: Double) {
    STACK_I("SE_STACK_UPGRADE_I", 4.0),
    STACK_II("SE_STACK_UPGRADE_II", 16.0),
    STACK_III("SE_STACK_UPGRADE_III", 64.0),
    EXP_STORAGE("SE_EXP_UPGRADE", 1.0),
    MAGNET("SE_MAGNET_UPGRADE", 1.0),
    VOID("SE_VOID_UPGRADE", 1.0),
    PAGE_EXPANSION("SE_PAGE_UPGRADE", 1.0),
    WISE("SE_WISE_UPGRADE", 1.0),
    ENDER_WISE("SE_ENDER_WISE_UPGRADE", 1.0),
    EXTRACT("SE_EXTRACT_UPGRADE", 1.0),
    REMOTE("SE_REMOTE_UPGRADE", 1.0),
    OUTPUT("SE_OUTPUT_UPGRADE", 1.0),
    COMPRESSION("SE_COMPRESSION_UPGRADE", 1.0),
    ADVANCED_COMPRESSION("SE_ADVANCED_COMPRESSION_UPGRADE", 1.0);

    /** 是否为容量 (堆叠) 类升级。 */
    val isStack: Boolean get() = multiplier > 1.0

    /** 是否允许在同一容器叠装多枚 (仅翻页扩容)。 */
    val isStackable: Boolean get() = this == PAGE_EXPANSION

    /** 是否为翻页箱专属的压制升级。 */
    val isCompression: Boolean get() = this == COMPRESSION || this == ADVANCED_COMPRESSION

    /** 吸入经验时"翻倍"的独立触发概率 (智者系升级; 其余为 0)。 */
    val wiseChance: Double get() = when (this) {
        WISE -> 0.20        // 智者的护身符: 20% (与原版护身符一致)
        ENDER_WISE -> 0.50  // 末影智者: 50%
        else -> 0.0
    }

    companion object {
        /** 按 Slimefun 物品解析升级类型; 非升级物品返回 null。 */
        fun fromItem(item: ItemStack?): UpgradeType? {
            if (item == null || item.type.isAir) return null
            val id = SlimefunItem.getByItem(item)?.id ?: return null
            return entries.firstOrNull { it.itemId == id }
        }
    }
}
