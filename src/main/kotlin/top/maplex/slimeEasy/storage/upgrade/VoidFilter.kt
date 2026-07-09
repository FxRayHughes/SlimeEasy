package top.maplex.slimeEasy.storage.upgrade

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.ItemCodec
import top.maplex.slimeEasy.storage.core.ItemKey

/**
 * 虚空升级的销毁过滤表 (带保留数量)。
 *
 * 每个容器维护一组 **物品身份 ([ItemKey]) → 保留数量** 的映射。启用虚空升级后,
 * 命中本表的物品在容器内**封顶到其保留数量**: 已存 < 保留数量时正常入库直到填满,
 * 超出保留数量的入库部分在入库前被湮灭。保留数量为 0 即"全部销毁"(默认)。
 *
 * 以序列化列表保存在 BlockData (键 [DATA_KEY]), 每条 = `base64(物品)|保留数量`, 条目
 * 间以 `;` 分隔 (base64 字母表不含 `;` 与 `|`, 不会冲突)。兼容旧格式 (仅物品无保留数,
 * 读为保留 0)。
 */
object VoidFilter {

    private const val DATA_KEY = "se_void_filter"
    private const val ENTRY_SEP = ";"
    private const val FIELD_SEP = "|"

    /** 读取"物品身份 → 保留数量"映射 (保序); 空表返回空 map。 */
    fun read(location: Location): LinkedHashMap<ItemKey, Int> {
        val map = LinkedHashMap<ItemKey, Int>()
        val raw = StorageCacheUtils.getData(location, DATA_KEY)
        if (raw.isNullOrEmpty()) return map
        for (entry in raw.split(ENTRY_SEP)) {
            if (entry.isEmpty()) continue
            val sep = entry.lastIndexOf(FIELD_SEP)
            // 旧格式无 '|' 分隔: 整条即物品编码, 保留数视为 0
            val enc = if (sep <= 0) entry else entry.substring(0, sep)
            val keep = if (sep <= 0) 0 else entry.substring(sep + 1).toIntOrNull() ?: 0
            val key = ItemCodec.decode(enc)?.let(ItemKey::of) ?: continue
            map[key] = keep.coerceAtLeast(0)
        }
        return map
    }

    /** 某物品是否在销毁列表中。 */
    fun contains(location: Location, item: ItemStack): Boolean {
        val key = ItemKey.of(item) ?: return false
        return read(location).containsKey(key)
    }

    /** 某物品的保留数量; 不在列表中返回 null。 */
    fun keep(location: Location, item: ItemStack): Int? {
        val key = ItemKey.of(item) ?: return null
        return read(location)[key]
    }

    /** 切换标记: 未标记则加入 (默认保留 0), 已标记则移除; 返回加入后是否处于标记态。 */
    fun toggle(location: Location, item: ItemStack): Boolean {
        val key = ItemKey.of(item) ?: return false
        val map = read(location)
        val nowMarked = if (map.remove(key) == null) { map[key] = 0; true } else false
        write(location, map)
        return nowMarked
    }

    /** 调整已标记物品的保留数量 ([delta] 可负, 结果夹取到 ≥0); 未标记则忽略。 */
    fun addKeep(location: Location, item: ItemStack, delta: Int) {
        val key = ItemKey.of(item) ?: return
        val map = read(location)
        val cur = map[key] ?: return
        map[key] = (cur + delta).coerceAtLeast(0)
        write(location, map)
    }

    /** 从销毁列表移除某物品。 */
    fun remove(location: Location, item: ItemStack) {
        val key = ItemKey.of(item) ?: return
        val map = read(location)
        if (map.remove(key) != null) write(location, map)
    }

    /**
     * 计算虚空过滤后**应放入**的数量 (其余湮灭)。
     *
     * - 未标记销毁: 原样返回 [incoming] (不影响正常入库);
     * - 已标记: 上限为保留数量 K, 即 `min(incoming, max(0, K - stored))`。
     *
     * @param stored 该物品在容器内的当前数量
     * @param incoming 本次来料数量
     */
    fun admit(location: Location, item: ItemStack, stored: Long, incoming: Long): Long {
        val k = keep(location, item) ?: return incoming
        val room = (k.toLong() - stored).coerceAtLeast(0L)
        return minOf(incoming, room)
    }

    private fun write(location: Location, map: Map<ItemKey, Int>) {
        val data = map.entries.joinToString(ENTRY_SEP) { ItemCodec.encode(it.key.template) + FIELD_SEP + it.value }
        StorageCacheUtils.setData(location, DATA_KEY, data)
    }
}
