package top.maplex.slimeEasy.machine.breaker

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import org.bukkit.block.Block
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.machine.common.BlockEffect
import top.maplex.slimeEasy.machine.common.FrequencyResolver
import top.maplex.slimeEasy.machine.common.MachineProtection
import top.maplex.slimeEasy.machine.common.PistonSupport
import top.maplex.slimeEasy.util.locationKey
import java.util.concurrent.ConcurrentHashMap

/**
 * 自动破坏机。
 *
 * 机器本体为涂蜡铜箱子。放置后随 Slimefun 原生 ticker 运行:
 * 1. 检测相邻活塞及其推杆朝向;
 * 2. 按频率间隔破坏活塞正前方方块;
 * 3. 掉落物存入本箱子 (自动兼容双箱)。
 *
 * 频率由活塞上带拉杆的展示框旋转角度决定, 详见 [FrequencyResolver]。
 */
class AutoBreaker(
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
        // 放置时记录所有者, 供 tick 时以其身份做领地保护校验
        addItemHandler(object : BlockPlaceHandler(false) {
            override fun onPlayerPlace(e: BlockPlaceEvent) {
                MachineProtection.recordOwner(e.block, e.player)
            }
        })
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) {
                onTick(b)
            }

            // 涉及方块破坏与容器写入, 必须在主线程执行
            override fun isSynchronized(): Boolean = true
        })
    }

    /** 单个机器方块的一次 tick 处理。 */
    private fun onTick(machine: Block) {
        val piston = PistonSupport.findAdjacentPiston(machine, BreakerLogic.PISTON_TYPES) ?: run {
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
        // 领地保护: 机器所有者无破坏权限时跳过 (尊重 WorldGuard 等保护插件)
        if (!MachineProtection.canBreak(machine, target)) return
        // 破坏前捕获方块数据: 破坏后目标变为空气, 音效与粒子需据此还原外观
        val brokenData = target.blockData
        if (BreakerLogic.tryBreak(machine, target)) {
            BlockEffect.playBreak(target, brokenData)
        }
    }
}
