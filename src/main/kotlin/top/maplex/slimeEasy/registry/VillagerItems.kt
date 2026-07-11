package top.maplex.slimeEasy.registry

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import top.maplex.slimeEasy.config.SEText

/**
 * 简易村民功能的物品模板与配方定义中心。
 *
 * 仅存放 [SlimefunItemStack] 模板与合成配方, 不涉及行为逻辑。空的[村民捕捉器][VILLAGER_CATCHER]
 * 作为交易器 / 刷铁机 / 小学的合成核心 (Slimefun 按 ID 匹配材料)。全部使用增强工作台配方。
 */
object VillagerItems {

    // ============================ 材料 ============================

    /** 僵尸信号 ID。 */
    const val ZOMBIE_SIGNAL_ID = "SE_ZOMBIE_SIGNAL"

    /** 僵尸信号: 腐肉 + 铁制成的诱导信号, 刷铁机的必备催化剂。 */
    val ZOMBIE_SIGNAL: SlimefunItemStack = SEText.stack(
        ZOMBIE_SIGNAL_ID,
        Material.ZOMBIE_HEAD,
        I18n.raw("items.villager-items-001"),
        "",
        I18n.raw("items.villager-items-002"),
        I18n.raw("items.villager-items-003")
    )
    val ZOMBIE_SIGNAL_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.ROTTEN_FLESH), ItemStack(Material.ROTTEN_FLESH), ItemStack(Material.ROTTEN_FLESH),
        ItemStack(Material.ROTTEN_FLESH), ItemStack(Material.IRON_INGOT), ItemStack(Material.ROTTEN_FLESH),
        ItemStack(Material.ROTTEN_FLESH), ItemStack(Material.ROTTEN_FLESH), ItemStack(Material.ROTTEN_FLESH)
    )

    // ============================ 村民捕捉器 ============================

    /** 村民捕捉器 ID (空 / 满共用此 ID, 靠 PDC 区分)。 */
    const val VILLAGER_CATCHER_ID = "SE_VILLAGER_CATCHER"

    /**
     * 村民捕捉器 (空): 右键活体村民即把其全部属性收进物品; 满捕捉器潜行右键放出村民。
     *
     * 空捕捉器同时是村民交易器 / 胶囊刷铁机的合成核心。
     */
    val VILLAGER_CATCHER: SlimefunItemStack = SEText.stack(
        VILLAGER_CATCHER_ID,
        Material.GLASS_BOTTLE,
        I18n.raw("items.villager-items-004"),
        "",
        I18n.raw("items.villager-items-005"),
        I18n.raw("items.villager-items-006"),
        "",
        I18n.raw("items.villager-items-007")
    )
    val VILLAGER_CATCHER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.NAME_TAG), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.GLASS_BOTTLE), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.EMERALD), ItemStack(Material.IRON_INGOT)
    )

    // ============================ 村民交易器 ============================

    const val VILLAGER_TRADER_ID = "SE_VILLAGER_TRADER"
    val VILLAGER_TRADER: SlimefunItemStack = SEText.stack(
        VILLAGER_TRADER_ID,
        Material.GLASS,
        I18n.raw("items.villager-items-008"),
        "",
        I18n.raw("items.villager-items-009"),
        "",
        I18n.raw("items.villager-items-010"),
        I18n.raw("items.villager-items-011"),
        I18n.raw("items.villager-items-012"),
        "",
        I18n.raw("items.villager-items-013"),
        I18n.raw("items.villager-items-014"),
        I18n.raw("items.villager-items-015")
    )
    val VILLAGER_TRADER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.GLASS), ItemStack(Material.EMERALD), ItemStack(Material.GLASS),
        ItemStack(Material.EMERALD), VILLAGER_CATCHER.clone(), ItemStack(Material.EMERALD),
        ItemStack(Material.GLASS), ItemStack(Material.EMERALD_BLOCK), ItemStack(Material.GLASS)
    )

    // ============================ 胶囊刷铁机 ============================

    const val IRON_FARM_ID = "SE_IRON_FARM"
    val IRON_FARM: SlimefunItemStack = SEText.stack(
        IRON_FARM_ID,
        Material.GLASS,
        I18n.raw("items.villager-items-016"),
        "",
        I18n.raw("items.villager-items-017"),
        "",
        I18n.raw("items.villager-items-018"),
        I18n.raw("items.villager-items-019"),
        I18n.raw("items.villager-items-020"),
        I18n.raw("items.villager-items-021")
    )
    val IRON_FARM_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_BLOCK), ZOMBIE_SIGNAL.clone(), ItemStack(Material.IRON_BLOCK),
        ZOMBIE_SIGNAL.clone(), VILLAGER_CATCHER.clone(), ZOMBIE_SIGNAL.clone(),
        ItemStack(Material.IRON_BLOCK), ItemStack(Material.IRON_BLOCK), ItemStack(Material.IRON_BLOCK)
    )

    /** 刷铁机速度升级 ID (可叠放, 数量即级数)。 */
    const val IRON_FARM_SPEED_UPGRADE_ID = "SE_IRON_FARM_SPEED_UPGRADE"
    val IRON_FARM_SPEED_UPGRADE: SlimefunItemStack = SEText.stack(
        IRON_FARM_SPEED_UPGRADE_ID,
        Material.CLOCK,
        I18n.raw("items.villager-items-022"),
        "",
        I18n.raw("items.villager-items-023"),
        I18n.raw("items.villager-items-024")
    )
    val IRON_FARM_SPEED_UPGRADE_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.REDSTONE), ItemStack(Material.CLOCK), ItemStack(Material.REDSTONE),
        ItemStack(Material.SUGAR), ItemStack(Material.GOLD_INGOT), ItemStack(Material.SUGAR),
        ItemStack(Material.REDSTONE), ItemStack(Material.CLOCK), ItemStack(Material.REDSTONE)
    )

    // ============================ 村民小学 ============================

    const val VILLAGER_SCHOOL_ID = "SE_VILLAGER_SCHOOL"
    val VILLAGER_SCHOOL: SlimefunItemStack = SEText.stack(
        VILLAGER_SCHOOL_ID,
        Material.LECTERN,
        I18n.raw("items.villager-items-025"),
        "",
        I18n.raw("items.villager-items-026"),
        I18n.raw("items.villager-items-027"),
        I18n.raw("items.villager-items-028")
    )
    val VILLAGER_SCHOOL_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.BOOK), ItemStack(Material.BOOKSHELF), ItemStack(Material.BOOK),
        ItemStack(Material.BOOKSHELF), VILLAGER_CATCHER.clone(), ItemStack(Material.BOOKSHELF),
        ItemStack(Material.BOOK), ItemStack(Material.LECTERN), ItemStack(Material.BOOK)
    )

    // ============================ 村民治愈机 ============================

    const val VILLAGER_HEALER_ID = "SE_VILLAGER_HEALER"
    val VILLAGER_HEALER: SlimefunItemStack = SEText.stack(
        VILLAGER_HEALER_ID,
        Material.GOLD_BLOCK,
        I18n.raw("items.villager-items-029"),
        "",
        I18n.raw("items.villager-items-030"),
        I18n.raw("items.villager-items-031"),
        I18n.raw("items.villager-items-032")
    )
    val VILLAGER_HEALER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.GOLD_INGOT), ItemStack(Material.GOLDEN_APPLE), ItemStack(Material.GOLD_INGOT),
        ItemStack(Material.GOLD_INGOT), VILLAGER_CATCHER.clone(), ItemStack(Material.GOLD_INGOT),
        ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK)
    )

    // ============================ 遗忘药剂 ============================

    const val FORGETTING_POTION_ID = "SE_FORGETTING_POTION"
    val FORGETTING_POTION: SlimefunItemStack = SEText.stack(
        FORGETTING_POTION_ID,
        Material.POTION,
        I18n.raw("items.villager-items-033"),
        "",
        I18n.raw("items.villager-items-034"),
        I18n.raw("items.villager-items-035"),
        I18n.raw("items.villager-items-036")
    )
    val FORGETTING_POTION_RECIPE: Array<ItemStack?> = arrayOf(
        null, ItemStack(Material.GHAST_TEAR), null,
        ItemStack(Material.FERMENTED_SPIDER_EYE), waterBottle(), ItemStack(Material.FERMENTED_SPIDER_EYE),
        null, ItemStack(Material.GLOWSTONE_DUST), null
    )

    /** 构造一个水瓶 (Water Bottle) 作配方材料: 玩家用玻璃瓶对水右键即可获得, 无需酿造裸药水。 */
    private fun waterBottle(): ItemStack = ItemStack(Material.POTION).apply {
        editMeta(PotionMeta::class.java) { it.basePotionType = PotionType.WATER }
    }
}
