package top.maplex.slimeEasy.registry

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEText

/**
 * 存储系统的物品模板与配方定义中心。
 *
 * 仅存放 [SlimefunItemStack] 模板与合成配方, 不涉及行为逻辑。升级组件 ID 必须与
 * [top.maplex.slimeEasy.storage.upgrade.UpgradeType] 中登记的 itemId 完全一致。
 * 物品名称 / Lore 经 [I18n] 从独立语言文件读取，修改后需重启生效。
 */
object StorageItems {

    // ============================ 存储方块 ============================

    const val DRAWER_ID = "SE_DRAWER"
    val DRAWER = SEText.stack(
        DRAWER_ID, Material.BARREL, I18n.raw("items.storage-items-001"),
        "", I18n.raw("items.storage-items-002"),
        I18n.raw("items.storage-items-003"),
        "", I18n.raw("items.storage-items-004"),
        I18n.raw("items.storage-items-005"),
        I18n.raw("items.storage-items-006"),
        "", I18n.raw("items.storage-items-007")
    )
    val DRAWER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.CHEST), ItemStack(Material.OAK_PLANKS),
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.BARREL), ItemStack(Material.OAK_PLANKS),
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.HOPPER), ItemStack(Material.OAK_PLANKS)
    )

    const val BOX_ID = "SE_PAGED_BOX"
    val BOX = SEText.stack(
        BOX_ID, Material.BARREL, I18n.raw("items.storage-items-008"),
        "", I18n.raw("items.storage-items-009"),
        I18n.raw("items.storage-items-010"),
        "", I18n.raw("items.storage-items-011"),
        I18n.raw("items.storage-items-012"),
        "", I18n.raw("items.storage-items-013")
    )
    val BOX_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.CHEST), ItemStack(Material.CHEST), ItemStack(Material.CHEST),
        ItemStack(Material.CHEST), ItemStack(Material.BARREL), ItemStack(Material.CHEST),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT)
    )

    // ============================ 存储网络 ============================

    const val CONTROLLER_ID = "SE_NET_CONTROLLER"
    val CONTROLLER = SEText.stack(
        CONTROLLER_ID, Material.CHISELED_BOOKSHELF, I18n.raw("items.storage-items-014"),
        "", I18n.raw("items.storage-items-015"),
        I18n.raw("items.storage-items-016"),
        I18n.raw("items.storage-items-017"),
        "", I18n.raw("items.storage-items-018"),
        I18n.raw("items.storage-items-019")
    )
    val CONTROLLER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ENDER_EYE), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.CHISELED_BOOKSHELF), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.COMPARATOR), ItemStack(Material.IRON_INGOT)
    )

    const val CONNECTOR_ID = "SE_NET_CONNECTOR"
    val CONNECTOR = SEText.stack(
        CONNECTOR_ID, Material.IRON_BARS, I18n.raw("items.storage-items-020"),
        "", I18n.raw("items.storage-items-021"),
        I18n.raw("items.storage-items-022")
    )
    val CONNECTOR_RECIPE: Array<ItemStack?> = arrayOf(
        null, ItemStack(Material.IRON_INGOT), null,
        ItemStack(Material.REDSTONE), ItemStack(Material.IRON_BARS), ItemStack(Material.REDSTONE),
        null, ItemStack(Material.IRON_INGOT), null
    )

    const val INPUT_PORT_ID = "SE_NET_INPUT_PORT"
    val INPUT_PORT = SEText.stack(
        INPUT_PORT_ID, Material.DROPPER, I18n.raw("items.storage-items-023"),
        "", I18n.raw("items.storage-items-024"),
        I18n.raw("items.storage-items-025"),
        "", I18n.raw("items.storage-items-026")
    )
    val INPUT_PORT_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.DROPPER), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_BARS), ItemStack(Material.IRON_INGOT)
    )

    const val OUTPUT_PORT_ID = "SE_NET_OUTPUT_PORT"
    val OUTPUT_PORT = SEText.stack(
        OUTPUT_PORT_ID, Material.DISPENSER, I18n.raw("items.storage-items-027"),
        "", I18n.raw("items.storage-items-028"),
        "", I18n.raw("items.storage-items-029")
    )
    val OUTPUT_PORT_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.DISPENSER), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_BARS), ItemStack(Material.IRON_INGOT)
    )

    const val REMOTE_TERMINAL_ID = "SE_REMOTE_TERMINAL"
    val REMOTE_TERMINAL = SEText.stack(
        REMOTE_TERMINAL_ID, Material.ENDER_EYE, I18n.raw("items.storage-items-030"),
        "", I18n.raw("items.storage-items-031"),
        I18n.raw("items.storage-items-032"),
        I18n.raw("items.storage-items-033"),
        I18n.raw("items.storage-items-034"),
        I18n.raw("items.storage-items-035"),
        "", I18n.raw("items.storage-items-036")
    )
    val REMOTE_TERMINAL_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ENDER_EYE), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.ENDER_PEARL), ItemStack(Material.CHISELED_BOOKSHELF), ItemStack(Material.ENDER_PEARL),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ECHO_SHARD), ItemStack(Material.IRON_INGOT)
    )

    // ============================ 升级组件 ============================
    // ID 必须与 UpgradeType.itemId 完全一致

    val STACK_I = upgrade("SE_STACK_UPGRADE_I", Material.COPPER_INGOT, I18n.raw("items.storage-items-037"), I18n.raw("items.storage-items-038"))
    val STACK_I_RECIPE = surround(Material.COPPER_INGOT, Material.PAPER)

    val STACK_II = upgrade("SE_STACK_UPGRADE_II", Material.IRON_INGOT, I18n.raw("items.storage-items-039"), I18n.raw("items.storage-items-040"))
    val STACK_II_RECIPE = surround(Material.IRON_INGOT, STACK_I)

    val STACK_III = upgrade("SE_STACK_UPGRADE_III", Material.GOLD_INGOT, I18n.raw("items.storage-items-041"), I18n.raw("items.storage-items-042"))
    val STACK_III_RECIPE = surround(Material.GOLD_INGOT, STACK_II)

    val EXP_UPGRADE = upgrade("SE_EXP_UPGRADE", Material.EXPERIENCE_BOTTLE, I18n.raw("items.storage-items-043"),
        I18n.raw("items.storage-items-044"))
    val EXP_UPGRADE_RECIPE = surround(Material.EXPERIENCE_BOTTLE, Material.GOLD_INGOT)

    val MAGNET_UPGRADE = upgrade("SE_MAGNET_UPGRADE", Material.IRON_NUGGET, I18n.raw("items.storage-items-045"),
        I18n.raw("items.storage-items-046"))
    val MAGNET_UPGRADE_RECIPE = surround(Material.IRON_NUGGET, Material.HEAVY_WEIGHTED_PRESSURE_PLATE)

    val VOID_UPGRADE = upgrade("SE_VOID_UPGRADE", Material.LAVA_BUCKET, I18n.raw("items.storage-items-047"),
        I18n.raw("items.storage-items-048"),
        I18n.raw("items.storage-items-049"),
        I18n.raw("items.storage-items-050"))
    val VOID_UPGRADE_RECIPE = surround(Material.LAVA_BUCKET, Material.OBSIDIAN)

    val PAGE_UPGRADE = upgrade("SE_PAGE_UPGRADE", Material.BOOK, I18n.raw("items.storage-items-051"),
        I18n.raw("items.storage-items-052"),
        I18n.raw("items.storage-items-053"),
        I18n.raw("items.storage-items-054"))
    val PAGE_UPGRADE_RECIPE = surround(Material.BOOK, Material.CHEST)

    val WISE_UPGRADE = upgrade("SE_WISE_UPGRADE", Material.EMERALD, I18n.raw("items.storage-items-055"),
        I18n.raw("items.storage-items-056"),
        I18n.raw("items.storage-items-057"),
        I18n.raw("items.storage-items-058"))
    val WISE_UPGRADE_RECIPE: Array<ItemStack?> = surround(Material.EXPERIENCE_BOTTLE, sfItemOr("WISE_TALISMAN", Material.EMERALD))

    val ENDER_WISE_UPGRADE = upgrade("SE_ENDER_WISE_UPGRADE", Material.EMERALD, I18n.raw("items.storage-items-059"),
        I18n.raw("items.storage-items-060"),
        I18n.raw("items.storage-items-061"),
        I18n.raw("items.storage-items-062"),
        I18n.raw("items.storage-items-063"))
    val ENDER_WISE_UPGRADE_RECIPE: Array<ItemStack?> = surround(Material.ENDER_PEARL, sfItemOr("ENDER_WISE_TALISMAN", Material.EMERALD))

    val EXTRACT_UPGRADE = upgrade("SE_EXTRACT_UPGRADE", Material.HOPPER, I18n.raw("items.storage-items-064"),
        I18n.raw("items.storage-items-065"),
        I18n.raw("items.storage-items-066"),
        I18n.raw("items.storage-items-067"),
        I18n.raw("items.storage-items-068"))
    val EXTRACT_UPGRADE_RECIPE = surround(Material.HOPPER, Material.IRON_INGOT)

    val REMOTE_UPGRADE = upgrade("SE_REMOTE_UPGRADE", Material.ENDER_EYE, I18n.raw("items.storage-items-069"),
        I18n.raw("items.storage-items-070"),
        I18n.raw("items.storage-items-071"),
        I18n.raw("items.storage-items-072"),
        I18n.raw("items.storage-items-073"))
    val REMOTE_UPGRADE_RECIPE = surround(Material.ENDER_PEARL, Material.ENDER_EYE)

    val OUTPUT_UPGRADE = upgrade("SE_OUTPUT_UPGRADE", Material.DROPPER, I18n.raw("items.storage-items-074"),
        I18n.raw("items.storage-items-075"),
        I18n.raw("items.storage-items-076"),
        I18n.raw("items.storage-items-077"),
        I18n.raw("items.storage-items-078"))
    // 1 个物品输出箱居中 + 8 张纸围绕
    val OUTPUT_UPGRADE_RECIPE: Array<ItemStack?> = surround(Material.PAPER, sfItemOr("OUTPUT_CHEST", Material.CHEST))

    /**
     * 取某 Slimefun 物品作为配方核心 (护身符 / 物品输出箱等); 未加载到 (如版本无此物品)
     * 则回退到一个原版占位物, 保证配方仍可注册不崩溃。
     */
    private fun sfItemOr(id: String, fallback: Material): ItemStack =
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById(id)?.item ?: ItemStack(fallback)

    /** 构造升级组件模板 (统一附加"升级组件"提示行)。 */
    private fun upgrade(id: String, material: Material, name: String, vararg desc: String): SlimefunItemStack =
        SEText.stack(id, material, name, "", *desc, "", I18n.raw("items.storage-items-079"))

    /** 生成"八周围材料 + 中心核心"的 3x3 配方。 */
    private fun surround(around: Material, core: Material): Array<ItemStack?> =
        Array(9) { if (it == 4) ItemStack(core) else ItemStack(around) }

    /** 生成"八周围材料 + 中心核心物品"的 3x3 配方。 */
    private fun surround(around: Material, core: ItemStack): Array<ItemStack?> =
        Array(9) { if (it == 4) core.clone() else ItemStack(around) }
}
