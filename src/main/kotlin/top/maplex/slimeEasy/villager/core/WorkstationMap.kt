package top.maplex.slimeEasy.villager.core

import org.bukkit.Material
import org.bukkit.NamespacedKey

/**
 * 村民职业与其工作站点方块的映射。
 *
 * 用于交易器判断"装配的村民是否与工作站方块匹配"——匹配才允许补货。键取职业
 * [NamespacedKey] 的路径部分 (如 `minecraft:farmer` → `farmer`), 与版本无关地识别职业,
 * 不引用可能随版本变动的枚举常量。无职业 / 傻子 (none / nitwit) 不在表中, 天然不匹配。
 */
object WorkstationMap {

    /** 职业路径 → 对应工作站方块。 */
    private val MAP: Map<String, Material> = mapOf(
        "armorer" to Material.BLAST_FURNACE,
        "butcher" to Material.SMOKER,
        "cartographer" to Material.CARTOGRAPHY_TABLE,
        "cleric" to Material.BREWING_STAND,
        "farmer" to Material.COMPOSTER,
        "fisherman" to Material.BARREL,
        "fletcher" to Material.FLETCHING_TABLE,
        "leatherworker" to Material.CAULDRON,
        "librarian" to Material.LECTERN,
        "mason" to Material.STONECUTTER,
        "shepherd" to Material.LOOM,
        "toolsmith" to Material.SMITHING_TABLE,
        "weaponsmith" to Material.GRINDSTONE
    )

    /** 某职业对应的工作站方块; 无职业 / 傻子返回 null。 */
    fun workstationFor(professionKey: NamespacedKey): Material? = MAP[professionKey.key]

    /** 某方块是否为(任一职业的)工作站方块。 */
    fun isWorkstation(material: Material): Boolean = MAP.containsValue(material)

    /** 职业与给定工作站方块是否匹配。 */
    fun matches(professionKey: NamespacedKey, material: Material?): Boolean =
        material != null && workstationFor(professionKey) == material
}
