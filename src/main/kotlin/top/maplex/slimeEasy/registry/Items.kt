package top.maplex.slimeEasy.registry

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * 物品堆栈与配方定义中心。
 *
 * 仅存放 [SlimefunItemStack] 模板与合成配方, 不涉及行为逻辑。
 */
object Items {

    /** 自动破坏机的全局唯一 ID。 */
    const val AUTO_BREAKER_ID = "SE_AUTO_BREAKER"

    /**
     * 自动破坏机物品模板。
     *
     * 使用涂蜡铜箱子作为机器本体: 放置后即为可交互的容器, 破坏产物直接存入其中。
     */
    val AUTO_BREAKER: SlimefunItemStack = SlimefunItemStack(
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
    val AUTO_PLACER: SlimefunItemStack = SlimefunItemStack(
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
    val CREEPER_WARD: SlimefunItemStack = SlimefunItemStack(
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
    val SURVEY_RULER: SlimefunItemStack = SlimefunItemStack(
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
    val ADVANCED_SURVEY_RULER: SlimefunItemStack = SlimefunItemStack(
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
    val BUTCHER: SlimefunItemStack = SlimefunItemStack(
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
    val BUTCHER_RANGE_UPGRADE: SlimefunItemStack = SlimefunItemStack(
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
    val BUTCHER_DAMAGE_UPGRADE: SlimefunItemStack = SlimefunItemStack(
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
}
