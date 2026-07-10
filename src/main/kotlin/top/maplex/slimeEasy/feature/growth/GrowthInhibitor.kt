package top.maplex.slimeEasy.feature.growth

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.handlers.EntityInteractHandler
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Breedable
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.villager.core.VillagerDisplay

/**
 * 生长抑制器。
 *
 * 手持右键一只幼年生物 → 将其**年龄锁定** ([Breedable.setAgeLock])，使其永远保持幼小、不再长大;
 * 再次右键已锁定的生物则解除锁定, 恢复正常生长 (开关语义)。
 *
 * 仅对可繁殖生物 ([Breedable]: 各类动物 / 村民等) 生效; 成年个体给出提示而不缩小。
 * 展示实体 (交易器 / 刷铁机内嵌) 一律跳过。交互经 Slimefun 原生 [EntityInteractHandler] 承接。
 */
class GrowthInhibitor(
    itemGroup: ItemGroup,
    item: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<ItemStack?>
) : SlimefunItem(itemGroup, item, recipeType, recipe) {

    override fun preRegister() {
        addItemHandler(EntityInteractHandler { e, _, offHand ->
            if (offHand) return@EntityInteractHandler // 仅主手
            val target = e.rightClicked as? Breedable ?: return@EntityInteractHandler
            if (VillagerDisplay.isDisplay(target)) return@EntityInteractHandler // 跳过展示实体
            e.isCancelled = true // 接管交互, 阻止原版右键 (繁殖 / 命名等)
            val player = e.player

            if (target.ageLock) {
                target.ageLock = false
                player.playSound(target.location, Sound.BLOCK_HONEY_BLOCK_BREAK, 1f, 1.2f)
                player.sendMessage("§a[生长抑制器] §7已解除锁定, 该生物恢复正常生长。")
                return@EntityInteractHandler
            }

            if (target.isAdult) {
                player.sendMessage("§c[生长抑制器] §7仅对幼年生物有效。")
                return@EntityInteractHandler
            }

            // 幼年 → 冻结年龄, 永久保持幼小
            target.setBaby()
            target.ageLock = true
            target.world.spawnParticle(Particle.HEART, target.location.add(0.0, 0.5, 0.0), 5, 0.3, 0.3, 0.3, 0.0)
            player.playSound(target.location, Sound.BLOCK_HONEY_BLOCK_PLACE, 1f, 0.8f)
            player.sendMessage("§a[生长抑制器] §7已抑制生长, 该幼年生物将永久保持幼小。")
        })
    }
}
