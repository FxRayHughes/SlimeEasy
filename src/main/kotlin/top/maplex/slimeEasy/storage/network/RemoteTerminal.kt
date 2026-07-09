package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy

/**
 * 远程终端 (手持工具)。
 *
 * 用法:
 * - **手持右键网络控制器**: 把本终端绑定到该控制器 (绑定信息存于物品 PDC);
 * - **手持右键空气 / 其它方块**: 远程打开已绑定网络的聚合终端 ([NetworkMenu]),
 *   随时随地存取全网库存。
 *
 * 绑定是"物品维度"的: 不同的终端可绑定不同控制器; 复制 / 堆叠的终端共享同一绑定。
 */
class RemoteTerminal(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    override fun preRegister() {
        addItemHandler(ItemUseHandler { e ->
            e.cancel() // 取消原版交互 (放置 / 使用)
            val player = e.player
            val item = e.item
            val block = e.clickedBlock.orElse(null)
            if (block != null && isController(block)) bind(player, item, block)
            else openBound(player, item)
        })
    }

    /** 把终端绑定到指定控制器方块。 */
    private fun bind(player: Player, item: ItemStack, controller: Block) {
        val loc = controller.location
        val value = "${loc.world?.name};${loc.blockX};${loc.blockY};${loc.blockZ}"
        item.editMeta { it.persistentDataContainer.set(KEY_BIND, PersistentDataType.STRING, value) }
        player.sendMessage("§b[远程终端] §7已绑定到网络控制器 §f(${loc.blockX}, ${loc.blockY}, ${loc.blockZ})")
    }

    /** 打开已绑定网络; 未绑定 / 绑定失效时提示。 */
    private fun openBound(player: Player, item: ItemStack) {
        val raw = item.itemMeta?.persistentDataContainer?.get(KEY_BIND, PersistentDataType.STRING)
        if (raw.isNullOrEmpty()) {
            player.sendMessage("§c[远程终端] §7尚未绑定, 请手持右键网络控制器进行绑定")
            return
        }
        val block = resolve(raw)
        if (block == null || !isController(block)) {
            player.sendMessage("§c[远程终端] §7绑定的网络控制器已不存在, 请重新绑定")
            return
        }
        NetworkMenu.open(NetworkRegistry.get(block), player)
    }

    /** 判断方块是否为网络控制器。 */
    private fun isController(block: Block): Boolean {
        val id = StorageCacheUtils.getBlock(block.location)?.sfId ?: return false
        return SlimefunItem.getById(id) is NetworkController
    }

    /** 解析绑定字符串为方块; 世界未加载 / 格式错误返回 null。 */
    private fun resolve(raw: String): Block? {
        val parts = raw.split(";")
        if (parts.size < 4) return null
        val world = Bukkit.getWorld(parts[0]) ?: return null
        val x = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        val z = parts[3].toIntOrNull() ?: return null
        return world.getBlockAt(x, y, z)
    }

    companion object {
        /** 绑定信息在物品 PDC 中的键 (值格式: "world;x;y;z")。 */
        private val KEY_BIND = NamespacedKey(SlimeEasy.instance, "terminal_bind")
    }
}
