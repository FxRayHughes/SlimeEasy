package top.maplex.slimeEasy.machine.common

import org.bukkit.Particle
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData

/**
 * 机器作用于方块时的表现层效果: 破坏 / 放置的音效与碎块粒子。
 *
 * 仅负责音画反馈, 不改动任何方块状态。所有方法均需在主线程调用。
 */
object BlockEffect {

    /**
     * 播放目标方块的破坏音效与碎块粒子 (取自破坏前的方块外观)。
     *
     * @param target 被破坏的目标方块 (此时通常已为空气)
     * @param brokenData 目标方块破坏前的方块数据, 用于取破坏音效与粒子外观
     */
    fun playBreak(target: Block, brokenData: BlockData) {
        val world = target.world
        val center = target.location.toCenterLocation()

        // 破坏音效: 使用方块自身音效组的 break sound, 与原版一致
        val breakSound = brokenData.material.createBlockData().soundGroup.breakSound
        world.playSound(center, breakSound, SoundCategory.BLOCKS, 1.0f, 0.8f)

        // 碎块粒子: BLOCK 粒子以破坏前的方块数据作为外观
        world.spawnParticle(Particle.BLOCK, center, 30, 0.25, 0.25, 0.25, brokenData)
    }

    /**
     * 播放目标方块的放置音效与少量碎块粒子 (取自新放置的方块外观)。
     *
     * @param target 被放置的目标方块 (此时已是新放置的方块)
     * @param placedData 新放置方块的方块数据, 用于取放置音效与粒子外观
     */
    fun playPlace(target: Block, placedData: BlockData) {
        val world = target.world
        val center = target.location.toCenterLocation()

        // 放置音效: 使用方块自身音效组的 place sound, 与原版放置一致
        val placeSound = placedData.material.createBlockData().soundGroup.placeSound
        world.playSound(center, placeSound, SoundCategory.BLOCKS, 1.0f, 1.0f)

        // 少量碎块粒子点缀, 数量少于破坏以体现"落定"而非"击碎"
        world.spawnParticle(Particle.BLOCK, center, 12, 0.25, 0.25, 0.25, placedData)
    }
}
