package top.maplex.slimeEasy.feature.survey

import org.bukkit.Material

/**
 * 矿石材质中文展示名。
 *
 * 覆盖 Slimefun `INDUSTRIAL_MINER_ORES` tag 默认包含的全部矿石
 * (石头系 + 深板岩系 + 下界系 + 镶金黑石), 采用官方中文译名。
 *
 * 若遇表外材质 (如配置 / 附属扩展了矿石 tag), 回退为枚举名的可读化转换,
 * 保证不会出现空缺或崩溃。
 */
object OreNames {

    private val NAMES: Map<Material, String> = buildMap {
        // 石头系
        put(Material.COAL_ORE, "煤矿石")
        put(Material.IRON_ORE, "铁矿石")
        put(Material.COPPER_ORE, "铜矿石")
        put(Material.GOLD_ORE, "金矿石")
        put(Material.REDSTONE_ORE, "红石矿石")
        put(Material.EMERALD_ORE, "绿宝石矿石")
        put(Material.LAPIS_ORE, "青金石矿石")
        put(Material.DIAMOND_ORE, "钻石矿石")
        // 深板岩系
        put(Material.DEEPSLATE_COAL_ORE, "深层煤矿石")
        put(Material.DEEPSLATE_IRON_ORE, "深层铁矿石")
        put(Material.DEEPSLATE_COPPER_ORE, "深层铜矿石")
        put(Material.DEEPSLATE_GOLD_ORE, "深层金矿石")
        put(Material.DEEPSLATE_REDSTONE_ORE, "深层红石矿石")
        put(Material.DEEPSLATE_EMERALD_ORE, "深层绿宝石矿石")
        put(Material.DEEPSLATE_LAPIS_ORE, "深层青金石矿石")
        put(Material.DEEPSLATE_DIAMOND_ORE, "深层钻石矿石")
        // 下界系
        put(Material.NETHER_QUARTZ_ORE, "下界石英矿石")
        put(Material.NETHER_GOLD_ORE, "下界金矿石")
        put(Material.GILDED_BLACKSTONE, "镶金黑石")
    }

    /** 返回矿石的中文名; 表外材质回退为枚举名可读化 (下划线转空格, 词首大写)。 */
    fun of(material: Material): String =
        NAMES[material] ?: material.name.split('_').joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
}
