package top.maplex.slimeEasy.villager.catcher

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.registry.VillagerItems
import top.maplex.slimeEasy.villager.core.VillagerCodec
import top.maplex.slimeEasy.villager.core.VillagerData

/**
 * 村民捕捉器物品的行为辅助 (空 / 满共用一个 Slimefun ID, 靠 PDC 区分)。
 *
 * 满捕捉器在物品 PDC ([KEY]) 中以 [VillagerCodec] 串保存被捕村民的全部快照; 空捕捉器无此键,
 * 同时作为交易器 / 刷铁机 / 小学的合成材料与运行期"村民载体"。
 */
object VillagerCatcher {

    /** 捕捉器 PDC 中保存村民快照串的键。 */
    val KEY: NamespacedKey = NamespacedKey(SlimeEasy.instance, "captured_villager")

    /** 该物品是否为村民捕捉器 (按 Slimefun ID)。 */
    fun isCatcher(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        return SlimefunItem.getByItem(item)?.id == VillagerItems.VILLAGER_CATCHER_ID
    }

    /** 满捕捉器中保存的村民快照; 空捕捉器 / 非捕捉器返回 null。 */
    fun dataOf(item: ItemStack?): VillagerData? {
        if (!isCatcher(item)) return null
        val raw = item!!.itemMeta?.persistentDataContainer?.get(KEY, PersistentDataType.STRING) ?: return null
        return VillagerCodec.decode(raw)
    }

    /** 是否为已装村民的满捕捉器。 */
    fun isFilled(item: ItemStack?): Boolean = dataOf(item) != null

    /** 一个空捕捉器物品 (合成模板克隆); 未注册时返回 null。 */
    fun emptyItem(): ItemStack? = SlimefunItem.getById(VillagerItems.VILLAGER_CATCHER_ID)?.item?.clone()

    /**
     * 由空捕捉器模板生成一个数量为 1 的满捕捉器: 写入村民快照, 并把名称 / lore 更新为被捕村民信息。
     */
    fun fill(data: VillagerData): ItemStack {
        val item = (emptyItem() ?: ItemStack(org.bukkit.Material.GLASS_BOTTLE)).apply { amount = 1 }
        item.editMeta { meta ->
            meta.persistentDataContainer.set(KEY, PersistentDataType.STRING, VillagerCodec.encode(data))
            meta.displayName(
                Component.text(I18n.text("messages.villager-catcher-001", "value0" to (data.professionLabel))).color(NamedTextColor.GREEN)
            )
            meta.lore(
                listOf(
                    Component.text(I18n.text("messages.villager-catcher-002", "value0" to (data.professionLabel))),
                    Component.text(I18n.text("messages.villager-catcher-003", "value0" to (data.level), "value1" to (data.recipes.size))),
                    Component.text(I18n.text("messages.villager-catcher-004"))
                )
            )
        }
        return item
    }

    /**
     * 从玩家主手消耗 1 个当前物品并给予 [give] (背包满则地面掉落)。
     *
     * 用于"空捕捉器 -1、满捕捉器 +1"(捕捉) 与"满捕捉器 -1、空捕捉器 +1"(释放) 两种置换。
     */
    fun replaceOneInHand(player: Player, give: ItemStack) {
        val hand = player.inventory.itemInMainHand
        if (hand.amount <= 1) {
            player.inventory.setItemInMainHand(give)
        } else {
            hand.amount -= 1
            player.inventory.setItemInMainHand(hand)
            player.inventory.addItem(give).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }
    }
}
