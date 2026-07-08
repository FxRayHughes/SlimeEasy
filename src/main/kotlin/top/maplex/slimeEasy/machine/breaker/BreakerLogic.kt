package top.maplex.slimeEasy.machine.breaker

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.util.concurrent.ThreadLocalRandom

/**
 * 破坏机的世界操作逻辑: 定位活塞、破坏目标方块、回收掉落物。
 *
 * 所有方法均需在主线程调用 (涉及方块与容器修改)。
 */
object BreakerLogic {

    /**
     * 破坏机只接受普通活塞驱动 (与放置机的粘性活塞区分)。
     *
     * 定位活塞与解析朝向的通用逻辑委托给 [PistonSupport], 本对象只保留破坏语义。
     */
    val PISTON_TYPES = setOf(Material.PISTON)

    /**
     * 尝试破坏目标方块并将掉落物收入机器箱子。
     *
     * 挖掘工具规则: 若箱子第 0 格为挖掘类工具, 则以该工具挖掘 (吃时运/精准采集
     * 等附魔), 并按耐久附魔概率消耗 1 点耐久; 工具耐久耗尽后清空该格, 之后自动
     * 回退为默认 (徒手) 挖掘逻辑。
     *
     * 仅当满足全部条件时执行破坏: 目标非空气/非液体、可破坏 (硬度 >= 0)、
     * 且箱子有足够空间容纳全部掉落物 (空间不足则放弃, 避免掉落物洒落)。
     *
     * @return 是否实际破坏了方块
     */
    fun tryBreak(machine: Block, target: Block): Boolean {
        if (!isBreakable(target)) return false

        val container = machine.state as? Container ?: return false
        val inventory = container.inventory

        // 扫描箱内首个挖掘工具用于挖掘 (吃时运/精准采集), 无则走默认 (徒手) 掉落
        val toolSlot = findToolSlot(inventory)
        val tool = toolSlot?.let { inventory.getItem(it) }
        val drops = if (tool != null) target.getDrops(tool) else target.drops

        if (drops.isEmpty()) {
            // 无掉落物 (如草、或工具不匹配) 仍执行破坏, 与原版一致
            target.type = Material.AIR
            consumeDurability(inventory, toolSlot, tool)
            return true
        }

        // 容量预检: 在库存快照上模拟放入, 零溢出才真正写入,
        // 从根本上避免掉落物洒落或部分丢失。
        val snapshot = inventory.storageContents.map { it?.clone() }.toTypedArray()
        if (!fitsInSnapshot(snapshot, drops.map { it.clone() })) return false

        inventory.addItem(*drops.map { it.clone() }.toTypedArray())
        target.type = Material.AIR
        consumeDurability(inventory, toolSlot, tool)
        return true
    }

    /** 判断目标方块是否允许被破坏。 */
    private fun isBreakable(target: Block): Boolean {
        val type = target.type
        if (type.isAir || type == Material.WATER || type == Material.LAVA) return false
        // 硬度小于 0 表示基岩等不可破坏方块
        if (type.hardness < 0) return false
        // 不破坏容器/机器类方块, 避免连锁破坏铜箱子或其他 Slimefun 机器
        if (target.state is Container) return false
        return true
    }

    /** 判断物品是否为挖掘类工具 (镐/斧/锹/锄/剑/剪)。 */
    private fun isDiggingTool(item: ItemStack): Boolean {
        if (item.type.maxDurability <= 0) return false
        val name = item.type.name
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") ||
            name.endsWith("_SHOVEL") || name.endsWith("_HOE") ||
            name.endsWith("_SWORD") || name == "SHEARS"
    }

    /**
     * 扫描库存存储槽, 返回首个挖掘工具的槽位索引; 无则返回 null。
     *
     * 相比固定第 0 格更灵活: 玩家可将工具放在箱内任意位置。
     */
    private fun findToolSlot(inventory: Inventory): Int? {
        val contents = inventory.storageContents
        for (i in contents.indices) {
            val item = contents[i] ?: continue
            if (isDiggingTool(item)) return i
        }
        return null
    }

    /**
     * 消耗指定槽位工具 1 点耐久, 遵循耐久 (Unbreaking) 附魔的免损概率。
     *
     * 耐久耗尽时清空该槽位, 使下次自动改用其余工具或回退默认挖掘。
     *
     * @param slot 工具所在槽位; 为 null (无工具) 时直接返回
     * @param tool 该槽位的工具实例
     */
    private fun consumeDurability(inventory: Inventory, slot: Int?, tool: ItemStack?) {
        if (slot == null || tool == null) return

        // 耐久附魔: 概率 level/(level+1) 免于损耗
        val unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING)
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) return

        val meta = tool.itemMeta as? Damageable ?: return
        val maxDurability = tool.type.maxDurability.toInt()
        if (maxDurability <= 0) return

        val newDamage = meta.damage + 1
        if (newDamage >= maxDurability) {
            // 工具损毁: 清空该槽位
            inventory.setItem(slot, null)
        } else {
            meta.damage = newDamage
            tool.itemMeta = meta
            inventory.setItem(slot, tool)
        }
    }

    /**
     * 在库存快照 (仅存储槽) 上模拟放入全部物品, 判断能否完全容纳。
     *
     * 逐个物品在快照上寻找可堆叠槽或空槽, 修改快照数量, 全部放下返回 true。
     */
    private fun fitsInSnapshot(
        snapshot: Array<ItemStack?>,
        items: List<ItemStack>
    ): Boolean {
        for (item in items) {
            var remaining = item.amount
            val maxStack = item.maxStackSize
            for (i in snapshot.indices) {
                if (remaining <= 0) break
                val slot = snapshot[i]
                if (slot == null) {
                    val put = minOf(remaining, maxStack)
                    snapshot[i] = item.clone().apply { amount = put }
                    remaining -= put
                } else if (slot.isSimilar(item) && slot.amount < maxStack) {
                    val put = minOf(remaining, maxStack - slot.amount)
                    slot.amount += put
                    remaining -= put
                }
            }
            if (remaining > 0) return false
        }
        return true
    }
}
