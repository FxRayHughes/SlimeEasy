package top.maplex.slimeEasy.storage.core

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.block.Block
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore

/**
 * 存储方块破坏时的掉落辅助。
 *
 * 统一把"库存内容 + 已装升级组件"拆成真实物品堆, 追加到破坏掉落列表 —— 拆除后
 * 内容与升级直接散落在地, 不再随方块物品 (PDC) 一并搬走。方块本体由 Slimefun 的
 * 破坏流程掉落, 此处只负责额外内容, 故破坏 handler **不应**再手动加入方块物品
 * (否则与 Slimefun 自动掉落叠加, 表现为"掉两个")。
 */
object StorageDrops {

    /**
     * 把 [storage] 全部库存与该位置已装升级, 作为真实物品堆追加到 [drops]。
     *
     * @param drops Slimefun 破坏 handler 提供的掉落列表 (已 clear, 只余方块本体由框架掉落)
     */
    fun spill(block: Block, storage: VirtualStorage, drops: MutableList<org.bukkit.inventory.ItemStack>) {
        // 库存内容: 每种物品按原版堆叠上限拆成若干真实堆
        for ((key, total) in storage.entries()) {
            var remaining = total
            val max = key.vanillaMaxStack.coerceAtLeast(1).toLong()
            while (remaining > 0) {
                val n = minOf(remaining, max).toInt()
                drops.add(key.toDisplay(n))
                remaining -= n
            }
        }
        // 已装升级组件原样掉落
        drops.addAll(UpgradeStore.readItems(block.location))

        // 仅散落输入槽 (0) 里货运途中尚未吸收的瞬态物品; 输出槽 (1) 是库存的镜像
        // (未从库存扣除), 已随上面的 storage.entries() 一并散落, 不可重复散落。
        val menu = StorageCacheUtils.getMenu(block.location)
        if (menu != null) {
            val stack = menu.getItemInSlot(0)
            if (stack != null && !stack.type.isAir && stack.amount > 0) drops.add(stack.clone())
        }

        // 经验存储 (经验升级容器) 破坏时, 以经验球返还存量
        spillExp(block)
    }

    /** 破坏时把该方块存储的经验点数以经验球返还 (海量经验拆成有限个球, 避免刷屏)。 */
    private fun spillExp(block: Block) {
        val points = top.maplex.slimeEasy.storage.drawer.DrawerExp.get(block)
        if (points <= 0) return
        val loc = block.location.toCenterLocation()
        val world = loc.world ?: return
        // 每个球承载的点数: 使球数量不超过 [MAX_ORBS], 且单球值封顶到 Int 范围
        val perOrb = maxOf(1L, (points + MAX_ORBS - 1) / MAX_ORBS)
        var remaining = points
        while (remaining > 0) {
            val v = minOf(remaining, perOrb).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            world.spawn(loc, org.bukkit.entity.ExperienceOrb::class.java) { it.experience = v }
            remaining -= v
        }
    }

    /** 经验返还时最多生成的经验球数量。 */
    private const val MAX_ORBS = 40
}
