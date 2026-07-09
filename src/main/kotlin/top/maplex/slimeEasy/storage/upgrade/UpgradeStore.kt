package top.maplex.slimeEasy.storage.upgrade

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.ItemCodec

/**
 * 升级组件的持久化与解算。
 *
 * 容器最多容纳 [MAX_SLOTS] 个**不同**升级组件, 以物品形式序列化保存在 BlockData
 * (键 [DATA_KEY]) —— 既用于破坏时原样返还, 也用于升级 GUI 展示。
 *
 * 逻辑侧通过 [resolve] 得到归纳后的 [UpgradeSet]; 重复类型在解算时自动去重
 * (GUI 侧亦应阻止放入重复类型)。
 */
object UpgradeStore {

    /** 升级槽上限 (6 种能力/容量升级 + 最多 4 个翻页扩容 + 余量)。 */
    const val MAX_SLOTS = 12

    private const val DATA_KEY = "se_upgrades"
    private const val SEP = ";"

    /** 读取容器当前的升级物品列表 (含无效项过滤, 顺序稳定)。 */
    fun readItems(location: Location): List<ItemStack> {
        val raw = StorageCacheUtils.getData(location, DATA_KEY) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(SEP).mapNotNull { ItemCodec.decode(it) }.take(MAX_SLOTS)
    }

    /** 写入升级物品列表 (超出 [MAX_SLOTS] 的部分截断)。 */
    fun writeItems(location: Location, items: List<ItemStack>) {
        val data = items.take(MAX_SLOTS).joinToString(SEP) { ItemCodec.encode(it) }
        StorageCacheUtils.setData(location, DATA_KEY, data)
    }

    /** 解算容器当前升级为 [UpgradeSet] (类型去重, 翻页扩容单独计数)。 */
    fun resolve(location: Location): UpgradeSet {
        val installed = readItems(location).mapNotNull { UpgradeType.fromItem(it) }
        val types = installed.toSet()
        val pageExpansions = installed.count { it == UpgradeType.PAGE_EXPANSION }
        return UpgradeSet(types, pageExpansions)
    }
}
