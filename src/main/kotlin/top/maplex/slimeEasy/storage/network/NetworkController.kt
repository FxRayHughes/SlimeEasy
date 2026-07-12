package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
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

/**
 * 存储网络控制器 (网络大脑与访问入口)。
 *
 * 职责:
 * - **访问**: 右键打开跨全网成员的聚合分页 GUI ([NetworkMenu]);
 * - **路由**: 每 tick 服务网络内的输入 / 输出端口，包括缓冲桥接与按端口模式启用的
 *   主动相邻 IO；具体事务与过滤规则集中在 [NetworkPortIO]；
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
        for (port in net.inputPorts) NetworkPortIO.serviceInput(net, port)
        for (port in net.outputPorts) NetworkPortIO.serviceOutput(net, port)
    }
}
