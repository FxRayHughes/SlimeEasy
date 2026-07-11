package top.maplex.slimeEasy.storage.network

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.util.BlockLocationCodec

/**
 * 远程升级 (手持绑定 + 安装式混合)。
 *
 * 用法:
 * 1. **手持右键网络控制器**: 把该控制器坐标写入本升级物品 PDC (选定目标);
 * 2. **把本升级放入抽屉 / 箱子的升级 GUI 槽位**: 容器读取 PDC 里的控制器坐标, 作为
 *    **远程成员**接入该控制器网络 (即使不在物理相邻范围内)。
 *
 * 绑定信息随物品走 (PDC): 装入容器后由 [top.maplex.slimeEasy.storage.core.ItemCodec]
 * 完整序列化进 se_upgrades 持久化; 容器侧与控制器侧的双向索引由 [RemoteBind] 维护。
 */
class RemoteUpgrade(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    override fun preRegister() {
        addItemHandler(ItemUseHandler { e ->
            e.cancel() // 取消原版交互 (放置 / 使用)
            val player = e.player
            val block = e.clickedBlock.orElse(null)
            if (block != null && NetworkControllerAccess.isController(block)) {
                if (!NetworkControllerAccess.canUse(player, block)) return@ItemUseHandler
                val loc = block.location
                val value = BlockLocationCodec.encode(block)
                e.item.editMeta { it.persistentDataContainer.set(KEY_CTRL, PersistentDataType.STRING, value) }
                player.sendMessage(I18n.text("messages.remote-upgrade-001", "value0" to (loc.blockX), "value1" to (loc.blockY), "value2" to (loc.blockZ)))
            } else {
                player.sendMessage(I18n.text("messages.remote-upgrade-002"))
            }
        })
    }

    companion object {
        /** 选定控制器坐标在升级物品 PDC 中的键 (值格式 "world;x;y;z")。 */
        private val KEY_CTRL = NamespacedKey(SlimeEasy.instance, "remote_upgrade_controller")

        /** 读取升级物品 PDC 中记录的控制器坐标; 未选定返回 null。 */
        fun controllerOf(item: ItemStack?): String? =
            item?.itemMeta?.persistentDataContainer?.get(KEY_CTRL, PersistentDataType.STRING)?.takeUnless { it.isEmpty() }
    }
}
