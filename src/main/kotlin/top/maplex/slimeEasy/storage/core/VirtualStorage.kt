package top.maplex.slimeEasy.storage.core

import org.bukkit.inventory.ItemStack

/**
 * 虚拟库存核心 (槽位预算制)。
 *
 * 以 [ItemKey] → long 计数保存物品。容量按**槽位**衡量:
 * - 每个槽位可存 `物品原版堆叠数 × [stackMultiplier]` (默认倍率 1 即原版堆叠数,
 *   堆叠升级使倍率 >1 而放大单槽容量);
 * - 一种物品数量超过单槽容量时**溢出到新槽位** (与 UI 拆格展示一致);
 * - 全部物品占用的槽位总数不得超过 [maxSlots], 种类数不得超过 [maxTypes]。
 * - 提供 [capacityPolicy] 时改由外部容量协议给出精确接收量，供磁盘等非槽位制后端复用。
 *
 * 抽屉与翻页箱共用本类, 差异仅在构造参数:
 * - 抽屉: [maxTypes] = 1, [maxSlots] = 抽屉内部槽数 (单一物品的大容量);
 * - 箱子: [maxTypes] = [maxSlots] = 页数 × 45 (受翻页扩容影响)。
 *
 * 本类不涉及持久化 IO, 只提供 [serialize]/[load]。非线程安全: 约定主线程访问。
 */
class VirtualStorage(
    var maxTypes: Int,
    var maxSlots: Int,
    var stackMultiplier: Double,
    private val capacityPolicy: CapacityPolicy? = null
) {

    /** 有序物品表: 保留插入顺序以支撑箱子分页稳定展示 (仅含数量 > 0 的物品)。 */
    private val contents = LinkedHashMap<ItemKey, Long>()

    /**
     * 物品身份记忆 (借鉴 SophisticatedStorage 的 memorized item)。
     *
     * 记录本存储"绑定过"的物品身份 —— 即使被取空仍保留, 使网络分发时该物品
     * **优先回到原存储位**, 减少碎片、稳定抽屉的单一物品身份。始终包含 [contents]
     * 的全部键, 并被约束在 [maxTypes] 以内 (超出时淘汰最早的空记忆)。
     */
    private val memory = LinkedHashSet<ItemKey>()

    /** 当前已存的不同物品种类数。 */
    val typeCount: Int get() = contents.size

    /** 是否完全为空。 */
    fun isEmpty(): Boolean = contents.isEmpty()

    /** 查询某物品当前数量; 不存在返回 0。 */
    fun count(key: ItemKey): Long = contents[key] ?: 0L

    /** 本存储是否"记住"该物品身份 (含当前持有与取空后的绑定)。 */
    fun remembers(key: ItemKey): Boolean = key in memory

    /** 将 [key] 记入身份记忆, 并把记忆规模约束在 [maxTypes] 内 (淘汰最早的空记忆)。 */
    private fun remember(key: ItemKey) {
        memory.add(key)
        if (memory.size <= maxTypes) return
        val it = memory.iterator()
        while (memory.size > maxTypes && it.hasNext()) {
            val k = it.next()
            if (k !in contents) it.remove() // 只淘汰已取空的记忆, 绝不丢弃在存物品
        }
    }

    /** 有序快照 (键 + 数量), 供分页 GUI 与掉落遍历使用。 */
    fun entries(): List<Pair<ItemKey, Long>> = contents.map { it.key to it.value }

    /** 单个槽位可存的该物品数量 = 原版堆叠数 × 倍率 (至少 1)。 */
    fun cellCapacity(key: ItemKey): Long =
        maxOf(1L, (key.vanillaMaxStack.toLong().toDouble() * stackMultiplier).toLong())

    /** 存放 [count] 个 [key] 需占用的槽位数 (向上取整)。 */
    private fun cellsFor(key: ItemKey, count: Long): Int {
        if (count <= 0) return 0
        val cap = cellCapacity(key)
        return ((count + cap - 1) / cap).toInt()
    }

    /** 当前占用的槽位总数。 */
    fun usedSlots(): Int = contents.entries.sumOf { cellsFor(it.key, it.value) }

    /** 若倍率改为 [multiplier], 现有内容需占用的槽位总数 (供卸载堆叠升级前校验)。 */
    fun slotsUnder(multiplier: Double): Int = contents.entries.sumOf { (key, count) ->
        val cap = maxOf(1L, (key.vanillaMaxStack.toLong().toDouble() * multiplier).toLong())
        ((count + cap - 1) / cap).toInt()
    }

    /**
     * 尝试插入 [amount] 个 [item]。
     *
     * 受两重约束: ① 种类数 ≤ [maxTypes]; ② 全部物品占用槽位总数 ≤ [maxSlots]
     * (单槽容量 = 原版堆叠 × 倍率, 溢出占新槽)。
     *
     * @param simulate true 时只试算不改动
     * @return 未能放入的剩余数量 (0 表示全部放入)
     */
    fun insert(item: ItemStack, amount: Long, simulate: Boolean): Long {
        if (amount <= 0) return 0
        val key = ItemKey.of(item) ?: return amount
        val existing = contents[key] ?: 0L
        if (capacityPolicy != null) {
            // 磁盘等非槽位制后端必须由策略给出精确接收量，避免先套用原版堆叠容量造成误拒绝。
            val accepted = capacityPolicy.acceptedAmount(this, item, amount).coerceIn(0L, amount)
            if (!simulate && accepted > 0) {
                contents[key] = existing + accepted
                remember(key)
            }
            return amount - accepted
        }
        if (existing == 0L && contents.size >= maxTypes) return amount // 种类已满且是新物品
        val cap = cellCapacity(key)
        // 其它物品已占的槽位; 本物品可用槽位 = 总预算 - 其它占用
        val slotsForOthers = usedSlots() - cellsFor(key, existing)
        val availSlots = maxSlots - slotsForOthers
        if (availSlots <= 0) return amount
        val maxForType = availSlots.toLong() * cap
        val room = maxForType - existing
        if (room <= 0) return amount
        val accepted = minOf(room, amount)
        if (!simulate) {
            contents[key] = existing + accepted
            remember(key) // 记入身份记忆 (供网络优先回位)
        }
        return amount - accepted
    }

    /**
     * 尝试取出某物品 [amount] 个。
     *
     * @return 实际取出的数量 (可能少于请求; 取空后从 [contents] 移除但**保留身份记忆**)
     */
    fun extract(key: ItemKey, amount: Long, simulate: Boolean): Long {
        if (amount <= 0) return 0
        val existing = contents[key] ?: return 0
        val taken = minOf(existing, amount)
        if (!simulate) {
            val left = existing - taken
            if (left <= 0) contents.remove(key) else contents[key] = left
            // 注意: 不清除 memory, 使取空后该物品仍优先回到本存储位
        }
        return taken
    }

    /**
     * 序列化为单个字符串, 供写入 BlockData。
     *
     * 格式: 每条 = base64(物品) + [FIELD_SEP] + 数量; 条目间以 [ENTRY_SEP] 分隔。
     * base64 字母表不含 ';' 与 '|', 故分隔符不会与内容冲突。
     *
     * 身份记忆 ([memory]) 中已取空的物品以 **数量 0** 写出, 以便重载后仍保留
     * "优先回位"的绑定关系; 在存物品自然以其真实数量写出。
     */
    fun serialize(): String = memory.joinToString(ENTRY_SEP) { key ->
        ItemCodec.encode(key.template) + FIELD_SEP + (contents[key] ?: 0L)
    }

    /** 用已有内容覆盖当前存储 (先清空), 供从 BlockData 恢复 (含身份记忆)。 */
    fun load(data: String?) {
        contents.clear()
        memory.clear()
        if (data.isNullOrEmpty()) return
        for (entry in data.split(ENTRY_SEP)) {
            val sep = entry.lastIndexOf(FIELD_SEP)
            if (sep <= 0) continue
            val item = ItemCodec.decode(entry.substring(0, sep)) ?: continue
            val amount = entry.substring(sep + 1).toLongOrNull() ?: continue
            val key = ItemKey.of(item) ?: continue
            memory.add(key)               // 记忆: 含在存 (>0) 与取空绑定 (=0)
            if (amount > 0) contents[key] = amount
        }
    }

    private companion object {
        const val ENTRY_SEP = ";"
        const val FIELD_SEP = "|"
    }
}

/**
 * 非槽位制虚拟库存的容量协议。
 *
 * 实现只计算本次请求最多可接受的数量，不得修改 [storage]；实际写入仍由
 * [VirtualStorage.insert] 统一完成，确保模拟插入不会产生副作用。
 */
fun interface CapacityPolicy {
    fun acceptedAmount(storage: VirtualStorage, item: ItemStack, requested: Long): Long
}
