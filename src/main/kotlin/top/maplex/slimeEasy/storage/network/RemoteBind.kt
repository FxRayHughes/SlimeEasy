package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.block.Block
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.UpgradeType
import top.maplex.slimeEasy.util.BlockLocationCodec

/**
 * 远程升级的双向绑定索引 (容器 ↔ 控制器)。
 *
 * - 容器侧 BlockData ([KEY_CONTAINER_CTRL]): 记录本容器当前挂靠的控制器坐标, 供变更
 *   差异对比与 build 时一致性校验;
 * - 控制器侧 BlockData ([KEY_CTRL_MEMBERS]): 记录挂靠到本控制器的远程容器坐标列表,
 *   供 [NetworkScan.build] 反查注入 members。
 *
 * 坐标编码 "world;x;y;z"; 控制器成员列表以换行分隔多条。所有读写走 BlockData
 * ([StorageCacheUtils]), 约定主线程调用。
 */
object RemoteBind {

    private const val KEY_CONTAINER_CTRL = "se_remote_ctrl"
    private const val KEY_CTRL_MEMBERS = "se_remote_members"

    /** 从容器已安装的远程升级物品 PDC 解析其选定的控制器坐标; 无则 null。 */
    private fun controllerFromUpgrades(container: Block): String? {
        for (item in UpgradeStore.readItems(container.location)) {
            if (UpgradeType.fromItem(item) == UpgradeType.REMOTE) return RemoteUpgrade.controllerOf(item)
        }
        return null
    }

    /**
     * 同步容器的远程绑定: 对比升级物品选定的控制器 (新) 与已记录挂靠 (旧), 变化时更新
     * 双向索引并使网络缓存失效。装入 / 取出 / 换绑三种变更均覆盖。
     */
    fun sync(container: Block) {
        val now = controllerFromUpgrades(container)
        val old = StorageCacheUtils.getData(container.location, KEY_CONTAINER_CTRL)?.takeUnless { it.isEmpty() }
        if (now == old) return
        if (old != null) BlockLocationCodec.decode(old)?.let { removeMember(it, container) }
        if (now != null) BlockLocationCodec.decode(now)?.let { addMember(it, container) }
        StorageCacheUtils.setData(container.location, KEY_CONTAINER_CTRL, now ?: "")
        NetworkRegistry.invalidateAll() // 拓扑变更: 远程成员增减后重建网络
    }

    /** 容器破坏时清理: 从挂靠控制器移除本容器并清空自身记录。 */
    fun clear(container: Block) {
        val old = StorageCacheUtils.getData(container.location, KEY_CONTAINER_CTRL)?.takeUnless { it.isEmpty() } ?: return
        BlockLocationCodec.decode(old)?.let { removeMember(it, container) }
        StorageCacheUtils.setData(container.location, KEY_CONTAINER_CTRL, "")
        NetworkRegistry.invalidateAll()
    }

    /**
     * 读取挂靠到 [controller] 的远程成员 (含一致性校验: 剔除已非容器 / 反向记录不指向本
     * 控制器的失效残留)。供 [NetworkScan.build] 注入 members。
     */
    fun remoteMembersOf(controller: Block): List<Pair<Block, CargoBufferBlock>> {
        val list = readMembers(controller)
        if (list.isEmpty()) return emptyList()
        val ctrlEnc = BlockLocationCodec.encode(controller)
        val result = ArrayList<Pair<Block, CargoBufferBlock>>(list.size)
        for (enc in list) {
            val b = BlockLocationCodec.decode(enc) ?: continue
            if (!StorageCacheUtils.hasBlock(b.location)) continue
            val sf = SlimefunItem.getById(StorageCacheUtils.getBlock(b.location)?.sfId ?: continue) as? CargoBufferBlock ?: continue
            if (StorageCacheUtils.getData(b.location, KEY_CONTAINER_CTRL) != ctrlEnc) continue // 反向记录须指向本控制器
            result.add(b to sf)
        }
        return result
    }

    // ---- 控制器成员列表读写 ----

    private fun readMembers(controller: Block): MutableList<String> {
        val raw = StorageCacheUtils.getData(controller.location, KEY_CTRL_MEMBERS)
        if (raw.isNullOrEmpty()) return mutableListOf()
        return raw.split("\n").filter { it.isNotEmpty() }.toMutableList()
    }

    private fun writeMembers(controller: Block, list: List<String>) {
        StorageCacheUtils.setData(controller.location, KEY_CTRL_MEMBERS, list.joinToString("\n"))
    }

    private fun addMember(controller: Block, container: Block) {
        val enc = BlockLocationCodec.encode(container)
        val list = readMembers(controller)
        if (enc !in list) { list.add(enc); writeMembers(controller, list) }
    }

    private fun removeMember(controller: Block, container: Block) {
        val list = readMembers(controller)
        if (list.remove(BlockLocationCodec.encode(container))) writeMembers(controller, list)
    }
}
