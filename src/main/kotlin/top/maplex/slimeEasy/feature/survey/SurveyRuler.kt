package top.maplex.slimeEasy.feature.survey

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
 * 右键地面时不锄地, 而是模拟工业矿机的采掘范围向下扫描, 在聊天栏列出可挖矿石及数量。
 * 按 [tiers] 参数化: 铜锄版仅 1 个层级 (普通工业矿机); 钻石锄版含 2 个层级
 * (进阶 + 普通, 分区展示), 用以区分两型矿机各自范围下的产出预估。
 *
 * 附带 [COOLDOWN_MILLIS] 冷却, 防止密集扫描造成卡顿。
 */
class SurveyRuler(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>,
    private val tiers: List<SurveyTier>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /** 玩家 UUID -> 上次使用时间戳 (毫秒)。内存态, 重启清空无副作用。 */
    private val lastUse = ConcurrentHashMap<UUID, Long>()

    override fun preRegister() {
        addItemHandler(ItemUseHandler { e ->
            // 无论是否点到方块, 均取消原版交互 (锄地)
            e.cancel()
            val block = e.clickedBlock.orElse(null) ?: return@ItemUseHandler
            handleUse(e.player, block)
        })
    }

    /** 处理一次勘察: 冷却校验 -> 逐层级扫描 -> 聊天栏输出。 */
    private fun handleUse(player: Player, block: Block) {
        val now = System.currentTimeMillis()
        val last = lastUse[player.uniqueId] ?: 0L
        val remain = COOLDOWN_MILLIS - (now - last)
        if (remain > 0) {
            player.sendMessage("§c[勘察尺] 冷却中, 还需 §e${"%.1f".format(remain / 1000.0)}s")
            return
        }
        lastUse[player.uniqueId] = now

        player.sendMessage("§6[勘察尺] §7坐标 §f${block.x}, ${block.y}, ${block.z} §7下方矿脉预估:")
        for (tier in tiers) {
            reportTier(player, block, tier)
        }
    }

    /** 扫描并输出单个层级的结果。 */
    private fun reportTier(player: Player, block: Block, tier: SurveyTier) {
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
        /** 勘察冷却 (毫秒)。 */
        private const val COOLDOWN_MILLIS = 5_000L
    }
}
