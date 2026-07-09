package top.maplex.slimeEasy.storage.upgrade

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.ItemCodec
import top.maplex.slimeEasy.storage.core.ItemKey

/**
 * 虚空升级的销毁过滤表。
 *
 * 每个容器维护一组被标记为"销毁"的物品身份 ([ItemKey])。启用虚空升级后,
 * 插入命中本表的物品在入库前被湮灭 (用于自动清除采矿等产生的垃圾物品)。
 *
 * 以序列化物品列表保存在 BlockData (键 [DATA_KEY]); 判定时解码回 [ItemKey] 比较。
 */
object VoidFilter {

    private const val DATA_KEY = "se_void_filter"
    private const val SEP = ";"

    /** 读取标记的物品身份集合。 */
    fun read(location: Location): Set<ItemKey> {
        val raw = StorageCacheUtils.getData(location, DATA_KEY) ?: return emptySet()
        if (raw.isEmpty()) return emptySet()
        return raw.split(SEP).mapNotNull { ItemCodec.decode(it)?.let(ItemKey::of) }.toSet()
    }

    /** 判断某物品是否被标记销毁。 */
    fun contains(location: Location, item: ItemStack): Boolean {
        val key = ItemKey.of(item) ?: return false
        return read(location).contains(key)
    }

    /** 切换某物品的标记状态 (已标记则取消, 未标记则加入); 返回切换后是否处于标记态。 */
    fun toggle(location: Location, item: ItemStack): Boolean {
        val key = ItemKey.of(item) ?: return false
        val current = read(location).toMutableSet()
        val nowMarked = if (!current.add(key)) { current.remove(key); false } else true
        write(location, current)
        return nowMarked
    }

    private fun write(location: Location, keys: Set<ItemKey>) {
        val data = keys.joinToString(SEP) { ItemCodec.encode(it.template) }
        StorageCacheUtils.setData(location, DATA_KEY, data)
    }
}
