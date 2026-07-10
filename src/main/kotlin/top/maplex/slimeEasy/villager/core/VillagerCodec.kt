package top.maplex.slimeEasy.villager.core

import org.bukkit.NamespacedKey
import org.bukkit.inventory.MerchantRecipe
import top.maplex.slimeEasy.storage.core.ItemCodec

/**
 * [VillagerData] 与单个字符串的编解码器 (写入捕捉器 PDC 与各机器 BlockData)。
 *
 * 版本化、分层分隔:
 * - 顶层字段以 `|` 分隔: `版本|职业键|类型键|等级|经验|成年(0/1)|交易段`;
 * - 交易段内多条交易以 `;` 分隔, 每条交易的字段以 `,` 分隔;
 * - 交易内的物品用 [ItemCodec] 编为 Base64 (标准 Base64 字母表不含 `| , ;`, 不冲突)。
 *
 * 解码失败 (数据损坏 / 跨大版本) 静默返回 null, 调用方按"无有效村民"处理, 避免崩溃。
 */
object VillagerCodec {

    private const val VERSION = "2"
    private const val TOP = "|"
    private const val RECIPE_SEP = ";"
    private const val FIELD = ","

    /**
     * 把村民快照编码为单字符串 (v2)。
     *
     * 顶层: `2|职业|类型|等级|经验|成年|僵尸|交易段`。
     */
    fun encode(data: VillagerData): String {
        val recipes = data.recipes.joinToString(RECIPE_SEP) { encodeRecipe(it) }
        return listOf(
            VERSION,
            data.professionKey.toString(),
            data.typeKey.toString(),
            data.level.toString(),
            data.experience.toString(),
            if (data.adult) "1" else "0",
            if (data.zombie) "1" else "0",
            recipes
        ).joinToString(TOP)
    }

    /** 解码 (兼容 v1: 无僵尸字段, 交易段在第 7 位); 失败返回 null。 */
    fun decode(raw: String?): VillagerData? {
        if (raw.isNullOrEmpty()) return null
        return try {
            val p = raw.split(TOP)
            when (p.getOrNull(0)) {
                "1" -> decodeCommon(p, zombie = false, recipeIndex = 6, minSize = 7)
                "2" -> decodeCommon(p, zombie = p[6] == "1", recipeIndex = 7, minSize = 8)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 按版本布局解出快照 (交易段不含 '|', 故按索引直取)。 */
    private fun decodeCommon(p: List<String>, zombie: Boolean, recipeIndex: Int, minSize: Int): VillagerData? {
        if (p.size < minSize) return null
        val recipeSeg = p[recipeIndex]
        val recipes = if (recipeSeg.isEmpty()) emptyList()
        else recipeSeg.split(RECIPE_SEP).mapNotNull { decodeRecipe(it) }
        return VillagerData(
            professionKey = NamespacedKey.fromString(p[1]) ?: return null,
            typeKey = NamespacedKey.fromString(p[2]) ?: return null,
            level = p[3].toInt(),
            experience = p[4].toInt(),
            adult = p[5] == "1",
            recipes = recipes,
            zombie = zombie
        )
    }

    /** 编码单条交易: 结果 / 材料1 / 材料2 / 使用次数 / 上限 / 给经验 / 村民经验 / 价格系数 / 需求 / 特价。 */
    private fun encodeRecipe(r: MerchantRecipe): String {
        val ings = r.ingredients
        val res = ItemCodec.encode(r.result)
        val i1 = ings.getOrNull(0)?.let { ItemCodec.encode(it) } ?: ""
        val i2 = ings.getOrNull(1)?.let { ItemCodec.encode(it) } ?: ""
        return listOf(
            res, i1, i2,
            r.uses.toString(), r.maxUses.toString(),
            if (r.hasExperienceReward()) "1" else "0",
            r.villagerExperience.toString(),
            r.priceMultiplier.toString(),
            r.demand.toString(),
            r.specialPrice.toString()
        ).joinToString(FIELD)
    }

    /** 解码单条交易; 缺字段 / 结果无效返回 null。 */
    private fun decodeRecipe(s: String): MerchantRecipe? {
        val f = s.split(FIELD)
        if (f.size < 10) return null
        val result = ItemCodec.decode(f[0]) ?: return null
        val recipe = MerchantRecipe(
            result,
            f[3].toInt(),                 // uses
            f[4].toInt(),                 // maxUses
            f[5] == "1",                  // experienceReward
            f[6].toInt(),                 // villagerExperience
            f[7].toFloat(),               // priceMultiplier
            f[8].toInt(),                 // demand
            f[9].toInt()                  // specialPrice
        )
        ItemCodec.decode(f[1])?.let { recipe.addIngredient(it) }
        if (f[2].isNotEmpty()) ItemCodec.decode(f[2])?.let { recipe.addIngredient(it) }
        return recipe
    }
}
