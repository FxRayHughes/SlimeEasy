package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.block.Block

/**
 * 网络端口工作模式。
 *
 * 缺少持久化数据的旧端口必须按 [CARGO] 解释，保证升级后既有货运线路不会突然开始
 * 扫描相邻容器。模式只控制 Slimefun 货运与主动相邻 IO；玩家缓冲及其网络转运始终可用。
 */
enum class PortMode(val cargoEnabled: Boolean, val activeEnabled: Boolean) {
    CARGO(cargoEnabled = true, activeEnabled = false),
    ACTIVE(cargoEnabled = false, activeEnabled = true),
    BOTH(cargoEnabled = true, activeEnabled = true);

    /** 按界面展示顺序循环到下一种模式。 */
    fun next(): PortMode = entries[(ordinal + 1) % entries.size]

    companion object {
        /** 固定 BlockData 键；修改会导致已保存端口模式丢失，禁止随意重命名。 */
        private const val DATA_KEY = "se_network_port_mode"

        /** 读取模式；未知值与旧端口均回退为仅货运模式。 */
        fun read(block: Block): PortMode =
            runCatching { valueOf(StorageCacheUtils.getData(block.location, DATA_KEY).orEmpty()) }
                .getOrDefault(CARGO)

        /** 持久化端口模式。 */
        fun write(block: Block, mode: PortMode) {
            StorageCacheUtils.setData(block.location, DATA_KEY, mode.name)
        }
    }
}
