package top.maplex.slimeEasy.storage.network

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.util.BlockLocationCodec

/**
 * 远程终端 (手持工具)。
 *
 * 用法:
 * - **手持右键网络控制器**: 把控制器追加到本终端的绑定列表 (绑定信息存于物品 PDC);
 * - **手持右键空气 / 其它方块**: 远程打开当前选中的网络终端 ([NetworkMenu]);
 * - **潜行右键**: 打开绑定管理界面, 可选择或移除控制器;
 * - 远程终端底栏中“下一页”左侧的按钮可轮换绑定的控制器,
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
            if (player.isSneaking) openManager(player, item)
            else if (block != null && NetworkControllerAccess.isController(block)) bind(player, item, block)
            else openBound(player, item)
        })
    }

    /** 把控制器追加到终端绑定列表; 已存在时仅切换为当前控制器。 */
    private fun bind(player: Player, item: ItemStack, controller: Block) {
        if (!NetworkControllerAccess.canUse(player, controller)) return
        val loc = controller.location
        val value = BlockLocationCodec.encode(controller)
        val bindings = readBindings(item)
        val existing = bindings.indexOf(value)
        val maxBindings = SEConfig.storageNetworkRemoteTerminalMaxBindings
        if (existing < 0 && maxBindings > 0 && bindings.size >= maxBindings) {
            player.sendMessage(I18n.text("messages.remote-terminal.binding-limit", "max" to maxBindings))
            return
        }
        val selected = if (existing >= 0) existing else bindings.size.also { bindings.add(value) }
        writeBindings(item, bindings, selected)
        val action = if (existing >= 0) I18n.text("messages.remote-terminal.actions.switched") else I18n.text("messages.remote-terminal.actions.bound")
        player.sendMessage(I18n.text(
            "messages.remote-terminal.bound",
            "action" to action, "x" to loc.blockX, "y" to loc.blockY, "z" to loc.blockZ, "count" to bindings.size
        ))
    }

    /** 打开当前选中的网络；未绑定、绑定失效、物品权限或控制器位置保护不通过时提示。 */
    private fun openBound(player: Player, item: ItemStack) {
        val bindings = readBindings(item)
        if (bindings.isEmpty()) {
            player.sendMessage(I18n.text("messages.remote-terminal.not-bound"))
            return
        }
        val selected = selectedIndex(item, bindings.size)
        val raw = bindings[selected]
        val block = BlockLocationCodec.decode(raw)
        if (block == null || !NetworkControllerAccess.isController(block)) {
            player.sendMessage(I18n.text("messages.remote-terminal.missing-controller"))
            return
        }
        if (!NetworkControllerAccess.canUse(player, block)) return
        val switcher = if (bindings.size > 1) ({ p: Player -> switchBound(p, item) }) else null
        NetworkMenu.open(NetworkRegistry.get(block), player, switcher)
    }

    /** 切换到下一个仍然有效且玩家同时通过物品权限与控制器位置保护的控制器。 */
    private fun switchBound(player: Player, item: ItemStack) {
        val bindings = readBindings(item)
        if (bindings.size <= 1) {
            player.sendMessage(I18n.text("messages.remote-terminal.no-other-binding"))
            return
        }
        val current = selectedIndex(item, bindings.size)
        for (offset in 1 until bindings.size) {
            val next = (current + offset) % bindings.size
            val block = BlockLocationCodec.decode(bindings[next]) ?: continue
            if (!NetworkControllerAccess.isController(block) || !NetworkControllerAccess.canUse(player, block, false)) continue
            writeBindings(item, bindings, next)
            player.sendMessage(I18n.text("messages.remote-terminal.switched", "controller" to describe(block)))
            NetworkMenu.open(NetworkRegistry.get(block), player) { p -> switchBound(p, item) }
            return
        }
        player.sendMessage(I18n.text("messages.remote-terminal.no-accessible-binding"))
    }

    /** 打开绑定管理界面: 左键选择, 右键移除。 */
    private fun openManager(player: Player, item: ItemStack) {
        ManagerView(player, item).menu.open(player)
    }

    private inner class ManagerView(private val player: Player, private val item: ItemStack) {
        val menu = ChestMenu(I18n.text("menus.remote-terminal.title"))
        private var page = 0

        init {
            menu.setEmptySlotsClickable(false)
            menu.setPlayerInventoryClickable(false)
            render()
        }

        private fun render() {
            val bindings = readBindings(item)
            val selected = selectedIndex(item, bindings.size)
            val pages = maxOf(1, (bindings.size + MANAGER_PAGE_SIZE - 1) / MANAGER_PAGE_SIZE)
            page = page.coerceIn(0, pages - 1)
            val from = page * MANAGER_PAGE_SIZE
            for (slot in 0 until MANAGER_PAGE_SIZE) {
                val index = from + slot
                val raw = bindings.getOrNull(index)
                if (raw == null) {
                    menu.addItem(slot, GuiItems.BACKGROUND) { _, _, _, _ -> false }
                    continue
                }
                val block = BlockLocationCodec.decode(raw)
                val valid = block != null && NetworkControllerAccess.isController(block)
                val icon = when {
                    !valid -> GuiItems.localized(Material.BARRIER, "menus.remote-terminal.invalid", "location" to raw)
                    index == selected -> GuiItems.localized(Material.LIME_STAINED_GLASS_PANE, "menus.remote-terminal.selected", "controller" to describe(block))
                    else -> GuiItems.localized(Material.ENDER_CHEST, "menus.remote-terminal.binding", "controller" to describe(block))
                }
                menu.addItem(slot, icon) { _, _, _, action ->
                    if (action.isRightClicked) removeBinding(index) else selectBinding(index)
                    false
                }
            }
            for (slot in MANAGER_PAGE_SIZE until 54) {
                menu.addItem(slot, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            }
            menu.addItem(MANAGER_PREV_SLOT, GuiItems.PREV_PAGE) { _, _, _, _ ->
                if (page > 0) { page--; render() }
                false
            }
            menu.addItem(MANAGER_INFO_SLOT, GuiItems.localized(
                Material.PAPER, "menus.remote-terminal.page-info",
                "page" to page + 1, "pages" to pages, "count" to bindings.size
            )) { _, _, _, _ -> false }
            menu.addItem(MANAGER_NEXT_SLOT, GuiItems.NEXT_PAGE) { _, _, _, _ ->
                if (page < pages - 1) { page++; render() }
                false
            }
        }

        private fun selectBinding(index: Int) {
            val bindings = readBindings(item)
            val raw = bindings.getOrNull(index) ?: return
            val block = BlockLocationCodec.decode(raw)
            if (block == null || !NetworkControllerAccess.isController(block)) {
                player.sendMessage(I18n.text("messages.remote-terminal.invalid-binding"))
                return
            }
            if (!NetworkControllerAccess.canUse(player, block)) return
            writeBindings(item, bindings, index)
            player.sendMessage(I18n.text("messages.remote-terminal.selected", "controller" to describe(block)))
            render()
        }

        private fun removeBinding(index: Int) {
            val bindings = readBindings(item)
            if (index !in bindings.indices) return
            val selected = selectedIndex(item, bindings.size)
            bindings.removeAt(index)
            val nextSelected = when {
                bindings.isEmpty() -> 0
                index < selected -> selected - 1
                selected >= bindings.size -> bindings.lastIndex
                else -> selected
            }
            writeBindings(item, bindings, nextSelected)
            player.sendMessage(I18n.text("messages.remote-terminal.removed", "count" to bindings.size))
            render()
        }
    }

    /** 读取绑定列表。旧版单坐标值天然按一行读入, 无需迁移。 */
    private fun readBindings(item: ItemStack): MutableList<String> {
        val raw = item.itemMeta?.persistentDataContainer?.get(KEY_BIND, PersistentDataType.STRING)
        if (raw.isNullOrEmpty()) return mutableListOf()
        return raw.lineSequence().filter { it.isNotEmpty() }.distinct().toMutableList()
    }

    private fun selectedIndex(item: ItemStack, size: Int): Int {
        if (size <= 0) return 0
        val stored = item.itemMeta?.persistentDataContainer?.get(KEY_SELECTED, PersistentDataType.INTEGER) ?: 0
        return stored.coerceIn(0, size - 1)
    }

    private fun writeBindings(item: ItemStack, bindings: List<String>, selected: Int) {
        item.editMeta { meta ->
            val pdc = meta.persistentDataContainer
            if (bindings.isEmpty()) {
                pdc.remove(KEY_BIND)
                pdc.remove(KEY_SELECTED)
            } else {
                pdc.set(KEY_BIND, PersistentDataType.STRING, bindings.joinToString("\n"))
                pdc.set(KEY_SELECTED, PersistentDataType.INTEGER, selected.coerceIn(bindings.indices))
            }
        }
    }

    private fun describe(block: Block): String =
        "${block.world.name} (${block.x}, ${block.y}, ${block.z})"

    companion object {
        private const val MANAGER_PAGE_SIZE = 45
        private const val MANAGER_PREV_SLOT = 45
        private const val MANAGER_INFO_SLOT = 49
        private const val MANAGER_NEXT_SLOT = 53

        /** 绑定信息在物品 PDC 中的键 (每行一个 "world;x;y;z", 兼容旧单值)。 */
        private val KEY_BIND = NamespacedKey(SlimeEasy.instance, "terminal_bind")
        private val KEY_SELECTED = NamespacedKey(SlimeEasy.instance, "terminal_selected")
    }
}
