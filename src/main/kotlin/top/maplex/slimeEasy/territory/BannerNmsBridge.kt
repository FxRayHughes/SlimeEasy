package top.maplex.slimeEasy.territory

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BannerBlockEntity
import org.bukkit.block.Block
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

/**
 * 按原版 BlockItem 放置流程把 ItemStack 数据组件写入真实 [BannerBlockEntity]。
 *
 * 旗帜花纹属于方块实体的隐式数据组件，单独修改 Bukkit BlockState 或普通组件映射不能可靠更新它；
 * 因此这里与原版 `BlockItem.updateBlockEntityComponents` 保持同一路径，并由 Paper dev bundle 锁定签名。
 */
internal object BannerNmsBridge {
    private var warningLogged = false

    /**
     * 应用模板并核对 NMS BannerPatternLayers 数量；成功后把方块实体变化加入区块广播队列。
     * 模板必须是只包含允许同步字段的干净旗帜，不能直接传入玩家物品以免复制其它组件。
     */
    fun apply(plugin: JavaPlugin, block: Block, template: ItemStack, expectedPatterns: Int): Boolean = runCatching {
        val level = (block.world as CraftWorld).handle
        val position = BlockPos(block.x, block.y, block.z)
        val blockEntity = level.getBlockEntity(position) as? BannerBlockEntity
            ?: error("Missing banner block entity at ${block.x},${block.y},${block.z}")

        blockEntity.applyComponentsFromItemStack(CraftItemStack.asNMSCopy(template))
        blockEntity.setChanged()
        if (blockEntity.patterns.layers().size != expectedPatterns) return@runCatching false

        // 方块状态未变化时仍须标记区块；ChunkHolder 才会同时广播 BannerBlockEntity 更新包。
        level.chunkSource.blockChanged(position)
        true
    }.getOrElse {
        warnOnce(plugin, it)
        false
    }

    /** NMS 写入失败只记录一次，避免菜单同步多面旗帜时刷屏；调用方仍会尝试 Bukkit 回退。 */
    private fun warnOnce(plugin: JavaPlugin, cause: Throwable) {
        if (warningLogged) return
        warningLogged = true
        plugin.logger.warning("Unable to apply banner components through the vanilla block-entity path: ${cause.message}")
    }
}
