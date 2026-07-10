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

    /**
     * 从玩家背包**指定槽位** [slot] 精确移除 [amount] 个物品 (点击存入用: 点哪组就扣哪组)。
     *
     * [slot] 为 [org.bukkit.event.inventory.InventoryClickEvent.getSlot] 语义 —— 玩家背包内的
     * 槽位下标, 可直接用于 [org.bukkit.inventory.PlayerInventory.getItem]。仅当该槽物品与 [key]
     * 同类时才从该槽扣除, 规避槽位异常时误删其它格; 该槽不足的余量再按物品身份 ([remove]) 从
     * 其余同类槽补扣, 保证背包扣除总量与实际入库一致。
     */
    fun removeFromSlot(player: Player, slot: Int, key: ItemKey, amount: Int) {
        if (amount <= 0) return
        var remaining = amount
        val stack = player.inventory.getItem(slot)
        if (key.matches(stack)) {
            val take = minOf(remaining, stack!!.amount)
            stack.amount -= take
            player.inventory.setItem(slot, if (stack.amount <= 0) null else stack)
            remaining -= take
        }
        if (remaining > 0) remove(player, key, remaining) // 点击槽不足, 余量按身份补扣
        player.updateInventory()
    }
}
