package top.maplex.slimeEasy.storage.drawer

import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.storage.upgrade.VoidFilter

/**
 * 抽屉的物品存取结算 (由 [DrawerListener] 的交互事件调用)。
 *
 * 存入受虚空过滤约束; 取出把物品加入玩家背包, 溢出部分掉落在脚下。
 * 全部操作在主线程进行。
 */
object DrawerInteract {

    /** 右键单击: 存入手中该组物品。 */
    fun depositHand(drawer: Drawer, block: Block, player: Player, hand: ItemStack) {
        if (hand.type.isAir) return
        deposit(drawer, block, player, listOf(hand))
    }

    /** 右键双击: 存入背包内所有与手中 (或抽屉已锁定) 同类的物品。 */
    fun depositAll(drawer: Drawer, block: Block, player: Player, hand: ItemStack?) {
        val storage = drawer.storageAt(block)
        val lockKey = storage.entries().firstOrNull()?.first ?: ItemKey.of(hand) ?: return
        val matching = player.inventory.contents.filterNotNull().filter { lockKey.matches(it) }
        deposit(drawer, block, player, matching)
    }

    private fun deposit(drawer: Drawer, block: Block, player: Player, stacks: List<ItemStack>) {
        val storage = drawer.storageAt(block)
        val hasVoid = UpgradeStore.resolve(block.location).hasVoid
        var storageChanged = false
        var handChanged = false
        for (stack in stacks) {
            if (stack.type.isAir || stack.amount <= 0) continue
            drawer.refreshCapacity(block, storage, stack)
            val key = ItemKey.of(stack) ?: continue
            // 虚空过滤: 封顶到保留数量, 超出部分湮灭 (未标记则原样尝试入库)
            val admit = if (hasVoid) VoidFilter.admit(block.location, stack, storage.count(key), stack.amount.toLong())
                        else stack.amount.toLong()
            val leftover = if (admit > 0) storage.insert(stack, admit, simulate = false).toInt() else 0
            if (admit - leftover > 0) storageChanged = true // 真正入库 (排除仅湮灭)
            if (leftover != stack.amount) { stack.amount = leftover; handChanged = true } // 剩余 = 未入库部分 (湮灭部分已消失)
        }
        if (storageChanged) drawer.saveStorage(block, storage)
        if (handChanged) player.updateInventory()
    }

    /** 左键: 取出一个。 */
    fun withdrawOne(drawer: Drawer, block: Block, player: Player) = withdraw(drawer, block, player, 1)

    /** Shift+左键: 取出一组 (按物品原版堆叠上限)。 */
    fun withdrawStack(drawer: Drawer, block: Block, player: Player) {
        val key = drawer.storageAt(block).entries().firstOrNull()?.first ?: return
        withdraw(drawer, block, player, key.vanillaMaxStack)
    }

    private fun withdraw(drawer: Drawer, block: Block, player: Player, amount: Int) {
        val storage = drawer.storageAt(block)
        val key = storage.entries().firstOrNull()?.first ?: return
        val taken = storage.extract(key, amount.toLong(), simulate = false).toInt()
        if (taken <= 0) return
        val overflow = player.inventory.addItem(key.toDisplay(taken))
        overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }
        drawer.saveStorage(block, storage)
    }
}
