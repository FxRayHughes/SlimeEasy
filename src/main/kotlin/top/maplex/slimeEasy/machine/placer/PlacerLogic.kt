package top.maplex.slimeEasy.machine.placer

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.inventory.Inventory

/**
 * 放置机的世界操作逻辑: 从机器箱子取方块, 放置到粘性活塞正前方的空位。
 *
 * 与破坏机相对: 破坏机"取出世界方块存入箱子", 放置机"取出箱子方块放入世界"。
 * 定位活塞与解析朝向的通用逻辑委托给 [PistonSupport]。
 * 所有方法均需在主线程调用 (涉及方块与容器修改)。
 */
object PlacerLogic {

    /** 放置机只接受粘性活塞驱动 (与破坏机的普通/粘性活塞区分)。 */
    val PISTON_TYPES = setOf(Material.STICKY_PISTON)

    /**
     * 尝试从机器箱子取一个方块放置到目标位置。
     *
     * 仅当满足全部条件时执行: 目标可被替换 (空气 / 水 / 草等 [Block.isReplaceable])、
     * 且箱子中存在可放置的方块物品。放置后对应物品数量减一。
     *
     * @return 是否实际放置了方块
     */
    fun tryPlace(machine: Block, target: Block): Boolean {
        if (!target.isReplaceable) return false

        val container = machine.state as? Container ?: return false
        val inventory = container.inventory

        val slot = findPlaceableSlot(inventory) ?: return false
        val item = inventory.getItem(slot) ?: return false

        // 放置: 直接以物品材质设为方块 (使用默认方块数据)
        target.setType(item.type, true)

        // 消耗一个
        val remaining = item.amount - 1
        if (remaining <= 0) {
            inventory.setItem(slot, null)
        } else {
            item.amount = remaining
            inventory.setItem(slot, item)
        }
        return true
    }

    /**
     * 在库存中查找第一个可放置为方块的物品槽位。
     *
     * @return 槽位索引, 无可放置物品返回 null
     */
    private fun findPlaceableSlot(inventory: Inventory): Int? {
        val contents = inventory.storageContents
        for (i in contents.indices) {
            val item = contents[i] ?: continue
            if (isPlaceableBlock(item.type)) return i
        }
        return null
    }

    /** 判断材质是否为可安全放置的方块 (真实方块、非空气、非容器类)。 */
    private fun isPlaceableBlock(type: Material): Boolean {
        if (!type.isBlock || type.isAir) return false
        // 排除容器类, 避免放出铜箱子等带库存方块引发的复杂状态
        return !isContainerType(type)
    }

    /** 判断材质放置后是否会成为容器方块 (箱子、熔炉、漏斗等)。 */
    private fun isContainerType(type: Material): Boolean {
        val name = type.name
        return name.endsWith("CHEST") || name.endsWith("SHULKER_BOX") ||
            name == "HOPPER" || name == "BARREL" || name == "DISPENSER" ||
            name == "DROPPER" || name.endsWith("FURNACE") || name == "BREWING_STAND"
    }
}
