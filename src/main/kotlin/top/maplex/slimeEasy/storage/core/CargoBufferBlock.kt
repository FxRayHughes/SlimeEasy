package top.maplex.slimeEasy.storage.core

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.attributes.NotHopperable
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.upgrade.UpgradeType
import top.maplex.slimeEasy.util.locationKey
import java.util.concurrent.ConcurrentHashMap

/**
 * 货运接入抽象基类。
 *
 * 为存储方块 (抽屉 / 翻页箱子) 统一提供"接入 Slimefun 原版货运网络"的能力。
 * 货运网络只识别方块位置上注册的 [BlockMenu], 且仅通过其槽位读写。本类据此
 * 注册一个**隐藏**的缓冲菜单 ([canOpen] 恒 false, 玩家无法打开):
 *
 * - **输入缓冲槽**: 货运塞入的物品经 [onItemStackChange] 吸入 [VirtualStorage],
 *   随即清空该槽, 使货运下一 tick 可继续塞入 —— 实现无限容量的"入口"。
 * - **输出缓冲槽**: [BlockTicker] 每 tick 从虚拟库存"借出"一堆到该槽 (借出即
 *   扣减库存, 破坏时连同缓冲槽一并掉落, 故不会凭空丢失); 货运取走后下一 tick
 *   再补 —— 实现无限容量的"出口"。
 *
 * 玩家面向的交互 UI (展示框 / 分页 GUI) 由子类自行实现, 与本缓冲菜单解耦。
 * 每个方块位置的库存实例按需从 BlockData 载入并缓存于 [storageCache]。
 *
 * 实现 [NotHopperable]: 外观是原版木桶, 若不拦截原版漏斗会把物品塞进木桶自带的
 * 27 格原版库存 —— 该库存本插件从不读写, 物品即被"吞掉"永久不可见。故拒绝原版漏斗
 * 塞入; 漏斗喂料统一走 [top.maplex.slimeEasy.storage.core.HopperExtract] 抽取升级。
 * (SlimeFun 货运网络经其自有传输而非 InventoryMoveItemEvent, 不受影响。)
 *
 * 所有存储读写约定在主线程进行 (ticker [BlockTicker.isSynchronized] = true)。
 */
abstract class CargoBufferBlock(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe), InventoryBlock, NotHopperable, UpgradeHost {

    /** 位置键 → 该方块的虚拟库存 (懒加载缓存)。 */
    private val storageCache = ConcurrentHashMap<String, VirtualStorage>()

    /** 位置键 → 输出补货轮换游标 (保证多物品逐种轮流输出, 不卡在第一种)。 */
    private val outputCursor = ConcurrentHashMap<String, Int>()

    /**
     * 位置键 → 上一 tick 在输出槽"镜像"的 (物品身份, 展示数量)。
     *
     * 输出槽只做**镜像展示**: 展示的物品仍留在 [VirtualStorage] 中, 不预先扣除。
     * 下一 tick 对比槽内剩余, 差额即货运实际取走量, 据此从库存真实扣除 —— 从而
     * "输出槽不真的运走物品, 货运取多少才扣多少"。
     */
    private val outputMirror = ConcurrentHashMap<String, Pair<ItemKey, Int>>()

    init { CargoBlockRegistry.register(this) }

    override fun getInputSlots(): IntArray = intArrayOf(INPUT_SLOT)
    override fun getOutputSlots(): IntArray = intArrayOf(OUTPUT_SLOT)

    /** 存储内容在 BlockData 中的键名 (子类区分抽屉 / 箱子)。 */
    protected abstract val storageDataKey: String

    /** 依据方块当前升级状态, 构造一个空的虚拟库存 (容量 / 种类数由子类决定)。 */
    protected abstract fun freshStorage(block: Block): VirtualStorage

    /**
     * 货运是否接受向本方块塞入物品。
     *
     * 默认恒 true; 经验存储模式下的容器可覆盖为 false, 拒绝货运塞物。
     */
    protected open fun acceptsCargoItems(block: Block): Boolean = true

    /**
     * 本方块是否向货运 / 输出槽借出物品。
     *
     * 默认恒 true; 经验存储模式下的抽屉应覆盖为 false —— 避免其残留的物品库存
     * 被继续借出, 也避免借出触发的展示刷新与经验展示互相覆盖。
     */
    protected open fun providesCargoItems(block: Block): Boolean = true

    /**
     * 插入前的预处理钩子, 返回"实际应尝试入库的数量"。
     *
     * 默认原样返回。虚空升级可在此对命中销毁表的物品返回 0 (直接丢弃),
     * 从而在入库前将其湮灭。
     */
    protected open fun filterInsert(block: Block, item: ItemStack, amount: Long): Long = amount

    /** 获取 (或懒加载) 指定方块的虚拟库存。 */
    fun storageAt(block: Block): VirtualStorage = storageCache.getOrPut(block.locationKey()) {
        freshStorage(block).apply { load(StorageCacheUtils.getData(block.location, storageDataKey)) }
    }

    /** 持久化指定方块的库存内容到 BlockData, 并通知子类刷新其展示。 */
    fun saveStorage(block: Block, storage: VirtualStorage) {
        val previousData = StorageCacheUtils.getData(block.location, storageDataKey)
        beforeStorageSave(block, storage, previousData)
        StorageCacheUtils.setData(block.location, storageDataKey, storage.serialize())
        onStorageChanged(block, storage)
        StorageChangeBus.fire(block) // 广播变更, 供打开中的 GUI 实时重绘
    }

    /**
     * 库存内容变更后的回调 (在 [saveStorage] 末尾触发)。
     *
     * 默认空实现。抽屉据此刷新面向玩家的展示框物品与数量文字。
     */
    protected open fun onStorageChanged(block: Block, storage: VirtualStorage) {}

    /** 落盘前变换钩子；翻页箱的压制升级据此只处理相对上次落盘新增的物品。 */
    protected open fun beforeStorageSave(block: Block, storage: VirtualStorage, previousData: String?) {}

    /** 从缓存移除某位置库存 (方块破坏后调用, 防止内存泄漏)。 */
    protected fun evictCache(block: Block) {
        val k = block.locationKey()
        storageCache.remove(k)
        outputCursor.remove(k)
        outputMirror.remove(k)
    }

    /**
     * 卸载某区块内全部位置的缓存 (由 [ChunkUnloadEvent] 触发)。
     *
     * 缓存是懒加载的纯内存副本, BlockData 才是真相源, 故卸载时直接丢弃即可,
     * 无需回写 (每次 [saveStorage] 都已同步落盘)。
     */
    internal fun evictChunk(worldName: String, cx: Int, cz: Int) {
        val prefix = "$worldName:"
        val iter = storageCache.keys.iterator()
        while (iter.hasNext()) {
            val key = iter.next()
            if (!key.startsWith(prefix)) continue
            val parts = key.split(":")
            if (parts.size < 4) continue
            val x = parts[1].toIntOrNull() ?: continue
            val z = parts[3].toIntOrNull() ?: continue
            if (x shr 4 == cx && z shr 4 == cz) { iter.remove(); outputCursor.remove(key); outputMirror.remove(key) }
        }
    }

    /**
     * 升级变更前的业务校验。
     *
     * 默认恒允许。抽屉据此拦截: 有物品时装经验升级、有经验时卸经验升级。
     *
     * @param type 本次变更涉及的升级类型
     * @param install true = 安装该升级; false = 卸下该升级
     * @return null 表示允许; 非 null 为拒绝原因 (展示给玩家)
     */
    override fun rejectUpgradeChange(block: Block, type: UpgradeType, install: Boolean): String? = null

    /**
     * 升级组件变更后的回调 (安装 / 卸下升级后由升级 GUI 调用)。
     *
     * 子类据此按新升级重算库存容量并刷新展示。默认空实现。
     */
    override fun onUpgradesChanged(block: Block) {}

    /**
     * 判断在仅保留 [remainingStackMultiplier] 倍率后, 现存库存是否仍装得下。
     *
     * 卸下堆叠升级会缩小容量, 若现存量超过新容量应拒绝卸下。默认恒允许,
     * 子类按其容量模型覆写。
     *
     * @param remainingStackMultiplier 卸下目标升级后剩余的堆叠倍率连乘
     */
    override fun capacityAllowsRemoval(block: Block, remainingStackMultiplier: Double): Boolean = true

    /**
     * 插入前的容量准备钩子。
     *
     * 不同容器的单格容量模型不同 (抽屉依所存物品堆叠上限, 箱子统一按 64 基准),
     * 网络路由 / 货运在调用 [VirtualStorage.insert] 前借此让容器按来料校准容量。
     * 默认空实现。
     */
    open fun prepareForInsert(block: Block, item: ItemStack) {}

    override fun preRegister() {
        // 注册隐藏缓冲菜单: 仅供货运挂载, 玩家不可打开
        BufferPreset()
        // 注册补货 ticker: 每 tick 把库存借出到输出槽
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) {
                supplyOutput(b)
                onCustomTick(b)
            }
            override fun isSynchronized(): Boolean = true
        })
    }

    /**
     * 每 tick 的附加行为钩子 (在补货之后触发)。
     *
     * 默认空实现。磁铁升级据此吸附附近掉落物 / 经验球。
     */
    protected open fun onCustomTick(block: Block) {}

    /**
     * 维护输出缓冲槽的"镜像": 展示库存里可供货运抽取的一堆物品, 但**不预先扣除**;
     * 通过对比上一 tick 的展示量与当前剩余量, 得出货运实际取走量并从库存扣除。
     *
     * 这样输出槽中的物品始终是库存的镜像 (库存 GUI / 网络聚合看到的数量正确),
     * 破坏时也只需散落库存本身, 不会与镜像重复。
     */
    private fun supplyOutput(block: Block) {
        val k = block.locationKey()
        if (!providesCargoItems(block)) { outputMirror.remove(k); return }
        val menu = StorageCacheUtils.getMenu(block.location) ?: return
        val storage = storageAt(block)
        var changed = false

        // 1. 结算: 上一 tick 镜像的物品被货运取走了多少, 从库存真实扣除
        val prev = outputMirror[k]
        if (prev != null) {
            val (pKey, shown) = prev
            val cur = menu.getItemInSlot(OUTPUT_SLOT)
            val remaining = if (cur != null && pKey.matches(cur)) cur.amount else 0
            val taken = shown - remaining
            if (taken > 0) { storage.extract(pKey, taken.toLong(), simulate = false); changed = true }
        }

        // 2. 重建镜像: 优先保持当前物品, 否则轮换到下一种有货的物品
        val entries = storage.entries()
        if (entries.isEmpty()) {
            if (menu.getItemInSlot(OUTPUT_SLOT) != null) menu.replaceExistingItem(OUTPUT_SLOT, null)
            outputMirror.remove(k)
            if (changed) saveStorage(block, storage)
            return
        }
        val cur = menu.getItemInSlot(OUTPUT_SLOT)
        val curKey = ItemKey.of(cur)?.takeIf { storage.count(it) > 0 }
        val chosen = curKey ?: run {
            val start = (outputCursor[k] ?: 0) % entries.size
            outputCursor[k] = (start + 1) % entries.size
            entries[start].first
        }
        val show = minOf(storage.count(chosen), chosen.vanillaMaxStack.toLong()).toInt()
        // 仅在展示内容变化时写槽, 避免每 tick 无谓刷新
        if (cur == null || !chosen.matches(cur) || cur.amount != show) {
            menu.replaceExistingItem(OUTPUT_SLOT, if (show > 0) chosen.toDisplay(show) else null)
        }
        outputMirror[k] = chosen to show
        if (changed) saveStorage(block, storage)
    }

    /** 隐藏缓冲菜单: 输入槽吸收、输出槽补货, 均由货运驱动。 */
    private inner class BufferPreset : BlockMenuPreset(id, itemName) {
        override fun init() { /* 隐藏菜单, 无需布局 */ }

        override fun canOpen(block: Block, player: Player): Boolean = false

        override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray =
            if (flow == ItemTransportFlow.INSERT) inputSlots else outputSlots

        override fun onItemStackChange(
            menu: DirtyChestMenu, slot: Int, previous: ItemStack?, next: ItemStack?
        ): ItemStack? {
            // 仅拦截货运塞入输入槽的情形; 输出槽变动是本插件补货/取走, 不处理
            if (slot != INPUT_SLOT || next == null || next.type.isAir) return next
            val block = (menu as? BlockMenu)?.block ?: return next
            if (!acceptsCargoItems(block)) return next
            val storage = storageAt(block)
            val requested = filterInsert(block, next, next.amount.toLong())
            if (requested <= 0) return null // 虚空升级湮灭
            val leftover = storage.insert(next, requested, simulate = false)
            saveStorage(block, storage)
            // 被虚空过滤削减的量 (next.amount - requested) 已湮灭, 不返还; 仅退回真正没装下的 leftover
            return if (leftover <= 0) null
            else next.clone().apply { amount = leftover.toInt().coerceAtMost(next.maxStackSize) }
        }
    }

    protected companion object {
        /** 缓冲菜单输入槽 (货运塞入)。 */
        const val INPUT_SLOT = 0

        /** 缓冲菜单输出槽 (货运抽出)。 */
        const val OUTPUT_SLOT = 1

    }
}
