package top.maplex.slimeEasy.feature.ward

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import org.bukkit.block.Block
import org.bukkit.entity.Creeper
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import kotlin.math.abs

/**
 * 苦力怕驱逐方块 (绿色地毯外观)。
 *
 * 放置后随 Slimefun 原生 ticker 运行, 每 tick 完成两件事:
 * 1. 续期以自身区块为中心、半径 [PROTECT_RADIUS] 内区块的保护期,
 *    使 [CreeperSpawnListener] 拦截这些区块内苦力怕的自然生成;
 * 2. 将保护区域内已存在的苦力怕沿"最近边界"方向推出区域。
 *
 * 推离方向选取苦力怕到 3x3 区域四条边界中的最近一条, 沿单一水平轴垂直推出 ——
 * 相比"径向远离方块", 路径最短且单调朝区域外, 避免斜向游走或来回抖动。
 *
 * 保护登记为内存 TTL 自愈式, 详见 [ProtectedChunks]。
 */
class CreeperWard(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    override fun preRegister() {
        addItemHandler(object : BlockTicker() {
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) {
                onTick(b)
            }

            // 涉及实体速度修改, 必须在主线程执行
            override fun isSynchronized(): Boolean = true
        })
    }

    private fun onTick(ward: Block) {
        // 1. 续期保护区块 (3x3 区块)
        ProtectedChunks.refresh(ward.chunk, PROTECT_RADIUS)
        // 2. 把区域内苦力怕沿最近边界推出
        repelCreepers(ward)
    }

    /**
     * 将保护区域 (3x3 区块) 内的苦力怕沿最近边界方向推出区域。
     *
     * 检测范围按方块坐标框选整个区域 (半径 [DETECT_RADIUS] 覆盖最坏情形),
     * 再按区块归属精确过滤, 与 [CreeperSpawnListener] 的判定口径一致 ——
     * 从而不会漏推靠近区域边角、离方块较远的苦力怕。
     */
    private fun repelCreepers(ward: Block) {
        val center = ward.location.toCenterLocation()
        val chunkX = ward.chunk.x
        val chunkZ = ward.chunk.z
        // 区域方块边界 (含): 区块坐标左移 4 位换算为方块坐标
        val minX = (chunkX - PROTECT_RADIUS) shl 4
        val maxX = ((chunkX + PROTECT_RADIUS) shl 4) + 15
        val minZ = (chunkZ - PROTECT_RADIUS) shl 4
        val maxZ = ((chunkZ + PROTECT_RADIUS) shl 4) + 15

        val creepers = ward.world.getNearbyEntitiesByType(Creeper::class.java, center, DETECT_RADIUS)
        for (creeper in creepers) {
            val loc = creeper.location
            // 仅处理确实位于本区域 (3x3 区块) 内的苦力怕
            val cx = loc.blockX shr 4
            val cz = loc.blockZ shr 4
            if (abs(cx - chunkX) > PROTECT_RADIUS || abs(cz - chunkZ) > PROTECT_RADIUS) continue

            creeper.velocity = exitVelocity(loc.x, loc.z, minX, maxX, minZ, maxZ)
        }
    }

    /**
     * 计算把某点沿"最近边界"推出区域的速度向量。
     *
     * 分别求到西/东/北/南四条边界的距离, 取最小者对应的轴与方向单轴推出;
     * 保证路径最短且始终朝区域外, 不产生斜向或反复。
     */
    private fun exitVelocity(
        x: Double, z: Double,
        minX: Int, maxX: Int, minZ: Int, maxZ: Int
    ): Vector {
        val toWest = x - minX          // 向 -X 出界所需
        val toEast = (maxX + 1) - x    // 向 +X 出界所需
        val toNorth = z - minZ         // 向 -Z 出界所需
        val toSouth = (maxZ + 1) - z   // 向 +Z 出界所需
        val minDist = minOf(toWest, toEast, toNorth, toSouth)

        val v = when (minDist) {
            toWest -> Vector(-1.0, 0.0, 0.0)
            toEast -> Vector(1.0, 0.0, 0.0)
            toNorth -> Vector(0.0, 0.0, -1.0)
            else -> Vector(0.0, 0.0, 1.0)
        }
        return v.multiply(PUSH_STRENGTH).setY(PUSH_UP)
    }

    companion object {
        /** 保护区块的切比雪夫半径; 1 = 3x3 区块 (中心 + 周围一圈)。 */
        private const val PROTECT_RADIUS = 1

        /**
         * 苦力怕检测半径 (格)。
         *
         * 3x3 区域跨 48 格, 方块可能偏居所在区块一角, 到区域最远边角约 40+ 格;
         * 取 48 以确保框选到区域内任意位置的苦力怕, 再由区块归属过滤多余对象。
         */
        private const val DETECT_RADIUS = 48.0

        /** 推出的水平速度强度 (每次 tick 施加, 足以在数刻内越出区域)。 */
        private const val PUSH_STRENGTH = 0.8

        /** 推出时附带的微小上抬速度, 帮助越过矮坎, 但不至于抛飞。 */
        private const val PUSH_UP = 0.2
    }
}
