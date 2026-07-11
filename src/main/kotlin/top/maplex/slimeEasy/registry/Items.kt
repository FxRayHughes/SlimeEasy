package top.maplex.slimeEasy.registry

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEText

/**
 * 物品堆栈与配方定义中心。
 *
 * 仅存放 [SlimefunItemStack] 模板与合成配方, 不涉及行为逻辑。
 * 物品名称 / Lore 经 [SEText] 从配置读取 (缺失以此处默认值自动写入), 修改需重启生效。
 */
object Items {

    /** 自动破坏机的全局唯一 ID。 */
    const val AUTO_BREAKER_ID = "SE_AUTO_BREAKER"

    /**
     * 自动破坏机物品模板。
     *
     * 使用涂蜡铜箱子作为机器本体: 放置后即为可交互的容器, 破坏产物直接存入其中。
     */
    val AUTO_BREAKER: SlimefunItemStack = SEText.stack(
        AUTO_BREAKER_ID,
        Material.WAXED_COPPER_CHEST,
        "&e自动破坏机",
        "",
        "&7在相邻放置 &f普通活塞&7, 机器会自动破坏",
        "&7活塞推杆朝向的方块并收入本箱子。",
        "",
        "&7在活塞上放置带拉杆的展示框,",
        "&7可通过旋转拉杆调节破坏频率。",
        "",
        "&7将工具放入箱内 &f任意位置 &7即用该工具",
        "&7挖掘 (享受时运/精准采集), 耐久耗尽后",
        "&7自动改用其余工具或恢复默认挖掘。"
    )

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
    val AUTO_PLACER: SlimefunItemStack = SEText.stack(
        AUTO_PLACER_ID,
        Material.WAXED_COPPER_CHEST,
        "&a自动放置机",
        "",
        "&7在相邻放置 &f粘性活塞&7, 机器会把箱内",
        "&7方块自动放置到活塞推杆朝向的空位。",
        "",
        "&7在活塞上放置带拉杆的展示框,",
        "&7可通过旋转拉杆调节放置频率。",
        "",
        "&7从箱内 &f第一个可放置方块 &7开始取用,",
        "&7目标非空位或无方块时自动跳过。"
    )

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
    val CREEPER_WARD: SlimefunItemStack = SEText.stack(
        CREEPER_WARD_ID,
        Material.GREEN_CARPET,
        "&a苦力怕驱逐方块",
        "",
        "&7放置后, 其所在区块及周围一圈",
        "&7共 &f3x3 个区块 &7内不再自然生成苦力怕,",
        "&7已进入范围的苦力怕会被持续推出,",
        "&7且在范围内 &c无法爆炸&7。",
        "",
        "&7保护随方块存在而持续, 破坏后失效。"
    )

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
    val SURVEY_RULER: SlimefunItemStack = SEText.stack(
        SURVEY_RULER_ID,
        Material.COPPER_HOE,
        "&b矿物勘察尺",
        "",
        "&7右键地面, 按 &f工业矿机 &7的采掘范围",
        "&7(&f7×7&7) 向下勘探, 列出可开采的",
        "&7矿石种类与数量。",
        "",
        "&7潜行 + 左键: 切换展示形式 (&f聊天栏 &7/ &f箱子界面&7)",
        "",
        "&7冷却: &f5 秒"
    )

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
    val ADVANCED_SURVEY_RULER: SlimefunItemStack = SEText.stack(
        ADVANCED_SURVEY_RULER_ID,
        Material.DIAMOND_HOE,
        "&b进阶矿物勘察尺",
        "",
        "&7右键地面, 按 &f当前勘察范围 &7向下勘探,",
        "&7列出可开采的矿石种类与数量。",
        "",
        "&7潜行 + 右键空气: 在 &f进阶工业矿机 &7(&f11×11&7)",
        "&7与 &f工业矿机 &7(&f7×7&7) 两种范围间切换。",
        "",
        "&7潜行 + 左键: 切换展示形式 (&f聊天栏 &7/ &f箱子界面&7)",
        "",
        "&7冷却: &f5 秒"
    )

    /**
     * 增强工作台配方 (3x3): 钻石锄 + 指南针 + 钻石块 (较普通版更高阶)。
     */
    val ADVANCED_SURVEY_RULER_RECIPE: Array<ItemStack?> = arrayOf(
        null, ItemStack(Material.COMPASS), null,
        null, ItemStack(Material.DIAMOND_HOE), null,
        null, ItemStack(Material.DIAMOND_BLOCK), null
    )

    /** 屠夫机器的全局唯一 ID。 */
    const val BUTCHER_ID = "SE_BUTCHER"

    /**
     * 屠夫机器物品模板 (观察者外观)。
     *
     * 观察者脸朝向 = 攻击方向; 右键开界面放武器 / 附魔书 / 食物。
     */
    val BUTCHER: SlimefunItemStack = SEText.stack(
        BUTCHER_ID,
        Material.OBSERVER,
        "&c屠夫机器",
        "",
        "&7自动攻击 &f脸朝向前方 3×3 &7区域内的",
        "&7非玩家生物 (范围横扫)。",
        "",
        "&7右键打开界面:",
        "&7· &f7 把武器&7: 第一把优先, 耐久耗尽自动切下一把",
        "&7· &f附魔书&7: 抢夺 / 锋利 / 火焰附加等生效",
        "&7· &f食物&7: 折算为攻击次数 (每 1 饱食 15 次, 内部缓存上限 100 饱食)",
        "&7· &f升级槽&7: 范围 / 伤害组件 (数量即级数, 最多 5)",
        "",
        "&7所有物品可用 &f物流网络 &7或 &f漏斗 &7自动输入",
        "&7(漏斗任意面对准机器即可)。",
        "&7击杀正常掉落物品与经验; 在他人领地内失效。"
    )

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
    val BUTCHER_RANGE_UPGRADE: SlimefunItemStack = SEText.stack(
        BUTCHER_RANGE_UPGRADE_ID,
        Material.ENDER_EYE,
        "&d屠夫 · 范围升级",
        "",
        "&7放入屠夫机器的范围升级槽。",
        "&7每一个: 攻击截面 &f+2 格&7、纵深 &f+1 格&7。",
        "&7(3×3 → 5×5 → 7×7 …, 按数量叠加)"
    )

    /** 范围升级配方: 末影之眼 + 箭矢环绕 (象征"射程延伸")。 */
    val BUTCHER_RANGE_UPGRADE_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.ARROW), ItemStack(Material.ARROW), ItemStack(Material.ARROW),
        ItemStack(Material.ARROW), ItemStack(Material.ENDER_EYE), ItemStack(Material.ARROW),
        ItemStack(Material.ARROW), ItemStack(Material.ARROW), ItemStack(Material.ARROW)
    )

    /** 屠夫机器 · 伤害升级组件 ID (可叠放, 数量即级数)。 */
    const val BUTCHER_DAMAGE_UPGRADE_ID = "SE_BUTCHER_DAMAGE_UPGRADE"

    /** 伤害升级组件: 每级伤害线性 +50% 基础值。 */
    val BUTCHER_DAMAGE_UPGRADE: SlimefunItemStack = SEText.stack(
        BUTCHER_DAMAGE_UPGRADE_ID,
        Material.BLAZE_POWDER,
        "&c屠夫 · 伤害升级",
        "",
        "&7放入屠夫机器的伤害升级槽。",
        "&7每一个: 攻击伤害 &f+50% 基础值&7。",
        "&7(×1.5 → ×2.0 → ×2.5 …, 按数量叠加)"
    )

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
    val AUTO_CLICKER: SlimefunItemStack = SEText.stack(
        AUTO_CLICKER_ID,
        Material.OBSERVER,
        "&6自动点击器",
        "",
        "&7以假玩家身份, 不断 &f左键 / 右键 &7脸朝向正前方的方块。",
        "",
        "&c需红石激活&7: 通电时工作, 断电即停。",
        "&7右键打开界面放入 &f一格物品&7: 点击时假玩家",
        "&7手持该物品交互 (如骨粉施肥 / 桶取放液体), 消耗后回写。",
        "&7物品可用 &f相邻漏斗 &7(输出对准本机) 或物流网络补充;",
        "&7界面内可装 &f抽取升级&7: 改为从相邻箱子 / 容器补料并支持黑 / 白名单。",
        "",
        "&7支持点击 &fSlimefun (粘液) 方块&7, 且 &f绕过其研究解锁限制&7。",
        "&8约每秒点击一次。"
    )

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
    val GROWTH_INHIBITOR: SlimefunItemStack = SEText.stack(
        GROWTH_INHIBITOR_ID,
        Material.SLIME_BALL,
        "&a生长抑制器",
        "",
        "&7右键一只 &f幼年生物&7: 锁定其年龄,",
        "&7使其 &f永远保持幼小&7、不再长大。",
        "",
        "&7再次右键 &f已锁定 &7的生物: 解除锁定, 恢复生长。",
        "&8(仅对动物 / 村民等可繁殖生物有效)"
    )

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
    private fun combatHarness(id: String, material: Material, name: String, damage: Int): SlimefunItemStack =
        SEText.stack(
            id, material, name,
            "",
            "&7给 &f快乐恶魂 (乐魂) &7右键佩戴。",
            "&7佩戴后乐魂以 &b守护者激光 &7主动攻击",
            "&7周围 &f视线内 &7的敌对生物 (&c不穿透方块&7)。",
            "",
            "&7每次激光造成 &c$damage 点 &7伤害 (&d药水/魔法伤害, 无视护甲&7)。"
        )

    val COMBAT_HARNESS_I: SlimefunItemStack = combatHarness(COMBAT_HARNESS_I_ID, Material.WHITE_HARNESS, "&b战斗挽具 I", 5)
    val COMBAT_HARNESS_II: SlimefunItemStack = combatHarness(COMBAT_HARNESS_II_ID, Material.LIME_HARNESS, "&a战斗挽具 II", 10)
    val COMBAT_HARNESS_III: SlimefunItemStack = combatHarness(COMBAT_HARNESS_III_ID, Material.ORANGE_HARNESS, "&6战斗挽具 III", 20)
    val COMBAT_HARNESS_IV: SlimefunItemStack = combatHarness(COMBAT_HARNESS_IV_ID, Material.RED_HARNESS, "&c战斗挽具 IV", 25)

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
    val QUARRY: SlimefunItemStack = SEText.stack(
        QUARRY_ID,
        Material.OBSERVER,
        "&b采石场",
        "",
        "&7观察者方块。&f脸朝向 &7的圆石若同时相邻",
        "&c岩浆 &7与 &9水 &7(不必是源头), 便持续产出圆石",
        "&7(不破坏该圆石)。",
        "",
        "&7本身 &f无容积&7, 产物直接输出到周围的",
        "&f容器 / 抽屉 / 翻页箱&7。",
        "",
        "&7右键打开界面放入 &f效率升级 I~V &7提速:",
        "&7空槽 &f1 个/秒&7; I=1 · II=6 · III=12 · IV=32 · V=64 &f个/0.5s&7。"
    )

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
    private fun quarryEfficiency(id: String, material: Material, name: String, rate: Int): SlimefunItemStack =
        SEText.stack(
            id, material, name,
            "",
            "&7放入采石场的 &f效率升级槽&7。",
            "&7生产速率提升至 &f$rate 个&7 / 0.5 秒。",
            "&8(仅认物品身份, 堆叠数量无关)"
        )

    val QUARRY_EFFICIENCY_I: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_I_ID, Material.COPPER_INGOT, "&f采石场效率升级 I", 1)
    val QUARRY_EFFICIENCY_II: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_II_ID, Material.IRON_INGOT, "&a采石场效率升级 II", 6)
    val QUARRY_EFFICIENCY_III: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_III_ID, Material.GOLD_INGOT, "&e采石场效率升级 III", 12)
    val QUARRY_EFFICIENCY_IV: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_IV_ID, Material.DIAMOND, "&b采石场效率升级 IV", 32)
    val QUARRY_EFFICIENCY_V: SlimefunItemStack =
        quarryEfficiency(QUARRY_EFFICIENCY_V_ID, Material.NETHERITE_INGOT, "&6采石场效率升级 V", 64)

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
}
