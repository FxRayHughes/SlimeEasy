package top.maplex.slimeEasy.villager.school

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
 * 村民小学。
 *
 * 界面由原生 [SchoolMenuPreset] 提供 (右键打开)。随 Slimefun ticker 运行: 当输入槽为傻子村民
 * (满捕捉器) 且输出槽空闲时开始计时, 到点后把该村民职业改为无职业 (可再就业), 结果落入输出槽。
 *
 * 计时以墙钟时间戳判定; 输入变动 / 输出被占用时计时清零重来。
 */
class VillagerSchool(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /** 转化起始时间戳键。 */
    private val keyStart = "se_school_start"

    override fun preRegister() {
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) {}
        })
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                spillMenu(e.block, drops)
            }
        })
        SchoolMenuPreset(id, itemName)
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) = onTick(b)
            override fun isSynchronized(): Boolean = true
        })
    }

    private fun onTick(block: Block) {
        val menu = StorageCacheUtils.getMenu(block.location) ?: return
        val input = menu.getItemInSlot(SchoolMenuPreset.INPUT_SLOT)
        val data = VillagerCatcher.dataOf(input)
        val outputOccupied = menu.getItemInSlot(SchoolMenuPreset.OUTPUT_SLOT)?.let { !it.type.isAir } ?: false

        if (data == null || !data.isNitwit || outputOccupied) {
            setStart(block, 0L)
            SchoolMenuPreset.updateInfo(menu, idleReason(data, outputOccupied))
            return
        }

        val now = System.currentTimeMillis()
        var start = getStart(block)
        if (start <= 0L) {
            start = now
            setStart(block, now)
        }
        val remaining = VillagerConfig.schoolConvertMillis - (now - start)
        if (remaining > 0) {
            SchoolMenuPreset.updateInfo(menu, "转化中… 剩余 §f${(remaining / 1000) + 1}s")
            return
        }

        // 到点转化: 职业改无职业, 结果入输出, 输入 -1
        val jobless = data.withProfession(VillagerData.NONE)
        menu.replaceExistingItem(SchoolMenuPreset.OUTPUT_SLOT, VillagerCatcher.fill(jobless))
        consumeOneInput(menu, input!!)
        setStart(block, 0L)
        SchoolMenuPreset.updateInfo(menu, "§a转化完成, 已放入输出槽")
    }

    /** 输入槽 -1 (堆叠则减一, 否则清空)。 */
    private fun consumeOneInput(menu: BlockMenu, input: ItemStack) {
        if (input.amount <= 1) menu.replaceExistingItem(SchoolMenuPreset.INPUT_SLOT, null)
        else menu.replaceExistingItem(SchoolMenuPreset.INPUT_SLOT, input.apply { amount -= 1 })
    }

    private fun spillMenu(block: Block, drops: MutableList<ItemStack>) {
        val menu = StorageCacheUtils.getMenu(block.location) ?: return
        for (slot in SchoolMenuPreset.FUNCTIONAL_SLOTS) {
            menu.getItemInSlot(slot)?.let { if (!it.type.isAir) drops.add(it.clone()) }
        }
    }

    private fun idleReason(data: VillagerData?, outputOccupied: Boolean): String = when {
        data == null -> "§7待放入傻子村民"
        !data.isNitwit -> "§c仅接受傻子(呆滞)村民"
        outputOccupied -> "§c请先取走输出槽"
        else -> "§7待机"
    }

    private fun getStart(block: Block): Long =
        StorageCacheUtils.getData(block.location, keyStart)?.toLongOrNull() ?: 0L

    private fun setStart(block: Block, millis: Long) =
        StorageCacheUtils.setData(block.location, keyStart, millis.toString())
}
