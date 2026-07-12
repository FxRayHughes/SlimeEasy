package top.maplex.slimeEasy.storage.disk

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import top.maplex.slimeEasy.storage.core.VirtualStorage

/**
 * 磁盘管理器：以雕纹书架六个可见书槽安装磁盘，并作为存储网络成员提供合并库存。
 *
 * 本类刻意不注册 [CargoBufferBlock] 的隐藏货运菜单，因为该菜单的 0/1 槽会与雕纹
 * 书架真实书槽冲突；物品存取统一通过相邻存储网络，玩家界面只负责磁盘管理与状态查看。
 */
class DiskManager(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : CargoBufferBlock(itemGroup, item, recipeType, recipe) {

    /** 基类协议要求的保留键；本类覆盖持久化钩子，因此不会把内容写入该 BlockData 键。 */
    override val storageDataKey: String = "se_disk_manager_items"

    override fun freshStorage(block: Block): VirtualStorage = DiskManagerStorage.runtime(block)

    /** 按书架内六张磁盘 UUID 查询 Slimefun UniversalData，并仅在内存中合并。 */
    override fun loadStorageData(block: Block, storage: VirtualStorage) =
        DiskManagerStorage.loadAggregate(block, storage)

    /** 把合并库存增量分配回实际磁盘的 UniversalData，不产生方块内容副本。 */
    override fun persistStorageData(block: Block, storage: VirtualStorage) =
        DiskManagerStorage.syncToDisks(block, storage)

    /** 安装状态变化后从六张磁盘重建合并镜像。 */
    fun rebuildFromDisks(block: Block) = DiskManagerStorage.rebuildAggregate(this, block)

    /** 方块破坏和磁盘热插拔后清理旧合并库存缓存。 */
    fun clearCache(block: Block) = evictCache(block)

    override fun preRegister() {
        // 不调用 super：保留六个原生书槽给可见磁盘，避免隐藏货运槽覆盖书本。
        addItemHandler(DiskManagerHandlers.place(this))
        addItemHandler(DiskManagerHandlers.break_(this))
        addItemHandler(DiskManagerHandlers.use(this))
    }
}
