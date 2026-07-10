package top.maplex.slimeEasy.villager.potion

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.registry.VillagerItems

/**
 * 遗忘药剂物品的识别辅助。
 *
 * 药剂本身为普通 Slimefun 物品 (无行为), 右键村民的效果由 [ForgettingPotionListener] 承接。
 */
object ForgettingPotion {

    /** 该物品是否为遗忘药剂 (按 Slimefun ID)。 */
    fun isPotion(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        return SlimefunItem.getByItem(item)?.id == VillagerItems.FORGETTING_POTION_ID
    }
}
