package top.maplex.slimeEasy.feature.goggles

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.registry.Items
import top.maplex.slimeEasy.storage.core.GuiItems

/** 工程师护目镜的分类、功能、物品类型、附属与工作状态多选过滤界面。 */
internal object EngineerGogglesFilterMenu {

    /**
     * 54 格固定槽位协议：0..44 是当前分页内容，45/53 翻页，46/47/50/51/52 切换五类筛选，
     * 48 重置，49 显示汇总。修改这些槽位时必须同步导航渲染，避免内容页覆盖控制按钮。
     */
    private const val PAGE_SIZE = 45
    private const val PREVIOUS_SLOT = 45
    private const val GROUP_TAB_SLOT = 46
    private const val ITEM_TAB_SLOT = 47
    private const val RESET_SLOT = 48
    private const val SUMMARY_SLOT = 49
    private const val ADDON_TAB_SLOT = 50
    private const val STATE_TAB_SLOT = 51
    private const val FUNCTION_TAB_SLOT = 52
    private const val NEXT_SLOT = 53

    private enum class Mode { GROUPS, ITEMS, ADDONS, FUNCTIONS, STATES }

    /** 打开绑定于玩家主手护目镜的过滤界面；物品离开主手后界面拒绝继续写入。 */
    fun open(player: Player) {
        if (heldGoggles(player) == null) {
            player.sendActionBar(I18n.component("messages.engineer-goggles.must-hold"))
            return
        }
        View(player).open()
    }

    private class View(private val player: Player) {
        private val menu = ChestMenu(I18n.text("menus.engineer-goggles.title"))
        private var mode = Mode.GROUPS
        private var page = 0

        init {
            menu.setEmptySlotsClickable(false)
            menu.setPlayerInventoryClickable(false)
        }

        fun open() {
            render()
            menu.open(player)
        }

        private fun render() {
            val goggles = heldGoggles(player) ?: run {
                player.closeInventory()
                player.sendActionBar(I18n.component("messages.engineer-goggles.must-hold"))
                return
            }
            val snapshot = EngineerGogglesFilter.read(goggles)
            val itemTypes = itemTypes()
            val groups = groups(itemTypes)
            val addons = itemTypes.map { it.addon.name }.distinct().sorted()
            val entryCount = when (mode) {
                Mode.GROUPS -> groups.size
                Mode.ITEMS -> itemTypes.size
                Mode.ADDONS -> addons.size
                Mode.FUNCTIONS -> EngineerGogglesFunction.entries.size
                Mode.STATES -> EngineerGogglesWorkState.entries.size
            }
            val pageCount = ((entryCount + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
            page = page.coerceIn(0, pageCount - 1)

            for (slot in 0 until 54) {
                menu.addItem(slot, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            }
            when (mode) {
                Mode.GROUPS -> renderGroups(snapshot, groups)
                Mode.ITEMS -> renderItems(snapshot, itemTypes)
                Mode.ADDONS -> renderAddons(snapshot, addons)
                Mode.FUNCTIONS -> renderFunctions(snapshot)
                Mode.STATES -> renderStates(snapshot)
            }
            renderNavigation(snapshot, groups.size, itemTypes.size, addons.size, pageCount)
        }

        /** Slimefun 分类图标沿用指南中的本地化图标，只追加本护目镜的启用状态。 */
        private fun renderGroups(snapshot: EngineerGogglesFilter.Snapshot, groups: List<ItemGroup>) {
            groups.page().forEachIndexed { index, group ->
                val groupKey = group.key.toString()
                val enabled = groupKey !in snapshot.disabledGroups
                val icon = appendState(group.getItem(player).clone(), enabled)
                menu.addItem(index, icon) { clicked, _, _, _ ->
                    toggleHeld(clicked) { EngineerGogglesFilter.toggleGroup(it, groupKey) }
                    false
                }
            }
        }

        /** 单项页列出全部可作为方块或多方块目标的物品类型，与潜行右键共享隐藏集合。 */
        private fun renderItems(snapshot: EngineerGogglesFilter.Snapshot, items: List<SlimefunItem>) {
            items.page().forEachIndexed { index, item ->
                val enabled = item.id !in snapshot.hiddenItems
                val icon = appendState(item.item.clone(), enabled)
                menu.addItem(index, icon) { clicked, _, _, _ ->
                    toggleHeld(clicked) { EngineerGogglesFilter.toggleItem(it, item.id) }
                    false
                }
            }
        }

        private fun renderAddons(snapshot: EngineerGogglesFilter.Snapshot, addons: List<String>) {
            addons.page().forEachIndexed { index, addon ->
                val enabled = addon !in snapshot.disabledAddons
                val icon = GuiItems.localized(
                    Material.BOOK,
                    "menus.engineer-goggles.addon",
                    "addon" to addon,
                    "state" to state(enabled)
                )
                menu.addItem(index, icon) { clicked, _, _, _ ->
                    toggleHeld(clicked) { EngineerGogglesFilter.toggleAddon(it, addon) }
                    false
                }
            }
        }

        private fun renderFunctions(snapshot: EngineerGogglesFilter.Snapshot) {
            EngineerGogglesFunction.entries.page().forEachIndexed { index, function ->
                val enabled = function.filterKey !in snapshot.disabledFunctions
                val icon = GuiItems.localized(
                    function.icon,
                    "menus.engineer-goggles.functions.${function.filterKey}",
                    "state" to state(enabled)
                )
                menu.addItem(index, icon) { clicked, _, _, _ ->
                    toggleHeld(clicked) { EngineerGogglesFilter.toggleFunction(it, function) }
                    false
                }
            }
        }

        private fun renderStates(snapshot: EngineerGogglesFilter.Snapshot) {
            EngineerGogglesWorkState.entries.page().forEachIndexed { index, workState ->
                val enabled = workState.filterKey !in snapshot.disabledStates
                val icon = GuiItems.localized(
                    workState.icon,
                    "menus.engineer-goggles.states.${workState.filterKey}",
                    "state" to state(enabled)
                )
                menu.addItem(index, icon) { clicked, _, _, _ ->
                    toggleHeld(clicked) { EngineerGogglesFilter.toggleState(it, workState) }
                    false
                }
            }
        }

        private fun renderNavigation(
            snapshot: EngineerGogglesFilter.Snapshot,
            groupCount: Int,
            itemCount: Int,
            addonCount: Int,
            pageCount: Int
        ) {
            menu.addItem(PREVIOUS_SLOT, GuiItems.PREV_PAGE) { _, _, _, _ ->
                if (page > 0) {
                    page--
                    render()
                }
                false
            }
            menu.addItem(NEXT_SLOT, GuiItems.NEXT_PAGE) { _, _, _, _ ->
                if (page + 1 < pageCount) {
                    page++
                    render()
                }
                false
            }
            tab(GROUP_TAB_SLOT, Mode.GROUPS, "groups")
            tab(ITEM_TAB_SLOT, Mode.ITEMS, "items")
            tab(ADDON_TAB_SLOT, Mode.ADDONS, "addons")
            tab(STATE_TAB_SLOT, Mode.STATES, "states")
            tab(FUNCTION_TAB_SLOT, Mode.FUNCTIONS, "functions")
            menu.addItem(RESET_SLOT, GuiItems.localized(Material.BARRIER, "menus.engineer-goggles.reset")) {
                    clicked, _, _, _ ->
                toggleHeld(clicked) {
                    EngineerGogglesFilter.reset(it)
                    true
                }
                false
            }
            menu.addItem(
                SUMMARY_SLOT,
                GuiItems.localized(
                    Material.PAPER,
                    "menus.engineer-goggles.summary",
                    "groups" to (groupCount - snapshot.disabledGroups.size).coerceAtLeast(0),
                    "groupTotal" to groupCount,
                    "items" to (itemCount - snapshot.hiddenItems.size).coerceAtLeast(0),
                    "itemTotal" to itemCount,
                    "addons" to (addonCount - snapshot.disabledAddons.size).coerceAtLeast(0),
                    "addonTotal" to addonCount,
                    "functions" to enabledFunctions(snapshot),
                    "functionTotal" to EngineerGogglesFunction.entries.size,
                    "states" to enabledStates(snapshot),
                    "stateTotal" to EngineerGogglesWorkState.entries.size,
                    "page" to page + 1,
                    "pages" to pageCount
                )
            ) { _, _, _, _ -> false }
        }

        private fun tab(slot: Int, target: Mode, key: String) {
            val icon = GuiItems.localized(tabMaterial(target), "menus.engineer-goggles.tabs.$key")
            menu.addItem(slot, icon) { _, _, _, _ ->
                mode = target
                page = 0
                render()
                false
            }
        }

        /** 点击期间再次校验主手身份，防止数字键换物后把过滤数据写入其它物品。 */
        private fun toggleHeld(player: Player, operation: (ItemStack) -> Boolean) {
            val goggles = heldGoggles(player) ?: run {
                player.closeInventory()
                player.sendActionBar(I18n.component("messages.engineer-goggles.must-hold"))
                return
            }
            operation(goggles)
            player.inventory.setItemInMainHand(goggles)
            render()
        }

        private fun appendState(item: ItemStack, enabled: Boolean): ItemStack = item.apply {
            editMeta { meta ->
                val lore = meta.lore()?.toMutableList() ?: mutableListOf()
                lore += I18n.components("menus.engineer-goggles.entry-lore", "state" to state(enabled))
                meta.lore(lore)
            }
        }

        private fun tabMaterial(tab: Mode): Material =
            if (mode == tab) Material.LIME_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE

        private fun state(enabled: Boolean): String =
            I18n.raw("names.engineer-goggles.filter.${if (enabled) "shown" else "hidden"}")

        private fun enabledFunctions(snapshot: EngineerGogglesFilter.Snapshot): Int =
            (EngineerGogglesFunction.entries.size - snapshot.disabledFunctions.size).coerceAtLeast(0)

        private fun enabledStates(snapshot: EngineerGogglesFilter.Snapshot): Int =
            (EngineerGogglesWorkState.entries.size - snapshot.disabledStates.size).coerceAtLeast(0)

        private fun <T> List<T>.page(): List<T> = drop(page * PAGE_SIZE).take(PAGE_SIZE)
    }

    /**
     * 从可展示目标反向取得实际分类，再映射回 ItemGroup 注册表并稳定排序。
     * 不得调用 [ItemGroup.getItems]：FlexItemGroup 是指南导航节点，按 API 约定会主动抛出异常。
     */
    private fun groups(items: List<SlimefunItem>): List<ItemGroup> {
        val usedKeys = items.mapTo(HashSet()) { it.itemGroup.key }
        return Slimefun.getRegistry().allItemGroups
            .asSequence()
            .filter { it.key in usedKeys }
            .sortedBy { it.key.toString() }
            .toList()
    }

    /** 单项页排除不能作为世界目标的普通材料/工具，只保留方块物品和注册多方块。 */
    private fun itemTypes(): List<SlimefunItem> {
        val registry = Slimefun.getRegistry()
        val multiblocks = registry.multiBlocks.mapTo(HashSet()) { it.slimefunItem.id }
        return registry.enabledSlimefunItems
            .asSequence()
            .filter { it.item.type.isBlock || it.id in multiblocks }
            .sortedWith(compareBy<SlimefunItem> { it.itemGroup.key.toString() }.thenBy { it.id })
            .toList()
    }

    private fun heldGoggles(player: Player): ItemStack? =
        player.inventory.itemInMainHand.takeIf {
            SlimefunItem.getByItem(it)?.id == Items.ENGINEER_GOGGLES_ID
        }
}
