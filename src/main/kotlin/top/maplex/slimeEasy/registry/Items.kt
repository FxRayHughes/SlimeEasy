package top.maplex.slimeEasy.registry

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.config.SEText

/**
 * 物品堆栈与配方定义中心。
 *
 * 仅存放 [SlimefunItemStack] 模板与合成配方, 不涉及行为逻辑。
 * 物品名称 / Lore 经 [I18n] 从独立语言文件读取，修改后需重启生效。
 */
object Items {

    // ============================ 筛子与筛分原料 ============================

    const val SIEVE_ID = "SE_SIEVE"
    const val REINFORCED_SIEVE_ID = "SE_REINFORCED_SIEVE"

    /** 普通筛子指南图标；世界结构为木质活板门与其下方朝上的发射器。 */
    val SIEVE: SlimefunItemStack =
        SEText.localized(SIEVE_ID, Material.OAK_TRAPDOOR, "items.sieve")

    /** 强化筛子指南图标；独立结构在木质活板门与发射器之间增加脚手架。 */
    val REINFORCED_SIEVE: SlimefunItemStack =
        SEText.localized(REINFORCED_SIEVE_ID, Material.SCAFFOLDING, "items.reinforced-sieve")

    /** 四种需要先通过磨石制得的筛分原料 ID。 */
    const val SIEVE_DUST_ID = "SE_SIEVE_DUST"
    const val CRUSHED_NETHERRACK_ID = "SE_CRUSHED_NETHERRACK"
    const val CRUSHED_END_STONE_ID = "SE_CRUSHED_END_STONE"
    const val CRUSHED_BLACKSTONE_ID = "SE_CRUSHED_BLACKSTONE"

    /** 磨石产物模板；筛子据 Slimefun 物品身份区分它们与同材质的原版物品。 */
    val SIEVE_DUST: SlimefunItemStack =
        SEText.localized(SIEVE_DUST_ID, Material.SUGAR, "items.sieve-materials.dust")
    val CRUSHED_NETHERRACK: SlimefunItemStack =
        SEText.localized(CRUSHED_NETHERRACK_ID, Material.NETHERRACK, "items.sieve-materials.crushed-netherrack")
    val CRUSHED_END_STONE: SlimefunItemStack =
        SEText.localized(CRUSHED_END_STONE_ID, Material.END_STONE, "items.sieve-materials.crushed-end-stone")
    val CRUSHED_BLACKSTONE: SlimefunItemStack =
        SEText.localized(CRUSHED_BLACKSTONE_ID, Material.BLACKSTONE, "items.sieve-materials.crushed-blackstone")

    /**
     * 注册到原版 Slimefun 磨石的一格输入配方。
     *
     * 普通黑石已被 Slimefun 占用为“黑石 → 沙砾”，因此粉碎黑石使用磨制黑石作为不冲突的输入。
     */
    val SIEVE_DUST_RECIPE: Array<ItemStack?> = arrayOf(ItemStack(Material.SAND))
    val CRUSHED_NETHERRACK_RECIPE: Array<ItemStack?> = arrayOf(ItemStack(Material.NETHERRACK))
    val CRUSHED_END_STONE_RECIPE: Array<ItemStack?> = arrayOf(ItemStack(Material.END_STONE))
    val CRUSHED_BLACKSTONE_RECIPE: Array<ItemStack?> = arrayOf(ItemStack(Material.POLISHED_BLACKSTONE))

    /** 自动破坏机的全局唯一 ID。 */
    const val AUTO_BREAKER_ID = "SE_AUTO_BREAKER"

    /**
     * 自动破坏机物品模板。
     *
     * 使用涂蜡铜箱子作为机器本体: 放置后即为可交互的容器, 破坏产物直接存入其中。
     */
    val AUTO_BREAKER: SlimefunItemStack =
        SEText.localized(AUTO_BREAKER_ID, Material.WAXED_COPPER_CHEST, "items.auto-breaker")

    /**
     * 增强工作台配方 (3x3)。
     *
     * 以涂蜡铜箱子为核心, 四周活塞环绕, 象征"箱子 + 活塞"的组合主题。
     */
    val AUTO_BREAKER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.PISTON), ItemStack(Material.PISTON), ItemStack(Material.PISTON),
        ItemStack(Material.PISTON), ItemStack(Material.WAXED_COPPER_CHEST), ItemStack(Material.PISTON),
        ItemStack(Material.REDSTONE_BLOCK), ItemStack(Material.IRON_BLOCK), ItemStack(Material.REDSTONE_BLOCK)
    )

    /** 自动放置机的全局唯一 ID。 */
    const val AUTO_PLACER_ID = "SE_AUTO_PLACER"

    /**
     * 自动放置机物品模板。
     *
     * 使用涂蜡铜箱子作为机器本体, 与破坏机一致; 依靠物品名称与配方 (粘性活塞) 区分。
     */
    val AUTO_PLACER: SlimefunItemStack =
        SEText.localized(AUTO_PLACER_ID, Material.WAXED_COPPER_CHEST, "items.auto-placer")

    /**
     * 增强工作台配方 (3x3)。
     *
     * 以涂蜡铜箱子为核心, 四周 &f粘性活塞 环绕, 呼应放置机"推出方块"的主题。
     */
    val AUTO_PLACER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.STICKY_PISTON), ItemStack(Material.STICKY_PISTON), ItemStack(Material.STICKY_PISTON),
        ItemStack(Material.STICKY_PISTON), ItemStack(Material.WAXED_COPPER_CHEST), ItemStack(Material.STICKY_PISTON),
        ItemStack(Material.REDSTONE_BLOCK), ItemStack(Material.IRON_BLOCK), ItemStack(Material.REDSTONE_BLOCK)
    )

    /** 苦力怕驱逐方块的全局唯一 ID。 */
    const val CREEPER_WARD_ID = "SE_CREEPER_WARD"

    /**
     * 苦力怕驱逐方块物品模板。
     *
     * 使用绿色地毯作为外观: 平铺于地面, 象征一片"苦力怕的禁区"。
     */
    val CREEPER_WARD: SlimefunItemStack =
        SEText.localized(CREEPER_WARD_ID, Material.GREEN_CARPET, "items.creeper-ward")

    /**
     * 增强工作台配方 (3x3)。
     *
     * 铁剑镇于中央 (驱逐之力), 仙人掌环绕 (带刺排斥), 呼应"驱赶苦力怕"的主题。
     */
    val CREEPER_WARD_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.CACTUS), ItemStack(Material.CACTUS), ItemStack(Material.CACTUS),
        ItemStack(Material.CACTUS), ItemStack(Material.IRON_SWORD), ItemStack(Material.CACTUS),
        ItemStack(Material.CACTUS), ItemStack(Material.CACTUS), ItemStack(Material.CACTUS)
    )

    /** 矿物勘察尺 (普通) 的全局唯一 ID。 */
    const val SURVEY_RULER_ID = "SE_SURVEY_RULER"

    /**
     * 矿物勘察尺物品模板 (铜锄外观)。
     *
     * 右键地面不锄地, 而是按 &f工业矿机 &7采掘范围 (7×7) 向下扫描,
     * 在聊天栏列出可开采矿石及数量。
     */
    val SURVEY_RULER: SlimefunItemStack =
        SEText.localized(SURVEY_RULER_ID, Material.COPPER_HOE, "items.survey-ruler")

    /**
     * 增强工作台配方 (3x3): 铜锄 + 指南针 + 钻石。
     */
    val SURVEY_RULER_RECIPE: Array<ItemStack?> = arrayOf(
        null, ItemStack(Material.COMPASS), null,
        null, ItemStack(Material.COPPER_HOE), null,
        null, ItemStack(Material.DIAMOND), null
    )

    /** 进阶矿物勘察尺的全局唯一 ID。 */
    const val ADVANCED_SURVEY_RULER_ID = "SE_ADVANCED_SURVEY_RULER"

    /**
     * 进阶矿物勘察尺物品模板 (钻石锄外观)。
     *
     * 支持 &f进阶工业矿机 &7(11×11) 与 &f工业矿机 &7(7×7) 两种勘察范围,
     * 潜行右键空气切换当前范围, 右键地面按当前范围向下勘探。
     */
    val ADVANCED_SURVEY_RULER: SlimefunItemStack =
        SEText.localized(ADVANCED_SURVEY_RULER_ID, Material.DIAMOND_HOE, "items.advanced-survey-ruler")

    /**
     * 增强工作台配方 (3x3): 钻石锄 + 指南针 + 钻石块 (较普通版更高阶)。
     */
    val ADVANCED_SURVEY_RULER_RECIPE: Array<ItemStack?> = arrayOf(
        null, ItemStack(Material.COMPASS), null,
        null, ItemStack(Material.DIAMOND_HOE), null,
        null, ItemStack(Material.DIAMOND_BLOCK), null
    )

    /** 工程师护目镜的稳定 Slimefun ID；穿戴检测必须按此身份判断而非显示名。 */
    const val ENGINEER_GOGGLES_ID = "SE_ENGINEER_GOGGLES"

    /** 工程师护目镜使用铁头盔槽位，佩戴后启用仅自己可见的机器诊断全息图。 */
    val ENGINEER_GOGGLES: SlimefunItemStack =
        SEText.localized(
            ENGINEER_GOGGLES_ID,
            Material.IRON_HELMET,
            "items.engineer-goggles",
            "radius" to SEConfig.engineerGogglesRadius
        )

    /**
     * 增强工作台配方：玻璃与红石构成观察组件，指南针负责定位附近机器。
     * 中央必须是原版铁头盔，避免护目镜递归参与自身配方。
     */
    val ENGINEER_GOGGLES_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.GLASS_PANE), ItemStack(Material.REDSTONE), ItemStack(Material.GLASS_PANE),
        ItemStack(Material.REDSTONE), ItemStack(Material.IRON_HELMET), ItemStack(Material.REDSTONE),
        null, ItemStack(Material.COMPASS), null
    )

    /** 组合护目镜的稳定 ID；所有工程师扫描与筛选入口必须同时接受此身份。 */
    const val ENGINEER_NIGHT_VISION_GOGGLES_ID = "SE_ENGINEER_NIGHT_VISION_GOGGLES"

    /** 工程师夜视护目镜沿用铁头盔外观，同时由 SlimefunArmorPiece 提供原生夜视效果。 */
    val ENGINEER_NIGHT_VISION_GOGGLES: SlimefunItemStack =
        SEText.localized(
            ENGINEER_NIGHT_VISION_GOGGLES_ID,
            Material.IRON_HELMET,
            "items.engineer-night-vision-goggles",
            "radius" to SEConfig.engineerGogglesRadius
        )

    /**
     * 增强工作台纵向合并配方；两个输入都使用 Slimefun 物品模板，普通皮革帽或铁头盔不能代替。
     */
    val ENGINEER_NIGHT_VISION_GOGGLES_RECIPE: Array<ItemStack?> = arrayOf(
        null, SlimefunItems.NIGHT_VISION_GOGGLES, null,
        null, ENGINEER_GOGGLES, null,
        null, null, null
    )

    /** 判断某 Slimefun ID 是否具备完整工程师护目镜能力，避免各入口的组合型号名单漂移。 */
    fun isEngineerGogglesId(id: String?): Boolean =
        id == ENGINEER_GOGGLES_ID || id == ENGINEER_NIGHT_VISION_GOGGLES_ID

    /** 屠夫机器的全局唯一 ID。 */
    const val BUTCHER_ID = "SE_BUTCHER"

    /**
     * 屠夫机器物品模板 (观察者外观)。
     *
     * 观察者脸朝向 = 攻击方向; 右键开界面放武器 / 附魔书 / 食物。
     */
    val BUTCHER: SlimefunItemStack =
        SEText.localized(BUTCHER_ID, Material.OBSERVER, "items.butcher")

    /**
     * 增强工作台配方 (3x3)。
     *
     * 观察者居中, 铁剑镇顶 (屠戮), 下界之星与红石块供能, 呼应"自动作战装置"。
     */
    val BUTCHER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_SWORD), ItemStack(Material.DIAMOND_SWORD), ItemStack(Material.IRON_SWORD),
        ItemStack(Material.REDSTONE_BLOCK), ItemStack(Material.OBSERVER), ItemStack(Material.REDSTONE_BLOCK),
        ItemStack(Material.IRON_BLOCK), ItemStack(Material.NETHER_STAR), ItemStack(Material.IRON_BLOCK)
    )

    /** 屠夫机器 · 范围升级组件 ID (可叠放, 数量即级数)。 */
    const val BUTCHER_RANGE_UPGRADE_ID = "SE_BUTCHER_RANGE_UPGRADE"

    /** 范围升级组件: 每级攻击截面 +2 格、纵深 +1 格。 */
    val BUTCHER_RANGE_UPGRADE: SlimefunItemStack =
        SEText.localized(BUTCHER_RANGE_UPGRADE_ID, Material.ENDER_EYE, "items.butcher-range-upgrade")

    /** 范围升级配方: 末影之眼 + 箭矢环绕 (象征"射程延伸")。 */
    val BUTCHER_RANGE_UPGRADE_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.ARROW), ItemStack(Material.ARROW), ItemStack(Material.ARROW),
        ItemStack(Material.ARROW), ItemStack(Material.ENDER_EYE), ItemStack(Material.ARROW),
        ItemStack(Material.ARROW), ItemStack(Material.ARROW), ItemStack(Material.ARROW)
    )

    /** 屠夫机器 · 伤害升级组件 ID (可叠放, 数量即级数)。 */
    const val BUTCHER_DAMAGE_UPGRADE_ID = "SE_BUTCHER_DAMAGE_UPGRADE"

    /** 伤害升级组件: 每级伤害线性 +50% 基础值。 */
    val BUTCHER_DAMAGE_UPGRADE: SlimefunItemStack =
        SEText.localized(BUTCHER_DAMAGE_UPGRADE_ID, Material.BLAZE_POWDER, "items.butcher-damage-upgrade")

    /** 伤害升级配方: 烈焰粉 + 钻石剑供伤 (象征"锋锐强化")。 */
    val BUTCHER_DAMAGE_UPGRADE_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.BLAZE_POWDER), ItemStack(Material.DIAMOND_SWORD), ItemStack(Material.BLAZE_POWDER),
        ItemStack(Material.BLAZE_POWDER), ItemStack(Material.DIAMOND), ItemStack(Material.BLAZE_POWDER),
        ItemStack(Material.BLAZE_POWDER), ItemStack(Material.BLAZE_POWDER), ItemStack(Material.BLAZE_POWDER)
    )

    /** 自动点击器的全局唯一 ID。 */
    const val AUTO_CLICKER_ID = "SE_AUTO_CLICKER"

    /**
     * 自动点击器物品模板 (观察者外观)。
     *
     * 脸朝向 = 点击方向; 需红石激活, 激活后不断左键 / 右键正前方方块 (含 Slimefun 方块)。
     * 内置一格容积, 点击时假玩家手持该物品交互, 可用漏斗补料。可装抽取升级从箱子等容器补料。
     */
    val AUTO_CLICKER: SlimefunItemStack =
        SEText.localized(AUTO_CLICKER_ID, Material.OBSERVER, "items.auto-clicker")

    /**
     * 增强工作台配方 (3x3)。
     *
     * 观察者居中, 红石供能, 金料环绕 (自动点击装置)。
     */
    val AUTO_CLICKER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.GOLD_INGOT), ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.OBSERVER), ItemStack(Material.REDSTONE),
        ItemStack(Material.GOLD_INGOT), ItemStack(Material.REDSTONE_BLOCK), ItemStack(Material.GOLD_INGOT)
    )

    /** 生长抑制器的全局唯一 ID。 */
    const val GROWTH_INHIBITOR_ID = "SE_GROWTH_INHIBITOR"

    /**
     * 生长抑制器物品模板 (史莱姆球外观)。
     *
     * 手持右键幼年生物 → 锁定其年龄, 永久保持幼小; 再次右键解除。
     */
    val GROWTH_INHIBITOR: SlimefunItemStack =
        SEText.localized(GROWTH_INHIBITOR_ID, Material.SLIME_BALL, "items.growth-inhibitor")

    /**
     * 增强工作台配方 (3x3): 蜂巢封存 + 史莱姆 (幼小) + 命名牌 (定格实体)。
     */
    val GROWTH_INHIBITOR_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.SLIME_BALL), ItemStack(Material.HONEYCOMB), ItemStack(Material.SLIME_BALL),
        ItemStack(Material.HONEYCOMB), ItemStack(Material.NAME_TAG), ItemStack(Material.HONEYCOMB),
        ItemStack(Material.SLIME_BALL), ItemStack(Material.HONEYCOMB), ItemStack(Material.SLIME_BALL)
    )

    // ============================ 战斗挽具 (给快乐恶魂/乐魂佩戴) ============================
    // 基于原版 *_HARNESS 物品: 玩家右键乐魂即由原版装备, PDC 保留; 佩戴后由 HarnessCombat 驱动激光作战。

    const val COMBAT_HARNESS_I_ID = "SE_COMBAT_HARNESS_I"
    const val COMBAT_HARNESS_II_ID = "SE_COMBAT_HARNESS_II"
    const val COMBAT_HARNESS_III_ID = "SE_COMBAT_HARNESS_III"
    const val COMBAT_HARNESS_IV_ID = "SE_COMBAT_HARNESS_IV"

    /** 构造某档战斗挽具模板 (统一说明佩戴与作战)。 */
    private fun combatHarness(id: String, material: Material, key: String, damage: Int): SlimefunItemStack =
        SEText.localized(id, material, key, "damage" to damage)

    val COMBAT_HARNESS_I: SlimefunItemStack = combatHarness(COMBAT_HARNESS_I_ID, Material.WHITE_HARNESS, "items.combat-harness.tier-1", 5)
    val COMBAT_HARNESS_II: SlimefunItemStack = combatHarness(COMBAT_HARNESS_II_ID, Material.LIME_HARNESS, "items.combat-harness.tier-2", 10)
    val COMBAT_HARNESS_III: SlimefunItemStack = combatHarness(COMBAT_HARNESS_III_ID, Material.ORANGE_HARNESS, "items.combat-harness.tier-3", 20)
    val COMBAT_HARNESS_IV: SlimefunItemStack = combatHarness(COMBAT_HARNESS_IV_ID, Material.RED_HARNESS, "items.combat-harness.tier-4", 25)

    /** I: 海晶碎片环绕白色挽具 (守护者激光之源)。 */
    val COMBAT_HARNESS_I_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.PRISMARINE_SHARD), ItemStack(Material.PRISMARINE_SHARD), ItemStack(Material.PRISMARINE_SHARD),
        ItemStack(Material.PRISMARINE_SHARD), ItemStack(Material.WHITE_HARNESS), ItemStack(Material.PRISMARINE_SHARD),
        ItemStack(Material.PRISMARINE_SHARD), ItemStack(Material.PRISMARINE_SHARD), ItemStack(Material.PRISMARINE_SHARD)
    )

    /** II: 海晶砂粒环绕上一档。 */
    val COMBAT_HARNESS_II_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.PRISMARINE_CRYSTALS), ItemStack(Material.PRISMARINE_CRYSTALS), ItemStack(Material.PRISMARINE_CRYSTALS),
        ItemStack(Material.PRISMARINE_CRYSTALS), COMBAT_HARNESS_I.clone(), ItemStack(Material.PRISMARINE_CRYSTALS),
        ItemStack(Material.PRISMARINE_CRYSTALS), ItemStack(Material.PRISMARINE_CRYSTALS), ItemStack(Material.PRISMARINE_CRYSTALS)
    )

    /** III: 海晶灯环绕上一档。 */
    val COMBAT_HARNESS_III_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.SEA_LANTERN), ItemStack(Material.SEA_LANTERN), ItemStack(Material.SEA_LANTERN),
        ItemStack(Material.SEA_LANTERN), COMBAT_HARNESS_II.clone(), ItemStack(Material.SEA_LANTERN),
        ItemStack(Material.SEA_LANTERN), ItemStack(Material.SEA_LANTERN), ItemStack(Material.SEA_LANTERN)
    )

    /** IV: 下界之星镇顶 + 海晶砂粒环绕上一档。 */
    val COMBAT_HARNESS_IV_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.PRISMARINE_CRYSTALS), ItemStack(Material.NETHER_STAR), ItemStack(Material.PRISMARINE_CRYSTALS),
        ItemStack(Material.PRISMARINE_CRYSTALS), COMBAT_HARNESS_III.clone(), ItemStack(Material.PRISMARINE_CRYSTALS),
        ItemStack(Material.PRISMARINE_CRYSTALS), ItemStack(Material.PRISMARINE_CRYSTALS), ItemStack(Material.PRISMARINE_CRYSTALS)
    )

    // ============================ 采石场 (观察者方块, 圆石 + 岩浆 + 水 → 产圆石) ============================

    /** 采石场的全局唯一 ID。 */
    const val QUARRY_ID = "SE_QUARRY"

    /**
     * 采石场物品模板 (观察者外观)。
     *
     * 脸朝向的圆石同时相邻岩浆与水时产出圆石; 无容积, 产物输出到周围容器。
     */
    val QUARRY: SlimefunItemStack =
        SEText.localized(QUARRY_ID, Material.OBSERVER, "items.quarry")

    /**
     * 增强工作台配方 (3x3)。
     *
     * 观察者居中 (侦测), 圆石四角 (采石主题), 水桶 / 岩浆桶点睛 (生产条件),
     * 活塞与红石块供能。
     */
    val QUARRY_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.COBBLESTONE), ItemStack(Material.WATER_BUCKET), ItemStack(Material.COBBLESTONE),
        ItemStack(Material.LAVA_BUCKET), ItemStack(Material.OBSERVER), ItemStack(Material.PISTON),
        ItemStack(Material.COBBLESTONE), ItemStack(Material.REDSTONE_BLOCK), ItemStack(Material.COBBLESTONE)
    )

    // 五档效率升级组件: 档位仅由物品身份决定 (与 [top.maplex.slimeEasy.machine.quarry.QuarryTier] 一致), 与堆叠数量无关。

    const val QUARRY_EFFICIENCY_I_ID = "SE_QUARRY_EFFICIENCY_I"
    const val QUARRY_EFFICIENCY_II_ID = "SE_QUARRY_EFFICIENCY_II"
    const val QUARRY_EFFICIENCY_III_ID = "SE_QUARRY_EFFICIENCY_III"
    const val QUARRY_EFFICIENCY_IV_ID = "SE_QUARRY_EFFICIENCY_IV"
    const val QUARRY_EFFICIENCY_V_ID = "SE_QUARRY_EFFICIENCY_V"

    /** 构造某档采石场效率升级模板 (统一说明放入槽位与速率)。 */
    private fun quarryEfficiency(id: String, material: Material, key: String): SlimefunItemStack =
        SEText.localized(id, material, key)

    val QUARRY_EFFICIENCY_I: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_I_ID, Material.COPPER_INGOT, "items.quarry-efficiency.tier-1")
    val QUARRY_EFFICIENCY_II: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_II_ID, Material.IRON_INGOT, "items.quarry-efficiency.tier-2")
    val QUARRY_EFFICIENCY_III: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_III_ID, Material.GOLD_INGOT, "items.quarry-efficiency.tier-3")
    val QUARRY_EFFICIENCY_IV: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_IV_ID, Material.DIAMOND, "items.quarry-efficiency.tier-4")
    val QUARRY_EFFICIENCY_V: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_V_ID, Material.NETHERITE_INGOT, "items.quarry-efficiency.tier-5")

    /** I: 活塞 (效率) 与红石环绕铜锭 (基础档)。 */
    val QUARRY_EFFICIENCY_I_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.REDSTONE), ItemStack(Material.PISTON), ItemStack(Material.REDSTONE),
        ItemStack(Material.PISTON), ItemStack(Material.COPPER_INGOT), ItemStack(Material.PISTON),
        ItemStack(Material.REDSTONE), ItemStack(Material.PISTON), ItemStack(Material.REDSTONE)
    )

    /** II: 铁锭环绕上一档。 */
    val QUARRY_EFFICIENCY_II_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.IRON_INGOT), QUARRY_EFFICIENCY_I.clone(), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_INGOT)
    )

    /** III: 金锭环绕上一档。 */
    val QUARRY_EFFICIENCY_III_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.GOLD_INGOT), ItemStack(Material.GOLD_INGOT), ItemStack(Material.GOLD_INGOT),
        ItemStack(Material.GOLD_INGOT), QUARRY_EFFICIENCY_II.clone(), ItemStack(Material.GOLD_INGOT),
        ItemStack(Material.GOLD_INGOT), ItemStack(Material.GOLD_INGOT), ItemStack(Material.GOLD_INGOT)
    )

    /** IV: 钻石环绕上一档。 */
    val QUARRY_EFFICIENCY_IV_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.DIAMOND), ItemStack(Material.DIAMOND), ItemStack(Material.DIAMOND),
        ItemStack(Material.DIAMOND), QUARRY_EFFICIENCY_III.clone(), ItemStack(Material.DIAMOND),
        ItemStack(Material.DIAMOND), ItemStack(Material.DIAMOND), ItemStack(Material.DIAMOND)
    )

    /** V: 下界合金锭环绕上一档。 */
    val QUARRY_EFFICIENCY_V_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.NETHERITE_INGOT), ItemStack(Material.NETHERITE_INGOT), ItemStack(Material.NETHERITE_INGOT),
        ItemStack(Material.NETHERITE_INGOT), QUARRY_EFFICIENCY_IV.clone(), ItemStack(Material.NETHERITE_INGOT),
        ItemStack(Material.NETHERITE_INGOT), ItemStack(Material.NETHERITE_INGOT), ItemStack(Material.NETHERITE_INGOT)
    )

    const val QUARRY_NETHERRACK_UPGRADE_ID = "SE_QUARRY_NETHERRACK_UPGRADE"
    const val QUARRY_END_STONE_UPGRADE_ID = "SE_QUARRY_END_STONE_UPGRADE"
    const val QUARRY_BLACKSTONE_UPGRADE_ID = "SE_QUARRY_BLACKSTONE_UPGRADE"

    val QUARRY_NETHERRACK_UPGRADE: SlimefunItemStack =
        SEText.localized(QUARRY_NETHERRACK_UPGRADE_ID, Material.NETHERRACK, "items.quarry-output.netherrack")
    val QUARRY_NETHERRACK_UPGRADE_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.NETHER_BRICKS), ItemStack(Material.NETHERRACK), ItemStack(Material.NETHER_BRICKS),
        ItemStack(Material.NETHERRACK), ItemStack(Material.MAGMA_BLOCK), ItemStack(Material.NETHERRACK),
        ItemStack(Material.NETHER_BRICKS), ItemStack(Material.NETHERRACK), ItemStack(Material.NETHER_BRICKS)
    )

    val QUARRY_END_STONE_UPGRADE: SlimefunItemStack =
        SEText.localized(QUARRY_END_STONE_UPGRADE_ID, Material.END_STONE, "items.quarry-output.end-stone")
    val QUARRY_END_STONE_UPGRADE_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.PURPUR_BLOCK), ItemStack(Material.END_STONE), ItemStack(Material.PURPUR_BLOCK),
        ItemStack(Material.END_STONE), ItemStack(Material.ENDER_EYE), ItemStack(Material.END_STONE),
        ItemStack(Material.PURPUR_BLOCK), ItemStack(Material.END_STONE), ItemStack(Material.PURPUR_BLOCK)
    )

    /**
     * 黑石产物升级比现有产物升级更昂贵：黑曜石四角、黑石四边、钻石块居中。
     * 不使用远古残骸作为前置，保持无中生有资源链从零起步。
     */
    val QUARRY_BLACKSTONE_UPGRADE: SlimefunItemStack =
        SEText.localized(QUARRY_BLACKSTONE_UPGRADE_ID, Material.BLACKSTONE, "items.quarry-output.blackstone")
    val QUARRY_BLACKSTONE_UPGRADE_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.OBSIDIAN), ItemStack(Material.BLACKSTONE), ItemStack(Material.OBSIDIAN),
        ItemStack(Material.BLACKSTONE), ItemStack(Material.DIAMOND_BLOCK), ItemStack(Material.BLACKSTONE),
        ItemStack(Material.OBSIDIAN), ItemStack(Material.BLACKSTONE), ItemStack(Material.OBSIDIAN)
    )

    // ============================ 简易的领地 ============================

    const val TERRITORY_CORE_ID = "SE_TERRITORY_CORE"
    const val TERRITORY_FLAG_ID = "SE_TERRITORY_FLAG"

    /** 放下后认领以核心所在区块为中心的3×3区域；一个玩家只能拥有一个核心。 */
    val TERRITORY_CORE: SlimefunItemStack =
        SEText.localized(TERRITORY_CORE_ID, Material.LODESTONE, "items.territory-core")

    /**
     * 前期配方刻意不要求下界材料；磁石仅作为自定义物品外观，不代表原版磁石配方成本。
     */
    val TERRITORY_CORE_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.COPPER_BLOCK), ItemStack(Material.COMPASS), ItemStack(Material.COPPER_BLOCK),
        ItemStack(Material.IRON_BLOCK), ItemStack(Material.EMERALD), ItemStack(Material.IRON_BLOCK),
        ItemStack(Material.STONE_BRICKS), ItemStack(Material.REDSTONE_BLOCK), ItemStack(Material.STONE_BRICKS)
    )

    /** 每面旗帜认领以所在区块为中心的3×3并集；同领地覆盖允许重叠。 */
    val TERRITORY_FLAG: SlimefunItemStack =
        SEText.localized(TERRITORY_FLAG_ID, Material.WHITE_BANNER, "items.territory-flag")

    val TERRITORY_FLAG_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.WHITE_WOOL), ItemStack(Material.WHITE_BANNER), ItemStack(Material.WHITE_WOOL),
        ItemStack(Material.COPPER_INGOT), ItemStack(Material.COMPASS), ItemStack(Material.COPPER_INGOT),
        ItemStack(Material.OAK_LOG), ItemStack(Material.EMERALD), ItemStack(Material.OAK_LOG)
    )
}
