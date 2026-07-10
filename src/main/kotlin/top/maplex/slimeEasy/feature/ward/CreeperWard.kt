package top.maplex.slimeEasy.feature.ward

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import org.bukkit.block.Block
import org.bukkit.NamespacedKey
import org.bukkit.entity.Creeper
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.SEConfig
import kotlin.math.abs

/**
 * 苦力怕驱逐方块 (绿色地毯外观)。
 *
 * 放置后随 Slimefun 原生 ticker 运行, 每 tick 完成两件事:
 * 1. 续期以自身区块为中心、半径 [PROTECT_RADIUS] 内区块的保护期,
 *    使 [CreeperControlListener] 拦截这些区块内苦力怕的自然生成与爆炸;
 * 2. 将保护区域内已存在的苦力怕沿"离开全部受保护区块的最近方向"推出;
 *    若某苦力怕被连续推离 [MAX_PUSH_ATTEMPTS] 次仍未离开 (被墙/坑卡住或
 *    区域内有玩家持续吸引), 直接移除, 作为兜底。
 *
 * 推离方向由 [ProtectedChunks.exitDirection] 基于所有受保护区块的并集计算,
 * 与具体是哪一个驱逐方块无关 —— 因此多个方块保护区重叠时, 对同一苦力怕给出
 * 一致方向, 不会在重叠带相互对推、把苦力怕挤在中间。
 *
 * "连续推离次数" 记录在苦力怕自身的 PersistentDataContainer 上, 随实体生死
 * 自动回收, 无需插件侧维护映射表; 苦力怕一旦离开区域便不再被处理, 计数停止累加。
 *
 * 保护登记为内存 TTL 自愈式, 详见 [ProtectedChunks]。
 */
class CreeperWard(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    /** 记录苦力怕连续被推离次数的 PDC 键。 */
    private val pushCountKey = NamespacedKey(SlimeEasy.instance, "ward_push_count")

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
        // 1. 续期保护区块 (半径由配置决定, 默认 3x3 区块)
        ProtectedChunks.refresh(ward.chunk, SEConfig.creeperWardProtectRadius)
        // 2. 把区域内苦力怕沿最近边界推出
        repelCreepers(ward)
    }

    /**
     * 将保护区域 (3x3 区块) 内的苦力怕沿全局出口方向推出。
     *
     * 检测范围按方块坐标框选整个区域 (半径 [DETECT_RADIUS] 覆盖最坏情形),
     * 再按区块归属精确过滤; 方向统一由 [ProtectedChunks.exitDirection] 依据全部
     * 受保护区块并集给出, 因此重叠区内不同方块不会给出冲突方向。
     */
    private fun repelCreepers(ward: Block) {
        val center = ward.location.toCenterLocation()
        val chunkX = ward.chunk.x
        val chunkZ = ward.chunk.z
        val worldHigh = ward.world.uid.mostSignificantBits
        val protectRadius = SEConfig.creeperWardProtectRadius
        val maxAttempts = SEConfig.creeperWardMaxPushAttempts

        val creepers = ward.world.getNearbyEntitiesByType(Creeper::class.java, center, SEConfig.creeperWardDetectRadius)
        for (creeper in creepers) {
            val loc = creeper.location
            // 仅处理确实位于本方块保护区域 (3x3 区块) 内的苦力怕
            val cx = loc.blockX shr 4
            val cz = loc.blockZ shr 4
            if (abs(cx - chunkX) > protectRadius || abs(cz - chunkZ) > protectRadius) continue

            // 出口方向基于全局并集, 越出所有相连保护区后不再受推
            val (dx, dz) = ProtectedChunks.exitDirection(worldHigh, cx, cz) ?: continue

            // 累计连续推离次数: 超阈值判定为"推不出去", 直接移除
            val attempts = creeper.persistentDataContainer
                .getOrDefault(pushCountKey, PersistentDataType.INTEGER, 0) + 1
            if (attempts >= maxAttempts) {
                creeper.remove()
                continue
            }
            creeper.persistentDataContainer.set(pushCountKey, PersistentDataType.INTEGER, attempts)

            creeper.velocity = Vector(dx.toDouble(), 0.0, dz.toDouble())
                .multiply(SEConfig.creeperWardPushStrength)
                .setY(SEConfig.creeperWardPushUp)
        }
    }
}
