package top.maplex.slimeEasy.storage.drawer

import top.maplex.slimeEasy.config.I18n
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import top.maplex.slimeEasy.storage.core.GuiItems
import top.maplex.slimeEasy.storage.core.StorageChangeBus
import top.maplex.slimeEasy.storage.core.UpgradeMenu
import top.maplex.slimeEasy.util.locationKey

/**
 * 经验抽屉的操作 GUI (经验存储升级启用时替代物品交互)。
 *
 * 布局: 中央经验瓶显示当前存量 (点数 + 约合等级); 一行"存入全部"与取出方式按钮;
 * 一行按等级取出的按钮 (1/5/10/20/30/40/50)。取出按玩家**当前等级**换算所需点数,
 * 不足则取尽库存；取出方式可在直接给予与掉落经验球之间切换。经验模式下抽屉不接受 /
 * 不输出物品, 故此界面不涉及物品格。
 *
 * 打开中的界面订阅 [StorageChangeBus] 实时刷新: 磁铁在后台吸入经验、或别处存取时,
 * 存量显示即时更新。
 */
object ExpMenu {

    /** 取出按钮对应的等级档位。 */
    private val WITHDRAW_LEVELS = intArrayOf(1, 5, 10, 20, 30, 40, 50)

    /** 取出按钮所在槽位 (与档位一一对应)。 */
    private val WITHDRAW_SLOTS = intArrayOf(19, 20, 21, 22, 23, 24, 25)

    private const val INFO_SLOT = 4
    private const val DEPOSIT_SLOT = 13
    private const val PAYOUT_MODE_SLOT = 17
    private const val UPGRADE_SLOT = 8

    /** 位置键 → 打开中的界面 (实时刷新用)。 */
    private val openViews = java.util.concurrent.ConcurrentHashMap<String, MutableSet<View>>()

    init {
        StorageChangeBus.subscribe { block ->
            openViews[block.locationKey()]?.toList()?.forEach { it.render() }
        }
    }

    fun open(block: Block, player: Player) {
        View(block).apply { menu.open(player) }
    }

    private class View(val block: Block) {
        val menu = ChestMenu(I18n.text("menus.experience.title"))

        init {
            menu.setEmptySlotsClickable(false)
            for (i in 0 until 27) menu.addItem(i, GuiItems.BACKGROUND) { _, _, _, _ -> false }
            openViews.computeIfAbsent(block.locationKey()) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(this)
            menu.addMenuCloseHandler { openViews[block.locationKey()]?.remove(this) }
            render()
        }

        fun render() {
            val points = DrawerExp.get(block)
            val levels = ExpUtil.levelsFromPoints(points)
            menu.replaceExistingItem(
                INFO_SLOT,
                GuiItems.localized(Material.EXPERIENCE_BOTTLE, "menus.experience.stored", "points" to points, "levels" to levels)
            )
            menu.addMenuClickHandler(INFO_SLOT) { _, _, _, _ -> false }

            menu.replaceExistingItem(
                DEPOSIT_SLOT,
                GuiItems.localized(Material.LIME_STAINED_GLASS_PANE, "menus.experience.deposit-all")
            )
            menu.addMenuClickHandler(DEPOSIT_SLOT) { p, _, _, _ ->
                DrawerExp.deposit(block, p); render(); false
            }

            val payoutMode = DrawerExp.payoutMode(block)
            menu.replaceExistingItem(
                PAYOUT_MODE_SLOT,
                GuiItems.localized(payoutMode.icon, payoutMode.menuKey)
            )
            menu.addMenuClickHandler(PAYOUT_MODE_SLOT) { _, _, _, _ ->
                DrawerExp.togglePayoutMode(block)
                render()
                false
            }

            // 升级入口: 经验模式下右键只开本页, 需在此提供管理升级组件的入口
            menu.replaceExistingItem(UPGRADE_SLOT, GuiItems.UPGRADE_ENTRY)
            menu.addMenuClickHandler(UPGRADE_SLOT) { p, _, _, _ ->
                val logic = StorageCacheUtils.getBlock(block.location)?.sfId
                    ?.let { SlimefunItem.getById(it) } as? CargoBufferBlock
                if (logic != null) UpgradeMenu.open(logic, block, p, I18n.text("menus.experience.upgrade-title"))
                false
            }

            for ((idx, lv) in WITHDRAW_LEVELS.withIndex()) {
                val slot = WITHDRAW_SLOTS[idx]
                menu.replaceExistingItem(
                    slot,
                    GuiItems.localized(Material.EXPERIENCE_BOTTLE, "menus.experience.withdraw", "levels" to lv)
                )
                menu.addMenuClickHandler(slot) { p, _, _, _ ->
                    DrawerExp.withdrawLevels(block, p, lv); render(); false
                }
            }
        }
    }
}
