package top.maplex.slimeEasy.storage.disk

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunUniversalData
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import top.maplex.slimeEasy.storage.core.VirtualStorage
import java.util.UUID

/**
 * 基于 Slimefun UniversalData 的磁盘数据仓库。
 *
 * UniversalData 以 UUID 为主键写入 Slimefun 当前配置的 SQL 后端（SQLite/MySQL/
 * PostgreSQL），正好满足磁盘跨方块移动后的查询需求。磁盘物品自身只保存 UUID，不保存
 * 任何物品模板或数量。
 */
object DiskDataRepository {

    /** UniversalData KV 中的内容键；该键属于数据库协议，发布后不得改名。 */
    private const val CONTENTS_KEY = "se_disk_contents"

    /** 创建与磁盘规格绑定的新 UniversalData 记录。 */
    fun create(tier: DiskTier): UUID {
        val data = controller().createUniversalData(tier.itemId)
        data.setData(CONTENTS_KEY, "")
        return data.uuid
    }

    /** 按 UUID 查询数据库并还原完整 ItemStack 身份与数量。 */
    fun load(id: UUID, tier: DiskTier): VirtualStorage {
        val storage = VirtualStorage(DiskStore.MAX_TYPES, Int.MAX_VALUE, 1.0)
        val data = find(id, tier) ?: return storage
        storage.load(data.getData(CONTENTS_KEY))
        return storage
    }

    /** 把单盘内容写回 Slimefun SQL 数据层。 */
    fun save(id: UUID, tier: DiskTier, storage: VirtualStorage): Boolean {
        val data = find(id, tier) ?: return false
        data.setData(CONTENTS_KEY, storage.serialize())
        return true
    }

    /** UUID 必须确实属于同规格磁盘，避免篡改 PDC 后覆盖其它 UniversalData。 */
    fun exists(id: UUID, tier: DiskTier): Boolean = find(id, tier) != null

    private fun find(id: UUID, tier: DiskTier): SlimefunUniversalData? {
        val controller = controller()
        val data = controller.getUniversalDataFromCache(id)
            ?: controller.getUniversalData(id)?.also(controller::loadUniversalData)
            ?: return null
        if (!data.isDataLoaded) controller.loadUniversalData(data)
        return data.takeIf { it.sfId == tier.itemId && !it.isPendingRemove }
    }

    private fun controller() = Slimefun.getDatabaseManager().blockDataController
}
