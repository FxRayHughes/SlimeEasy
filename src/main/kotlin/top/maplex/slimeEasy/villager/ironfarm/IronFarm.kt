package top.maplex.slimeEasy.villager.ironfarm

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.machine.common.MachineProtection
import top.maplex.slimeEasy.villager.catcher.VillagerCatcher
import top.maplex.slimeEasy.villager.core.VillagerConfig

/**
 * 胶囊刷铁机。
 *
 * 外观为透明玻璃, 内嵌村民 + 僵尸展示 ([IronFarmDisplay]), 产铁瞬间闪现铁傀儡。界面由原生
 * [IronFarmMenuPreset] 提供 (右键打开)。随 Slimefun ticker 运行: 当村民 (满捕捉器) + 僵尸信号 +
 * 食物三者齐全时, 按配置间隔 (可被速度升级缩短) 向输出区产出铁锭并消耗少量食物。
 *
 * 产出节奏以墙钟时间戳判定, 不受 tick 频率 / 重载影响。
 */
class IronFarm(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /** 产出时间戳键。 */
    private val keyLast = "se_iron_last"

    override fun preRegister() {
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) {
                MachineProtection.recordOwner(e.block, e.player)
            }
        })
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                spillMenu(e.block, drops)
                IronFarmDisplay.removeAll(e.block)
            }
        })
        IronFarmMenuPreset(id, itemName)
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) = onTick(b)
            override fun isSynchronized(): Boolean = true
        })
    }

    private fun onTick(block: Block) {
        val menu = StorageCacheUtils.getMenu(block.location) ?: return
        val villagerItem = IronFarmMenuPreset.villagerItem(menu)
        val data = VillagerCatcher.dataOf(villagerItem)
        IronFarmDisplay.ensure(block, data)

        val hasSignal = IronFarmMenuPreset.hasSignal(menu)
        val food = IronFarmMenuPreset.foodItem(menu)
        if (data == null || !hasSignal || food == null) {
            IronFarmMenuPreset.updateInfo(menu, false, missReason(data != null, hasSignal, food != null))
            return
        }

        val speed = IronFarmMenuPreset.speedLevel(menu)
        val interval = (VillagerConfig.ironProduceMillis / (1.0 + speed * VillagerConfig.ironSpeedStep)).toLong().coerceAtLeast(1L)
        val now = System.currentTimeMillis()
        if (now - getLast(block) < interval) {
            IronFarmMenuPreset.updateInfo(menu, true, "产铁中…")
            return
        }

        val produced = pushIron(menu)
        if (produced == 0) {
            IronFarmMenuPreset.updateInfo(menu, true, "§c输出已满")
            return
        }
        consumeFood(menu, VillagerConfig.ironFoodPerCycle)
        setLast(block, now)
        IronFarmDisplay.flashGolem(block)
        IronFarmMenuPreset.updateInfo(menu, true, "已产出 §f${produced}§7 铁锭")
    }

    /** 逐个把铁锭推入输出区; 返回实际产出数量 (输出满则提前停止)。 */
    private fun pushIron(menu: BlockMenu): Int {
        var produced = 0
        while (produced < VillagerConfig.ironPerCycle) {
            val leftover = menu.pushItem(ItemStack(Material.IRON_INGOT, 1), *IronFarmMenuPreset.OUTPUT_SLOTS)
            if (leftover != null) break // 输出已满
            produced++
        }
        return produced
    }

    /** 消耗食物槽 [count] 个食物 (不足则清空)。 */
    private fun consumeFood(menu: BlockMenu, count: Int) {
        val food = menu.getItemInSlot(IronFarmMenuPreset.FOOD_SLOT) ?: return
        val remain = food.amount - count.coerceAtLeast(0)
        menu.replaceExistingItem(IronFarmMenuPreset.FOOD_SLOT, if (remain > 0) food.apply { amount = remain } else null)
    }

    /** 破坏时散落全部功能槽物品。 */
    private fun spillMenu(block: Block, drops: MutableList<ItemStack>) {
        val menu = StorageCacheUtils.getMenu(block.location) ?: return
        for (slot in IronFarmMenuPreset.FUNCTIONAL_SLOTS) {
            menu.getItemInSlot(slot)?.let { if (!it.type.isAir) drops.add(it.clone()) }
        }
    }

    private fun missReason(villager: Boolean, signal: Boolean, food: Boolean): String = when {
        !villager -> "§c缺少村民 (满捕捉器)"
        !signal -> "§c缺少僵尸信号"
        !food -> "§c缺少食物"
        else -> ""
    }

    private fun getLast(block: Block): Long =
        StorageCacheUtils.getData(block.location, keyLast)?.toLongOrNull() ?: 0L

    private fun setLast(block: Block, millis: Long) =
        StorageCacheUtils.setData(block.location, keyLast, millis.toString())
}
