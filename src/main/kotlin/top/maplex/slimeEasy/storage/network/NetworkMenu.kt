package top.maplex.slimeEasy.storage.network

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.libraries.dough.chat.ChatInput
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.SlimeEasy
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
 * GUI 打开时对网络拓扑做一次快照 (来自 [NetworkRegistry] 缓存); 库存读写实时。控制器访问权在
 * 打开、重绘以及每次存取前重新校验，撤权后旧菜单不能继续操作。
 */
object NetworkMenu {

    private const val PAGE_SIZE = 45
    private const val PREV_SLOT = 45
    private const val SORT_FIELD_SLOT = 46
    private const val SORT_DIR_SLOT = 47
    private const val INFO_SLOT = 48
    private const val SEARCH_SLOT = 49
    private const val SWITCH_TERMINAL_SLOT = 52
    private const val NEXT_SLOT = 53

    /** 当前打开中的全部终端视图 (用于库存变更时实时重绘)。 */
    private val openViews = java.util.concurrent.ConcurrentHashMap.newKeySet<View>()

    init {
        // 订阅库存变更: 任一成员库存改动 (本终端 / 别的玩家 / 货运 / 磁铁 / 成员界面)
        // 都重绘所有打开中的终端, 保证实时一致。终端数量少, 全量重绘开销可忽略。
        StorageChangeBus.subscribe { _ -> openViews.toList().forEach { it.render() } }
    }

    /** 所有物理与远程入口都在创建视图前校验控制器，避免调用方遗漏目标位置权限。 */
    fun open(net: StorageNetwork, player: Player, switchTerminal: ((Player) -> Unit)? = null) {
        if (!NetworkControllerAccess.canUse(player, net.controller)) return
        View(net, player, switchTerminal).menu.open(player)
    }

    private class View(
        val net: StorageNetwork,
        val player: Player,
        val switchTerminal: ((Player) -> Unit)?
    ) {
        val menu = ChestMenu(I18n.text("menus.network.title"))
        var page = 0

        /**
         * 冻结的显示顺序 (key 序列)。
         *
         * 取出**不重排**: 沿用本快照, 取空的物品以空占位留在原位、其余位置不动, 避免
         * 连续取出时图标跳动、玩家翻页找不到。仅排序切换 / 存入等操作 ([render] resort=true)
         * 按最新排序偏好重建, 顺带清理取空的占位。
         */
        private var order: MutableList<ItemKey>? = null

        /**
         * 搜索关键词 (瞬时视图状态, 不持久化)。空串表示无过滤。
         *
         * 纯显示层过滤: 仅影响本次渲染筛选出的 key, 不改动冻结顺序 [order] —— 清空搜索后
         * 原布局原样恢复。符合"搜索是临时筛选"的语义, 故不写入玩家 PDC。
         */
        private var filter: String = ""

        // init 须在上述属性声明之后: init 中的 render() 会读 filter/order, Kotlin 按声明顺序
        // 初始化, 声明在 init 之后则执行时它们尚为 JVM 默认值 (非空 filter 读到 null → NPE)。
        init {
            menu.setEmptySlotsClickable(false)
            menu.setPlayerInventoryClickable(true)
            menu.addPlayerInventoryClickHandler { p, slot, item, _ -> deposit(p, slot, item); false }
            openViews.add(this)
            menu.addMenuCloseHandler { openViews.remove(this) }
            render()
        }

        /**
         * 重绘界面。
         *
         * @param resort true 时按当前排序偏好重建显示顺序 (清理取空占位); false (默认) 沿用
         *   冻结顺序, 仅刷新数量、取空处留空占位、外部新增物品追加末尾 —— 保证连续取出时
         *   其余物品位置稳定。
         */
        fun render(resort: Boolean = false) {
            // 权限可能在菜单打开后被即时收回；任何重绘都顺便关闭已失效的远程视图。
            if (!ensureAccess(message = false)) return
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

            // 纯显示层过滤: 从冻结顺序中筛出名称匹配关键词者 (不改动 order 本身);
            // 无关键词时即完整顺序。取空占位 (总量 0) 在有搜索时一并剔除, 避免搜索结果留空格。
            val view = if (filter.isEmpty()) order
            else order.filter { (amountByKey[it] ?: 0L) > 0 && sortName(it).contains(filter) }

            val pages = maxOf(1, (view.size + PAGE_SIZE - 1) / PAGE_SIZE)
            page = page.coerceIn(0, pages - 1)
            val from = page * PAGE_SIZE
            for (i in 0 until PAGE_SIZE) {
                val key = view.getOrNull(from + i)
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
            menu.addItem(INFO_SLOT, GuiItems.localized(
                org.bukkit.Material.PAPER, "menus.network.page-info",
                "page" to page + 1, "pages" to pages, "members" to net.members.size
            )) { _, _, _, _ -> false }
            if (switchTerminal != null) {
                menu.addItem(SWITCH_TERMINAL_SLOT, GuiItems.localized(
                    org.bukkit.Material.ENDER_EYE, "menus.network.switch-terminal"
                )) { p, _, _, _ ->
                    switchTerminal.invoke(p)
                    false
                }
            }
            renderSortButtons()
            renderSearchButton()
        }

        /** 渲染搜索按钮: 左键输入关键词, 有关键词时右键清空。 */
        private fun renderSearchButton() {
            val icon = if (filter.isEmpty())
                GuiItems.localized(org.bukkit.Material.SPYGLASS, "menus.network.search.idle")
            else
                GuiItems.localized(org.bukkit.Material.SPYGLASS, "menus.network.search.active", "query" to filter)
            menu.addItem(SEARCH_SLOT, icon) { _, _, _, act ->
                if (filter.isNotEmpty() && act.isRightClicked) { filter = ""; page = 0; render() }
                else promptSearch()
                false
            }
        }

        /**
         * 关闭界面并等待玩家在聊天栏输入搜索关键词。
         *
         * 复用 Slimefun 自带的 [ChatInput] (其指南搜索同款输入机制)。回调在异步聊天线程,
         * 故 [Bukkit.getScheduler] 切回主线程再操作 GUI: 设关键词、复位到首页、重开界面。
         * 玩家离线则丢弃。关闭期间本视图已从 [openViews] 移除, 不受库存变更重绘干扰。
         */
        private fun promptSearch() {
            if (!ensureAccess(message = true)) return
            player.sendMessage(I18n.text("messages.network.search-prompt"))
            player.closeInventory()
            ChatInput.waitForPlayer(SlimeEasy.instance, player) { input ->
                Bukkit.getScheduler().runTask(SlimeEasy.instance, Runnable {
                    if (!player.isOnline) return@Runnable
                    if (!NetworkControllerAccess.canUse(player, net.controller)) return@Runnable
                    filter = if (input.equals("cancel", ignoreCase = true)) "" else input.trim().lowercase()
                    page = 0
                    openViews.add(this) // 关界面时 close handler 已移除本视图, 重开前重新登记以恢复实时重绘
                    render()
                    menu.open(player)
                })
            }
        }

        /** 渲染排序控制按钮 (字段切换 + 方向切换); 点击后写回玩家偏好并重绘。 */
        private fun renderSortButtons() {
            val fieldName = if (TerminalSortState.field(player) == TerminalSortState.Field.NAME) I18n.text("names.sort-field.name") else I18n.text("names.sort-field.amount")
            menu.addItem(SORT_FIELD_SLOT, GuiItems.localized(
                org.bukkit.Material.NAME_TAG, "menus.network.sort-field", "field" to fieldName
            )) { _, _, _, _ ->
                TerminalSortState.cycleField(player); render(resort = true); false
            }
            val dirName = if (TerminalSortState.descending(player)) I18n.text("names.sort-direction.descending") else I18n.text("names.sort-direction.ascending")
            menu.addItem(SORT_DIR_SLOT, GuiItems.localized(
                org.bukkit.Material.COMPARATOR, "menus.network.sort-direction", "direction" to dirName
            )) { _, _, _, _ ->
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
            if (!ensureAccess(message = true)) return
            val got = net.extract(key, amount.toLong()).toInt()
            if (got <= 0) return
            player.inventory.addItem(key.toDisplay(got)).values
                .forEach { player.world.dropItemNaturally(player.location, it) }
            render()
        }

        /** 从玩家**点击的那一槽** [slot] 精确扣除实际入库数量 (点哪组扣哪组)。 */
        private fun deposit(player: Player, slot: Int, item: ItemStack?) {
            if (!ensureAccess(message = true)) return
            if (item == null || item.type.isAir) return
            val key = ItemKey.of(item) ?: return
            val leftover = net.insert(key, item.amount.toLong())
            val stored = item.amount - leftover.toInt()
            if (stored > 0) {
                top.maplex.slimeEasy.storage.core.InventoryOps.removeFromSlot(player, slot, key, stored)
                render(resort = true) // 存入属主动操作, 按排序归位
            }
        }

        /** 拒绝后立即移除并关闭视图，防止撤权玩家继续读取或修改网络库存。 */
        private fun ensureAccess(message: Boolean): Boolean {
            if (NetworkControllerAccess.canUse(player, net.controller, message)) return true
            openViews.remove(this)
            player.closeInventory()
            return false
        }
    }
}
