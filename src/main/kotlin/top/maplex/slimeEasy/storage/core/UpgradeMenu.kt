package top.maplex.slimeEasy.storage.core

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.drawer.VoidMenu
import top.maplex.slimeEasy.storage.upgrade.UpgradeSet
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.UpgradeType

/**
 * 通用升级管理 GUI (抽屉 / 箱子共用)。
 *
 * 顶部 [UpgradeStore.MAX_SLOTS] 个升级槽以"按钮"方式工作:
 * - 空槽: 手持有效且未重复的升级组件点击 → 安装;
 * - 已装: 点击 → 卸下退回玩家 (卸下堆叠升级若使容量低于现存量则由
 *   [CargoBufferBlock.capacityAllowsRemoval] 拒绝)。
 *
 * 底部在已装虚空升级时提供销毁列表配置入口。容量重算与展示刷新委托给
 * [CargoBufferBlock.onUpgradesChanged], 与具体容器类型解耦。
 */
object UpgradeMenu {

    private val UPGRADE_SLOTS = (0 until UpgradeStore.MAX_SLOTS).toList()
    private const val VOID_SLOT = 13

    fun open(block: CargoBufferBlock, target: Block, player: Player, title: String) {
        val menu = ChestMenu(title)
        menu.setEmptySlotsClickable(false)
        // 点击背包内的升级组件也可安装 (不必手持)
        menu.addPlayerInventoryClickHandler { p, _, item, _ ->
            if (UpgradeType.fromItem(item) != null) install(menu, block, target, p, item)
            false
        }
        render(menu, block, target)
        menu.open(player)
    }

    private fun render(menu: ChestMenu, block: CargoBufferBlock, target: Block) {
        val items = UpgradeStore.readItems(target.location)
        for ((i, slot) in UPGRADE_SLOTS.withIndex()) {
            val installed = items.getOrNull(i)
            if (installed != null) {
                menu.addItem(slot, installed) { p, _, _, _ -> uninstall(menu, block, target, p, i); false }
            } else {
                menu.addItem(slot, GuiItems.UPGRADE_PLACEHOLDER) { p, _, _, _ ->
                    install(menu, block, target, p, p.inventory.itemInMainHand); false
                }
            }
        }
        if (UpgradeStore.resolve(target.location).hasVoid) {
            menu.addItem(VOID_SLOT, GuiItems.VOID_CONFIG) { p, _, _, _ -> VoidMenu.open(target, p); false }
        }
    }

    /**
     * 安装 [source] 指定的升级组件。
     *
     * [source] 既可能是玩家主手, 也可能是背包中被点击的那一组; 统一按物品身份从背包
     * 移除一枚 (不依赖具体槽位), 确保手持与背包点击两条路径行为一致。
     */
    private fun install(menu: ChestMenu, block: CargoBufferBlock, target: Block, player: Player, source: ItemStack) {
        val type = UpgradeType.fromItem(source) ?: return
        val items = UpgradeStore.readItems(target.location).toMutableList()
        if (items.size >= UpgradeStore.MAX_SLOTS) return
        val sameCount = items.count { UpgradeType.fromItem(it) == type }
        if (type.isStackable) {
            // 翻页扩容: 可叠装, 但不超过"抵达最大页数"所需的枚数
            if (sameCount >= UpgradeSet.MAX_PAGES - 1) { player.sendMessage("§c翻页扩容已达上限"); return }
        } else if (sameCount > 0) {
            player.sendMessage("§c该升级已安装, 不可重复"); return
        }
        block.rejectUpgradeChange(target, type, install = true)?.let { player.sendMessage(it); return }
        items.add(source.clone().apply { amount = 1 })
        UpgradeStore.writeItems(target.location, items)
        player.inventory.removeItem(source.clone().apply { amount = 1 })
        afterChange(menu, block, target, player)
    }

    private fun uninstall(menu: ChestMenu, block: CargoBufferBlock, target: Block, player: Player, index: Int) {
        val items = UpgradeStore.readItems(target.location).toMutableList()
        val removed = items.getOrNull(index) ?: return
        val type = UpgradeType.fromItem(removed)
        if (type != null) {
            block.rejectUpgradeChange(target, type, install = false)?.let { player.sendMessage(it); return }
            if (type.isStack) {
                val remaining = items.filterIndexed { i, _ -> i != index }.mapNotNull { UpgradeType.fromItem(it) }.toSet()
                val mult = UpgradeSet(remaining).capacityMultiplier
                if (!block.capacityAllowsRemoval(target, mult)) {
                    player.sendMessage("§c库存过多, 无法卸下该堆叠升级"); return
                }
            }
        }
        items.removeAt(index)
        UpgradeStore.writeItems(target.location, items)
        player.inventory.addItem(removed).values.forEach { player.world.dropItemNaturally(player.location, it) }
        afterChange(menu, block, target, player)
    }

    private fun afterChange(menu: ChestMenu, block: CargoBufferBlock, target: Block, player: Player) {
        block.onUpgradesChanged(target)
        player.updateInventory()
        render(menu, block, target)
    }
}
