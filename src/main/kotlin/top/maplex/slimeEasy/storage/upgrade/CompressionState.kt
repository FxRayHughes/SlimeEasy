package top.maplex.slimeEasy.storage.upgrade

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Location

/**
 * 压制升级的逐容器持久化选项。
 *
 * 默认拒绝不可逆配方，避免玩家在未明确授权时把原料压成无法拆回的物品。
 */
object CompressionState {

    /** BlockData 协议键；修改会使已保存容器丢失该选项。 */
    private const val IRREVERSIBLE_KEY = "se_compression_irreversible"

    /** 查询指定容器是否允许处理不可逆压缩配方。 */
    fun allowsIrreversible(location: Location): Boolean =
        StorageCacheUtils.getData(location, IRREVERSIBLE_KEY)?.toBooleanStrictOrNull() ?: false

    /** 切换不可逆配方开关、即时持久化并返回新状态。 */
    fun toggleIrreversible(location: Location): Boolean {
        val enabled = !allowsIrreversible(location)
        StorageCacheUtils.setData(location, IRREVERSIBLE_KEY, enabled.toString())
        return enabled
    }
}
