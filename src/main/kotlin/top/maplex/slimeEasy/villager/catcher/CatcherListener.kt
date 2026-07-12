package top.maplex.slimeEasy.villager.catcher

import top.maplex.slimeEasy.config.I18n
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Villager
import org.bukkit.entity.ZombieVillager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import top.maplex.slimeEasy.villager.core.VillagerData
import top.maplex.slimeEasy.villager.core.VillagerDisplay

/**
 * 村民捕捉器的交互监听。
 *
 * - **捕捉** ([PlayerInteractEntityEvent]): 空捕捉器右键活体村民 → 抓取其全部属性存入新的满捕捉器,
 *   移除该村民; 主手空捕捉器 -1、满捕捉器 +1。展示实体 (带标记) 一律跳过, 避免破坏交易器 / 刷铁机。
 * - **释放** ([PlayerInteractEvent], 潜行 + 右键空气 / 方块): 满捕捉器在目标位置生成活体村民并还原全部属性,
 *   满捕捉器 -1、返还空捕捉器 +1 (容器可复用)。
 *
 * 捕捉器为普通 Slimefun 物品 (无 ItemUseHandler), 故所有交互由本 Bukkit 监听器独占处理；监听器必须
 * 忽略已取消事件，让领地与其它保护插件能在捕捉或释放产生世界副作用前直接阻断操作。
 */
class CatcherListener : Listener {

    /** 捕捉: 空捕捉器右键活体村民 / 僵尸村民。 */
    @EventHandler(ignoreCancelled = true)
    fun onCapture(e: PlayerInteractEntityEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        val player = e.player
        val hand = player.inventory.itemInMainHand
        if (!VillagerCatcher.isCatcher(hand)) return
        val target = e.rightClicked
        if (target !is Villager && target !is ZombieVillager) return
        // 展示实体 (交易器 / 刷铁机内嵌) 交由对应机器交互处理, 此处不拦截、不提示
        if (VillagerDisplay.isDisplay(target)) return
        if (player.isSneaking) return // 潜行留给释放语义
        e.isCancelled = true // 拦下原版交易界面

        if (VillagerCatcher.isFilled(hand)) {
            player.sendMessage(I18n.text("messages.catcher.already-filled"))
            return
        }

        val data = when (target) {
            is Villager -> VillagerData.capture(target)
            is ZombieVillager -> VillagerData.captureZombie(target)
            else -> return
        }
        target.remove()
        VillagerCatcher.replaceOneInHand(player, VillagerCatcher.fill(data))
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f)
        player.sendMessage(I18n.text("messages.catcher.captured", "villager" to data.professionLabel))
    }

    /** 释放: 潜行 + 右键 (空气 / 方块), 满捕捉器放出村民。 */
    @EventHandler(ignoreCancelled = true)
    fun onRelease(e: PlayerInteractEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        if (e.action != Action.RIGHT_CLICK_AIR && e.action != Action.RIGHT_CLICK_BLOCK) return
        val player = e.player
        if (!player.isSneaking) return
        val hand = e.item ?: return
        val data = VillagerCatcher.dataOf(hand) ?: return // 仅满捕捉器
        // 点到 Slimefun 方块 (如交易器) 时让该方块自身监听处理, 不在此释放, 避免双重触发
        e.clickedBlock?.let {
            if (com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.hasBlock(it.location)) return
        }
        e.isCancelled = true

        val loc = releaseLocation(e)
        data.spawnEntity(loc) // 按形态生成僵尸村民 / 普通村民并还原属性
        // 释放消耗满捕捉器并返还空捕捉器 (容器可复用); 空模板缺失的极端情况仅消耗不返还
        val back = VillagerCatcher.emptyItem()
        if (back != null) {
            VillagerCatcher.replaceOneInHand(player, back)
        } else {
            val cur = player.inventory.itemInMainHand
            if (cur.amount <= 1) player.inventory.setItemInMainHand(null)
            else { cur.amount -= 1; player.inventory.setItemInMainHand(cur) }
        }
        player.playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f)
        player.sendMessage(I18n.text("messages.catcher.released", "villager" to data.professionLabel))
    }

    /** 释放坐标: 点到方块则其上方, 否则玩家视线前方 2 格。 */
    private fun releaseLocation(e: PlayerInteractEvent): Location {
        val block = e.clickedBlock
        if (block != null) return block.location.add(0.5, 1.0, 0.5)
        val player = e.player
        return player.eyeLocation.add(player.location.direction.multiply(2.0))
    }
}
