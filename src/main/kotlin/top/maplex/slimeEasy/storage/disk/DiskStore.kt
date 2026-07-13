package top.maplex.slimeEasy.storage.disk

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.QuantityFormat
import top.maplex.slimeEasy.storage.core.VirtualStorage
import java.util.UUID

/**
 * 磁盘物品身份与数据库内容的唯一读写入口。
 *
 * 物品 PDC 只保存稳定 UUID 与格式版本；完整物品模板和数量由 [DiskDataRepository]
 * 通过 Slimefun SQL 数据层按 UUID 查询。拆下、掉落或跨区块搬运只移动轻量身份。
 */
object DiskStore {

    /** 磁盘数据库 UUID 的持久化键；键名一经发布不得修改。 */
    val ID_KEY = NamespacedKey(SlimeEasy.instance, "storage_disk_id")

    /** 磁盘数据版本键，为未来格式迁移保留稳定入口。 */
    val VERSION_KEY = NamespacedKey(SlimeEasy.instance, "storage_disk_version")

    const val MAX_TYPES = 64
    private const val DATA_VERSION = 1

    /** 按磁盘 UUID 查询运行时库存；无 UUID 的新磁盘尚未入库，按空盘处理。 */
    fun read(item: ItemStack): VirtualStorage {
        val tier = DiskTier.of(item) ?: return VirtualStorage(MAX_TYPES, Int.MAX_VALUE, 1.0)
        return idOf(item)?.let { DiskDataRepository.load(it, tier) }
            ?: VirtualStorage(MAX_TYPES, Int.MAX_VALUE, 1.0)
    }

    /**
     * 把运行时内容事务写回数据库，并确保物品携带 UUID 与最新状态 Lore。
     *
     * 返回新物品而不原地修改调用方对象，避免该物品同时被 Bukkit 库存和 GUI 引用时
     * 出现不可追踪的 ItemStack 身份变化。
     */
    fun write(item: ItemStack, tier: DiskTier, storage: VirtualStorage): ItemStack {
        val result = item.clone().apply { amount = 1 }
        val existingId = idOf(result)?.takeIf { DiskDataRepository.exists(it, tier) }
        val id = existingId ?: DiskDataRepository.create(tier)
        check(DiskDataRepository.save(id, tier, storage)) { "Unable to persist Slimefun disk data: $id" }
        result.editMeta { meta ->
            meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id.toString())
            meta.persistentDataContainer.set(VERSION_KEY, PersistentDataType.INTEGER, DATA_VERSION)
        }
        return display(result, tier, storage)
    }

    /** 只刷新状态 Lore，不触发数据库写入，供频繁重绘的 UI 使用。 */
    fun display(item: ItemStack, tier: DiskTier, storage: VirtualStorage): ItemStack = item.clone().apply {
        amount = 1
        editMeta { meta ->
            meta.lore(I18n.components(
                "items.storage.disks.status-lore",
                "types" to storage.typeCount,
                "maxTypes" to MAX_TYPES,
                "items" to QuantityFormat.grouped(totalItems(storage)),
                "used" to formatBytes(usedEighthBytes(tier, storage)),
                "capacity" to QuantityFormat.grouped(tier.capacityBytes)
            ))
        }
    }

    /** 非空磁盘不能作为普通物品再次写入磁盘，避免递归数据与容量绕过。 */
    fun isNonEmptyDisk(item: ItemStack?): Boolean {
        val tier = DiskTier.of(item) ?: return false
        return !read(item!!).isEmpty() && tier.capacityBytes > 0
    }

    /**
     * 按协议以 1/8 字节为最小单位计算占用，避免整数除法把不足 8 个物品错误舍入。
     * 换算后恰好等于 `类型数 × x × 8 字节 + 总物品数 / 8 字节`。
     */
    fun usedEighthBytes(tier: DiskTier, storage: VirtualStorage): Long =
        storage.typeCount * tier.bytesPerType * 8L + totalItems(storage)

    /**
     * 把内部 1/8 字节单位按半入规则四舍五入为整数字节；容量判断仍保留精确单位，
     * 这里只统一玩家看到的磁盘与管理器状态，避免小数字节造成误解。
     */
    fun formatBytes(eighthBytes: Long): String =
        QuantityFormat.grouped((eighthBytes + 4L) / 8L)

    /** 当前磁盘还能接收多少个指定物品。 */
    fun roomFor(tier: DiskTier, storage: VirtualStorage, item: ItemStack): Long {
        if (isNonEmptyDisk(item)) return 0
        val key = ItemKey.of(item) ?: return 0
        val isNewType = storage.count(key) == 0L
        val nextTypes = storage.typeCount + if (isNewType) 1 else 0
        if (nextTypes > MAX_TYPES) return 0
        val bytesForItems = tier.capacityBytes - nextTypes * tier.bytesPerType
        if (bytesForItems <= 0) return 0
        val maxItems = bytesForItems * 8L
        return (maxItems - totalItems(storage)).coerceAtLeast(0L)
    }

    /** 磁盘内全部物品数量，使用 Long 防止高容量磁盘计数溢出。 */
    fun totalItems(storage: VirtualStorage): Long = storage.entries().sumOf { it.second }

    /** 解析磁盘轻量 UUID；格式损坏时返回 null，不把错误值带入 SQL 查询。 */
    fun idOf(item: ItemStack): UUID? {
        val raw = item.itemMeta?.persistentDataContainer?.get(ID_KEY, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

}
