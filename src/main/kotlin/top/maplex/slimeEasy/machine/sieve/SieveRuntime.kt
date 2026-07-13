package top.maplex.slimeEasy.machine.sieve

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Dispenser
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.TrapDoor
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.SEConfig
import java.util.UUID
import kotlin.math.max

/** 一台筛子的稳定坐标键；以玩家交互的活板门方块为锚点。 */
internal data class SieveKey(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int
) {
    companion object {
        fun of(block: Block): SieveKey = SieveKey(
            block.world.uid,
            block.x,
            block.y,
            block.z
        )
    }
}

/** [SieveRuntime.advance] 的推进结果。 */
internal sealed interface SieveAdvanceResult {
    /** 本次点击距离上次有效点击太近，不推进进度。 */
    data object Throttled : SieveAdvanceResult

    /** 已推进但尚未达到结算次数。 */
    data class Progressed(val progress: Double, val required: Double) : SieveAdvanceResult

    /** 已达到结算次数；调用方应验证事件并决定完成或回退。 */
    data class ReadyToComplete(val inputSnapshot: ItemStack) : SieveAdvanceResult
}

/**
 * 筛子的短生命周期运行态与 BlockDisplay 表现层。
 *
 * 不持久化任何进度：输入改变、机器被破坏、区块卸载或插件关闭时直接清除。
 * 原料仅由 [Sieve] 在完成结算时扣除，因此清除运行态不会造成物品损失。
 */
internal object SieveRuntime {

    private data class Process(
        val recipeKey: String,
        val inputSnapshot: ItemStack,
        var progress: Double,
        var lastActionTick: Long,
        var completing: Boolean
    )

    private data class ActiveVisual(
        val display: BlockDisplay,
        var blockData: BlockData
    )

    private val processes = HashMap<SieveKey, Process>()
    private val activeVisuals = HashMap<SieveKey, ActiveVisual>()

    /**
     * 已从活动表移除但仍在播放完成动画的实体。
     *
     * 与活动实体分开记录，避免下一份原料立刻开始时复用旧实体，并被旧删除任务误删。
     */
    private val finishingVisuals = HashMap<SieveKey, MutableSet<BlockDisplay>>()

    /** 返回当前正在处理的配方键，供输入选择优先维持同一配方。 */
    fun currentRecipeKey(block: Block): String? = processes[SieveKey.of(block)]?.recipeKey

    /**
     * 尝试推进一次筛分。
     *
     * 到达最后一步时只返回 [SieveAdvanceResult.ReadyToComplete]，不把展示高度先压到零；
     * 只有调用方确认全部合成事件通过、成功扣除原料后才调用 [complete]。
     */
    fun advance(
        block: Block,
        recipeKey: String,
        requiredProgress: Double,
        progressPerAction: Double,
        visualData: BlockData,
        inputSnapshot: ItemStack?,
        reinforced: Boolean
    ): SieveAdvanceResult {
        val key = SieveKey.of(block)
        val required = requiredProgress.coerceAtLeast(0.01)
        val step = progressPerAction.coerceAtLeast(0.01)
        val now = block.world.gameTime

        var process = processes[key]
        if (process == null || process.recipeKey != recipeKey) {
            cancelActive(key)
            val consumedInput = inputSnapshot?.clone()?.apply { amount = 1 }
                ?: return SieveAdvanceResult.Throttled
            process = Process(
                recipeKey = recipeKey,
                inputSnapshot = consumedInput,
                progress = 0.0,
                lastActionTick = Long.MIN_VALUE,
                completing = false
            )
            processes[key] = process
        }

        if (process.completing) return SieveAdvanceResult.Throttled

        val minimumInterval = SEConfig.sieveMinimumActionIntervalTicks
        if (
            minimumInterval >= 0 &&
            process.lastActionTick != Long.MIN_VALUE &&
            now - process.lastActionTick < minimumInterval
        ) {
            return SieveAdvanceResult.Throttled
        }

        process.lastActionTick = now
        ensureVisual(block, key, visualData)

        val next = process.progress + step
        if (next >= required) {
            process.completing = true
            return SieveAdvanceResult.ReadyToComplete(process.inputSnapshot.clone())
        }

        process.progress = next
        val ratio = (next / required).coerceIn(0.0, 1.0).toFloat()
        updateVisual(block, key, visualData, ratio)
        playHitEffect(block, visualData, ratio, reinforced)
        return SieveAdvanceResult.Progressed(next, required)
    }

    /**
     * 最后一步未能提交（例如合成事件被取消）时回退到“差一步完成”的稳定状态。
     */
    fun deferCompletion(
        block: Block,
        recipeKey: String,
        requiredProgress: Double,
        progressPerAction: Double,
        visualData: BlockData,
        reinforced: Boolean
    ) {
        val key = SieveKey.of(block)
        val required = requiredProgress.coerceAtLeast(0.01)
        val progress = (required - progressPerAction.coerceAtLeast(0.01)).coerceAtLeast(0.0)
        val process = processes[key]

        if (process == null || process.recipeKey != recipeKey) {
            val input = process?.inputSnapshot ?: return
            processes[key] = Process(
                recipeKey = recipeKey,
                inputSnapshot = input.clone(),
                progress = progress,
                lastActionTick = block.world.gameTime,
                completing = false
            )
        } else {
            process.progress = progress
            process.completing = false
        }

        ensureVisual(block, key, visualData)
        updateVisual(block, key, visualData, (progress / required).coerceIn(0.0, 1.0).toFloat())
    }

    /**
     * 提交完成：清除进度，播放最终压扁、破碎粒子和破坏音效，并在动画结束后删除实体。
     */
    fun complete(block: Block, visualData: BlockData, reinforced: Boolean) {
        val key = SieveKey.of(block)
        processes.remove(key)

        if (!SEConfig.sieveAnimationEnabled) {
            removeActiveVisual(key)
            playBreakEffect(block, visualData, reinforced)
            return
        }

        val existingVisual = activeVisuals.remove(key)
        val visual = existingVisual ?: run {
            val created = createVisual(block, key, visualData)
            activeVisuals.remove(key)
            created
        }

        val display = visual.display
        if (!display.isValid) {
            display.remove()
            playBreakEffect(block, visualData, reinforced)
            return
        }

        if (visual.blockData != visualData) {
            display.block = visualData
            visual.blockData = visualData
        }

        val completionTicks = SEConfig.sieveCompletionAnimationTicks
        val target = pinnedTransformation(
            horizontal = SEConfig.sieveEndHorizontalScale,
            height = 0F
        )

        finishingVisuals.getOrPut(key) { LinkedHashSet() }.add(display)
        playBreakEffect(block, visualData, reinforced)

        /*
         * required-actions=1 或展示实体被外部删除后重建时，先让客户端收到初始形态，下一 tick 再下发
         * 高度归零的目标关键帧；正常已有展示则可立即继续插值。
         */
        val initialFrameDelay = if (existingVisual == null) 1 else 0
        if (initialFrameDelay == 0) {
            applyTransformation(display, target, completionTicks)
        } else {
            Bukkit.getScheduler().runTask(
                SlimeEasy.instance,
                Runnable {
                    if (display.isValid && finishingVisuals[key]?.contains(display) == true) {
                        applyTransformation(display, target, completionTicks)
                    }
                }
            )
        }

        val removalDelay = max(1, initialFrameDelay + completionTicks + 1).toLong()
        Bukkit.getScheduler().runTaskLater(
            SlimeEasy.instance,
            Runnable {
                display.remove()
                finishingVisuals[key]?.let { set ->
                    set.remove(display)
                    if (set.isEmpty()) finishingVisuals.remove(key)
                }
            },
            removalDelay
        )
    }

    /** 取消当前加工，但保留已经提交完成、仍在播放收尾动画的展示。 */
    fun cancelProcess(block: Block) = cancelActive(SieveKey.of(block))

    private fun cancelActive(key: SieveKey) {
        processes.remove(key)?.let { returnInput(key, it.inputSnapshot) }
        removeActiveVisual(key)
    }

    /** 机器结构失效时，清除进度、活动展示和仍在收尾的展示。 */
    fun clear(block: Block) = clear(SieveKey.of(block))

    private fun clear(key: SieveKey) {
        cancelActive(key)
        finishingVisuals.remove(key)?.forEach { it.remove() }
    }

    private fun returnInput(key: SieveKey, input: ItemStack) {
        val world = Bukkit.getWorld(key.worldId) ?: return
        val anchor = world.getBlockAt(key.x, key.y, key.z)
        val stack = input.clone().apply { amount = 1 }
        val leftovers = findReturnDispenser(anchor)?.inventory?.addItem(stack)
        val dropLocation = anchor.location.add(0.5, 0.5, 0.5)

        if (leftovers == null) {
            world.dropItemNaturally(dropLocation, stack)
        } else {
            leftovers.values.forEach { world.dropItemNaturally(dropLocation, it) }
        }
    }

    private fun findReturnDispenser(anchor: Block): Dispenser? {
        val below = anchor.getRelative(BlockFace.DOWN)
        if (below.type == Material.DISPENSER) return below.state as? Dispenser

        val twoBelow = anchor.getRelative(BlockFace.DOWN, 2)
        if (below.type == Material.SCAFFOLDING && twoBelow.type == Material.DISPENSER) {
            return twoBelow.state as? Dispenser
        }

        return null
    }

    /** 区块卸载时抛弃该区块内全部内存进度并删除临时实体。 */
    fun clearChunk(worldId: UUID, chunkX: Int, chunkZ: Int) {
        val keys = buildSet {
            processes.keys.filterTo(this) { it.inChunk(worldId, chunkX, chunkZ) }
            activeVisuals.keys.filterTo(this) { it.inChunk(worldId, chunkX, chunkZ) }
            finishingVisuals.keys.filterTo(this) { it.inChunk(worldId, chunkX, chunkZ) }
        }
        keys.forEach(::clear)
    }

    /** 世界卸载时清除该世界中的全部筛子运行态。 */
    fun clearWorld(worldId: UUID) {
        val keys = buildSet {
            processes.keys.filterTo(this) { it.worldId == worldId }
            activeVisuals.keys.filterTo(this) { it.worldId == worldId }
            finishingVisuals.keys.filterTo(this) { it.worldId == worldId }
        }
        keys.forEach(::clear)
    }

    /** 插件关闭时删除全部临时展示实体。 */
    fun shutdown() {
        processes.keys.toList().forEach(::clear)
        activeVisuals.values.forEach { it.display.remove() }
        finishingVisuals.values.flatten().forEach { it.remove() }
        activeVisuals.clear()
        finishingVisuals.clear()
        processes.clear()
    }

    private fun SieveKey.inChunk(worldId: UUID, chunkX: Int, chunkZ: Int): Boolean =
        this.worldId == worldId && (x shr 4) == chunkX && (z shr 4) == chunkZ

    private fun ensureVisual(
        block: Block,
        key: SieveKey,
        visualData: BlockData
    ): ActiveVisual? {
        if (!SEConfig.sieveAnimationEnabled) {
            removeActiveVisual(key)
            return null
        }

        val current = activeVisuals[key]
        if (current != null && current.display.isValid) {
            if (current.blockData != visualData) {
                current.display.block = visualData
                current.blockData = visualData
            }
            return current
        }

        current?.display?.remove()
        activeVisuals.remove(key)
        return createVisual(block, key, visualData)
    }

    private fun createVisual(
        block: Block,
        key: SieveKey,
        visualData: BlockData
    ): ActiveVisual {
        val display = block.world.spawn(visualAnchor(block), BlockDisplay::class.java) {
            it.block = visualData
            it.isPersistent = false
            it.isInvulnerable = true
            it.billboard = Display.Billboard.FIXED
            it.viewRange = 0.75F
            it.shadowRadius = 0F
            it.shadowStrength = 0F
            it.interpolationDelay = 0
            it.interpolationDuration = 0
            it.transformation = pinnedTransformation(
                horizontal = SEConfig.sieveStartHorizontalScale,
                height = SEConfig.sieveStartHeightScale
            )
        }

        return ActiveVisual(display, visualData).also { activeVisuals[key] = it }
    }

    private fun updateVisual(
        block: Block,
        key: SieveKey,
        visualData: BlockData,
        ratio: Float
    ) {
        val visual = ensureVisual(block, key, visualData) ?: return
        val clamped = ratio.coerceIn(0F, 1F)
        val horizontal = lerp(
            SEConfig.sieveStartHorizontalScale,
            SEConfig.sieveEndHorizontalScale,
            clamped
        )
        val height = lerp(SEConfig.sieveStartHeightScale, 0F, clamped)
        val target = pinnedTransformation(horizontal, height)
        val interpolationTicks = SEConfig.sieveInterpolationTicks

        /*
         * 新实体延后一 tick 再给第一个目标关键帧，确保客户端先收到初始 0.8×0.8×0.8 状态，
         * 而不是把生成包和目标变换合并后直接显示第一阶段终点。
         */
        if (visual.display.ticksLived == 0) {
            Bukkit.getScheduler().runTask(
                SlimeEasy.instance,
                Runnable {
                    val live = activeVisuals[key]
                    if (live?.display === visual.display && visual.display.isValid) {
                        applyTransformation(visual.display, target, interpolationTicks)
                    }
                }
            )
        } else {
            applyTransformation(visual.display, target, interpolationTicks)
        }
    }

    private fun applyTransformation(
        display: BlockDisplay,
        target: Transformation,
        interpolationTicks: Int
    ) {
        display.interpolationDelay = 0
        display.interpolationDuration = interpolationTicks
        display.transformation = target
    }

    /**
     * BlockDisplay 原点位于方块模型的底部角点。
     *
     * 实体位置被定义为底面中心，因此缩放为 (w,h,w) 时同步平移 (-w/2,0,-w/2)。
     * 两端关键帧都满足 translation = -scale/2，客户端线性插值的每个中间帧也保持该等式，
     * 所以底面中心在整个动画中严格固定。
     */
    private fun pinnedTransformation(horizontal: Float, height: Float): Transformation =
        Transformation(
            Vector3f(-horizontal / 2F, 0F, -horizontal / 2F),
            AxisAngle4f(),
            Vector3f(horizontal, height, horizontal),
            AxisAngle4f()
        )

    /** 取关闭活板门筛面的上表面中心；常规底半活板门的高度为 3/16 格。 */
    private fun visualAnchor(block: Block): Location {
        val trapDoor = block.blockData as? TrapDoor
        val surfaceY = if (trapDoor?.half == Bisected.Half.TOP && !trapDoor.isOpen) {
            1.0
        } else {
            3.0 / 16.0
        }
        return block.location.add(0.5, surfaceY + 0.002, 0.5)
    }

    private fun playHitEffect(block: Block, visualData: BlockData, ratio: Float, reinforced: Boolean) {
        val horizontal = lerp(
            SEConfig.sieveStartHorizontalScale,
            SEConfig.sieveEndHorizontalScale,
            ratio.coerceIn(0F, 1F)
        )
        val height = lerp(SEConfig.sieveStartHeightScale, 0F, ratio.coerceIn(0F, 1F))
        val location = visualAnchor(block).add(0.0, max(0.04F, height * 0.35F).toDouble(), 0.0)

        block.world.spawnParticle(
            Particle.BLOCK_CRUMBLE,
            location,
            14,
            (horizontal * 0.28F).toDouble(),
            max(0.03F, height * 0.12F).toDouble(),
            (horizontal * 0.28F).toDouble(),
            0.02,
            visualData
        )
        if (reinforced) {
            playScaffoldingUndersideParticles(block, visualData, 10, 0.018)
        }

        val sounds = visualData.soundGroup
        block.world.playSound(
            location,
            sounds.hitSound,
            SoundCategory.BLOCKS,
            sounds.volume * 0.35F,
            sounds.pitch * (0.95F + ratio.coerceIn(0F, 1F) * 0.12F)
        )
    }

    private fun playBreakEffect(block: Block, visualData: BlockData, reinforced: Boolean) {
        val location = visualAnchor(block).add(0.0, 0.08, 0.0)
        block.world.spawnParticle(
            Particle.BLOCK_CRUMBLE,
            location,
            42,
            0.22,
            0.10,
            0.22,
            0.045,
            visualData
        )
        if (reinforced) {
            playScaffoldingUndersideParticles(block, visualData, 24, 0.04)
        }

        val sounds = visualData.soundGroup
        block.world.playSound(
            location,
            sounds.breakSound,
            SoundCategory.BLOCKS,
            sounds.volume * 0.7F,
            sounds.pitch
        )
    }

    private fun playScaffoldingUndersideParticles(block: Block, visualData: BlockData, count: Int, speed: Double) {
        val location = block.location.add(0.5, -0.25, 0.5)
        block.world.spawnParticle(
            Particle.BLOCK_CRUMBLE,
            location,
            count,
            0.26,
            0.04,
            0.26,
            speed,
            visualData
        )
    }

    private fun removeActiveVisual(key: SieveKey) {
        activeVisuals.remove(key)?.display?.remove()
    }

    private fun lerp(start: Float, end: Float, ratio: Float): Float =
        start + (end - start) * ratio
}
