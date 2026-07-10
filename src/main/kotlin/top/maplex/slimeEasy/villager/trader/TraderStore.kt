package top.maplex.slimeEasy.villager.trader

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Material
import org.bukkit.block.Block
import top.maplex.slimeEasy.villager.core.VillagerCodec
import top.maplex.slimeEasy.villager.core.VillagerData

/**
 * 村民交易器的方块状态持久化 (基于 Slimefun BlockData, 仅 String 值)。
 *
 * 存三项: 装配村民快照 ([VillagerCodec] 串)、工作站方块材质、上次补货墙钟时间戳。
 * 所有方法须在主线程调用。
 */
object TraderStore {

    private const val KEY_VILLAGER = "se_trader_villager"
    private const val KEY_WORKSTATION = "se_trader_workstation"
    private const val KEY_RESTOCK = "se_trader_restock"

    /** 装配村民快照; 未装配返回 null。 */
    fun getVillager(block: Block): VillagerData? =
        VillagerCodec.decode(StorageCacheUtils.getData(block.location, KEY_VILLAGER))

    /** 写入 / 清除装配村民 (null 清除)。 */
    fun setVillager(block: Block, data: VillagerData?) {
        StorageCacheUtils.setData(block.location, KEY_VILLAGER, data?.let { VillagerCodec.encode(it) } ?: "")
    }

    /** 是否已装配村民。 */
    fun hasVillager(block: Block): Boolean = getVillager(block) != null

    /** 工作站方块材质; 未装配返回 null。 */
    fun getWorkstation(block: Block): Material? =
        StorageCacheUtils.getData(block.location, KEY_WORKSTATION)
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Material.valueOf(it) }.getOrNull() }

    /** 写入 / 清除工作站方块 (null 清除)。 */
    fun setWorkstation(block: Block, material: Material?) {
        StorageCacheUtils.setData(block.location, KEY_WORKSTATION, material?.name ?: "")
    }

    /** 上次补货时间戳 (毫秒); 无记录返回 0。 */
    fun getLastRestock(block: Block): Long =
        StorageCacheUtils.getData(block.location, KEY_RESTOCK)?.toLongOrNull() ?: 0L

    /** 写入上次补货时间戳。 */
    fun setLastRestock(block: Block, millis: Long) {
        StorageCacheUtils.setData(block.location, KEY_RESTOCK, millis.toString())
    }
}
