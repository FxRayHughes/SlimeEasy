package top.maplex.slimeEasy.machine.quarry

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * 采石场产物类型。
 *
 * [COBBLESTONE] 是无产物升级时的默认值；其余类型由对应 Slimefun 升级组件启用。
 */
enum class QuarryOutput(
    val itemId: String?,
    val material: Material,
    val displayName: String
) {
    COBBLESTONE(null, Material.COBBLESTONE, "圆石"),
    NETHERRACK("SE_QUARRY_NETHERRACK_UPGRADE", Material.NETHERRACK, "地狱岩"),
    END_STONE("SE_QUARRY_END_STONE_UPGRADE", Material.END_STONE, "末地石");

    companion object {
        /** 按 Slimefun 物品身份解析产物升级；空槽或其它物品返回默认圆石。 */
        fun fromItem(item: ItemStack?): QuarryOutput {
            if (item == null || item.type.isAir) return COBBLESTONE
            val id = SlimefunItem.getByItem(item)?.id ?: return COBBLESTONE
            return entries.firstOrNull { it.itemId == id } ?: COBBLESTONE
        }

        /** 是否为可放入采石场产物升级槽的组件。 */
        fun isUpgrade(item: ItemStack?): Boolean = fromItem(item) != COBBLESTONE
    }
}
