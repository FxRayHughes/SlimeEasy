package top.maplex.slimeEasy.storage.box

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.StorageDisplay
import top.maplex.slimeEasy.storage.core.UpgradeMenu
import top.maplex.slimeEasy.util.locationKey

/**
 * 翻页箱的分页存取 GUI。
 *
 * 前 5 行 (45 格) 分页展示库存物品, 每格图标数量视觉封顶 99, 真实数量写入 lore;
 * 末行为导航栏: 上一页 / 页码 / 升级插件 / 下一页。
 *
 * 交互:
 * - 点击箱内物品: 左键取一组 / 右键取一个 (取出到背包, 溢出掉落);
 * - 点击自己背包物品: 存入箱子 (受种类数、容量、虚空过滤约束)。
 *
 * 每次打开创建一个 [View] 持有页码, 翻页 / 变更后就地重绘。
 */
object PagedBoxMenu {

    private const val PAGE_SIZE = 45
    private const val PREV_SLOT = 45
    private const val INFO_SLOT = 48
    private const val UPGRADE_SLOT = 50
    private const val NEXT_SLOT = 53

    /** 各方块位置当前打开的视图集合, 用于任一处变更后即时同步刷新其它已打开页面。 */
    private val openViews = java.util.concurrent.ConcurrentHashMap<String, MutableSet<View>>()

    init {
        // 订阅库存变更: 货运 / 网络终端 / 磁铁等外部改动本箱时, 打开中的箱子界面实时刷新
        top.maplex.slimeEasy.storage.core.StorageChangeBus.subscribe { block -> refreshAll(block) }
    }

    /** 刷新某方块所有已打开视图 (即时同步, 保证数据一致)。 */
    fun refreshAll(block: Block) {
        openViews[block.locationKey()]?.toList()?.forEach { it.render() }
    }

    fun open(box: PagedBox, block: Block, player: Player) {
        View(box, block, player).apply { menu.open(player) }
    }

    private class View(val box: PagedBox, val block: Block, val player: Player) {
        val menu = ChestMenu("§9翻页存储箱")
        var page = 0

        init {
            menu.setEmptySlotsClickable(false)
            // 玩家点击自己背包的物品 → 存入
            menu.setPlayerInventoryClickable(true)
            menu.addPlayerInventoryClickHandler { p, slot, item, _ -> depositFromInventory(p, slot, item); false }
            openViews.computeIfAbsent(block.locationKey()) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(this)
            menu.addMenuCloseHandler { openViews[block.locationKey()]?.remove(this) }
            render()
        }

        /**
         * 冻结的每格 key 布局 (第 i 格显示哪种物品)。
         *
         * 取出**不重排**: 同一物品的多格从尾部消耗, 取空的格以空占位留在原位、其余格位置
         * 不动, 避免连续取出时物品跳格、玩家翻页找不到。仅存入 / 升级变更等操作
         * ([render] resort=true) 按库存顺序重建, 顺带清理空占位。
         */
        private var slotKeys: MutableList<ItemKey>? = null

        /**
         * 重绘界面。
         *
         * @param resort true 时按库存插入序重建每格布局 (清理取空占位); false (默认) 沿用
         *   冻结布局, 仅刷新各格数量、取空处留空占位、外部新增物品追加末尾。
         */
        fun render(resort: Boolean = false) {
            // 拆格显示: 单格容量 = 原版堆叠 × 堆叠升级倍率; 超出溢出到新格
            val storage = box.storageAt(block)
            val entries = storage.entries()
            val cap: (ItemKey) -> Long = { storage.cellCapacity(it) }
            val countByKey = HashMap<ItemKey, Long>(entries.size * 2)
            for ((k, c) in entries) countByKey[k] = c

            val slots = if (resort || slotKeys == null) {
                StorageDisplay.toCells(entries, cap).map { it.key }.toMutableList().also { slotKeys = it }
            } else slotKeys!!.also { sk ->
                // 稳定: 外部新增 / 增多的物品按实时所需格数在末尾补足, 不打乱既有布局
                val alloc = HashMap<ItemKey, Int>()
                for (k in sk) alloc[k] = (alloc[k] ?: 0) + 1
                for ((k, c) in entries) {
                    val need = ((c + cap(k) - 1) / cap(k)).toInt()
                    repeat((need - (alloc[k] ?: 0)).coerceAtLeast(0)) { sk.add(k) }
                }
            }

            // 逐格按物品剩余量分摊: 同一物品的连续格前面填满单格容量, 尾部留空 (取出从尾部消耗)
            val remain = HashMap<ItemKey, Long>(countByKey)
            val amounts = LongArray(slots.size)
            for (idx in slots.indices) {
                val r = remain[slots[idx]] ?: 0L
                if (r > 0L) { val n = minOf(r, cap(slots[idx])); amounts[idx] = n; remain[slots[idx]] = r - n }
            }

            // 页数由翻页扩容决定 (总槽位 = 页数×45, 插入已被约束在此预算内)
            val pages = box.pages(block)
            page = page.coerceIn(0, pages - 1)
            val from = page * PAGE_SIZE
            for (i in 0 until PAGE_SIZE) {
                val gi = from + i
                val key = slots.getOrNull(gi)
                val amt = if (gi < amounts.size) amounts[gi] else 0L
                if (key != null && amt > 0L) {
                    menu.addItem(i, StorageDisplay.icon(StorageDisplay.Cell(key, amt, countByKey[key] ?: amt))) { p, _, _, action ->
                        // 左键取一组 (原版堆叠), 右键取一个
                        withdraw(p, key, if (action.isRightClicked) 1 else key.vanillaMaxStack); false
                    }
                } else {
                    menu.addItem(i, GuiItems.BACKGROUND) { _, _, _, _ -> false } // 取空占位, 位置保留
                }
            }
            renderNav(pages)
        }

        /** 渲染底部导航栏。 */
        private fun renderNav(pages: Int) {
            for (s in PAGE_SIZE until 54) menu.addItem(s, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            menu.addItem(PREV_SLOT, GuiItems.PREV_PAGE) { _, _, _, _ -> if (page > 0) { page--; render() }; false }
            menu.addItem(NEXT_SLOT, GuiItems.NEXT_PAGE) { _, _, _, _ -> if (page < pages - 1) { page++; render() }; false }
            menu.addItem(INFO_SLOT, GuiItems.named(org.bukkit.Material.PAPER, "§e第 ${page + 1}/$pages 页")) { _, _, _, _ -> false }
            menu.addItem(UPGRADE_SLOT, GuiItems.UPGRADE_ENTRY) { p, _, _, _ ->
                UpgradeMenu.open(box, block, p, "§9存储箱升级"); false
            }
        }

        /** 取出某物品到玩家背包 (溢出掉落脚下)。 */
        private fun withdraw(player: Player, key: ItemKey, amount: Int) {
            val storage = box.storageAt(block)
            val taken = storage.extract(key, amount.toLong(), simulate = false).toInt()
            if (taken <= 0) return
            player.inventory.addItem(key.toDisplay(taken)).values
                .forEach { player.world.dropItemNaturally(player.location, it) }
            box.saveStorage(block, storage)
            refreshAll(block)
        }

        /**
         * 把玩家**点击的那一槽** [slot] 物品存入箱子。
         *
         * 从点击槽位精确扣除实际离开背包的数量 (点哪组扣哪组), 详见 [InventoryOps.removeFromSlot]。
         */
        private fun depositFromInventory(player: Player, slot: Int, item: ItemStack?) {
            if (item == null || item.type.isAir) return
            val key = top.maplex.slimeEasy.storage.core.ItemKey.of(item) ?: return
            val loc = block.location
            val upgrades = top.maplex.slimeEasy.storage.upgrade.UpgradeStore.resolve(loc)
            val storage = box.storageAt(block)
            box.prepareForInsert(block, item) // 同步槽位预算与倍率
            // 虚空过滤: 封顶到保留数量, 超出部分湮灭 (未标记则原样尝试入库)
            val admit = if (upgrades.hasVoid)
                top.maplex.slimeEasy.storage.upgrade.VoidFilter.admit(loc, item, storage.count(key), item.amount.toLong())
            else item.amount.toLong()
            val leftover = if (admit > 0) storage.insert(item, admit, simulate = false) else admit
            val stored = (admit - leftover).toInt()
            val consumed = item.amount - leftover.toInt() // 离开背包 = 真正入库 + 湮灭
            if (consumed > 0) {
                top.maplex.slimeEasy.storage.core.InventoryOps.removeFromSlot(player, slot, key, consumed)
                if (stored > 0) box.saveStorage(block, storage) // 有真正入库才落盘 (并广播刷新其它视图)
                render(resort = true) // 存入属主动操作, 本视图按库存顺序重排归位
            }
        }
    }
}
