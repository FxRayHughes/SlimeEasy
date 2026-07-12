package top.maplex.slimeEasy.machine.sieve

import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Dispenser
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.registry.Items
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * 与 Slimefun 磨石操作方式一致的手动多方块筛子。
 *
 * 结构为“橡木活板门 + 下方朝上的发射器”。每份输入按配方需要多次有效筛动；原料只在最后
 * 一步的全部合成事件通过后才消耗。筛面上的 [org.bukkit.entity.BlockDisplay] 使用客户端变换插值
 * 展示原料逐渐缩小、压扁的过程；每个 [ChanceDrop] 仍独立掷骰，一次完成可同时命中多项产物。
 */
class Sieve(
    itemGroup: ItemGroup,
    item: SlimefunItemStack
) : MultiBlockMachine(
    itemGroup,
    item,
    arrayOf<ItemStack?>(
        null, null, null,
        null, ItemStack(Material.OAK_TRAPDOOR), null,
        null, null, null
    ),
    BlockFace.SELF
) {

    /**
     * 向 Slimefun 指南登记所有“输入 → 可能产物”组合。
     *
     * 这些配对仅用于配方展示；实际产量、概率和所需操作次数统一由 [SieveRecipe] 决定。
     */
    override fun registerDefaultRecipes(recipes: MutableList<ItemStack>) {
        for (recipe in SIEVE_RECIPES) {
            for (output in recipe.displayOutputs) {
                recipes.add(recipe.input.clone())
                recipes.add(output.clone())
            }
        }
    }

    /**
     * 推进或完成一次筛分。
     *
     * 普通有效点击只更新内存进度与展示，不修改发射器物品。达到最后一步后先生成产物并派发全部
     * [MultiBlockCraftEvent]；只有事件均未取消、机器与输入再次验证成功时，才原子地消耗一份输入
     * 并输出结果。这样机器破坏、区块卸载、输入改变或插件关闭都不会造成原料丢失。
     */
    override fun onInteract(player: Player, block: Block) {
        val structure = findStructure(block) ?: run {
            SieveRuntime.clear(block)
            return
        }
        val linked = findLinkedSieves(block, structure)
        val candidates = prepareLinkedCandidates(player, linked)
        var advanced = false

        for (candidate in candidates) {
            if (processSieve(player, candidate)) {
                advanced = true
            }
        }

        if (!advanced) {
            Slimefun.getLocalization().sendMessage(player, "machines.unknown-material", true)
        }
    }

    private fun prepareLinkedCandidates(player: Player, linked: List<Block>): List<WorkCandidate> {
        val handRecipe = findHandRecipe(player)
        val handSnapshot = player.inventory.itemInMainHand.clone().apply { amount = 1 }
        val active = ArrayList<WorkCandidate>()
        val idle = ArrayList<Pair<Block, SieveStructure>>()

        for (sieveBlock in linked) {
            val structure = findStructure(sieveBlock) ?: continue
            val activeRecipeKey = SieveRuntime.currentRecipeKey(sieveBlock)
            if (activeRecipeKey != null) {
                val recipe = SIEVE_RECIPES.firstOrNull { it.key == activeRecipeKey } ?: continue
                active += WorkCandidate(sieveBlock, structure, recipe, null, InputSource.ACTIVE, null)
            } else {
                idle += sieveBlock to structure
            }
        }

        val handCount = if (handRecipe == null) 0 else player.inventory.itemInMainHand.amount.coerceAtMost(idle.size)
        val handCandidates = if (handRecipe != null && handCount > 0) {
            idle.take(handCount).map { (sieveBlock, structure) ->
                WorkCandidate(sieveBlock, structure, handRecipe, handSnapshot.clone(), InputSource.HAND, null)
            }
        } else {
            emptyList()
        }

        val dispenserCandidates = idle.drop(handCount).mapNotNull { (sieveBlock, structure) ->
            val input = findDispenserInput(structure.dispenser.inventory) ?: return@mapNotNull null
            WorkCandidate(sieveBlock, structure, input.recipe, input.inputSnapshot, InputSource.DISPENSER, input.slot)
        }

        return active + handCandidates + dispenserCandidates
    }

    private fun processSieve(player: Player, candidate: WorkCandidate): Boolean {
        val consumedInput = when (candidate.source) {
            InputSource.ACTIVE -> null
            InputSource.HAND -> {
                if (!consumeHandInput(player, candidate.recipe)) return false
                candidate.inputSnapshot
            }
            InputSource.DISPENSER -> {
                val slot = candidate.dispenserSlot ?: return false
                val liveInput = candidate.structure.dispenser.inventory.getItem(slot) ?: return false
                if (!matchesInput(liveInput, candidate.recipe.input)) return false
                consumeOne(candidate.structure.dispenser.inventory, slot, liveInput)
                candidate.inputSnapshot
            }
        }

        val result = SieveRuntime.advance(
            block = candidate.block,
            recipeKey = candidate.recipe.key,
            requiredProgress = candidate.recipe.effectiveRequiredProgress(),
            progressPerAction = candidate.structure.progressPerAction,
            visualData = candidate.recipe.visualBlockData,
            inputSnapshot = consumedInput,
            reinforced = candidate.structure.reinforced
        )
        when (result) {
            SieveAdvanceResult.Throttled,
            is SieveAdvanceResult.Progressed -> return true

            is SieveAdvanceResult.ReadyToComplete -> completeRecipe(
                player = player,
                sieveBlock = candidate.block,
                inputSnapshot = result.inputSnapshot,
                recipe = candidate.recipe
            )
        }
        return true
    }

    /**
     * 完成阶段的事务提交。
     *
     * 产物先在内存中生成并逐项经过事件链；事件执行期间不扣材料、不投递产物。事件全部通过后，
     * 再从当前方块状态重新取得发射器和槽位，防止监听器或其他同步逻辑替换输入后错误结算。
     */
    private fun completeRecipe(
        player: Player,
        sieveBlock: Block,
        inputSnapshot: ItemStack,
        recipe: SieveRecipe
    ) {
        val acceptedOutputs = ArrayList<ItemStack>()

        for (rolledOutput in recipe.roll()) {
            val event = MultiBlockCraftEvent(
                player,
                this,
                inputSnapshot.clone(),
                rolledOutput
            )
            Bukkit.getPluginManager().callEvent(event)

            if (event.isCancelled) {
                val currentStructure = findStructure(sieveBlock)
                if (currentStructure == null) {
                    SieveRuntime.clear(sieveBlock)
                    return
                }
                SieveRuntime.deferCompletion(
                    block = sieveBlock,
                    recipeKey = recipe.key,
                    requiredProgress = recipe.effectiveRequiredProgress(),
                    progressPerAction = currentStructure.progressPerAction,
                    visualData = recipe.visualBlockData,
                    reinforced = currentStructure.reinforced
                )
                return
            }

            acceptedOutputs += event.output.clone()
        }

        // 事件处理完成后重新读取实时方块与库存，避免提交到已经被替换或破坏的机器。
        val liveStructure = findStructure(sieveBlock)
        val liveDispenser = liveStructure?.dispenser
        if (liveDispenser == null || sieveBlock.type != Material.OAK_TRAPDOOR) {
            SieveRuntime.clear(sieveBlock)
            return
        }

        val inventory = liveDispenser.inventory
        acceptedOutputs.forEach { output ->
            handleCraftedItem(output, liveDispenser.block, inventory)
        }

        SieveRuntime.complete(sieveBlock, recipe.visualBlockData, liveStructure.reinforced)
    }

    /** 优先继续当前配方；当前输入已经被取走时再选择库存中的首个其他合法输入。 */
    private fun findHandRecipe(player: Player): SieveRecipe? {
        val hand = player.inventory.itemInMainHand
        if (hand.type.isAir || hand.amount <= 0) return null

        return SIEVE_RECIPES.firstOrNull { matchesInput(hand, it.input) }
    }

    private fun consumeHandInput(player: Player, recipe: SieveRecipe): Boolean {
        val hand = player.inventory.itemInMainHand
        if (hand.type.isAir || hand.amount <= 0) return false
        if (!matchesInput(hand, recipe.input)) return false

        if (hand.amount <= 1) {
            player.inventory.setItemInMainHand(null)
        } else {
            hand.amount -= 1
        }
        return true
    }

    private fun findDispenserInput(inventory: Inventory): DispenserInput? {

        for (slot in 0 until inventory.size) {
            val current = inventory.getItem(slot) ?: continue
            val recipe = SIEVE_RECIPES.firstOrNull { matchesInput(current, it.input) } ?: continue

            val input = current.clone().apply { amount = 1 }
            return DispenserInput(slot, recipe, input)
        }

        return null
    }

    private fun consumeOne(inventory: Inventory, slot: Int, input: ItemStack) {
        if (input.amount <= 1) {
            inventory.setItem(slot, null)
        } else {
            input.amount -= 1
        }
    }

    private fun matchesInput(candidate: ItemStack, expected: ItemStack): Boolean {
        if (candidate.type.isAir || candidate.amount <= 0) return false
        if (!expected.hasItemMeta()) return candidate.type == expected.type
        return SlimefunUtils.isItemSimilar(candidate, expected, true)
    }

    private data class WorkCandidate(
        val block: Block,
        val structure: SieveStructure,
        val recipe: SieveRecipe,
        val inputSnapshot: ItemStack?,
        val source: InputSource,
        val dispenserSlot: Int?
    )

    private enum class InputSource {
        ACTIVE,
        HAND,
        DISPENSER
    }

    private data class DispenserInput(
        val slot: Int,
        val recipe: SieveRecipe,
        val inputSnapshot: ItemStack
    )

    private data class SieveStructure(
        val dispenser: Dispenser,
        val progressPerAction: Double,
        val reinforced: Boolean
    ) {
        companion object {
            fun basic(block: Block): SieveStructure {
                val dispenser = block.getRelative(BlockFace.DOWN).state as Dispenser
                return SieveStructure(dispenser, 1.0, false)
            }
        }
    }

    private fun findStructure(block: Block): SieveStructure? {
        if (block.type != Material.OAK_TRAPDOOR) return null

        val below = block.getRelative(BlockFace.DOWN)
        if (below.type == Material.DISPENSER) {
            return SieveStructure(below.state as Dispenser, 1.0, false)
        }

        val twoBelow = block.getRelative(BlockFace.DOWN, 2)
        if (below.type == Material.SCAFFOLDING && twoBelow.type == Material.DISPENSER) {
            return SieveStructure(twoBelow.state as Dispenser, SEConfig.sieveScaffoldingSpeedMultiplier, true)
        }

        return null
    }

    private fun findLinkedSieves(origin: Block, originStructure: SieveStructure): List<Block> {
        val maxLinked = SEConfig.sieveMaxLinkedSieves
        val result = ArrayList<Block>(maxLinked)
        val visited = HashSet<String>()
        val queue = ArrayDeque<Block>()

        fun key(block: Block): String = "${block.x}:${block.y}:${block.z}"

        visited += key(origin)
        queue.add(origin)

        while (queue.isNotEmpty() && result.size < maxLinked) {
            val current = queue.removeFirst()
            val currentStructure = findStructure(current)
            if (currentStructure == null || currentStructure.reinforced != originStructure.reinforced) continue

            result += current

            for (face in LINK_FACES) {
                val next = current.getRelative(face)
                if (next.y != origin.y) continue
                val nextKey = key(next)
                if (!visited.add(nextKey)) continue
                if (findStructure(next)?.reinforced == originStructure.reinforced) {
                    queue.add(next)
                }
            }
        }

        return result
    }

    /** 一个独立的百分比掉落事件；概率从 `sieve.chances` 动态读取。 */
    private data class ChanceDrop(
        val inputKey: String,
        val outputKey: String,
        val defaultPercent: Int,
        val output: ItemStack
    ) {
        val percent: Int
            get() = SEConfig.sieveChance(inputKey, outputKey, defaultPercent)
    }

    /** 单种筛分输入，以及其展示方块、所需操作数、保底产物和逐项独立概率产物。 */
    private data class SieveRecipe(
        val key: String,
        val input: ItemStack,
        val visualMaterial: Material,
        val defaultRequiredProgress: Int,
        val guaranteed: List<ItemStack> = emptyList(),
        val chanceDrops: List<ChanceDrop>
    ) {
        val requiredProgress: Int
            get() = SEConfig.sieveRequiredProgress(key, defaultRequiredProgress)

        fun effectiveRequiredProgress(): Double =
            (requiredProgress * SEConfig.sieveActionMultiplier).coerceAtLeast(0.01)

        val visualBlockData: BlockData
            get() = visualMaterial.createBlockData()

        val displayOutputs: List<ItemStack>
            get() = guaranteed + chanceDrops.map(ChanceDrop::output)

        /** 克隆保底物品，并为每个概率条目分别生成一次 0..99 随机数。 */
        fun roll(): List<ItemStack> = buildList {
            guaranteed.forEach { add(it.clone()) }
            chanceDrops.forEach { drop ->
                if (Random.nextInt(100) < drop.percent) add(drop.output.clone())
            }
        }
    }

    companion object {
        private val LINK_FACES = arrayOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)

        /**
         * 固定筛矿表。
         *
         * “破碎/粉碎”阶段的铁、金、铜使用原版原矿簇，尘土阶段使用 Slimefun 矿粉；
         * 原版没有矿簇的锡、铝在所有阶段均使用 Slimefun 矿粉。自定义粉碎材料通过最接近的原版
         * 方块材质生成 BlockDisplay，避免非方块型 Slimefun 物品无法用于 BlockDisplay。
         */
        private val SIEVE_RECIPES = listOf(
            SieveRecipe(
                key = "dirt",
                input = ItemStack(Material.DIRT),
                visualMaterial = Material.DIRT,
                defaultRequiredProgress = 4,
                guaranteed = listOf(item(SlimefunItems.STONE_CHUNK, 2)),
                chanceDrops = chances(
                    chance("dirt", "wheat-seeds", 7, Material.WHEAT_SEEDS),
                    chance("dirt", "short-grass", 7, Material.SHORT_GRASS),
                    chance("dirt", "melon-seeds", 3, Material.MELON_SEEDS),
                    chance("dirt", "pumpkin-seeds", 3, Material.PUMPKIN_SEEDS),
                    chance("dirt", "sugar-cane", 3, Material.SUGAR_CANE),
                    chance("dirt", "carrot", 2, Material.CARROT),
                    chance("dirt", "potato", 2, Material.POTATO),
                    chance("dirt", "oak-sapling", 2, Material.OAK_SAPLING),
                    chance("dirt", "acacia-sapling", 1, Material.ACACIA_SAPLING),
                    chance("dirt", "spruce-sapling", 1, Material.SPRUCE_SAPLING),
                    chance("dirt", "birch-sapling", 1, Material.BIRCH_SAPLING),
                    chance("dirt", "bamboo", 2, Material.BAMBOO)
                )
            ),
            SieveRecipe(
                key = "grass-block",
                input = ItemStack(Material.GRASS_BLOCK),
                visualMaterial = Material.GRASS_BLOCK,
                defaultRequiredProgress = 6,
                guaranteed = listOf(item(SlimefunItems.STONE_CHUNK, 2)),
                chanceDrops = chances(
                    chance("grass-block", "wheat-seeds", 14, Material.WHEAT_SEEDS),
                    chance("grass-block", "short-grass", 14, Material.SHORT_GRASS),
                    chance("grass-block", "melon-seeds", 6, Material.MELON_SEEDS),
                    chance("grass-block", "pumpkin-seeds", 6, Material.PUMPKIN_SEEDS),
                    chance("grass-block", "sugar-cane", 6, Material.SUGAR_CANE),
                    chance("grass-block", "carrot", 4, Material.CARROT),
                    chance("grass-block", "potato", 4, Material.POTATO),
                    chance("grass-block", "oak-sapling", 4, Material.OAK_SAPLING),
                    chance("grass-block", "acacia-sapling", 2, Material.ACACIA_SAPLING),
                    chance("grass-block", "spruce-sapling", 2, Material.SPRUCE_SAPLING),
                    chance("grass-block", "birch-sapling", 2, Material.BIRCH_SAPLING),
                    chance("grass-block", "bamboo", 4, Material.BAMBOO)
                )
            ),
            SieveRecipe(
                key = "gravel",
                input = ItemStack(Material.GRAVEL),
                visualMaterial = Material.GRAVEL,
                defaultRequiredProgress = 8,
                chanceDrops = chances(
                    chance("gravel", "flint", 25, Material.FLINT),
                    chance("gravel", "coal", 13, Material.COAL),
                    chance("gravel", "lapis-lazuli", 5, Material.LAPIS_LAZULI),
                    chance("gravel", "diamond", 1, Material.DIAMOND),
                    chance("gravel", "emerald", 1, Material.EMERALD),
                    chance("gravel", "raw-iron", 20, Material.RAW_IRON),
                    chance("gravel", "raw-gold", 3, Material.RAW_GOLD),
                    chance("gravel", "raw-copper", 6, Material.RAW_COPPER),
                    chance("gravel", "tin-dust", 6, SlimefunItems.TIN_DUST),
                    chance("gravel", "aluminum-dust", 13, SlimefunItems.ALUMINUM_DUST)
                )
            ),
            SieveRecipe(
                key = "dust",
                input = Items.SIEVE_DUST.clone(),
                visualMaterial = Material.WHITE_CONCRETE_POWDER,
                defaultRequiredProgress = 8,
                chanceDrops = chances(
                    chance("dust", "bone-meal", 20, Material.BONE_MEAL),
                    chance("dust", "redstone", 13, Material.REDSTONE),
                    chance("dust", "gunpowder", 7, Material.GUNPOWDER),
                    chance("dust", "glowstone-dust", 6, Material.GLOWSTONE_DUST),
                    chance("dust", "blaze-powder", 5, Material.BLAZE_POWDER),
                    chance("dust", "iron-dust", 20, SlimefunItems.IRON_DUST),
                    chance("dust", "gold-dust", 3, SlimefunItems.GOLD_DUST),
                    chance("dust", "copper-dust", 6, SlimefunItems.COPPER_DUST),
                    chance("dust", "tin-dust", 7, SlimefunItems.TIN_DUST),
                    chance("dust", "aluminum-dust", 13, SlimefunItems.ALUMINUM_DUST)
                )
            ),
            SieveRecipe(
                key = "crushed-netherrack",
                input = Items.CRUSHED_NETHERRACK.clone(),
                visualMaterial = Material.NETHERRACK,
                defaultRequiredProgress = 12,
                chanceDrops = chances(
                    chance("crushed-netherrack", "raw-iron", 17, Material.RAW_IRON),
                    chance("crushed-netherrack", "raw-gold", 17, Material.RAW_GOLD),
                    chance("crushed-netherrack", "raw-copper", 10, Material.RAW_COPPER)
                )
            ),
            SieveRecipe(
                key = "soul-sand",
                input = ItemStack(Material.SOUL_SAND),
                visualMaterial = Material.SOUL_SAND,
                defaultRequiredProgress = 7,
                guaranteed = listOf(ItemStack(Material.QUARTZ)),
                chanceDrops = chances(
                    chance("soul-sand", "nether-wart", 5, Material.NETHER_WART),
                    chance("soul-sand", "ghast-tear", 2, Material.GHAST_TEAR),
                    chance("soul-sand", "extra-quartz", 33, Material.QUARTZ)
                )
            ),
            SieveRecipe(
                key = "soul-soil",
                input = ItemStack(Material.SOUL_SOIL),
                visualMaterial = Material.SOUL_SOIL,
                defaultRequiredProgress = 11,
                guaranteed = listOf(ItemStack(Material.QUARTZ)),
                chanceDrops = chances(
                    chance("soul-soil", "nether-wart", 5, Material.NETHER_WART),
                    chance("soul-soil", "ghast-tear", 2, Material.GHAST_TEAR),
                    chance("soul-soil", "extra-quartz", 33, Material.QUARTZ)
                )
            ),
            SieveRecipe(
                key = "sand",
                input = ItemStack(Material.SAND),
                visualMaterial = Material.SAND,
                defaultRequiredProgress = 6,
                chanceDrops = chances(
                    chance("sand", "cocoa-beans", 3, Material.COCOA_BEANS),
                    chance("sand", "cactus", 3, Material.CACTUS),
                    chance("sand", "jungle-sapling", 2, Material.JUNGLE_SAPLING),
                    chance("sand", "brown-mushroom", 1, Material.BROWN_MUSHROOM),
                    chance("sand", "raw-iron", 20, Material.RAW_IRON),
                    chance("sand", "raw-gold", 3, Material.RAW_GOLD),
                    chance("sand", "raw-copper", 6, Material.RAW_COPPER),
                    chance("sand", "tin-dust", 6, SlimefunItems.TIN_DUST),
                    chance("sand", "aluminum-dust", 13, SlimefunItems.ALUMINUM_DUST)
                )
            ),
            SieveRecipe(
                key = "crushed-end-stone",
                input = Items.CRUSHED_END_STONE.clone(),
                visualMaterial = Material.END_STONE,
                defaultRequiredProgress = 20,
                chanceDrops = chances(
                    chance("crushed-end-stone", "tin-dust", 10, SlimefunItems.TIN_DUST),
                    chance("crushed-end-stone", "ender-pearl", 5, Material.ENDER_PEARL),
                    chance("crushed-end-stone", "chorus-fruit", 8, Material.CHORUS_FRUIT),
                    chance("crushed-end-stone", "popped-chorus-fruit", 4, Material.POPPED_CHORUS_FRUIT),
                    chance("crushed-end-stone", "shulker-shell", 1, Material.SHULKER_SHELL)
                )
            ),
            SieveRecipe(
                key = "crushed-blackstone",
                input = Items.CRUSHED_BLACKSTONE.clone(),
                visualMaterial = Material.BLACKSTONE,
                defaultRequiredProgress = 16,
                chanceDrops = chances(
                    chance("crushed-blackstone", "basalt", 20, Material.BASALT),
                    chance("crushed-blackstone", "quartz", 15, Material.QUARTZ),
                    chance("crushed-blackstone", "gold-nugget", 12, Material.GOLD_NUGGET),
                    chance("crushed-blackstone", "magma-cream", 5, Material.MAGMA_CREAM),
                    chance("crushed-blackstone", "glowstone-dust", 6, Material.GLOWSTONE_DUST),
                    chance("crushed-blackstone", "blaze-powder", 4, Material.BLAZE_POWDER),
                    chance("crushed-blackstone", "crying-obsidian", 2, Material.CRYING_OBSIDIAN),
                    chance("crushed-blackstone", "ancient-debris", 1, Material.ANCIENT_DEBRIS)
                )
            )
        )

        /** 把逐项声明的概率事件转换为不可变列表。 */
        private fun chances(vararg drops: ChanceDrop): List<ChanceDrop> = drops.toList()

        /** 以原版材质构造一项可配置的独立掉落事件。 */
        private fun chance(input: String, output: String, default: Int, material: Material): ChanceDrop =
            chance(input, output, default, ItemStack(material))

        /** 以原版或 Slimefun 物品模板构造一项可配置的独立掉落事件。 */
        private fun chance(input: String, output: String, default: Int, source: ItemStack): ChanceDrop =
            ChanceDrop(input, output, default, item(source))

        /** 克隆物品模板并设置数量，避免修改 Slimefun 或原版共享模板。 */
        private fun item(source: ItemStack, amount: Int = 1): ItemStack =
            source.clone().apply { this.amount = amount }
    }
}
