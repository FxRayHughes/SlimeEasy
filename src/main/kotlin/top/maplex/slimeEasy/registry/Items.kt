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
        "&7已进入范围的苦力怕会被持续推开。",
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
}
