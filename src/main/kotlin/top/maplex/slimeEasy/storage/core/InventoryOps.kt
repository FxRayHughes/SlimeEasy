package top.maplex.slimeEasy.storage.core

import org.bukkit.entity.Player

/**
 * 存储 GUI 的背包操作辅助。
 *
 * 存入操作统一走本类, **按物品身份 (ItemKey) 增量移除**, 不依赖点击回调返回的
 * 槽位下标语义 —— 从而规避"回调 slot 到底是背包索引还是 GUI raw slot"的不确定性,
 * 避免误删背包其它格物品。
 */
object InventoryOps {

    /**
     * 从玩家背包移除 [amount] 个与 [key] 同类的物品。
     *
     * 依赖 Bukkit [org.bukkit.inventory.Inventory.removeItem] 的按相似度匹配移除,
     * 与本插件 [ItemKey] 的同类判定 (isSimilar) 语义一致。
     */
    fun remove(player: Player, key: ItemKey, amount: Int) {
        if (amount <= 0) return
        var remaining = amount
        // removeItem 单次上限为一组物品语义; 用模板堆叠拆分, 确保大额也能扣净
        while (remaining > 0) {
            val batch = minOf(remaining, key.template.maxStackSize.coerceAtLeast(1))
            val stack = key.template.clone().apply { this.amount = batch }
            val notRemoved = player.inventory.removeItem(stack).values.sumOf { it.amount }
            val removed = batch - notRemoved
            remaining -= batch
            if (removed < batch) break // 背包已无更多同类, 停止
        }
        player.updateInventory()
    }
}
