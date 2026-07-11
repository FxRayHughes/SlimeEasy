package top.maplex.slimeEasy.storage.upgrade

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Location
import org.bukkit.block.BlockFace

/**
 * 升级的"生效面"配置 (抽取 / 输出各一)。
 *
 * 控制该升级在容器 / 点击器的**哪些相邻方向**生效。默认六向全启用; BlockData 只存
 * **禁用**的面集合(键 [dataKey]), 空 = 全启用 —— 未配置时天然全开, 向后兼容。
 * 以 [Location] 定位, 与宿主类型无关(容器 / 点击器通用)。
 */
class FaceConfig(private val dataKey: String) {

    /** 当前启用的方向集合(= 全六向 − 已禁用)。 */
    fun faces(location: Location): Set<BlockFace> {
        val disabled = disabled(location)
        return ALL.filterTo(LinkedHashSet()) { it !in disabled }
    }

    /** 某方向是否启用。 */
    fun isEnabled(location: Location, face: BlockFace): Boolean = face !in disabled(location)

    /** 切换某方向的启用状态。 */
    fun toggle(location: Location, face: BlockFace) {
        val disabled = disabled(location).toMutableSet()
        if (!disabled.remove(face)) disabled.add(face)
        StorageCacheUtils.setData(location, dataKey, disabled.joinToString(SEP) { it.name })
    }

    private fun disabled(location: Location): Set<BlockFace> {
        val raw = StorageCacheUtils.getData(location, dataKey)
        if (raw.isNullOrEmpty()) return emptySet()
        return raw.split(SEP).mapNotNullTo(HashSet()) { runCatching { BlockFace.valueOf(it) }.getOrNull() }
    }

    companion object {
        private const val SEP = ","

        /** 支持配置的六个方向(固定顺序, 供 GUI 稳定排列)。 */
        val ALL = listOf(
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        )

        /** 抽取升级的生效面。 */
        val EXTRACT = FaceConfig("se_extract_faces")

        /** 输出升级的生效面。 */
        val OUTPUT = FaceConfig("se_output_faces")
    }
}
