package top.maplex.slimeEasy.storage.upgrade

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import top.maplex.slimeEasy.storage.core.ItemKey

/**
 * 从 Bukkit 合成表提取出的压缩规则。
 *
 * [choices] 保留每个格子的 RecipeChoice，判定自定义物品时不会退化为仅比较 Material。
 */
data class CompressionRecipe(
    val inputAmount: Int,
    val output: ItemStack,
    val reversible: Boolean,
    val choices: List<RecipeChoice>
) {
    /** 物品必须同时满足正方形配方中的每一个原料选择，才能视作同物品压缩配方。 */
    fun accepts(item: ItemStack): Boolean = choices.all { it.test(item) }
}

/**
 * 运行时压缩配方索引。
 *
 * 首次实际压制时再扫描 Bukkit 配方，确保服务端与其它插件已经完成配方注册；结果随后缓存，
 * 避免每次容器变更都遍历全服配方。
 */
object CompressionRecipes {

    /** 候选仅包含完整 2×2 或 3×3、且全部格子均有原料的有序配方。 */
    private val recipes: List<CompressionRecipe> by lazy(::scan)

    /**
     * 为 [item] 选择当前升级等级可用的最高压缩率配方。
     *
     * 高级升级同时命中 2×2 与 3×3 时优先 3×3；普通升级只可能命中 2×2。
     */
    fun find(item: ItemStack, maxGridSize: Int, allowIrreversible: Boolean): CompressionRecipe? =
        recipes.asSequence()
            .filter { it.inputAmount <= maxGridSize * maxGridSize }
            .filter { allowIrreversible || it.reversible }
            .filter { it.accepts(item) }
            .maxByOrNull { it.inputAmount }

    /** 拍摄当前 Bukkit 配方快照，并在同一快照中计算每条压缩规则是否可逆。 */
    private fun scan(): List<CompressionRecipe> {
        val registered = Bukkit.recipeIterator().asSequence().toList()
        return registered.mapNotNull { recipe -> compressionRecipe(recipe, registered) }
    }

    /** 将严格的正方形有序配方转换为压缩规则，其余配方返回 null。 */
    private fun compressionRecipe(recipe: Recipe, registered: List<Recipe>): CompressionRecipe? {
        val shaped = recipe as? ShapedRecipe ?: return null
        val shape = shaped.shape
        val side = shape.size
        if (side !in 2..3 || shape.any { it.length != side || ' ' in it }) return null
        val choices = shape.flatMap { row -> row.mapNotNull(shaped.choiceMap::get) }
        if (choices.size != side * side) return null
        val output = shaped.result
        if (output.type.isAir || ItemKey.of(output) == null) return null
        val reversible = registered.any { isReverse(it, choices.first(), output, side * side) }
        return CompressionRecipe(side * side, output, reversible, choices)
    }

    /**
     * 判断是否存在精确反向配方：消耗压缩产物，并完整返还本次压制消耗的原物品数量。
     */
    private fun isReverse(recipe: Recipe, original: RecipeChoice, compressed: ItemStack, originalAmount: Int): Boolean {
        val result = recipe.result
        if (result.amount != originalAmount || !original.test(result)) return false
        val choices = when (recipe) {
            is ShapedRecipe -> recipe.shape.flatMap { row -> row.mapNotNull(recipe.choiceMap::get) }
            is ShapelessRecipe -> recipe.choiceList
            else -> return false
        }
        return choices.size == compressed.amount && choices.all { it.test(compressed) }
    }
}
