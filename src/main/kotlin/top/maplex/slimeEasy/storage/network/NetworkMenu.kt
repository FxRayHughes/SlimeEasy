package top.maplex.slimeEasy.storage.network

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.StorageChangeBus
import top.maplex.slimeEasy.storage.core.StorageDisplay

/**
 * 存储网络的聚合分页 GUI。
 *
 * 前 5 行 (45 格) 分页展示**全网合并**库存, 末行为导航栏。交互:
 * - 点击物品: 左键取一组 / 右键取一个 (跨成员取出到背包, 溢出掉落);
 * - 点击自己背包物品: 按优先级分发存入网络。
 *
 * GUI 打开时对网络拓扑做一次快照 (来自 [NetworkRegistry] 缓存); 库存读写实时。
 */
object NetworkMenu {

    private const val PAGE_SIZE = 45
    private const val PREV_SLOT = 45
    private const val SORT_FIELD_SLOT = 46
    private const val SORT_DIR_SLOT = 47
    private const val INFO_SLOT = 48
    private const val NEXT_SLOT = 53

    /** 当前打开中的全部终端视图 (用于库存变更时实时重绘)。 */
    private val openViews = java.util.concurrent.ConcurrentHashMap.newKeySet<View>()

    init {
        // 订阅库存变更: 任一成员库存改动 (本终端 / 别的玩家 / 货运 / 磁铁 / 成员界面)
        // 都重绘所有打开中的终端, 保证实时一致。终端数量少, 全量重绘开销可忽略。
        StorageChangeBus.subscribe { _ -> openViews.toList().forEach { it.render() } }
    }

    fun open(net: StorageNetwork, player: Player) {
        View(net, player).menu.open(player)
    }

    private class View(val net: StorageNetwork, val player: Player) {
        val menu = ChestMenu("§9存储网络终端")
        var page = 0

        init {
            menu.setEmptySlotsClickable(false)
            menu.setPlayerInventoryClickable(true)
            menu.addPlayerInventoryClickHandler { p, slot, item, _ -> deposit(p, slot, item); false }
            openViews.add(this)
            menu.addMenuCloseHandler { openViews.remove(this) }
            render()
        }

        /**
         * 冻结的显示顺序 (key 序列)。
         *
         * 取出**不重排**: 沿用本快照, 取空的物品以空占位留在原位、其余位置不动, 避免
         * 连续取出时图标跳动、玩家翻页找不到。仅排序切换 / 存入等操作 ([render] resort=true)
         * 按最新排序偏好重建, 顺带清理取空的占位。
         */
        private var order: MutableList<ItemKey>? = null

        /**
         * 重绘界面。
         *
         * @param resort true 时按当前排序偏好重建显示顺序 (清理取空占位); false (默认) 沿用
         *   冻结顺序, 仅刷新数量、取空处留空占位、外部新增物品追加末尾 —— 保证连续取出时
         *   其余物品位置稳定。
         */
        fun render(resort: Boolean = false) {
            // 聚合显示: 全网合并后**一种物品一个图标** (总量写入 lore), 不按堆叠拆格平铺;
            // 分组拆格的多格展示留给成员容器 (翻页箱) 自身界面。
            val agg = net.aggregate()
            val amountByKey = HashMap<ItemKey, Long>(agg.size * 2)
            for ((k, v) in agg) amountByKey[k] = v

            val order = if (resort || this.order == null) {
                // 重排: 按玩家持久化排序偏好重建, 仅含当前有量的物品 (清理空占位)
                sortEntries(agg).map { it.first }.toMutableList().also { this.order = it }
            } else this.order!!.also { ord ->
                // 稳定: 外部新增物品 (别处存入 / 货运) 追加末尾, 不打乱既有布局
                val known = ord.toHashSet()
                for ((k, _) in agg) if (k !in known) ord.add(k)
            }

            val pages = maxOf(1, (order.size + PAGE_SIZE - 1) / PAGE_SIZE)
            page = page.coerceIn(0, pages - 1)
            val from = page * PAGE_SIZE
            for (i in 0 until PAGE_SIZE) {
                val key = order.getOrNull(from + i)
                val total = if (key != null) amountByKey[key] ?: 0L else 0L
                if (key != null && total > 0) {
                    menu.addItem(i, StorageDisplay.aggregatedIcon(key, total)) { p, _, _, act ->
                        // 左键取一组 (原版堆叠), 右键取一个
                        withdraw(p, key, if (act.isRightClicked) 1 else key.vanillaMaxStack); false
                    }
                } else menu.addItem(i, GuiItems.BACKGROUND) { _, _, _, _ -> false } // 取空占位, 位置保留
            }
            renderNav(pages)
        }

        private fun renderNav(pages: Int) {
            for (s in PAGE_SIZE until 54) menu.addItem(s, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            menu.addItem(PREV_SLOT, GuiItems.PREV_PAGE) { _, _, _, _ -> if (page > 0) { page--; render() }; false }
            menu.addItem(NEXT_SLOT, GuiItems.NEXT_PAGE) { _, _, _, _ -> if (page < pages - 1) { page++; render() }; false }
            menu.addItem(INFO_SLOT, GuiItems.named(org.bukkit.Material.PAPER,
                "§e第 ${page + 1}/$pages 页", "§7成员: ${net.members.size}")) { _, _, _, _ -> false }
            renderSortButtons()
        }

        /** 渲染排序控制按钮 (字段切换 + 方向切换); 点击后写回玩家偏好并重绘。 */
        private fun renderSortButtons() {
            val fieldName = if (TerminalSortState.field(player) == TerminalSortState.Field.NAME) "名称" else "存量"
            menu.addItem(SORT_FIELD_SLOT, GuiItems.named(org.bukkit.Material.NAME_TAG,
                "§b排序: §f$fieldName", "§7点击切换 名称 / 存量")) { _, _, _, _ ->
                TerminalSortState.cycleField(player); render(resort = true); false
            }
            val dirName = if (TerminalSortState.descending(player)) "倒序 ↓" else "正序 ↑"
            menu.addItem(SORT_DIR_SLOT, GuiItems.named(org.bukkit.Material.COMPARATOR,
                "§b顺序: §f$dirName", "§7点击切换 正序 / 倒序")) { _, _, _, _ ->
                TerminalSortState.toggleDir(player); render(resort = true); false
            }
        }

        /** 按玩家持久化偏好排序聚合条目 (名称 / 存量, 正序 / 倒序)。 */
        private fun sortEntries(list: List<Pair<ItemKey, Long>>): List<Pair<ItemKey, Long>> {
            val cmp = when (TerminalSortState.field(player)) {
                TerminalSortState.Field.NAME -> compareBy<Pair<ItemKey, Long>> { sortName(it.first) }
                TerminalSortState.Field.AMOUNT -> compareBy { it.second }
            }
            val sorted = list.sortedWith(cmp)
            return if (TerminalSortState.descending(player)) sorted.asReversed() else sorted
        }

        /** 取物品用于排序的名称: 有自定义名取其纯文本, 否则回退到材质名; 统一小写。 */
        private fun sortName(key: ItemKey): String {
            val name = key.template.itemMeta?.displayName()
                ?.let { PlainTextComponentSerializer.plainText().serialize(it) }
                ?: key.template.type.name
            return name.lowercase()
        }

        private fun withdraw(player: Player, key: ItemKey, amount: Int) {
            val got = net.extract(key, amount.toLong()).toInt()
            if (got <= 0) return
            player.inventory.addItem(key.toDisplay(got)).values
                .forEach { player.world.dropItemNaturally(player.location, it) }
            render()
        }

        /** 从玩家**点击的那一槽** [slot] 精确扣除实际入库数量 (点哪组扣哪组)。 */
        private fun deposit(player: Player, slot: Int, item: ItemStack?) {
            if (item == null || item.type.isAir) return
            val key = ItemKey.of(item) ?: return
            val leftover = net.insert(key, item.amount.toLong())
            val stored = item.amount - leftover.toInt()
            if (stored > 0) {
                top.maplex.slimeEasy.storage.core.InventoryOps.removeFromSlot(player, slot, key, stored)
                render(resort = true) // 存入属主动操作, 按排序归位
            }
        }
    }
}
