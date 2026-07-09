package top.maplex.slimeEasy.storage.network

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack

/**
 * 网络输入 / 输出端口。
 *
 * 提供一个**隐藏**单槽缓冲菜单供 Slimefun 货运接入 (玩家不可打开):
 * - 输入端口 ([isInput] = true): 货运把物品塞入缓冲槽, 由控制器 ticker 分发进网络;
 * - 输出端口: 控制器 ticker 从网络取出物品填入缓冲槽, 供货运抽走。
 *
 * 端口自身不存储、不遍历网络; 路由集中由 [NetworkController] 的 ticker 驱动,
 * 端口只是网络对接原版物流的"闸口"。放置 / 破坏使网络缓存失效。
 */
class NetworkPort(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>,
    val isInput: Boolean
) : SlimefunItem(itemGroup, item, recipeType, recipe), InventoryBlock {

    override fun getInputSlots(): IntArray = if (isInput) intArrayOf(SLOT) else IntArray(0)
    override fun getOutputSlots(): IntArray = if (isInput) IntArray(0) else intArrayOf(SLOT)

    override fun preRegister() {
        PortPreset()
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) = NetworkRegistry.invalidateAll()
        })
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) =
                NetworkRegistry.invalidateAll()
        })
    }

    private inner class PortPreset : BlockMenuPreset(id, itemName) {
        override fun init() {}
        override fun canOpen(block: Block, player: Player): Boolean = false
        override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray = when {
            isInput && flow == ItemTransportFlow.INSERT -> intArrayOf(SLOT)
            !isInput && flow == ItemTransportFlow.WITHDRAW -> intArrayOf(SLOT)
            else -> IntArray(0)
        }
    }

    companion object {
        /** 缓冲槽位。 */
        const val SLOT = 0
    }
}
