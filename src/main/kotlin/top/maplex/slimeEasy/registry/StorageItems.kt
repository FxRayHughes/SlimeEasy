package top.maplex.slimeEasy.registry

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
    val DRAWER = SEText.localized(DRAWER_ID, Material.BARREL, "items.storage.drawer")
    val DRAWER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.CHEST), ItemStack(Material.OAK_PLANKS),
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.BARREL), ItemStack(Material.OAK_PLANKS),
        ItemStack(Material.OAK_PLANKS), ItemStack(Material.HOPPER), ItemStack(Material.OAK_PLANKS)
    )

    const val BOX_ID = "SE_PAGED_BOX"
    val BOX = SEText.localized(BOX_ID, Material.BARREL, "items.storage.paged-box")
    val BOX_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.CHEST), ItemStack(Material.CHEST), ItemStack(Material.CHEST),
        ItemStack(Material.CHEST), ItemStack(Material.BARREL), ItemStack(Material.CHEST),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT)
    )

    /** 磁盘管理器方块 ID；雕纹书架材质提供六个可见且可持久化的书本安装位。 */
    const val DISK_MANAGER_ID = "SE_DISK_MANAGER"
    val DISK_MANAGER = SEText.localized(
        DISK_MANAGER_ID, Material.CHISELED_BOOKSHELF, "items.storage.disk-manager"
    )
    val DISK_MANAGER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.COMPARATOR), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.CHISELED_BOOKSHELF), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT)
    )

    // 磁盘 ID 同时用于规格识别与 UniversalData sfId 校验，属于持久化协议的一部分。
    const val DISK_1K_ID = "SE_ITEM_DISK_1K"
    const val DISK_4K_ID = "SE_ITEM_DISK_4K"
    const val DISK_16K_ID = "SE_ITEM_DISK_16K"
    const val DISK_64K_ID = "SE_ITEM_DISK_64K"
    const val DISK_128K_ID = "SE_ITEM_DISK_128K"
    const val DISK_256K_ID = "SE_ITEM_DISK_256K"

    val DISK_1K = SEText.localized(DISK_1K_ID, Material.BOOK, "items.storage.disks.1k")
    val DISK_4K = SEText.localized(DISK_4K_ID, Material.BOOK, "items.storage.disks.4k")
    val DISK_16K = SEText.localized(DISK_16K_ID, Material.BOOK, "items.storage.disks.16k")
    val DISK_64K = SEText.localized(DISK_64K_ID, Material.BOOK, "items.storage.disks.64k")
    val DISK_128K = SEText.localized(DISK_128K_ID, Material.BOOK, "items.storage.disks.128k")
    val DISK_256K = SEText.localized(DISK_256K_ID, Material.BOOK, "items.storage.disks.256k")

    // 容量逐级升级，确保合成时必须消耗上一档磁盘，而不是绕过成长链。
    val DISK_1K_RECIPE = surround(Material.PAPER, Material.BOOK)
    val DISK_4K_RECIPE = surround(Material.COPPER_INGOT, DISK_1K)
    val DISK_16K_RECIPE = surround(Material.IRON_INGOT, DISK_4K)
    val DISK_64K_RECIPE = surround(Material.GOLD_INGOT, DISK_16K)
    val DISK_128K_RECIPE = surround(Material.DIAMOND, DISK_64K)
    val DISK_256K_RECIPE = surround(Material.NETHERITE_SCRAP, DISK_128K)

    // ============================ 存储网络 ============================

    const val CONTROLLER_ID = "SE_NET_CONTROLLER"
    val CONTROLLER = SEText.localized(CONTROLLER_ID, Material.CHISELED_BOOKSHELF, "items.storage.network.controller")
    val CONTROLLER_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ENDER_EYE), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.CHISELED_BOOKSHELF), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.COMPARATOR), ItemStack(Material.IRON_INGOT)
    )

    const val CONNECTOR_ID = "SE_NET_CONNECTOR"
    val CONNECTOR = SEText.localized(CONNECTOR_ID, Material.IRON_BARS, "items.storage.network.connector")
    val CONNECTOR_RECIPE: Array<ItemStack?> = arrayOf(
        null, ItemStack(Material.IRON_INGOT), null,
        ItemStack(Material.REDSTONE), ItemStack(Material.IRON_BARS), ItemStack(Material.REDSTONE),
        null, ItemStack(Material.IRON_INGOT), null
    )

    const val INPUT_PORT_ID = "SE_NET_INPUT_PORT"
    val INPUT_PORT = SEText.localized(INPUT_PORT_ID, Material.DROPPER, "items.storage.network.input-port")
    val INPUT_PORT_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.DROPPER), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_BARS), ItemStack(Material.IRON_INGOT)
    )

    const val OUTPUT_PORT_ID = "SE_NET_OUTPUT_PORT"
    val OUTPUT_PORT = SEText.localized(OUTPUT_PORT_ID, Material.DISPENSER, "items.storage.network.output-port")
    val OUTPUT_PORT_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.HOPPER), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.REDSTONE), ItemStack(Material.DISPENSER), ItemStack(Material.REDSTONE),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.IRON_BARS), ItemStack(Material.IRON_INGOT)
    )

    const val REMOTE_TERMINAL_ID = "SE_REMOTE_TERMINAL"
    val REMOTE_TERMINAL = SEText.localized(REMOTE_TERMINAL_ID, Material.ENDER_EYE, "items.storage.remote-terminal")
    val REMOTE_TERMINAL_RECIPE: Array<ItemStack?> = arrayOf(
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ENDER_EYE), ItemStack(Material.IRON_INGOT),
        ItemStack(Material.ENDER_PEARL), ItemStack(Material.CHISELED_BOOKSHELF), ItemStack(Material.ENDER_PEARL),
        ItemStack(Material.IRON_INGOT), ItemStack(Material.ECHO_SHARD), ItemStack(Material.IRON_INGOT)
    )

    // ============================ 升级组件 ============================
    // ID 必须与 UpgradeType.itemId 完全一致

    val STACK_I = upgrade("SE_STACK_UPGRADE_I", Material.COPPER_INGOT, "items.storage.upgrades.stack-1")
    val STACK_I_RECIPE = surround(Material.COPPER_INGOT, Material.PAPER)

    val STACK_II = upgrade("SE_STACK_UPGRADE_II", Material.IRON_INGOT, "items.storage.upgrades.stack-2")
    val STACK_II_RECIPE = surround(Material.IRON_INGOT, STACK_I)

    val STACK_III = upgrade("SE_STACK_UPGRADE_III", Material.GOLD_INGOT, "items.storage.upgrades.stack-3")
    val STACK_III_RECIPE = surround(Material.GOLD_INGOT, STACK_II)

    val EXP_UPGRADE = upgrade("SE_EXP_UPGRADE", Material.EXPERIENCE_BOTTLE, "items.storage.upgrades.experience")
    val EXP_UPGRADE_RECIPE = surround(Material.EXPERIENCE_BOTTLE, Material.GOLD_INGOT)

    val MAGNET_UPGRADE = upgrade("SE_MAGNET_UPGRADE", Material.IRON_NUGGET, "items.storage.upgrades.magnet")
    val MAGNET_UPGRADE_RECIPE = surround(Material.IRON_NUGGET, Material.HEAVY_WEIGHTED_PRESSURE_PLATE)

    val VOID_UPGRADE = upgrade("SE_VOID_UPGRADE", Material.LAVA_BUCKET, "items.storage.upgrades.void")
    val VOID_UPGRADE_RECIPE = surround(Material.LAVA_BUCKET, Material.OBSIDIAN)

    val PAGE_UPGRADE = upgrade("SE_PAGE_UPGRADE", Material.BOOK, "items.storage.upgrades.page")
    val PAGE_UPGRADE_RECIPE = surround(Material.BOOK, Material.CHEST)

    val WISE_UPGRADE = upgrade("SE_WISE_UPGRADE", Material.EMERALD, "items.storage.upgrades.wise")
    val WISE_UPGRADE_RECIPE: Array<ItemStack?> = surround(Material.EXPERIENCE_BOTTLE, sfItemOr("WISE_TALISMAN", Material.EMERALD))

    val ENDER_WISE_UPGRADE = upgrade("SE_ENDER_WISE_UPGRADE", Material.EMERALD, "items.storage.upgrades.ender-wise")
    val ENDER_WISE_UPGRADE_RECIPE: Array<ItemStack?> = surround(Material.ENDER_PEARL, sfItemOr("ENDER_WISE_TALISMAN", Material.EMERALD))

    val EXTRACT_UPGRADE = upgrade("SE_EXTRACT_UPGRADE", Material.HOPPER, "items.storage.upgrades.extract")
    val EXTRACT_UPGRADE_RECIPE = surround(Material.HOPPER, Material.IRON_INGOT)

    val REMOTE_UPGRADE = upgrade("SE_REMOTE_UPGRADE", Material.ENDER_EYE, "items.storage.upgrades.remote")
    val REMOTE_UPGRADE_RECIPE = surround(Material.ENDER_PEARL, Material.ENDER_EYE)

    val OUTPUT_UPGRADE = upgrade("SE_OUTPUT_UPGRADE", Material.DROPPER, "items.storage.upgrades.output")
    // 1 个物品输出箱居中 + 8 张纸围绕
    val OUTPUT_UPGRADE_RECIPE: Array<ItemStack?> = surround(Material.PAPER, sfItemOr("OUTPUT_CHEST", Material.CHEST))

    /** 普通压制升级：运行时仅处理 2×2 压缩配方。 */
    val COMPRESSION_UPGRADE = upgrade("SE_COMPRESSION_UPGRADE", Material.PISTON, "items.storage.upgrades.compression")
    val COMPRESSION_UPGRADE_RECIPE = surround(Material.PISTON, Material.CRAFTING_TABLE)

    /** 高级压制升级：由普通版本升级而来，并额外支持 3×3 压缩配方。 */
    val ADVANCED_COMPRESSION_UPGRADE = upgrade(
        "SE_ADVANCED_COMPRESSION_UPGRADE", Material.STICKY_PISTON, "items.storage.upgrades.advanced-compression"
    )
    val ADVANCED_COMPRESSION_UPGRADE_RECIPE = surround(Material.SLIME_BALL, COMPRESSION_UPGRADE)

    /**
     * 取某 Slimefun 物品作为配方核心 (护身符 / 物品输出箱等); 未加载到 (如版本无此物品)
     * 则回退到一个原版占位物, 保证配方仍可注册不崩溃。
     */
    private fun sfItemOr(id: String, fallback: Material): ItemStack =
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById(id)?.item ?: ItemStack(fallback)

    /** 构造升级组件模板 (统一附加"升级组件"提示行)。 */
    private fun upgrade(id: String, material: Material, key: String): SlimefunItemStack =
        SEText.localized(id, material, key)

    /** 生成"八周围材料 + 中心核心"的 3x3 配方。 */
    private fun surround(around: Material, core: Material): Array<ItemStack?> =
        Array(9) { if (it == 4) ItemStack(core) else ItemStack(around) }

    /** 生成"八周围材料 + 中心核心物品"的 3x3 配方。 */
    private fun surround(around: Material, core: ItemStack): Array<ItemStack?> =
        Array(9) { if (it == 4) core.clone() else ItemStack(around) }
}
