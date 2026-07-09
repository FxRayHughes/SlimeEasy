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
import top.maplex.slimeEasy.feature.ward.CreeperControlListener
import top.maplex.slimeEasy.feature.ward.CreeperWard
import top.maplex.slimeEasy.machine.breaker.AutoBreaker
import top.maplex.slimeEasy.machine.placer.AutoPlacer
import top.maplex.slimeEasy.storage.box.PagedBox
import top.maplex.slimeEasy.storage.drawer.Drawer
import top.maplex.slimeEasy.storage.drawer.DrawerListener
import top.maplex.slimeEasy.storage.network.NetworkConnector
import top.maplex.slimeEasy.storage.network.NetworkController
import top.maplex.slimeEasy.storage.network.NetworkPort
import top.maplex.slimeEasy.storage.network.RemoteTerminal

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

    /** 矿物勘察尺 (普通/进阶) 研究解锁所需经验等级。 */
    private const val SURVEY_RULER_RESEARCH_COST = 10

    /** 存储系统各研究解锁所需经验等级 (按进阶程度递增)。 */
    private const val DRAWER_RESEARCH_COST = 8          // 入门: 单一物品抽屉
    private const val BOX_RESEARCH_COST = 15            // 进阶: 多物品翻页箱
    private const val STORAGE_UPGRADE_RESEARCH_COST = 20 // 增强: 各类升级组件
    private const val NETWORK_RESEARCH_COST = 30        // 高阶: 存储网络

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

        // 12. 注册存储系统
        registerStorage(addon)
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

        // 研究解锁 (按主题分组, 成本随进阶递增)
        research("storage_drawer", 9006, "海量抽屉", DRAWER_RESEARCH_COST, drawer)
        research("storage_box", 9007, "翻页存储箱", BOX_RESEARCH_COST, box)
        research("storage_upgrades", 9008, "存储升级组件", STORAGE_UPGRADE_RESEARCH_COST,
            stackI, stackII, stackIII, expUp, magnetUp, voidUp, pageUp, wiseUp, enderWiseUp)
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
