package top.maplex.slimeEasy.registry

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * 存储系统的物品模板与配方定义中心。
 *
 * 仅存放 [SlimefunItemStack] 模板与合成配方, 不涉及行为逻辑。升级组件 ID 必须与
 * [top.maplex.slimeEasy.storage.upgrade.UpgradeType] 中登记的 itemId 完全一致。
 */
object StorageItems {

    // ============================ 存储方块 ============================

    const val DRAWER_ID = "SE_DRAWER"
    val DRAWER = SlimefunItemStack(
        DRAWER_ID, Material.BARREL, "&e海量抽屉",
        "", "&7存储 &f单一种类 &7物品, 数量远超原版堆叠。",
        "&7放置后朝向你的一面会显示物品; &f准星对准 &7查看数量。",
        "", "&7展示框右键: 存入手中该组; &f双击 &7存入背包全部同类;",
        "&7展示框左键: 取出一个; &fShift+左键 &7取出一组;",
        "&f右键木桶本体 &7打开操作界面 (含升级入口)。",
        "", "&7可接入原版货运网络。"
    )
    val DRAWER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.CHEST), ItemStack(Material.OAK_PLANKS),
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.BARREL), ItemStack(Material.OAK_PLANKS),
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.HOPPER), ItemStack(Material.OAK_PLANKS)
    )

    const val BOX_ID = "SE_PAGED_BOX"
    val BOX = SlimefunItemStack(
        BOX_ID, Material.BARREL, "&6翻页存储箱",
        "", "&7存储 &f多种 &7物品 (每种一格, 至多 45 种),",
        "&7每格数量远超原版堆叠。",
        "", "&7右键打开分页界面: 点击箱内物品取出,",
        "&7点击背包物品存入; 界面内可打开升级 GUI。",
        "", "&7可接入原版货运网络。"
    )
    val BOX_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.CHEST), ItemStack(Material.CHEST), ItemStack(Material.CHEST),
        ItemStack(Material.CHEST), ItemStack(Material.BARREL), ItemStack(Material.CHEST),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT)
    )

    // ============================ 存储网络 ============================

    const val CONTROLLER_ID = "SE_NET_CONTROLLER"
    val CONTROLLER = SlimefunItemStack(
        CONTROLLER_ID, Material.CHISELED_BOOKSHELF, "&b网络控制器",
        "", "&7存储网络的大脑与访问入口。",
        "&7右键打开 &f聚合终端&7: 一处存取全网所有",
        "&7抽屉 / 存储箱的库存。",
        "", "&7每 tick 驱动网络内的输入 / 输出端口路由,",
        "&7桥接原版货运。覆盖半径 &f24 格&7。"
    )
    val CONTROLLER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ENDER_EYE), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.CHISELED_BOOKSHELF), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.COMPARATOR), ItemStack(Material.IRON_INGOT)
    )

    const val CONNECTOR_ID = "SE_NET_CONNECTOR"
    val CONNECTOR = SlimefunItemStack(
        CONNECTOR_ID, Material.IRON_BARS, "&7网络连接器",
        "", "&7网络导线: 连接控制器与各存储方块 / 端口。",
        "&7相邻放置即导通, 无需朝向。"
    )
    val CONNECTOR_RECIPE: Array<ItemStack?> = arrayOf(
        null, ItemStack(Material.IRON_INGOT), null,
        ItemStack(Material.REDSTONE), ItemStack(Material.IRON_BARS), ItemStack(Material.REDSTONE),
        null, ItemStack(Material.IRON_INGOT), null
    )

    const val INPUT_PORT_ID = "SE_NET_INPUT_PORT"
    val INPUT_PORT = SlimefunItemStack(
        INPUT_PORT_ID, Material.DROPPER, "&a网络输入端口",
        "", "&7货运塞入的物品会被 &f分发进网络&7,",
        "&7按优先级填入各成员容器。",
        "", "&7需连入含控制器的网络方可工作。"
    )
    val INPUT_PORT_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.DROPPER), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_BARS), ItemStack(Material.IRON_INGOT)
    )

    const val OUTPUT_PORT_ID = "SE_NET_OUTPUT_PORT"
    val OUTPUT_PORT = SlimefunItemStack(
        OUTPUT_PORT_ID, Material.DISPENSER, "&c网络输出端口",
        "", "&7从网络取出物品供货运抽走。",
        "", "&7需连入含控制器的网络方可工作。"
    )
    val OUTPUT_PORT_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.DISPENSER), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_BARS), ItemStack(Material.IRON_INGOT)
    )

    const val REMOTE_TERMINAL_ID = "SE_REMOTE_TERMINAL"
    val REMOTE_TERMINAL = SlimefunItemStack(
        REMOTE_TERMINAL_ID, Material.ENDER_EYE, "&d远程终端",
        "", "&7手持右键 &f网络控制器 &7进行绑定;",
        "&7之后手持右键即可 &f远程打开 &7该网络终端,",
        "&7随时随地存取全网库存。",
        "", "&7绑定信息存于物品自身。"
    )
    val REMOTE_TERMINAL_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ENDER_EYE), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.ENDER_PEARL), ItemStack(Material.CHISELED_BOOKSHELF), ItemStack(Material.ENDER_PEARL),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ECHO_SHARD), ItemStack(Material.IRON_INGOT)
    )

    // ============================ 升级组件 ============================
    // ID 必须与 UpgradeType.itemId 完全一致

    val STACK_I = upgrade("SE_STACK_UPGRADE_I", Material.COPPER_INGOT, "&f堆叠升级 I", "&7单格容量 &f×4")
    val STACK_I_RECIPE = surround(Material.COPPER_INGOT, Material.PAPER)

    val STACK_II = upgrade("SE_STACK_UPGRADE_II", Material.IRON_INGOT, "&e堆叠升级 II", "&7单格容量 &f×16")
    val STACK_II_RECIPE = surround(Material.IRON_INGOT, STACK_I)

    val STACK_III = upgrade("SE_STACK_UPGRADE_III", Material.GOLD_INGOT, "&6堆叠升级 III", "&7单格容量 &f×64")
    val STACK_III_RECIPE = surround(Material.GOLD_INGOT, STACK_II)

    val EXP_UPGRADE = upgrade("SE_EXP_UPGRADE", Material.EXPERIENCE_BOTTLE, "&a经验存储升级",
        "&7装入抽屉后改为 &f存储经验&7 (拒绝物品货运)。")
    val EXP_UPGRADE_RECIPE = surround(Material.EXPERIENCE_BOTTLE, Material.GOLD_INGOT)

    val MAGNET_UPGRADE = upgrade("SE_MAGNET_UPGRADE", Material.IRON_NUGGET, "&b磁铁升级",
        "&7每 tick 吸附附近 &f6 格 &7内的掉落物 / 经验球。")
    val MAGNET_UPGRADE_RECIPE = surround(Material.IRON_NUGGET, Material.HEAVY_WEIGHTED_PRESSURE_PLATE)

    val VOID_UPGRADE = upgrade("SE_VOID_UPGRADE", Material.LAVA_BUCKET, "&8虚空升级",
        "&7命中销毁列表的物品在容器内 &f封顶到保留数量&7,",
        "&7超出部分入库前 &c湮灭&7 (保留 0 即全毁);",
        "&7在升级 GUI 中配置列表与保留数量。")
    val VOID_UPGRADE_RECIPE = surround(Material.LAVA_BUCKET, Material.OBSIDIAN)

    val PAGE_UPGRADE = upgrade("SE_PAGE_UPGRADE", Material.BOOK, "&d翻页扩容",
        "&7为&f翻页存储箱&7增加 &f1 页 &7容量;",
        "&7可叠装, 基础 1 页最多扩至 &f5 页&7 (装 4 枚)。",
        "&8(对抽屉无效)")
    val PAGE_UPGRADE_RECIPE = surround(Material.BOOK, Material.CHEST)

    val WISE_UPGRADE = upgrade("SE_WISE_UPGRADE", Material.EMERALD, "&a智者升级",
        "&7经验容器 &f吸入经验 &7时,",
        "&7有 &f20% &7几率使该次经验翻倍。",
        "&8(需与经验+磁铁升级同装)")
    val WISE_UPGRADE_RECIPE: Array<ItemStack?> = surround(Material.EXPERIENCE_BOTTLE, talisman("WISE_TALISMAN", Material.EMERALD))

    val ENDER_WISE_UPGRADE = upgrade("SE_ENDER_WISE_UPGRADE", Material.EMERALD, "&5末影智者升级",
        "&7经验容器 &f吸入经验 &7时,",
        "&7有 &f50% &7几率使该次经验翻倍。",
        "&7可与智者升级叠加 (独立触发)。",
        "&8(需与经验+磁铁升级同装)")
    val ENDER_WISE_UPGRADE_RECIPE: Array<ItemStack?> = surround(Material.ENDER_PEARL, talisman("ENDER_WISE_TALISMAN", Material.EMERALD))

    val EXTRACT_UPGRADE = upgrade("SE_EXTRACT_UPGRADE", Material.HOPPER, "&e抽取升级",
        "&7装入容器后, 每 tick 从相邻",
        "&7六个方向的漏斗主动提取物品入库。",
        "&8(经验模式下不生效)")
    val EXTRACT_UPGRADE_RECIPE = surround(Material.HOPPER, Material.IRON_INGOT)

    val REMOTE_UPGRADE = upgrade("SE_REMOTE_UPGRADE", Material.ENDER_EYE, "&d远程升级",
        "&7手持右键网络控制器 &f选定目标&7,",
        "&7再把本升级装入抽屉 / 箱子升级槽,",
        "&7使其 &f远程接入 &7该控制器网络",
        "&7(无视物理相邻范围)。")
    val REMOTE_UPGRADE_RECIPE = surround(Material.ENDER_PEARL, Material.ENDER_EYE)

    /**
     * 取某 Slimefun 护身符的物品作为配方核心; 未加载到 (如版本无此物品) 则回退到
     * 一个原版占位物, 保证配方仍可注册不崩溃。
     */
    private fun talisman(id: String, fallback: Material): ItemStack =
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById(id)?.item ?: ItemStack(fallback)

    /** 构造升级组件模板 (统一附加"升级组件"提示行)。 */
    private fun upgrade(id: String, material: Material, name: String, vararg desc: String): SlimefunItemStack =
        SlimefunItemStack(id, material, name, "", *desc, "", "&8[存储升级组件]")

    /** 生成"八周围材料 + 中心核心"的 3x3 配方。 */
    private fun surround(around: Material, core: Material): Array<ItemStack?> =
        Array(9) { if (it == 4) ItemStack(core) else ItemStack(around) }

    /** 生成"八周围材料 + 中心核心物品"的 3x3 配方。 */
    private fun surround(around: Material, core: ItemStack): Array<ItemStack?> =
        Array(9) { if (it == 4) core.clone() else ItemStack(around) }
}
