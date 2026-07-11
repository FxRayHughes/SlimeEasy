package top.maplex.slimeEasy.machine.quarry

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import org.bukkit.block.Block
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.util.locationKey
import java.util.concurrent.ConcurrentHashMap

/**
 * 采石场 (观察者方块)。
 *
 * 附着 (脸朝向) 的圆石同时相邻岩浆与水时, 持续产出当前选择的材料 (默认圆石), 推送到
 * 本机周围的容器 / 抽屉 / 翻页箱。速率由升级槽内的效率组件决定 (见 [QuarryTier]):
 * - **无升级**: 每 [BASE_INTERVAL] 个 tick 产 [BASE_PER_OPERATION] 个 (≈ 1 个/秒);
 * - **各档**: 每 tick (≈0.5 秒) 产该档 `perOperation` 个。
 *
 * 判定与产出委托 [QuarryLogic]; 升级界面由 [QuarryMenuPreset] 提供。
 */
class Quarry(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /**
     * 每台机器的基础节流计数器 (键为方块位置字符串)。
     *
     * 仅"无升级"档需要按配置间隔计数; 内存态即可 —— 重启后从 0 重数无副作用。
     */
    private val counters = ConcurrentHashMap<String, Int>()

    override fun preRegister() {
        // 注册升级界面预设 (构造即自注册; 右键由 Slimefun 自动打开)
        QuarryMenuPreset(id, itemName)
        // 破坏: 散落升级槽内物品, 清理计数
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                spillUpgrade(e.block, drops)
                counters.remove(e.block.locationKey())
            }
        })
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) = onTick(b)

            // 涉及方块数据读取与容器写入, 必须主线程
            override fun isSynchronized(): Boolean = true
        })
    }

    /** 单台采石场的一次 tick。 */
    private fun onTick(quarry: Block) {
        val menu = StorageCacheUtils.getMenu(quarry.location) ?: return
        val tier = QuarryMenuPreset.tierOf(menu)
        val output = QuarryMenuPreset.outputOf(menu)
        val cobble = QuarryLogic.attachedCobblestone(quarry)
        val producing = cobble != null && QuarryLogic.isProducing(cobble)
        QuarryMenuPreset.updateInfo(menu, attached = cobble != null, producing = producing, tier = tier, output = output)

        val key = quarry.locationKey()
        if (!producing) {
            counters.remove(key)
            return
        }

        val amount = if (tier != null) {
            tier.perOperation // 有升级: 每 tick 产出
        } else {
            // 无升级: 每 BASE_INTERVAL 个 tick 产 BASE_PER_OPERATION 个 (≈ 1 个/秒)
            val count = counters.getOrDefault(key, 0) + 1
            if (count < BASE_INTERVAL) {
                counters[key] = count
                return
            }
            counters[key] = 0
            BASE_PER_OPERATION
        }
        QuarryLogic.output(quarry, output.material, amount)
    }

    /** 破坏时把升级槽内物品加入掉落列表并清空该槽。 */
    private fun spillUpgrade(quarry: Block, drops: MutableList<ItemStack>) {
        val menu = StorageCacheUtils.getMenu(quarry.location) ?: return
        for (slot in QuarryMenuPreset.FUNCTIONAL_SLOTS) {
            val item = menu.getItemInSlot(slot) ?: continue
            if (item.type.isAir) continue
            drops.add(item.clone())
            menu.replaceExistingItem(slot, null)
        }
    }

    private companion object {
        /** 无升级档的产出节流 (Slimefun tick 数)。 */
        val BASE_INTERVAL: Int get() = SEConfig.quarryBaseIntervalTicks

        /** 无升级档每次产出数量。 */
        val BASE_PER_OPERATION: Int get() = SEConfig.quarryBaseOutput
    }
}
