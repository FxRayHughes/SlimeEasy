package top.maplex.slimeEasy.storage.network

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.machine.common.MachineProtection
import top.maplex.slimeEasy.storage.core.FilterMenu
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.upgrade.FaceConfig
import top.maplex.slimeEasy.storage.upgrade.ItemFilter

/**
 * 网络输入 / 输出端口。
 *
 * 端口同时提供玩家可打开的单槽缓冲、Slimefun 货运桥接和可选的主动相邻 IO。
 * 三类入口统一受方向过滤器约束；主动 IO 仍由 [NetworkController] 的唯一 ticker 调度，
 * 端口自身不注册 ticker，避免网络规模增大时产生重复扫描。
 */
class NetworkPort(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>,
    val isInput: Boolean
) : SlimefunItem(itemGroup, item, recipeType, recipe), InventoryBlock {

    override fun getInputSlots(): IntArray = if (isInput) intArrayOf(BUFFER_SLOT) else IntArray(0)
    override fun getOutputSlots(): IntArray = if (isInput) IntArray(0) else intArrayOf(BUFFER_SLOT)

    override fun preRegister() {
        PortPreset()
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) {
                MachineProtection.recordOwner(e.block, e.player)
                NetworkPortIO.markPlaced(e.block, isInput)
                NetworkRegistry.invalidateAll()
            }
        })
        addItemHandler(object : BlockBreakHandler(false, false) {
            override fun onPlayerBreak(e: BlockBreakEvent, tool: ItemStack, drops: MutableList<ItemStack>) {
                val network = NetworkRegistry.findByPort(e.block)
                drops.addAll(NetworkPortIO.clearBuffer(network, e.block, isInput))
                NetworkPortIO.clearRuntime(e.block)
                NetworkRegistry.invalidateAll()
            }
        })
    }

    private inner class PortPreset : BlockMenuPreset(
        id,
        I18n.text(if (isInput) "menus.network-port.input-title" else "menus.network-port.output-title")
    ) {
        override fun init() {
            val reserved = intArrayOf(STATUS_SLOT, MODE_SLOT, BUFFER_SLOT, FILTER_SLOT)
            drawBackground(GuiItems.BACKGROUND, (0 until 27).filter { it !in reserved }.toIntArray())
            setPlayerInventoryClickable(true)
        }

        override fun canOpen(block: Block, player: Player): Boolean = true

        /** 静态协议只声明方向；动态重载负责按方块模式和来料过滤。 */
        override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray = when {
            isInput && flow == ItemTransportFlow.INSERT -> intArrayOf(BUFFER_SLOT)
            !isInput && flow == ItemTransportFlow.WITHDRAW -> intArrayOf(BUFFER_SLOT)
            else -> IntArray(0)
        }

        override fun getSlotsAccessedByItemTransport(
            menu: DirtyChestMenu,
            flow: ItemTransportFlow,
            item: ItemStack?
        ): IntArray {
            val block = (menu as? BlockMenu)?.block ?: return IntArray(0)
            if (!PortMode.read(block).cargoEnabled) return IntArray(0)
            if (isInput) {
                if (flow != ItemTransportFlow.INSERT || item == null || !filter().allows(block.location, item)) {
                    return IntArray(0)
                }
            } else if (flow != ItemTransportFlow.WITHDRAW) {
                return IntArray(0)
            }
            return intArrayOf(BUFFER_SLOT)
        }

        // Slimefun 可能通过 Block 或 Location 重载克隆菜单，两条路径必须安装同一组按位置 handler。
        override fun newInstance(menu: BlockMenu, block: Block) = setup(menu, block)
        override fun newInstance(menu: BlockMenu, location: Location) = setup(menu, location.block)

        @Suppress("DEPRECATION")
        private fun setup(menu: BlockMenu, block: Block) {
            render(menu, block)
            menu.addMenuClickHandler(STATUS_SLOT, ChestMenuUtils.getEmptyClickHandler())
            menu.addMenuClickHandler(MODE_SLOT, ChestMenu.MenuClickHandler { player, _, _, _ ->
                val network = NetworkRegistry.findByPort(block)
                val drops = NetworkPortIO.clearBuffer(network, block, isInput)
                NetworkPortIO.dropAt(block, drops)
                if (drops.isNotEmpty()) player.sendMessage(I18n.text("messages.network-port.buffer-dropped"))
                PortMode.write(block, PortMode.read(block).next())
                render(menu, block)
                false
            })
            menu.addMenuClickHandler(FILTER_SLOT, ChestMenu.MenuClickHandler { player, _, _, _ ->
                FilterMenu.open(
                    block,
                    player,
                    filter(),
                    faceConfig(),
                    I18n.text(if (isInput) "menus.upgrades.extract-title" else "menus.upgrades.output-title")
                ) {
                    val dropped = NetworkPortIO.sanitizeBuffer(NetworkRegistry.findByPort(block), block, isInput)
                    if (dropped) player.sendMessage(I18n.text("messages.network-port.buffer-dropped"))
                    render(menu, block)
                }
                false
            })
            menu.addMenuClickHandler(BUFFER_SLOT, bufferClickHandler(block))
            menu.addPlayerInventoryClickHandler { _, _, item, action ->
                when {
                    !action.isShiftClicked -> true
                    !isInput -> false
                    item == null || item.type.isAir -> true
                    else -> filter().allows(block.location, item)
                }
            }
        }

        /** 输入槽允许双向手动操作；输出槽只允许把已有物品取走，禁止光标/数字键写入。 */
        @Suppress("DEPRECATION")
        private fun bufferClickHandler(block: Block): ChestMenu.AdvancedMenuClickHandler =
            object : ChestMenu.AdvancedMenuClickHandler {
                override fun onClick(
                    player: Player,
                    slot: Int,
                    item: ItemStack?,
                    action: me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction
                ): Boolean = true

                override fun onClick(
                    event: org.bukkit.event.inventory.InventoryClickEvent,
                    player: Player,
                    slot: Int,
                    cursor: ItemStack?,
                    action: me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction
                ): Boolean {
                    if (isInput) {
                        return cursor == null || cursor.type.isAir || filter().allows(block.location, cursor)
                    }
                    return event.action in OUTPUT_TAKE_ACTIONS
                }
            }

        /** 重绘只改动态按钮，不覆盖功能缓冲槽中的真实物品。 */
        private fun render(menu: BlockMenu, block: Block) {
            val mode = PortMode.read(block)
            // GuiItems.localized 会在替换占位符后统一解析 `&`，此处必须保留原始颜色码，
            // 不能先用 I18n.text 转成 `§` 再嵌入，否则嵌套状态颜色无法被同一解析器识别。
            val role = I18n.raw(if (isInput) "names.network-port-role.input" else "names.network-port-role.output")
            val modeName = I18n.raw("names.port-mode.${mode.name.lowercase()}")
            val networkState = I18n.raw(
                if (NetworkRegistry.findByPort(block) != null) "names.network-status.connected"
                else "names.network-status.disconnected"
            )
            menu.replaceExistingItem(STATUS_SLOT, GuiItems.localized(
                if (isInput) Material.HOPPER else Material.DROPPER,
                "menus.network-port.status",
                "role" to role,
                "mode" to modeName,
                "network" to networkState,
                "cargo" to stateName(mode.cargoEnabled),
                "active" to stateName(mode.activeEnabled)
            ))
            menu.replaceExistingItem(MODE_SLOT, GuiItems.localized(
                modeMaterial(mode), "menus.network-port.mode", "mode" to modeName
            ))
            menu.replaceExistingItem(
                FILTER_SLOT,
                if (isInput) GuiItems.EXTRACT_CONFIG else GuiItems.OUTPUT_CONFIG
            )
        }

        private fun stateName(enabled: Boolean): String =
            I18n.raw(if (enabled) "names.common.enabled" else "names.common.disabled")

        private fun modeMaterial(mode: PortMode): Material = when (mode) {
            PortMode.CARGO -> Material.CHEST_MINECART
            PortMode.ACTIVE -> Material.PISTON
            PortMode.BOTH -> Material.COMPARATOR
        }

        private fun filter(): ItemFilter = if (isInput) ItemFilter.EXTRACT else ItemFilter.OUTPUT
        private fun faceConfig(): FaceConfig = if (isInput) FaceConfig.EXTRACT else FaceConfig.OUTPUT
    }

    companion object {
        /** 固定 27 格 UI 槽位协议；方块菜单内容会持久化，禁止无迁移地调整。 */
        const val STATUS_SLOT = 4
        const val MODE_SLOT = 11
        const val BUFFER_SLOT = 13
        const val FILTER_SLOT = 15

        private val OUTPUT_TAKE_ACTIONS = setOf(
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_SLOT,
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.COLLECT_TO_CURSOR
        )
    }
}
