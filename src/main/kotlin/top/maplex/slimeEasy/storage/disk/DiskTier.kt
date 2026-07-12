package top.maplex.slimeEasy.storage.disk

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.registry.StorageItems

/**
 * 物品存储磁盘规格。
 *
 * [kib] 同时参与总字节数与每种物品索引开销计算，因此该枚举属于持久化容量协议，
 * 已发布规格不得改名或改变数值。
 */
enum class DiskTier(val itemId: String, val kib: Int) {
    K1(StorageItems.DISK_1K_ID, 1),
    K4(StorageItems.DISK_4K_ID, 4),
    K16(StorageItems.DISK_16K_ID, 16),
    K64(StorageItems.DISK_64K_ID, 64),
    K128(StorageItems.DISK_128K_ID, 128),
    K256(StorageItems.DISK_256K_ID, 256);

    /** 磁盘可用的总字节数。 */
    val capacityBytes: Long get() = kib * 1024L

    /** 每种物品身份占用的索引字节数。 */
    val bytesPerType: Long get() = kib * 8L

    companion object {
        /** 按 Slimefun ID 识别磁盘，避免名称或 Lore 被修改后误判。 */
        fun of(item: ItemStack?): DiskTier? {
            if (item == null || item.type.isAir) return null
            val id = SlimefunItem.getByItem(item)?.id ?: return null
            return entries.firstOrNull { it.itemId == id }
        }
    }
}
