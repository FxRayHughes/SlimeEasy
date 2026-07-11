package top.maplex.slimeEasy.villager.healer

import top.maplex.slimeEasy.config.I18n
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
import org.bukkit.block.Block
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.villager.catcher.VillagerCatcher
import top.maplex.slimeEasy.villager.core.VillagerConfig
import top.maplex.slimeEasy.villager.core.VillagerData

/**
 * 村民治愈机。
 *
 * 界面由原生 [HealerMenuPreset] 提供 (右键打开)。随 Slimefun ticker 运行: 当输入槽为僵尸村民
 * (满捕捉器)、金苹果槽有普通金苹果且输出槽空闲时开始计时, 到点后消耗一个金苹果, 把僵尸村民
 * 治愈为普通村民 (保留职业), 结果落入输出槽。
 *
 * 计时以墙钟时间戳判定; 输入变动 / 缺金苹果 / 输出被占用时计时清零重来。
 */
class VillagerHealer(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /** 治愈起始时间戳键。 */
    private val keyStart = "se_healer_start"

    override fun preRegister() {
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) {}
        })
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                spillMenu(e.block, drops)
            }
        })
        HealerMenuPreset(id, itemName)
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) = onTick(b)
            override fun isSynchronized(): Boolean = true
        })
    }

    private fun onTick(block: Block) {
        val menu = StorageCacheUtils.getMenu(block.location) ?: return
        val input = menu.getItemInSlot(HealerMenuPreset.INPUT_SLOT)
        val data = VillagerCatcher.dataOf(input)
        val hasApple = HealerMenuPreset.hasApple(menu)
        val outputOccupied = menu.getItemInSlot(HealerMenuPreset.OUTPUT_SLOT)?.let { !it.type.isAir } ?: false

        if (data == null || !data.zombie || !hasApple || outputOccupied) {
            setStart(block, 0L)
            HealerMenuPreset.updateInfo(menu, idleReason(data, hasApple, outputOccupied))
            return
        }

        val now = System.currentTimeMillis()
        var start = getStart(block)
        if (start <= 0L) {
            start = now
            setStart(block, now)
        }
        val remaining = VillagerConfig.healerConvertMillis - (now - start)
        if (remaining > 0) {
            HealerMenuPreset.updateInfo(menu, I18n.text("messages.villager-healer.progress", "seconds" to (remaining / 1000) + 1))
            return
        }

        // 到点治愈: 消耗 1 金苹果, 僵尸村民转普通村民, 结果入输出, 输入 -1
        consumeApple(menu)
        menu.replaceExistingItem(HealerMenuPreset.OUTPUT_SLOT, VillagerCatcher.fill(data.cured()))
        consumeOneInput(menu, input!!)
        setStart(block, 0L)
        HealerMenuPreset.updateInfo(menu, I18n.text("messages.villager-healer.completed"))
    }

    /** 金苹果槽 -1 (堆叠则减一, 否则清空)。 */
    private fun consumeApple(menu: BlockMenu) {
        val apple = menu.getItemInSlot(HealerMenuPreset.APPLE_SLOT) ?: return
        if (apple.amount <= 1) menu.replaceExistingItem(HealerMenuPreset.APPLE_SLOT, null)
        else menu.replaceExistingItem(HealerMenuPreset.APPLE_SLOT, apple.apply { amount -= 1 })
    }

    /** 输入槽 -1 (堆叠则减一, 否则清空)。 */
    private fun consumeOneInput(menu: BlockMenu, input: ItemStack) {
        if (input.amount <= 1) menu.replaceExistingItem(HealerMenuPreset.INPUT_SLOT, null)
        else menu.replaceExistingItem(HealerMenuPreset.INPUT_SLOT, input.apply { amount -= 1 })
    }

    private fun spillMenu(block: Block, drops: MutableList<ItemStack>) {
        val menu = StorageCacheUtils.getMenu(block.location) ?: return
        for (slot in HealerMenuPreset.FUNCTIONAL_SLOTS) {
            menu.getItemInSlot(slot)?.let { if (!it.type.isAir) drops.add(it.clone()) }
        }
    }

    private fun idleReason(data: VillagerData?, hasApple: Boolean, outputOccupied: Boolean): String = when {
        data == null -> I18n.text("messages.villager-healer.waiting-input")
        !data.zombie -> I18n.text("messages.villager-healer.invalid-input")
        !hasApple -> I18n.text("messages.villager-healer.missing-apple")
        outputOccupied -> I18n.text("messages.villager-healer.output-blocked")
        else -> I18n.text("messages.villager-healer.idle")
    }

    private fun getStart(block: Block): Long =
        StorageCacheUtils.getData(block.location, keyStart)?.toLongOrNull() ?: 0L

    private fun setStart(block: Block, millis: Long) =
        StorageCacheUtils.setData(block.location, keyStart, millis.toString())
}
