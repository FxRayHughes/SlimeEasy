package top.maplex.slimeEasy.villager.core

import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Villager
import org.bukkit.entity.ZombieVillager
import org.bukkit.inventory.MerchantRecipe

/**
 * 一个村民 (或僵尸村民) 的可持久化快照 (捕捉器 / 各机器共用)。
 *
 * 保留"打交道"真正关心且稳定可还原的属性: 职业、变种类型、等级、经验、成年与否、交易列表,
 * 以及是否为 [僵尸形态][zombie]。职业与类型以 [NamespacedKey] 记录, 经 [Registry] 解析,
 * 不引用可能随版本变动的枚举常量。僵尸村民无交易 (recipes 为空)。
 *
 * 序列化由 [VillagerCodec] 负责; 本类只做与实体的互转。
 */
data class VillagerData(
    val professionKey: NamespacedKey,
    val typeKey: NamespacedKey,
    val level: Int,
    val experience: Int,
    val adult: Boolean,
    val recipes: List<MerchantRecipe>,
    /** 是否为僵尸村民形态 (治愈机的转化对象; 亦影响释放时生成的实体类型)。 */
    val zombie: Boolean = false
) {

    /** 是否为傻子 (呆滞) 村民 —— 村民小学的转化对象。 */
    val isNitwit: Boolean get() = professionKey.key == "nitwit"

    /** 是否为无职业普通村民。 */
    val isJobless: Boolean get() = professionKey.key == "none"

    /** 职业展示名 (取路径, 首字母大写); 僵尸形态追加标注。 */
    val professionLabel: String
        get() = professionKey.key.replaceFirstChar { it.uppercaseChar() } + if (zombie) " · 僵尸" else ""

    /** 在指定位置生成对应实体 (僵尸村民 / 普通村民) 并还原其全部属性; 返回该实体。 */
    fun spawnEntity(location: Location): LivingEntity =
        if (zombie) {
            location.world.spawn(location, ZombieVillager::class.java).also { applyToZombie(it) }
        } else {
            location.world.spawn(location, Villager::class.java).also { applyTo(it) }
        }

    /** 把全部属性 (含交易) 还原到一个活体村民上。 */
    fun applyTo(villager: Villager) {
        applyAppearance(villager)
        villager.villagerExperience = experience.coerceAtLeast(0)
        if (adult) villager.setAdult() else villager.setBaby()
        // 先定职业再灌交易, 避免职业变更触发原版重生成交易覆盖我们的列表
        if (recipes.isNotEmpty()) villager.recipes = recipes
    }

    /** 把职业 / 类型 / 成年状态还原到一个僵尸村民上。 */
    fun applyToZombie(zombie: ZombieVillager) {
        resolveProfession(professionKey)?.let { zombie.villagerProfession = it }
        resolveType(typeKey)?.let { zombie.villagerType = it }
        if (adult) zombie.setAdult() else zombie.setBaby()
    }

    /** 仅还原外观相关属性 (职业 / 类型 / 等级), 供展示实体使用, 不含交易与经验。 */
    fun applyAppearance(villager: Villager) {
        resolveProfession(professionKey)?.let { villager.profession = it }
        resolveType(typeKey)?.let { villager.villagerType = it }
        villager.villagerLevel = level.coerceIn(1, 5)
    }

    /** 返回一个仅替换职业的副本 (村民小学 / 遗忘转化用)。 */
    fun withProfession(key: NamespacedKey): VillagerData = copy(professionKey = key)

    /** 返回治愈为普通村民的副本 (僵尸形态清除; 职业 / 类型保留)。 */
    fun cured(): VillagerData = copy(zombie = false)

    companion object {

        /** minecraft:none 无职业键。 */
        val NONE: NamespacedKey = NamespacedKey.minecraft("none")

        /** 从活体村民抓取快照。 */
        fun capture(villager: Villager): VillagerData = VillagerData(
            professionKey = villager.profession.key,
            typeKey = villager.villagerType.key,
            level = villager.villagerLevel,
            experience = villager.villagerExperience,
            adult = villager.isAdult,
            recipes = villager.recipes,
            zombie = false
        )

        /** 从僵尸村民抓取快照 (无交易 / 等级经验取默认)。 */
        fun captureZombie(zombie: ZombieVillager): VillagerData = VillagerData(
            professionKey = zombie.villagerProfession.key,
            typeKey = zombie.villagerType.key,
            level = 1,
            experience = 0,
            adult = zombie.isAdult,
            recipes = emptyList(),
            zombie = true
        )

        /** 按键解析职业; 未知返回 null。 */
        fun resolveProfession(key: NamespacedKey): Villager.Profession? = Registry.VILLAGER_PROFESSION.get(key)

        /** 按键解析变种类型; 未知返回 null。 */
        fun resolveType(key: NamespacedKey): Villager.Type? = Registry.VILLAGER_TYPE.get(key)
    }
}
