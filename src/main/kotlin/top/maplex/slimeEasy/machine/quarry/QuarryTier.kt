package top.maplex.slimeEasy.machine.quarry

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.inventory.ItemStack

/**
 * 采石场效率档位。
 *
 * 每个档位对应一个独立的效率升级组件 (与 [top.maplex.slimeEasy.registry.Items] 定义一致),
 * 放入采石场升级槽即生效。速率约定 (本项目 1 个 Slimefun tick ≈ 0.5 秒):
 *
 * - **无升级** (空槽): 每 1 秒产 1 个 (= 每 2 tick 产 1 个), 见 [Quarry] 的基础节流;
 * - **各档 ([I]~[V])**: 每 tick (≈0.5 秒) 产 [perOperation] 个, 无额外节流。
 *
 * 档位仅由槽内物品**身份**决定, 与堆叠数量无关。
 *
 * @property itemId       对应效率升级组件的 Slimefun 物品 ID (解析主键)
 * @property perOperation 每次生产操作产出的圆石数量
 */
enum class QuarryTier(val itemId: String, val perOperation: Int) {
    I("SE_QUARRY_EFFICIENCY_I", 1),
    II("SE_QUARRY_EFFICIENCY_II", 6),
    III("SE_QUARRY_EFFICIENCY_III", 12),
    IV("SE_QUARRY_EFFICIENCY_IV", 32),
    V("SE_QUARRY_EFFICIENCY_V", 64);

    companion object {
        /** 按 Slimefun 物品解析效率档位; 非效率升级组件返回 null (视为无升级)。 */
        fun fromItem(item: ItemStack?): QuarryTier? {
            if (item == null || item.type.isAir) return null
            val id = SlimefunItem.getByItem(item)?.id ?: return null
            return entries.firstOrNull { it.itemId == id }
        }
    }
}
