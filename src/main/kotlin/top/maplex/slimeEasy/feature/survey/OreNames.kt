package top.maplex.slimeEasy.feature.survey

import top.maplex.slimeEasy.config.I18n
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

    private val NAME_KEYS: Map<Material, String> = buildMap {
        // 石头系
        put(Material.COAL_ORE, "names.materials.coal-ore")
        put(Material.IRON_ORE, "names.materials.iron-ore")
        put(Material.COPPER_ORE, "names.materials.copper-ore")
        put(Material.GOLD_ORE, "names.materials.gold-ore")
        put(Material.REDSTONE_ORE, "names.materials.redstone-ore")
        put(Material.EMERALD_ORE, "names.materials.emerald-ore")
        put(Material.LAPIS_ORE, "names.materials.lapis-ore")
        put(Material.DIAMOND_ORE, "names.materials.diamond-ore")
        // 深板岩系
        put(Material.DEEPSLATE_COAL_ORE, "names.materials.deepslate-coal-ore")
        put(Material.DEEPSLATE_IRON_ORE, "names.materials.deepslate-iron-ore")
        put(Material.DEEPSLATE_COPPER_ORE, "names.materials.deepslate-copper-ore")
        put(Material.DEEPSLATE_GOLD_ORE, "names.materials.deepslate-gold-ore")
        put(Material.DEEPSLATE_REDSTONE_ORE, "names.materials.deepslate-redstone-ore")
        put(Material.DEEPSLATE_EMERALD_ORE, "names.materials.deepslate-emerald-ore")
        put(Material.DEEPSLATE_LAPIS_ORE, "names.materials.deepslate-lapis-ore")
        put(Material.DEEPSLATE_DIAMOND_ORE, "names.materials.deepslate-diamond-ore")
        // 下界系
        put(Material.NETHER_QUARTZ_ORE, "names.materials.nether-quartz-ore")
        put(Material.NETHER_GOLD_ORE, "names.materials.nether-gold-ore")
        put(Material.GILDED_BLACKSTONE, "names.materials.gilded-blackstone")
    }

    /** 返回矿石的中文名; 表外材质回退为枚举名可读化 (下划线转空格, 词首大写)。 */
    fun of(material: Material): String =
        NAME_KEYS[material]?.let { I18n.text(it) } ?: material.name.split('_').joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
}
