package top.maplex.slimeEasy.machine.butcher

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.block.Block

/**
 * 屠夫机器的数字状态持久化 (基于 Slimefun BlockData)。
 *
 * 物品 (武器 / 附魔书 / 食物 / 升级组件) 已改由原生 [ButcherMenuPreset] 的 BlockMenu
 * 槽位自动持久化, 本对象只负责**攻击次数余量**这一数字的读写 (非物品, 无法放入槽位)。
 *
 * 所有方法须在主线程调用 (BlockData 读写)。
 */
object ButcherStorage {

    /** 剩余攻击次数余量键 (食物折算而来)。 */
    private const val KEY_FUEL = "se_butcher_fuel"

    /** 剩余攻击次数余量 (无数据时为 0)。 */
    fun getFuel(block: Block): Long =
        StorageCacheUtils.getData(block.location, KEY_FUEL)?.toLongOrNull() ?: 0L

    /** 写入剩余攻击次数余量 (非负)。 */
    fun setFuel(block: Block, value: Long) {
        StorageCacheUtils.setData(block.location, KEY_FUEL, value.coerceAtLeast(0L).toString())
    }
}
