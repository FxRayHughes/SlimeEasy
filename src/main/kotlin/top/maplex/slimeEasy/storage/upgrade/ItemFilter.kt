package top.maplex.slimeEasy.storage.upgrade

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.storage.core.ItemCodec
import top.maplex.slimeEasy.storage.core.ItemKey

/** 过滤模式。 */
enum class FilterMode { BLACKLIST, WHITELIST }

/**
 * 通用物品黑 / 白名单过滤器 (抽取升级 / 输出升级共用)。
 *
 * 每个容器维护一组物品身份 ([ItemKey]) 名单 + 一个模式:
 * - **黑名单** (默认): 命中名单的物品**不处理**, 其余放行 —— 空名单 = 全部放行 (兼容未配置);
 * - **白名单**: 仅命中名单的物品放行 —— 空名单 = 全部拒绝。
 *
 * 名单以序列化列表存于 BlockData (键 [listKey]), 每条 = `base64(物品)`, 以 `;` 分隔
 * ([ItemCodec] 的 base64 字母表不含 `;`, 不冲突)。模式单独存于键 [modeKey]。
 * 数据以 [Location] 定位, 与宿主类型无关 (容器 / 点击器通用)。
 */
class ItemFilter(private val listKey: String, private val modeKey: String) {

    /** 读取名单 (保序); 空名单返回空集。 */
    fun read(location: Location): LinkedHashSet<ItemKey> {
        val set = LinkedHashSet<ItemKey>()
        val raw = StorageCacheUtils.getData(location, listKey)
        if (raw.isNullOrEmpty()) return set
        for (enc in raw.split(ENTRY_SEP)) {
            if (enc.isEmpty()) continue
            ItemCodec.decode(enc)?.let(ItemKey::of)?.let(set::add)
        }
        return set
    }

    /** 当前模式; 未设置时由全局配置决定默认黑 / 白名单。 */
    fun mode(location: Location): FilterMode =
        when (StorageCacheUtils.getData(location, modeKey)) {
            FilterMode.WHITELIST.name -> FilterMode.WHITELIST
            FilterMode.BLACKLIST.name -> FilterMode.BLACKLIST
            else -> if (SEConfig.storageFilterDefaultWhitelist) FilterMode.WHITELIST else FilterMode.BLACKLIST
        }

    /** 设置模式。 */
    fun setMode(location: Location, mode: FilterMode) {
        StorageCacheUtils.setData(location, modeKey, mode.name)
    }

    /** 某物品是否在名单中。 */
    fun contains(location: Location, item: ItemStack): Boolean {
        val key = ItemKey.of(item) ?: return false
        return read(location).contains(key)
    }

    /** 切换标记: 未在名单则加入, 已在则移除; 返回加入后是否处于标记态。 */
    fun toggle(location: Location, item: ItemStack): Boolean {
        val key = ItemKey.of(item) ?: return false
        val set = read(location)
        val nowMarked = if (!set.remove(key)) {
            if (set.size >= SEConfig.storageFilterMaxItems) return false
            set.add(key)
            true
        } else false
        write(location, set)
        return nowMarked
    }

    /** 从名单移除某物品。 */
    fun remove(location: Location, item: ItemStack) {
        val key = ItemKey.of(item) ?: return
        val set = read(location)
        if (set.remove(key)) write(location, set)
    }

    /**
     * 判断某物品是否允许通过 (被处理)。
     *
     * 无法识别的物品 (null / AIR) 一律放行。黑名单: 命中则拒; 白名单: 命中才放。
     */
    fun allows(location: Location, item: ItemStack): Boolean {
        val key = ItemKey.of(item) ?: return true
        val listed = read(location).contains(key)
        return when (mode(location)) {
            FilterMode.BLACKLIST -> !listed
            FilterMode.WHITELIST -> listed
        }
    }

    private fun write(location: Location, set: Set<ItemKey>) {
        val data = set.joinToString(ENTRY_SEP) { ItemCodec.encode(it.template) }
        StorageCacheUtils.setData(location, listKey, data)
    }

    companion object {
        private const val ENTRY_SEP = ";"

        /** 抽取升级的过滤器 (控制从相邻容器抽取哪些物品)。 */
        val EXTRACT = ItemFilter("se_extract_filter", "se_extract_mode")

        /** 输出升级的过滤器 (控制向相邻容器推送哪些物品)。 */
        val OUTPUT = ItemFilter("se_output_filter", "se_output_mode")
    }
}
