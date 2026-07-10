package top.maplex.slimeEasy.registry

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
        "&2僵尸信号",
        "",
        "&7以腐肉与铁锭合成的僵尸气息信号。",
        "&7胶囊刷铁机的必备催化剂 (装入即可, 不消耗)。"
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
        "&a村民捕捉器",
        "",
        "&7右键活体村民: &f收入此瓶 &7(保留其全部属性)。",
        "&7潜行 + 右键: &f放出 &7瓶中村民。",
        "",
        "&7空瓶亦可作交易器 / 刷铁机的合成材料。"
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
        "&b村民交易器",
        "",
        "&7透明容器, 内嵌一只小村民展示。",
        "",
        "&7空块右键: 放入 &f村民 (满捕捉器) &7或 &f工作站方块&7;",
        "&7已装村民时右键: &f直接打开交易界面&7;",
        "&7潜行 + 右键: &f取出 &7村民或工作站方块。",
        "",
        "&7装配的村民与工作站方块匹配时, 按配置间隔 &f自动补货&7,",
        "&7补货不受时间 / 范围等外部因素影响。",
        "&7玩家与其交易, 而无需操心保护村民。"
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
        "&f胶囊刷铁机",
        "",
        "&7透明容器, 内嵌村民与僵尸展示; 产铁瞬间闪现铁傀儡。",
        "",
        "&7右键打开界面, 放入:",
        "&7· &f村民 (满捕捉器)&7 · &f僵尸信号&7 · &f食物&7 · &f速度升级&7;",
        "&7三者齐全 (村民 + 僵尸信号 + 食物) 时按周期产出铁锭,",
        "&7每周期消耗少量食物; 速度升级可缩短周期。"
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
        "&e刷铁机 · 速度升级",
        "",
        "&7放入胶囊刷铁机的速度升级槽。",
        "&7堆叠数量 = 级数; 级数越高产铁周期越短。"
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
        "&6村民小学",
        "",
        "&7右键打开界面, 放入 &f傻子村民 (满捕捉器)&7,",
        "&7按配置时间后自动转化为 &f无职业普通村民&7 (可再就业),",
        "&7转化结果落入输出槽。"
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
        "&6村民治愈机",
        "",
        "&7右键打开界面, 放入 &f僵尸村民 (满捕捉器)&7 与 &f普通金苹果&7,",
        "&7按配置时间后把僵尸村民 &f治愈为普通村民&7 (保留职业),",
        "&7每次消耗一个普通金苹果; 结果落入输出槽。"
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
        "&d遗忘药剂",
        "",
        "&7右键一只活体村民: 使其 &f忘却职业&7,",
        "&7变回无职业普通村民 (可重新就业)。",
        "&7每次消耗一瓶。"
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
