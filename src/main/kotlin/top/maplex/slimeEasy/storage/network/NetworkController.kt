package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import org.bukkit.block.Block
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.ItemKey

/**
 * 存储网络控制器 (网络大脑与访问入口)。
 *
 * 职责:
 * - **访问**: 右键打开跨全网成员的聚合分页 GUI ([NetworkMenu]);
 * - **路由**: 每 tick 服务网络内的输入 / 输出端口 —— 把输入端口缓冲的物品分发
 *   进网络, 从网络取物填充输出端口缓冲, 从而桥接原版货运;
 * - **拓扑**: 放置 / 破坏时使 [NetworkRegistry] 失效。
 *
 * 网络拓扑由 [NetworkScan] BFS 得出并缓存, tick 直接取缓存, 开销可控。
 */
class NetworkController(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    override fun preRegister() {
        addItemHandler(BlockUseHandler { e ->
            val block = e.clickedBlock.orElse(null) ?: return@BlockUseHandler
            e.cancel()
            NetworkMenu.open(NetworkRegistry.get(block), e.player)
        })
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) = service(b)
            override fun isSynchronized(): Boolean = true
        })
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) = NetworkRegistry.invalidateAll()
        })
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                NetworkRegistry.remove(e.block); NetworkRegistry.invalidateAll()
            }
        })
    }

    /** 每 tick 服务输入 / 输出端口。 */
    private fun service(controller: Block) {
        val net = NetworkRegistry.get(controller)
        for (port in net.inputPorts) drainInput(net, port)
        for (port in net.outputPorts) fillOutput(net, port)
    }

    /** 把输入端口缓冲槽的物品分发进网络。 */
    private fun drainInput(net: StorageNetwork, port: Block) {
        val menu = StorageCacheUtils.getMenu(port.location) ?: return
        val stack = menu.getItemInSlot(NetworkPort.SLOT) ?: return
        if (stack.type.isAir || stack.amount <= 0) return
        val key = ItemKey.of(stack) ?: return
        val leftover = net.insert(key, stack.amount.toLong())
        if (leftover != stack.amount.toLong()) {
            menu.replaceExistingItem(NetworkPort.SLOT, if (leftover <= 0) null else stack.clone().apply { amount = leftover.toInt() })
        }
    }

    /** 从网络取一堆物品填入输出端口缓冲槽 (多物品逐种轮换, 不卡在第一种)。 */
    private fun fillOutput(net: StorageNetwork, port: Block) {
        val menu = StorageCacheUtils.getMenu(port.location) ?: return
        if (menu.getItemInSlot(NetworkPort.SLOT) != null) return
        val all = net.aggregate()
        if (all.isEmpty()) return
        val pk = port.location.let { "${it.world?.name}:${it.blockX}:${it.blockY}:${it.blockZ}" }
        val start = (outputCursor[pk] ?: 0) % all.size
        val (key, amount) = all[start]
        outputCursor[pk] = (start + 1) % all.size
        val take = minOf(amount, key.vanillaMaxStack.toLong())
        val got = net.extract(key, take)
        if (got > 0) menu.replaceExistingItem(NetworkPort.SLOT, key.toDisplay(got.toInt()))
    }

    /** 输出端口位置键 → 轮换游标。 */
    private val outputCursor = java.util.concurrent.ConcurrentHashMap<String, Int>()
}
