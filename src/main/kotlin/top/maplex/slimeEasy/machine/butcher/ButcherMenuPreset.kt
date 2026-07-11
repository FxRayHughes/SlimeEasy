package top.maplex.slimeEasy.machine.butcher

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.storage.core.GuiItems

/**
 * 屠夫机器的操作界面 (Slimefun 原生 [BlockMenu] 预设)。
 *
 * 相比临时 ChestMenu, 原生 BlockMenu 的**空槽 (inventorySlots) 由 Slimefun 自动持久化**:
 * 玩家放入 / 取出 / shift 全部原生支持, 且内容即存储 —— 无需快照回写, 从根本上消除
 * "界面回存覆盖 ticker 写入"的并发问题。右键由 Slimefun 自动打开 (依赖 [canOpen])。
 *
 * 槽位布局 (6×9):
 * - [INFO_SLOT] 信息面板 (预设槽, 展示当前状态与说明);
 * - [WEAPON_SLOTS] 第二行居中 7 格武器 (自由放置; 第一把优先, 耐久耗尽自动切下一把);
 * - [BOOK_SLOT] 附魔书 (抢夺/锋利/火焰附加合并到武器);
 * - [FOOD_SLOT] 食物 (兼作货运/漏斗输入; 折算为攻击次数);
 * - [RANGE_SLOT]/[DAMAGE_SLOT] 范围/伤害升级组件 (堆叠数量 = 级数);
 * - 其余为背景与文字标签。
 *
 * 武器/书/食物/升级为 inventory 槽 (init 中不占用), 其余 addItem 的为预设槽 (受保护)。
 */
class ButcherMenuPreset(id: String, title: String) : BlockMenuPreset(id, title) {

    override fun init() {
        // 背景铺满装饰槽, 再放文字标签与信息面板 (这些成为受保护的预设槽);
        // 未被占用的功能槽自动成为可自由放置且持久化的 inventory 槽。
        // 背景与所有标签/信息槽必须带"取消交互"处理器 ([ChestMenuUtils.getEmptyClickHandler],
        // 返回 false → 取消点击): 因菜单默认 emptyClickable=true, 仅 addItem 而不加处理器的
        // 槽会被玩家自由拿走。drawBackground 已内置该处理器; 标签/信息需显式添加。
        drawBackground(GuiItems.BACKGROUND, backgroundSlots())
        protectedItem(INFO_SLOT, infoTemplate())
        // 每个功能区以对应色的彩色玻璃标注, 便于一眼分辨槽位用途
        protectedItem(LABEL_WEAPON, label(Material.YELLOW_STAINED_GLASS_PANE, "menus.butcher.labels.weapon"))
        protectedItem(LABEL_BOOK, label(Material.MAGENTA_STAINED_GLASS_PANE, "menus.butcher.labels.enchantment-book"))
        protectedItem(LABEL_FOOD, label(Material.ORANGE_STAINED_GLASS_PANE, "menus.butcher.labels.food"))
        protectedItem(LABEL_RANGE, label(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "menus.butcher.labels.range-upgrade"))
        protectedItem(LABEL_DAMAGE, label(Material.RED_STAINED_GLASS_PANE, "menus.butcher.labels.damage-upgrade"))
    }

    /** 放置一个受保护 (不可被玩家拿走 / 放入) 的预设图标。 */
    private fun protectedItem(slot: Int, item: ItemStack) {
        addItem(slot, item, ChestMenuUtils.getEmptyClickHandler())
    }

    /** 任何玩家均可打开 (与存储方块一致; 领地保护由攻击逻辑另行处理)。 */
    override fun canOpen(block: Block, player: Player): Boolean = true

    /** 货运接入: INSERT 可及全部功能槽 (按物品类型细分见下), 无 WITHDRAW。 */
    override fun getSlotsAccessedByItemTransport(flow: ItemTransportFlow): IntArray =
        if (flow == ItemTransportFlow.INSERT) FUNCTIONAL_SLOTS else IntArray(0)

    /** 货运精细路由: 按物品类型 (武器/书/食物/升级) 返回其应进入的槽; 无法归类或已满则拒绝。 */
    override fun getSlotsAccessedByItemTransport(
        menu: DirtyChestMenu, flow: ItemTransportFlow, item: ItemStack
    ): IntArray =
        if (flow == ItemTransportFlow.INSERT && menu is BlockMenu) routeSlots(menu, item) else IntArray(0)

    companion object {
        /** 信息面板槽。 */
        const val INFO_SLOT = 4

        /** 武器槽 (第二行居中 7 格, 两端留边框对称; 第一把优先使用)。 */
        val WEAPON_SLOTS = intArrayOf(10, 11, 12, 13, 14, 15, 16)

        /** 范围 / 伤害升级级数上限 (堆叠超过此数视为无效); 实时读取配置。 */
        val MAX_UPGRADE_LEVEL: Int get() = SEConfig.butcherMaxUpgradeLevel

        /** 附魔书槽。 */
        const val BOOK_SLOT = 21

        /** 食物槽 (兼货运/漏斗输入)。 */
        const val FOOD_SLOT = 23

        /** 范围升级槽。 */
        const val RANGE_SLOT = 29

        /** 伤害升级槽。 */
        const val DAMAGE_SLOT = 33

        // 文字标签槽 (功能槽左侧一格)
        private const val LABEL_BOOK = 20
        private const val LABEL_FOOD = 22
        private const val LABEL_RANGE = 28
        private const val LABEL_DAMAGE = 32
        private const val LABEL_WEAPON = 0

        /** 全部功能 (inventory) 槽: 破坏时据此掉落。 */
        val FUNCTIONAL_SLOTS: IntArray = WEAPON_SLOTS + intArrayOf(BOOK_SLOT, FOOD_SLOT, RANGE_SLOT, DAMAGE_SLOT)

        /** 预设 (背景) 槽 = 全部 54 格中除功能槽与信息/标签槽外的部分。 */
        private fun backgroundSlots(): IntArray {
            val occupied = FUNCTIONAL_SLOTS.toHashSet().apply {
                add(INFO_SLOT); add(LABEL_BOOK); add(LABEL_FOOD); add(LABEL_RANGE); add(LABEL_DAMAGE); add(LABEL_WEAPON)
            }
            return (0 until 54).filter { it !in occupied }.toIntArray()
        }

        /**
         * 首个**有效武器**槽及其物品; 无则返回 null。
         *
         * 按 [ButcherLogic.isWeapon] 判定, 跳过玩家误放入武器槽的非武器物品 (如钻石),
         * 避免其挡在真武器前导致停机。
         */
        fun firstWeapon(menu: BlockMenu): Pair<Int, ItemStack>? {
            for (slot in WEAPON_SLOTS) {
                val item = menu.getItemInSlot(slot) ?: continue
                if (ButcherLogic.isWeapon(item)) return slot to item
            }
            return null
        }

        /** 附魔书槽物品 (可能非书, 由调用侧判定)。 */
        fun bookAt(menu: BlockMenu): ItemStack? = menu.getItemInSlot(BOOK_SLOT)?.takeIf { !it.type.isAir }

        /** 食物槽物品。 */
        fun foodAt(menu: BlockMenu): ItemStack? = menu.getItemInSlot(FOOD_SLOT)?.takeIf { !it.type.isAir }

        /** 范围升级级数 (= 组件堆叠数量, 封顶 [MAX_UPGRADE_LEVEL]; 非该组件为 0)。 */
        fun rangeLevel(menu: BlockMenu): Int =
            (menu.getItemInSlot(RANGE_SLOT)?.takeIf { ButcherLogic.isRangeUpgrade(it) }?.amount ?: 0)
                .coerceAtMost(MAX_UPGRADE_LEVEL)

        /** 伤害升级级数 (封顶 [MAX_UPGRADE_LEVEL])。 */
        fun damageLevel(menu: BlockMenu): Int =
            (menu.getItemInSlot(DAMAGE_SLOT)?.takeIf { ButcherLogic.isDamageUpgrade(it) }?.amount ?: 0)
                .coerceAtMost(MAX_UPGRADE_LEVEL)

        /**
         * 按物品类型返回其应进入的槽 (供漏斗抽取 / 货运插入统一路由)。
         *
         * - 武器 → 全部武器槽 (pushItem 自动填入空槽);
         * - 附魔书 → 书槽; 食物 → 食物槽;
         * - 范围 / 伤害升级 → 对应升级槽, 但已达 [MAX_UPGRADE_LEVEL] 则拒收 (返回空);
         * - 其余无法归类 → 空 (不接收)。
         */
        fun routeSlots(menu: BlockMenu, item: ItemStack): IntArray = when {
            ButcherLogic.isWeapon(item) -> WEAPON_SLOTS
            ButcherLogic.isBook(item) -> intArrayOf(BOOK_SLOT)
            ButcherLogic.isRangeUpgrade(item) ->
                if (slotAmount(menu, RANGE_SLOT) < MAX_UPGRADE_LEVEL) intArrayOf(RANGE_SLOT) else IntArray(0)
            ButcherLogic.isDamageUpgrade(item) ->
                if (slotAmount(menu, DAMAGE_SLOT) < MAX_UPGRADE_LEVEL) intArrayOf(DAMAGE_SLOT) else IntArray(0)
            ButcherLogic.nutritionOf(item.type) > 0 -> intArrayOf(FOOD_SLOT)
            else -> IntArray(0)
        }

        /** 某槽当前物品数量 (空槽为 0)。 */
        private fun slotAmount(menu: BlockMenu, slot: Int): Int =
            menu.getItemInSlot(slot)?.takeIf { !it.type.isAir }?.amount ?: 0

        /** 信息面板静态模板 (无 block 上下文时的初始占位, 打开/tick 后由 [updateInfo] 刷新)。 */
        private fun infoTemplate(): ItemStack =
            GuiItems.localized(Material.OBSERVER, "menus.butcher.info-template")

        /**
         * 刷新信息面板为当前状态 (武器 / 余量 / 范围 / 伤害)。
         *
         * 直接写底层库存 ([BlockMenu.toInventory] `setItem`) 而非 `replaceExistingItem`:
         * 后者恒 `markDirty()` 会使菜单每次攻击存盘; 信息面板是预设槽 (不持久化, 重载
         * 由 [init] 重绘), 无需落盘。直更库存对正在查看的玩家实时可见, 且零 I/O。
         */
        fun updateInfo(menu: BlockMenu, fuel: Long) {
            val weapon = firstWeapon(menu)?.second?.type?.name ?: I18n.text("names.common.none")
            val satiety = fuel / ButcherLogic.ATTACKS_PER_NUTRITION
            val range = rangeLevel(menu)
            val dmg = damageLevel(menu)
            val span = 3 + 2 * range
            menu.toInventory().setItem(
                INFO_SLOT,
                GuiItems.localized(
                    Material.OBSERVER,
                    "menus.butcher.info",
                    "weapon" to weapon,
                    "satiety" to satiety,
                    "maxSatiety" to ButcherLogic.MAX_SATIETY,
                    "attacks" to fuel,
                    "span" to span,
                    "rangeLevel" to range,
                    "damageMultiplier" to "%.1f".format(1.0 + 0.5 * dmg),
                    "damageLevel" to dmg
                )
            )
        }

        /** 构造带名称与 lore 的彩色玻璃标签图标 (颜色对应功能区)。 */
        private fun label(material: Material, key: String): ItemStack = GuiItems.localized(material, key)
    }
}
