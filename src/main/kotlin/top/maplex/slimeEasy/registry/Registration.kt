package top.maplex.slimeEasy.registry

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.api.researches.Research
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.feature.survey.SurveyDisplayListener
import top.maplex.slimeEasy.feature.survey.SurveyRuler
import top.maplex.slimeEasy.feature.survey.SurveyTier
import top.maplex.slimeEasy.feature.growth.GrowthInhibitor
import top.maplex.slimeEasy.feature.harness.HarnessCombat
import top.maplex.slimeEasy.feature.ward.CreeperControlListener
import top.maplex.slimeEasy.feature.ward.CreeperWard
import top.maplex.slimeEasy.machine.breaker.AutoBreaker
import top.maplex.slimeEasy.machine.butcher.Butcher
import top.maplex.slimeEasy.machine.butcher.ButcherDeathListener
import top.maplex.slimeEasy.machine.clicker.AutoClicker
import top.maplex.slimeEasy.machine.placer.AutoPlacer
import top.maplex.slimeEasy.storage.box.PagedBox
import top.maplex.slimeEasy.storage.drawer.Drawer
import top.maplex.slimeEasy.storage.drawer.DrawerListener
import top.maplex.slimeEasy.storage.network.NetworkConnector
import top.maplex.slimeEasy.storage.network.NetworkController
import top.maplex.slimeEasy.storage.network.NetworkPort
import top.maplex.slimeEasy.storage.network.RemoteTerminal
import top.maplex.slimeEasy.storage.network.RemoteUpgrade
import top.maplex.slimeEasy.villager.catcher.CatcherListener
import top.maplex.slimeEasy.villager.ironfarm.IronFarm
import top.maplex.slimeEasy.villager.ironfarm.IronFarmListener
import top.maplex.slimeEasy.villager.healer.VillagerHealer
import top.maplex.slimeEasy.villager.potion.ForgettingPotionListener
import top.maplex.slimeEasy.villager.school.VillagerSchool
import top.maplex.slimeEasy.villager.trader.TraderListener
import top.maplex.slimeEasy.villager.trader.VillagerTrader

/**
 * 内容注册总入口。
 *
 * 负责把物品组、物品、研究按正确顺序注册到 Slimefun。
 * 必须在 Slimefun 加载完成后 (本插件 onEnable) 调用。
 */
object Registration {

    /** 自动破坏机研究解锁所需经验等级。 */
    private const val AUTO_BREAKER_RESEARCH_COST = 10

    /** 自动放置机研究解锁所需经验等级。 */
    private const val AUTO_PLACER_RESEARCH_COST = 10

    /** 苦力怕驱逐方块研究解锁所需经验等级。 */
    private const val CREEPER_WARD_RESEARCH_COST = 10

    /** 屠夫机器研究解锁所需经验等级。 */
    private const val BUTCHER_RESEARCH_COST = 15

    /** 自动点击器研究解锁所需经验等级。 */
    private const val AUTO_CLICKER_RESEARCH_COST = 15

    /** 生长抑制器研究解锁所需经验等级。 */
    private const val GROWTH_INHIBITOR_RESEARCH_COST = 10

    /** 战斗挽具研究解锁所需经验等级。 */
    private const val COMBAT_HARNESS_RESEARCH_COST = 20

    /** 矿物勘察尺 (普通/进阶) 研究解锁所需经验等级。 */
    private const val SURVEY_RULER_RESEARCH_COST = 10

    /** 存储系统各研究解锁所需经验等级 (按进阶程度递增)。 */
    private const val DRAWER_RESEARCH_COST = 8          // 入门: 单一物品抽屉
    private const val BOX_RESEARCH_COST = 15            // 进阶: 多物品翻页箱
    private const val STORAGE_UPGRADE_RESEARCH_COST = 20 // 增强: 各类升级组件
    private const val NETWORK_RESEARCH_COST = 30        // 高阶: 存储网络

    /** 简易村民各研究解锁所需经验等级。 */
    private const val VILLAGER_CATCHER_RESEARCH_COST = 12
    private const val ZOMBIE_SIGNAL_RESEARCH_COST = 8
    private const val VILLAGER_TRADER_RESEARCH_COST = 18
    private const val IRON_FARM_RESEARCH_COST = 20
    private const val VILLAGER_SCHOOL_RESEARCH_COST = 15
    private const val FORGETTING_POTION_RESEARCH_COST = 12
    private const val VILLAGER_HEALER_RESEARCH_COST = 15

    /** 普通工业矿机采掘半径 (7×7)。 */
    private const val MINER_RANGE = 3

    /** 进阶工业矿机采掘半径 (11×11)。 */
    private const val ADVANCED_MINER_RANGE = 5

    /**
     * 执行全部注册。
     *
     * @param addon 本附属实例, 用于将内容归属到本插件
     */
    fun registerAll(addon: SlimefunAddon) {
        // 1. 注册物品组
        Groups.UTILITY_MACHINES.register(addon)
        Groups.UTILITY_TOOLS.register(addon)

        // 2. 注册自动破坏机 (增强工作台配方)
        val autoBreaker = AutoBreaker(
            Groups.UTILITY_MACHINES,
            Items.AUTO_BREAKER,
            RecipeType.ENHANCED_CRAFTING_TABLE,
            Items.AUTO_BREAKER_RECIPE
        )
        autoBreaker.register(addon)

        // 3. 绑定破坏机研究: 花费 10 级经验解锁
        Research(
            NamespacedKey(SlimeEasy.instance, "auto_breaker"),
            9001,
            "自动破坏机",
            AUTO_BREAKER_RESEARCH_COST
        ).apply {
            addItems(autoBreaker)
            register()
        }

        // 4. 注册自动放置机 (增强工作台配方)
        val autoPlacer = AutoPlacer(
            Groups.UTILITY_MACHINES,
            Items.AUTO_PLACER,
            RecipeType.ENHANCED_CRAFTING_TABLE,
            Items.AUTO_PLACER_RECIPE
        )
        autoPlacer.register(addon)

        // 5. 绑定放置机研究: 花费 10 级经验解锁
        Research(
            NamespacedKey(SlimeEasy.instance, "auto_placer"),
            9002,
            "自动放置机",
            AUTO_PLACER_RESEARCH_COST
        ).apply {
            addItems(autoPlacer)
            register()
        }

        // 6. 注册苦力怕驱逐方块 (增强工作台配方)
        val creeperWard = CreeperWard(
            Groups.UTILITY_MACHINES,
            Items.CREEPER_WARD,
            RecipeType.ENHANCED_CRAFTING_TABLE,
            Items.CREEPER_WARD_RECIPE
        )
        creeperWard.register(addon)

        // 7. 绑定驱逐方块研究: 花费 10 级经验解锁
        Research(
            NamespacedKey(SlimeEasy.instance, "creeper_ward"),
            9003,
            "苦力怕驱逐方块",
            CREEPER_WARD_RESEARCH_COST
        ).apply {
            addItems(creeperWard)
            register()
        }

        // 8. 注册苦力怕管控监听器 (生成拦截 + 爆炸拦截)
        Bukkit.getPluginManager().registerEvents(
            CreeperControlListener(),
            SlimeEasy.instance
        )

        // 8b. 注册屠夫机器 (增强工作台配方) 与其范围/伤害升级组件
        val butcher = Butcher(
            Groups.UTILITY_MACHINES,
            Items.BUTCHER,
            RecipeType.ENHANCED_CRAFTING_TABLE,
            Items.BUTCHER_RECIPE
        )
        butcher.register(addon)

        val butcherRangeUp = SlimefunItem(
            Groups.UTILITY_MACHINES, Items.BUTCHER_RANGE_UPGRADE,
            RecipeType.ENHANCED_CRAFTING_TABLE, Items.BUTCHER_RANGE_UPGRADE_RECIPE
        ).also { it.register(addon) }
        val butcherDamageUp = SlimefunItem(
            Groups.UTILITY_MACHINES, Items.BUTCHER_DAMAGE_UPGRADE,
            RecipeType.ENHANCED_CRAFTING_TABLE, Items.BUTCHER_DAMAGE_UPGRADE_RECIPE
        ).also { it.register(addon) }

        research("butcher", 9010, "屠夫机器", BUTCHER_RESEARCH_COST, butcher, butcherRangeUp, butcherDamageUp)

        // 8c. 注册屠夫机器掉落 / 经验兜底监听器
        Bukkit.getPluginManager().registerEvents(ButcherDeathListener(), SlimeEasy.instance)

        // 8d. 注册自动点击器 (红石激活 + 一格容积 + 漏斗补料, 增强工作台配方)
        val autoClicker = AutoClicker(
            Groups.UTILITY_MACHINES,
            Items.AUTO_CLICKER,
            RecipeType.ENHANCED_CRAFTING_TABLE,
            Items.AUTO_CLICKER_RECIPE
        )
        autoClicker.register(addon)
        research("auto_clicker", 9018, "自动点击器", AUTO_CLICKER_RESEARCH_COST, autoClicker)

        // 9. 注册矿物勘察尺 (普通: 仅工业矿机范围)
        val surveyRuler = SurveyRuler(
            Groups.UTILITY_TOOLS,
            Items.SURVEY_RULER,
            RecipeType.ENHANCED_CRAFTING_TABLE,
            Items.SURVEY_RULER_RECIPE,
            listOf(SurveyTier("工业矿机", MINER_RANGE))
        )
        surveyRuler.register(addon)

        Research(
            NamespacedKey(SlimeEasy.instance, "survey_ruler"),
            9004,
            "矿物勘察尺",
            SURVEY_RULER_RESEARCH_COST
        ).apply {
            addItems(surveyRuler)
            register()
        }

        // 10. 注册进阶矿物勘察尺 (进阶 + 普通两种范围, 潜行右键空气切换)
        val advancedSurveyRuler = SurveyRuler(
            Groups.UTILITY_TOOLS,
            Items.ADVANCED_SURVEY_RULER,
            RecipeType.ENHANCED_CRAFTING_TABLE,
            Items.ADVANCED_SURVEY_RULER_RECIPE,
            listOf(
                SurveyTier("进阶工业矿机", ADVANCED_MINER_RANGE),
                SurveyTier("工业矿机", MINER_RANGE)
            )
        )
        advancedSurveyRuler.register(addon)

        Research(
            NamespacedKey(SlimeEasy.instance, "advanced_survey_ruler"),
            9005,
            "进阶矿物勘察尺",
            SURVEY_RULER_RESEARCH_COST
        ).apply {
            addItems(advancedSurveyRuler)
            register()
        }

        // 11. 注册勘察尺左键监听器 (潜行左键切换展示形式: 聊天栏 / 箱子界面)
        Bukkit.getPluginManager().registerEvents(
            SurveyDisplayListener(),
            SlimeEasy.instance
        )

        // 11b. 注册生长抑制器 (手持工具, 归实用工具)
        val growthInhibitor = GrowthInhibitor(
            Groups.UTILITY_TOOLS,
            Items.GROWTH_INHIBITOR,
            RecipeType.ENHANCED_CRAFTING_TABLE,
            Items.GROWTH_INHIBITOR_RECIPE
        )
        growthInhibitor.register(addon)
        research("growth_inhibitor", 9019, "生长抑制器", GROWTH_INHIBITOR_RESEARCH_COST, growthInhibitor)

        // 11c. 注册战斗挽具 (4 档, 归实用工具) 并启动乐魂作战定时任务
        fun harness(stack: SlimefunItemStack, recipe: Array<ItemStack?>): SlimefunItem =
            SlimefunItem(Groups.UTILITY_TOOLS, stack, RecipeType.ENHANCED_CRAFTING_TABLE, recipe).also { it.register(addon) }
        val h1 = harness(Items.COMBAT_HARNESS_I, Items.COMBAT_HARNESS_I_RECIPE)
        val h2 = harness(Items.COMBAT_HARNESS_II, Items.COMBAT_HARNESS_II_RECIPE)
        val h3 = harness(Items.COMBAT_HARNESS_III, Items.COMBAT_HARNESS_III_RECIPE)
        val h4 = harness(Items.COMBAT_HARNESS_IV, Items.COMBAT_HARNESS_IV_RECIPE)
        research("combat_harness", 9020, "战斗挽具", COMBAT_HARNESS_RESEARCH_COST, h1, h2, h3, h4)
        HarnessCombat.start()

        // 12. 注册存储系统
        registerStorage(addon)

        // 13. 注册简易村民
        registerVillager(addon)
    }

    /** 注册简易村民: 分类、6 个物品 + 速度升级、研究解锁与相关监听器。 */
    private fun registerVillager(addon: SlimefunAddon) {
        Groups.VILLAGER.register(addon)
        val et = RecipeType.ENHANCED_CRAFTING_TABLE

        // 无自定义行为的普通物品 (捕捉器 / 僵尸信号 / 速度升级 / 遗忘药剂)
        fun plain(stack: SlimefunItemStack, recipe: Array<ItemStack?>): SlimefunItem =
            SlimefunItem(Groups.VILLAGER, stack, et, recipe).also { it.register(addon) }

        val catcher = plain(VillagerItems.VILLAGER_CATCHER, VillagerItems.VILLAGER_CATCHER_RECIPE)
        val zombieSignal = plain(VillagerItems.ZOMBIE_SIGNAL, VillagerItems.ZOMBIE_SIGNAL_RECIPE)
        val forgettingPotion = plain(VillagerItems.FORGETTING_POTION, VillagerItems.FORGETTING_POTION_RECIPE)
        val speedUpgrade = plain(VillagerItems.IRON_FARM_SPEED_UPGRADE, VillagerItems.IRON_FARM_SPEED_UPGRADE_RECIPE)

        // 自定义方块
        val trader = VillagerTrader(
            Groups.VILLAGER, VillagerItems.VILLAGER_TRADER, et, VillagerItems.VILLAGER_TRADER_RECIPE
        ).also { it.register(addon) }
        val ironFarm = IronFarm(
            Groups.VILLAGER, VillagerItems.IRON_FARM, et, VillagerItems.IRON_FARM_RECIPE
        ).also { it.register(addon) }
        val school = VillagerSchool(
            Groups.VILLAGER, VillagerItems.VILLAGER_SCHOOL, et, VillagerItems.VILLAGER_SCHOOL_RECIPE
        ).also { it.register(addon) }
        val healer = VillagerHealer(
            Groups.VILLAGER, VillagerItems.VILLAGER_HEALER, et, VillagerItems.VILLAGER_HEALER_RECIPE
        ).also { it.register(addon) }

        // 研究解锁 (9011~9017)
        research("villager_catcher", 9011, "村民捕捉器", VILLAGER_CATCHER_RESEARCH_COST, catcher)
        research("zombie_signal", 9012, "僵尸信号", ZOMBIE_SIGNAL_RESEARCH_COST, zombieSignal)
        research("villager_trader", 9013, "村民交易器", VILLAGER_TRADER_RESEARCH_COST, trader)
        research("iron_farm", 9014, "胶囊刷铁机", IRON_FARM_RESEARCH_COST, ironFarm, speedUpgrade)
        research("villager_school", 9015, "村民小学", VILLAGER_SCHOOL_RESEARCH_COST, school)
        research("forgetting_potion", 9016, "遗忘药剂", FORGETTING_POTION_RESEARCH_COST, forgettingPotion)
        research("villager_healer", 9017, "村民治愈机", VILLAGER_HEALER_RESEARCH_COST, healer)

        // 监听器: 捕捉器捕捉/释放、交易器交互与关闭同步、刷铁机展示点击、遗忘药剂使用
        Bukkit.getPluginManager().registerEvents(CatcherListener(), SlimeEasy.instance)
        Bukkit.getPluginManager().registerEvents(TraderListener(), SlimeEasy.instance)
        Bukkit.getPluginManager().registerEvents(IronFarmListener(), SlimeEasy.instance)
        Bukkit.getPluginManager().registerEvents(ForgettingPotionListener(), SlimeEasy.instance)
    }

    /** 注册存储系统: 分类、存储方块、网络方块、升级组件、研究解锁与相关监听器。 */
    private fun registerStorage(addon: SlimefunAddon) {
        Groups.STORAGE.register(addon)
        val et = RecipeType.ENHANCED_CRAFTING_TABLE

        val drawer = Drawer(Groups.STORAGE, StorageItems.DRAWER, et, StorageItems.DRAWER_RECIPE).also { it.register(addon) }
        val box = PagedBox(Groups.STORAGE, StorageItems.BOX, et, StorageItems.BOX_RECIPE).also { it.register(addon) }
        val controller = NetworkController(Groups.STORAGE, StorageItems.CONTROLLER, et, StorageItems.CONTROLLER_RECIPE).also { it.register(addon) }
        val connector = NetworkConnector(Groups.STORAGE, StorageItems.CONNECTOR, et, StorageItems.CONNECTOR_RECIPE).also { it.register(addon) }
        val inputPort = NetworkPort(Groups.STORAGE, StorageItems.INPUT_PORT, et, StorageItems.INPUT_PORT_RECIPE, true).also { it.register(addon) }
        val outputPort = NetworkPort(Groups.STORAGE, StorageItems.OUTPUT_PORT, et, StorageItems.OUTPUT_PORT_RECIPE, false).also { it.register(addon) }
        val remoteTerminal = RemoteTerminal(Groups.STORAGE, StorageItems.REMOTE_TERMINAL, et, StorageItems.REMOTE_TERMINAL_RECIPE).also { it.register(addon) }

        // 升级组件 (无自定义行为的普通 Slimefun 物品)
        fun plain(stack: SlimefunItemStack, recipe: Array<ItemStack?>): SlimefunItem =
            SlimefunItem(Groups.STORAGE, stack, et, recipe).also { it.register(addon) }
        val stackI = plain(StorageItems.STACK_I, StorageItems.STACK_I_RECIPE)
        val stackII = plain(StorageItems.STACK_II, StorageItems.STACK_II_RECIPE)
        val stackIII = plain(StorageItems.STACK_III, StorageItems.STACK_III_RECIPE)
        val expUp = plain(StorageItems.EXP_UPGRADE, StorageItems.EXP_UPGRADE_RECIPE)
        val magnetUp = plain(StorageItems.MAGNET_UPGRADE, StorageItems.MAGNET_UPGRADE_RECIPE)
        val voidUp = plain(StorageItems.VOID_UPGRADE, StorageItems.VOID_UPGRADE_RECIPE)
        val pageUp = plain(StorageItems.PAGE_UPGRADE, StorageItems.PAGE_UPGRADE_RECIPE)
        val wiseUp = plain(StorageItems.WISE_UPGRADE, StorageItems.WISE_UPGRADE_RECIPE)
        val enderWiseUp = plain(StorageItems.ENDER_WISE_UPGRADE, StorageItems.ENDER_WISE_UPGRADE_RECIPE)
        val extractUp = plain(StorageItems.EXTRACT_UPGRADE, StorageItems.EXTRACT_UPGRADE_RECIPE)
        // 远程升级有 ItemUseHandler (右键控制器写 PDC), 故用专用子类而非 plain
        val remoteUp = RemoteUpgrade(Groups.STORAGE, StorageItems.REMOTE_UPGRADE, et, StorageItems.REMOTE_UPGRADE_RECIPE)
            .also { it.register(addon) }

        // 研究解锁 (按主题分组, 成本随进阶递增)
        research("storage_drawer", 9006, "海量抽屉", DRAWER_RESEARCH_COST, drawer)
        research("storage_box", 9007, "翻页存储箱", BOX_RESEARCH_COST, box)
        research("storage_upgrades", 9008, "存储升级组件", STORAGE_UPGRADE_RESEARCH_COST,
            stackI, stackII, stackIII, expUp, magnetUp, voidUp, pageUp, wiseUp, enderWiseUp, extractUp, remoteUp)
        research("storage_network", 9009, "存储网络", NETWORK_RESEARCH_COST,
            controller, connector, inputPort, outputPort, remoteTerminal)

        // 抽屉展示框交互监听器 + 存储缓存区块卸载清理监听器 + 经验球拦截监听器
        Bukkit.getPluginManager().registerEvents(DrawerListener(), SlimeEasy.instance)
        Bukkit.getPluginManager().registerEvents(
            top.maplex.slimeEasy.storage.core.StorageChunkListener(), SlimeEasy.instance
        )
        Bukkit.getPluginManager().registerEvents(
            top.maplex.slimeEasy.storage.drawer.MagnetOrbListener(), SlimeEasy.instance
        )
    }

    /** 绑定一个存储系统研究: 指定经验等级成本, 归入若干物品。 */
    private fun research(key: String, id: Int, name: String, cost: Int, vararg items: SlimefunItem) {
        Research(NamespacedKey(SlimeEasy.instance, key), id, name, cost).apply {
            addItems(*items)
            register()
        }
    }
}
