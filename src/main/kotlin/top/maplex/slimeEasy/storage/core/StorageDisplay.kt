package top.maplex.slimeEasy.storage.core

import top.maplex.slimeEasy.config.I18n
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack

/**
 * 存储 GUI 的"拆格展示"辅助。
 *
 * 把 "物品身份 → long 总量" 的库存拆成若干**满格槽位**: 单格容量 = 原版堆叠 × 堆叠
 * 升级倍率, 超出部分溢出到新格。
 * - 无堆叠升级 (倍率 1): 32 末影珍珠 → 2 格 × 16;
 * - 堆叠 ×4: 256 石头合并为 1 格 (原版堆叠 64 × 4), lore 显示真实数量。
 *
 * 单格数量可能远超原版堆叠上限, 图标视觉数量按原版堆叠封顶, **真实数量写入 lore**。
 */
object StorageDisplay {

    /**
     * 一个展示单元 (槽位)。
     *
     * @property key 物品身份
     * @property amount 本槽真实数量 (1..单格容量, 可超原版堆叠)
     * @property typeTotal 该物品在整个存储中的总量 (供 lore 展示)
     */
    data class Cell(val key: ItemKey, val amount: Long, val typeTotal: Long)

    /**
     * 把库存条目按单格容量拆成展示单元列表 (顺序稳定)。
     *
     * @param cellCap 给定物品的单格容量 (默认为原版堆叠数; 容器可传入含堆叠升级的容量)
     */
    fun toCells(
        entries: List<Pair<ItemKey, Long>>,
        cellCap: (ItemKey) -> Long = { it.vanillaMaxStack.toLong() }
    ): List<Cell> {
        val cells = ArrayList<Cell>()
        for ((key, total) in entries) {
            val cap = cellCap(key).coerceAtLeast(1L)
            var remaining = total
            while (remaining > 0) {
                val n = minOf(remaining, cap)
                cells.add(Cell(key, n, total))
                remaining -= n
            }
        }
        return cells
    }

    /**
     * 构造**聚合**展示图标: 不拆格, 一种物品一个图标 (供网络终端统筹全网库存)。
     *
     * 与 [icon]/[toCells] 的"按堆叠拆格平铺"相对: 此处一物一图标, 视觉数量按原版
     * 堆叠封顶, 真实总量写入 lore。
     */
    fun aggregatedIcon(key: ItemKey, total: Long): ItemStack {
        val visual = minOf(total, key.vanillaMaxStack.toLong()).toInt().coerceAtLeast(1)
        return key.toDisplay(visual).apply {
            editMeta {
                it.lore(listOf(Component.text(I18n.text("messages.storage-display-001", "value0" to (QuantityFormat.grouped(total))))))
            }
        }
    }

    /** 构造展示图标: 视觉数量按原版堆叠封顶, 真实数量 + 总量写入 lore。 */
    fun icon(cell: Cell): ItemStack {
        val visual = minOf(cell.amount, cell.key.vanillaMaxStack.toLong()).toInt()
        return cell.key.toDisplay(visual).apply {
            editMeta {
                it.lore(listOf(
                    Component.text(I18n.text("messages.storage-display-002", "value0" to (QuantityFormat.grouped(cell.amount)))),
                    Component.text(I18n.text("messages.storage-display-003", "value0" to (QuantityFormat.grouped(cell.typeTotal))))
                ))
            }
        }
    }
}
