package top.maplex.slimeEasy.villager.potion

import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import top.maplex.slimeEasy.villager.core.VillagerData
import top.maplex.slimeEasy.villager.core.VillagerDisplay

/**
 * 遗忘药剂的交互监听。
 *
 * 手持遗忘药剂右键一只活体村民 → 职业置为无职业 (none), 变回可再就业的普通村民, 消耗一瓶。
 * 展示实体 (带标记) 一律跳过, 避免改动交易器 / 刷铁机内的展示村民。
 */
class ForgettingPotionListener : Listener {

    @EventHandler
    fun onUse(e: PlayerInteractEntityEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        val player = e.player
        val hand = player.inventory.itemInMainHand
        if (!ForgettingPotion.isPotion(hand)) return
        val villager = e.rightClicked as? Villager ?: return
        // 展示实体交由对应机器交互处理, 此处不拦截、不提示
        if (VillagerDisplay.isDisplay(villager)) return
        e.isCancelled = true // 拦下原版交易界面

        val profession = VillagerData.resolveProfession(VillagerData.NONE)
        if (profession == null) {
            player.sendMessage("§c[遗忘药剂] §7当前版本无法解析无职业类型, 操作取消。")
            return
        }
        villager.profession = profession

        // 消耗一瓶
        if (hand.amount <= 1) player.inventory.setItemInMainHand(null)
        else { hand.amount -= 1; player.inventory.setItemInMainHand(hand) }

        villager.world.spawnParticle(Particle.WITCH, villager.location.add(0.0, 1.0, 0.0), 20, 0.3, 0.5, 0.3, 0.0)
        player.playSound(villager.location, Sound.ENTITY_WITCH_DRINK, 1f, 1f)
        player.sendMessage("§a[遗忘药剂] §7该村民已忘却职业, 变回无职业普通村民。")
    }
}
