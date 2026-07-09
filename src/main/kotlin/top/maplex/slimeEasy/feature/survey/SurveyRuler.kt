package top.maplex.slimeEasy.feature.survey

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 一个采掘层级: 对应某型工业矿机的扫描配置。
 *
 * @param label 展示名称 (如 "工业矿机")
 * @param range 水平半径; 采掘区为 (2·range+1)²
 */
data class SurveyTier(val label: String, val range: Int)

/**
 * 矿物勘察尺。
 *
 * 右键地面时不锄地, 而是模拟工业矿机的采掘范围向下扫描, 列出可挖矿石及数量。
 * 按 [tiers] 参数化: 铜锄版仅 1 个层级 (普通工业矿机); 钻石锄版含 2 个层级
 * (进阶 + 普通), 用以区分两型矿机各自范围下的产出预估。
 *
 * 交互约定 (状态均持久化于物品 PDC, 见 [SurveyState]):
 * - **右键地面**: 按当前选中层级向下勘察, 输出到当前展示形式;
 * - **潜行 + 右键空气**: 循环切换选中层级 (仅多层级版有效);
 * - **潜行 + 左键**: 切换展示形式 (聊天栏 / 箱子界面), 由 [SurveyDisplayListener] 处理。
 *
 * 冷却使用原版物品冷却 ([Player.setCooldown]): 既防止密集扫描造成卡顿,
 * 又能在物品栏显示冷却遮罩, 无需插件侧另行维护冷却状态。
 */
class SurveyRuler(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>,
    private val tiers: List<SurveyTier>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    override fun preRegister() {
        addItemHandler(ItemUseHandler { e ->
            // 无论是否点到方块, 均取消原版交互 (锄地)
            e.cancel()
            val player = e.player
            val block = e.clickedBlock.orElse(null)
            // 潜行 + 右键空气 (未点到方块): 切换当前选中层级, 不执行勘察
            if (block == null) {
                if (player.isSneaking) switchTier(player, e.item)
                return@ItemUseHandler
            }
            handleUse(player, e.item, block)
        })
    }

    /**
     * 潜行右键空气: 循环切换选中层级并提示。
     *
     * 单层级无可切换目标, 静默忽略 (避免无意义刷屏)。
     */
    private fun switchTier(player: Player, item: ItemStack) {
        if (tiers.size <= 1) return
        val next = (SurveyState.readTierIndex(item) + 1) % tiers.size
        SurveyState.writeTierIndex(item, next)
        val tier = tiers[next]
        val side = tier.range * 2 + 1
        player.sendMessage("§6[勘察尺] §7已切换勘察范围: §b${tier.label} §7(${side}×${side})")
    }

    /** 处理一次勘察: 冷却校验 -> 按展示形式输出当前选中层级 -> 置入原版冷却。 */
    private fun handleUse(player: Player, item: ItemStack, block: Block) {
        // 原版冷却为唯一真相源: 处于冷却期直接忽略 (物品栏遮罩已给出可视提示)
        if (player.hasCooldown(item)) return

        val tier = tiers[SurveyState.readTierIndex(item).coerceIn(tiers.indices)]
        when (SurveyState.readDisplay(item)) {
            SurveyDisplay.GUI -> SurveyGui.open(player, block, tier)
            SurveyDisplay.CHAT -> reportToChat(player, block, tier)
        }

        // 置入冷却 (tick): 触发物品栏冷却遮罩可视化
        player.setCooldown(item, COOLDOWN_TICKS)
    }

    /** 聊天栏展示: 坐标抬头 + 单层级明细。 */
    private fun reportToChat(player: Player, block: Block, tier: SurveyTier) {
        player.sendMessage("§6[勘察尺] §7坐标 §f${block.x}, ${block.y}, ${block.z} §7下方矿脉预估:")
        val side = tier.range * 2 + 1
        val counts = SurveyScanner.scan(block, tier.range)
        player.sendMessage("§b— ${tier.label} §7(${side}×${side})")
        if (counts.isEmpty()) {
            player.sendMessage("  §8未探测到可开采矿石")
            return
        }
        // 按数量降序, 便于一眼看出主要矿脉
        counts.entries
            .sortedByDescending { it.value }
            .forEach { (material, count) ->
                player.sendMessage("  §7• §f${OreNames.of(material)} §7× §a$count")
            }
    }

    companion object {
        /** 勘察冷却 (tick); 5 秒 = 100 tick, 与物品说明一致。 */
        private const val COOLDOWN_TICKS = 100
    }
}
