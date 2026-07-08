package top.maplex.slimeEasy.machine.placer

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.machine.common.BlockEffect
import top.maplex.slimeEasy.machine.common.FrequencyResolver
import top.maplex.slimeEasy.machine.common.PistonSupport
import java.util.concurrent.ConcurrentHashMap

/**
 * 自动放置机。
 *
 * 机器本体为涂蜡氧化铜箱子 (绿色, 区别于破坏机的橙色)。随 Slimefun 原生 ticker 运行:
 * 1. 检测相邻粘性活塞及其推杆朝向;
 * 2. 按频率间隔从本箱子取一个方块, 放置到活塞正前方的空位;
 * 3. 目标非空位或箱内无可放置方块时跳过。
 *
 * 频率由活塞上带拉杆的展示框旋转角度决定, 详见 [FrequencyResolver]。
 */
class AutoPlacer(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /**
     * 每台机器的 tick 计数器 (键为方块位置字符串)。
     *
     * 使用内存计数而非持久化: 计数仅用于节流, 服务器重启后从 0 重新计数无副作用,
     * 且原生 ticker 频率较高, 避免每 tick 写库带来的 IO 开销。
     */
    private val counters = ConcurrentHashMap<String, Int>()

    override fun preRegister() {
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) {
                onTick(b)
            }

            // 涉及方块放置与容器写入, 必须在主线程执行
            override fun isSynchronized(): Boolean = true
        })
    }

    /** 单个机器方块的一次 tick 处理。 */
    private fun onTick(machine: Block) {
        val piston = PistonSupport.findAdjacentPiston(machine, PlacerLogic.PISTON_TYPES) ?: run {
            counters.remove(machine.locationKey())
            return
        }

        val interval = FrequencyResolver.resolveInterval(piston)
        val key = machine.locationKey()
        val count = counters.getOrDefault(key, 0) + 1

        if (count < interval) {
            counters[key] = count
            return
        }
        counters[key] = 0

        val target = PistonSupport.resolveTarget(piston) ?: return
        if (PlacerLogic.tryPlace(machine, target)) {
            // 放置后 target 已是新方块, 取其数据供放置音效与粒子还原外观
            BlockEffect.playPlace(target, target.blockData)
        }
    }

    /** 生成方块位置的稳定字符串键。 */
    private fun Block.locationKey(): String =
        "${world.name}:$x:$y:$z"
}
