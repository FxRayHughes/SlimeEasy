package top.maplex.slimeEasy.villager.ironfarm

import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.IronGolem
import org.bukkit.entity.Villager
import org.bukkit.entity.Zombie
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.villager.core.VillagerData
import top.maplex.slimeEasy.villager.core.VillagerDisplay

/**
 * 胶囊刷铁机的展示层: 村民 + 僵尸并排常驻, 产铁瞬间闪现铁傀儡。
 *
 * 均为缩小的真实生物实体 (钉死为纯装饰)。村民外观取自装配的村民快照。
 */
object IronFarmDisplay {

    private const val KEY_VILLAGER = "se_iron_v"
    private const val KEY_ZOMBIE = "se_iron_z"
    private const val KEY_GOLEM = "se_iron_g"

    private const val SCALE_MOB = 0.42
    private const val SCALE_GOLEM = 0.32

    /** 铁傀儡近似原始身高 (格), 供垂直居中计算。 */
    private const val GOLEM_BASE_HEIGHT = 2.7

    /** 村民 / 僵尸左右并排的水平偏移 (格)。 */
    private const val SIDE_OFFSET = 0.22

    /** 铁傀儡闪现时长 (tick)。 */
    private const val GOLEM_TICKS = 40L

    /** 保障村民 + 僵尸展示存在 (村民外观取自 [data]); 无村民数据时清空展示。 */
    fun ensure(block: Block, data: VillagerData?) {
        if (data == null) {
            removeAll(block)
            return
        }
        val villager = VillagerDisplay.get(block, KEY_VILLAGER)
        if (villager == null || villager !is Villager || villager.isDead) {
            VillagerDisplay.spawn(
                block, KEY_VILLAGER, Villager::class.java, SCALE_MOB, dx = -SIDE_OFFSET
            ) { data.applyAppearance(it) }
        }
        val zombie = VillagerDisplay.get(block, KEY_ZOMBIE)
        if (zombie == null || zombie !is Zombie || zombie.isDead) {
            VillagerDisplay.spawn(
                block, KEY_ZOMBIE, Zombie::class.java, SCALE_MOB, dx = SIDE_OFFSET
            ) { it.setShouldBurnInDay(false) } // 展示僵尸白天不燃烧, 避免火焰视觉
        }
    }

    /** 产铁瞬间闪现铁傀儡: 若不存在则生成, 并调度 [GOLEM_TICKS] 后移除。 */
    fun flashGolem(block: Block) {
        if (VillagerDisplay.get(block, KEY_GOLEM) != null) return
        VillagerDisplay.spawn(block, KEY_GOLEM, IronGolem::class.java, SCALE_GOLEM, baseHeight = GOLEM_BASE_HEIGHT)
        Bukkit.getScheduler().runTaskLater(SlimeEasy.instance, Runnable {
            VillagerDisplay.remove(block, KEY_GOLEM)
        }, GOLEM_TICKS)
    }

    /** 移除全部展示实体 (破坏时调用)。 */
    fun removeAll(block: Block) {
        VillagerDisplay.remove(block, KEY_VILLAGER)
        VillagerDisplay.remove(block, KEY_ZOMBIE)
        VillagerDisplay.remove(block, KEY_GOLEM)
        // 兜底: 按方块空间清理可能的孤儿展示实体 (UUID 链断裂时按 key 删不到)
        VillagerDisplay.sweepAt(block)
    }
}
