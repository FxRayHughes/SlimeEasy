package top.maplex.slimeEasy.machine.sieve

import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Dispenser
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.registry.Items
import kotlin.random.Random

/**
 * 与 Slimefun 磨石操作方式一致的手动多方块筛子。
 *
 * 结构为“橡木活板门 + 下方朝上的发射器”。每次右键活板门即时处理发射器中的一份匹配原料，
 * 没有额外冷却或进度条，因此筛选速度与磨石相同。每个 [ChanceDrop] 都独立掷骰，一次筛选可
 * 同时命中多项产物；保底产物不参与概率抽取。
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
        null, ItemStack(Material.DISPENSER), null
    ),
    BlockFace.SELF
) {

    /**
     * 向 Slimefun 指南登记所有“输入 → 可能产物”组合。
     *
     * 这些配对仅用于配方展示；实际产量和概率统一由 [SieveRecipe.roll] 决定。
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
     * 处理一次玩家筛选：定位首个可筛输入、独立生成产物、派发合成事件，再消耗一份输入。
     *
     * 任一 [MultiBlockCraftEvent] 被取消时整次操作终止且不消耗原料。输出优先进入发射器或相邻
     * 物品输出箱，均无空间时沿用 [MultiBlockMachine] 的溢出掉落机制，避免静默吞物。
     */
    override fun onInteract(player: Player, block: Block) {
        val dispenser = block.getRelative(BlockFace.DOWN).getState(false) as? Dispenser ?: return
        val inventory = dispenser.inventory

        for (slot in 0 until inventory.size) {
            val current = inventory.getItem(slot) ?: continue
            val recipe = SIEVE_RECIPES.firstOrNull {
                SlimefunUtils.isItemSimilar(current, it.input, true)
            } ?: continue

            val outputs = recipe.roll().map { output ->
                val event = MultiBlockCraftEvent(player, this, singleItem(current), output)
                Bukkit.getPluginManager().callEvent(event)
                if (event.isCancelled) return
                event.output.clone()
            }

            if (current.amount == 1) {
                inventory.setItem(slot, null)
            } else {
                current.amount -= 1
            }

            for (output in outputs) {
                handleCraftedItem(output, dispenser.block, inventory)
            }
            SoundEffect.GRIND_STONE_INTERACT_SOUND.playAt(block)
            return
        }

        Slimefun.getLocalization().sendMessage(player, "machines.unknown-material", true)
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

    /** 单种筛分输入，以及不掷骰的保底产物和逐项独立计算的概率产物。 */
    private data class SieveRecipe(
        val input: ItemStack,
        val guaranteed: List<ItemStack> = emptyList(),
        val chanceDrops: List<ChanceDrop>
    ) {
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

        /**
         * 固定筛矿表。
         *
         * “破碎/粉碎”阶段的铁、金、铜使用原版原矿簇，尘土阶段使用 Slimefun 矿粉；
         * 原版没有矿簇的锡、铝在所有阶段均使用 Slimefun 矿粉。
         */
        private val SIEVE_RECIPES = listOf(
            SieveRecipe(
                input = ItemStack(Material.DIRT),
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
                    chance("dirt", "birch-sapling", 1, Material.BIRCH_SAPLING)
                )
            ),
            SieveRecipe(
                input = ItemStack(Material.GRAVEL),
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
                input = Items.SIEVE_DUST.clone(),
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
                input = Items.CRUSHED_NETHERRACK.clone(),
                chanceDrops = chances(
                    chance("crushed-netherrack", "raw-iron", 17, Material.RAW_IRON),
                    chance("crushed-netherrack", "raw-gold", 17, Material.RAW_GOLD),
                    chance("crushed-netherrack", "raw-copper", 10, Material.RAW_COPPER)
                )
            ),
            SieveRecipe(
                input = ItemStack(Material.SOUL_SAND),
                guaranteed = listOf(ItemStack(Material.QUARTZ)),
                chanceDrops = chances(
                    chance("soul-sand", "nether-wart", 5, Material.NETHER_WART),
                    chance("soul-sand", "ghast-tear", 2, Material.GHAST_TEAR),
                    chance("soul-sand", "extra-quartz", 33, Material.QUARTZ)
                )
            ),
            SieveRecipe(
                input = ItemStack(Material.SAND),
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
                input = Items.CRUSHED_END_STONE.clone(),
                chanceDrops = chances(
                    chance("crushed-end-stone", "tin-dust", 10, SlimefunItems.TIN_DUST),
                    chance("crushed-end-stone", "ender-pearl", 5, Material.ENDER_PEARL),
                    chance("crushed-end-stone", "chorus-fruit", 8, Material.CHORUS_FRUIT),
                    chance("crushed-end-stone", "popped-chorus-fruit", 4, Material.POPPED_CHORUS_FRUIT),
                    chance("crushed-end-stone", "shulker-shell", 1, Material.SHULKER_SHELL)
                )
            ),
            SieveRecipe(
                input = Items.CRUSHED_BLACKSTONE.clone(),
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

        /** 为合成事件构造恰好一份输入快照。 */
        private fun singleItem(source: ItemStack): ItemStack = item(source)
    }
}
