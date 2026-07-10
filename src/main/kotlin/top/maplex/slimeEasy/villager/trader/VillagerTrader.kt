package top.maplex.slimeEasy.villager.trader

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import org.bukkit.block.Block
import org.bukkit.entity.Villager
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.machine.common.MachineProtection
import top.maplex.slimeEasy.villager.catcher.VillagerCatcher
import top.maplex.slimeEasy.villager.core.VillagerConfig
import top.maplex.slimeEasy.villager.core.VillagerData
import top.maplex.slimeEasy.villager.core.VillagerDisplay
import top.maplex.slimeEasy.villager.core.WorkstationMap

/**
 * 村民交易器。
 *
 * 外观为透明玻璃 (GLASS), 内嵌一只缩小的展示村民 (真实生物实体)。放入村民后玩家可通过
 * 虚拟商人与之交易, 无需操心保护实体村民 (交易器交互见 [TraderListener])。
 *
 * 随 Slimefun 原生 ticker 运行, 仅做两件事: 保障展示实体存在 / 与装配村民职业一致; 按配置间隔
 * 补货 —— 当装配村民职业与工作站方块匹配, 且距上次补货超过配置秒数时, 把所有交易 uses 重置为 0。
 * 补货以墙钟时间戳判定, 不受时间 / 范围等外部因素影响。
 */
class VillagerTrader(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    override fun preRegister() {
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) {
                MachineProtection.recordOwner(e.block, e.player)
            }
        })
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                onBreak(e.block, drops)
            }
        })
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) = onTick(b)
            // 涉及实体生成与 BlockData 读写, 必须主线程
            override fun isSynchronized(): Boolean = true
        })
    }

    /** 破坏: 装配的村民作满捕捉器掉落、工作站方块原样掉落, 并清理展示实体。 */
    private fun onBreak(block: Block, drops: MutableList<ItemStack>) {
        TraderStore.getVillager(block)?.let { drops.add(VillagerCatcher.fill(it)) }
        TraderStore.getWorkstation(block)?.let { drops.add(ItemStack(it)) }
        removeDisplay(block)
    }

    /** 单个交易器一次 tick: 维护展示实体 + 补货判定。 */
    private fun onTick(block: Block) {
        val data = TraderStore.getVillager(block)
        if (data == null) {
            removeDisplay(block)
            return
        }
        ensureDisplay(block, data)
        restock(block, data)
    }

    /** 补货: 职业与工作站匹配且超过配置间隔时, 把所有交易 uses 归零。 */
    private fun restock(block: Block, data: VillagerData) {
        if (!WorkstationMap.matches(data.professionKey, TraderStore.getWorkstation(block))) return
        val now = System.currentTimeMillis()
        if (now - TraderStore.getLastRestock(block) < VillagerConfig.traderRestockMillis) return
        if (data.recipes.isNotEmpty()) {
            data.recipes.forEach { it.uses = 0 }
            TraderStore.setVillager(block, data)
        }
        TraderStore.setLastRestock(block, now)
    }

    companion object {

        /** 展示实体在 BlockData 中的键。 */
        const val DISPLAY_KEY = "se_trader_disp"

        /** 展示村民缩放比例 (身高约 0.98 格, 恰好嵌入单方块内)。 */
        private const val DISPLAY_SCALE = 0.5

        /** 若展示实体缺失 / 失效则按装配村民外观重建。 */
        fun ensureDisplay(block: Block, data: VillagerData) {
            val entity = VillagerDisplay.get(block, DISPLAY_KEY)
            if (entity == null || entity !is Villager || entity.isDead) spawnDisplay(block, data)
        }

        /** 按装配村民职业 / 类型 / 等级生成缩小的展示村民。 */
        fun spawnDisplay(block: Block, data: VillagerData) {
            VillagerDisplay.spawn(block, DISPLAY_KEY, Villager::class.java, DISPLAY_SCALE) { v ->
                data.applyAppearance(v)
            }
        }

        /** 移除展示实体。 */
        fun removeDisplay(block: Block) = VillagerDisplay.remove(block, DISPLAY_KEY)
    }
}
