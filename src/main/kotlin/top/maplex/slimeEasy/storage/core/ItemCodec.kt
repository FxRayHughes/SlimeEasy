package top.maplex.slimeEasy.storage.core

import org.bukkit.inventory.ItemStack
import java.util.Base64

/**
 * 物品与字符串的编解码器。
 *
 * 用于把 [ItemStack] 持久化进 Slimefun 的 BlockData (只接受 String 值)。
 * 底层采用 Paper 原生的 [ItemStack.serializeAsBytes] —— 它输出的是与
 * Minecraft 数据版本绑定的 NBT 字节, 跨版本升级由 Paper 自动迁移, 比
 * Bukkit 的 YAML Map 序列化更紧凑且稳定。
 */
object ItemCodec {

    /** 把物品编码为 Base64 字符串。 */
    fun encode(item: ItemStack): String =
        Base64.getEncoder().encodeToString(item.serializeAsBytes())

    /**
     * 把 Base64 字符串解码回物品; 解码失败 (数据损坏/格式不符) 返回 null。
     *
     * 失败静默返回 null 而非抛出: BlockData 可能因手改或跨大版本而失效,
     * 调用方应把 null 视为"该槽无有效物品"处理, 避免整块存储加载崩溃。
     */
    fun decode(data: String?): ItemStack? {
        if (data.isNullOrEmpty()) return null
        return try {
            ItemStack.deserializeBytes(Base64.getDecoder().decode(data))
        } catch (e: Exception) {
            null
        }
    }
}
