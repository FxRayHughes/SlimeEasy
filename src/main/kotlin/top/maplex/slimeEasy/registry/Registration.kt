package top.maplex.slimeEasy.registry

import top.maplex.slimeEasy.config.I18n
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.api.researches.Research
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.feature.survey.SurveyDisplayListener
import top.maplex.slimeEasy.feature.survey.SurveyRuler
import top.maplex.slimeEasy.feature.survey.SurveyTier
import top.maplex.slimeEasy.feature.growth.GrowthInhibitor
import top.maplex.slimeEasy.feature.harness.HarnessCombat
import top.maplex.slimeEasy.feature.goggles.EngineerGogglesDisplay
import top.maplex.slimeEasy.feature.ward.CreeperControlListener
import top.maplex.slimeEasy.feature.ward.CreeperWard
import top.maplex.slimeEasy.machine.breaker.AutoBreaker
import top.maplex.slimeEasy.machine.butcher.Butcher
import top.maplex.slimeEasy.machine.butcher.ButcherDeathListener
import top.maplex.slimeEasy.machine.clicker.AutoClicker
import top.maplex.slimeEasy.machine.placer.AutoPlacer
import top.maplex.slimeEasy.machine.quarry.Quarry
import top.maplex.slimeEasy.machine.sieve.Sieve
import top.maplex.slimeEasy.storage.box.PagedBox
import top.maplex.slimeEasy.storage.disk.DiskManager
import top.maplex.slimeEasy.storage.drawer.Drawer
import top.maplex.slimeEasy.storage.drawer.DrawerListener
import top.maplex.slimeEasy.storage.network.NetworkConnector
import top.maplex.slimeEasy.storage.network.NetworkController
import top.maplex.slimeEasy.storage.network.NetworkPort
import top.maplex.slimeEasy.storage.network.RemoteTerminal
import top.maplex.slimeEasy.storage.network.RemoteUpgrade
import top.maplex.slimeEasy.territory.TerritoryCore
import top.maplex.slimeEasy.territory.TerritoryBoundaryDisplay
import top.maplex.slimeEasy.territory.TerritoryBlockTransactionListener
import top.maplex.slimeEasy.territory.TerritoryFlag
import top.maplex.slimeEasy.territory.TerritoryInputListener
import top.maplex.slimeEasy.territory.TerritoryProtectionListener
import top.maplex.slimeEasy.territory.TerritoryProtectionBridge
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

    // 研究解锁所需经验等级 / 矿机采掘半径均来自 [SEConfig]; 注册期读取一次, 改动需重启生效。
    private val AUTO_BREAKER_RESEARCH_COST get() = SEConfig.autoBreakerResearch
    private val AUTO_PLACER_RESEARCH_COST get() = SEConfig.autoPlacerResearch
    private val CREEPER_WARD_RESEARCH_COST get() = SEConfig.creeperWardResearch
    private val BUTCHER_RESEARCH_COST get() = SEConfig.butcherResearch
    private val AUTO_CLICKER_RESEARCH_COST get() = SEConfig.autoClickerResearch
    private val QUARRY_RESEARCH_COST get() = SEConfig.quarryResearch
    private val GROWTH_INHIBITOR_RESEARCH_COST get() = SEConfig.growthInhibitorResearch
    private val ENGINEER_GOGGLES_RESEARCH_COST get() = SEConfig.engineerGogglesResearch
    private val COMBAT_HARNESS_RESEARCH_COST get() = SEConfig.combatHarnessResearch
    private val SURVEY_RULER_RESEARCH_COST get() = SEConfig.surveyRulerResearch
    private val SIEVE_RESEARCH_COST get() = SEConfig.sieveResearch

    private val DRAWER_RESEARCH_COST get() = SEConfig.storageDrawerResearch
    private val BOX_RESEARCH_COST get() = SEConfig.storageBoxResearch
    private val DISK_RESEARCH_COST get() = SEConfig.storageDiskResearch
    private val STORAGE_UPGRADE_RESEARCH_COST get() = SEConfig.storageUpgradeResearch
    private val NETWORK_RESEARCH_COST get() = SEConfig.storageNetworkResearch
    private val TERRITORY_RESEARCH_COST get() = SEConfig.territoryResearch

    private val VILLAGER_CATCHER_RESEARCH_COST get() = SEConfig.villagerCatcherResearch
    private val ZOMBIE_SIGNAL_RESEARCH_COST get() = SEConfig.zombieSignalResearch
    private val VILLAGER_TRADER_RESEARCH_COST get() = SEConfig.villagerTraderResearch
    private val IRON_FARM_RESEARCH_COST get() = SEConfig.ironFarmResearch
    private val VILLAGER_SCHOOL_RESEARCH_COST get() = SEConfig.villagerSchoolResearch
    private val FORGETTING_POTION_RESEARCH_COST get() = SEConfig.forgettingPotionResearch
    private val VILLAGER_HEALER_RESEARCH_COST get() = SEConfig.villagerHealerResearch

    /** 普通工业矿机采掘半径 (7×7)。 */
    private val MINER_RANGE get() = SEConfig.surveyMinerRange

    /** 进阶工业矿机采掘半径 (11×11)。 */
    private val ADVANCED_MINER_RANGE get() = SEConfig.surveyAdvancedMinerRange

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
        if (SEConfig.autoBreakerEnabled) {
            val autoBreaker = AutoBreaker(
                Groups.UTILITY_MACHINES,
                Items.AUTO_BREAKER,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                Items.AUTO_BREAKER_RECIPE
            )
            autoBreaker.register(addon)

            // 3. 绑定破坏机研究: 花费经验解锁
            Research(
                NamespacedKey(SlimeEasy.instance, "auto_breaker"),
                9001,
                I18n.text("research.auto-breaker"),
                AUTO_BREAKER_RESEARCH_COST
            ).apply {
                addItems(autoBreaker)
                register()
            }
        }

        // 4. 注册自动放置机 (增强工作台配方)
        if (SEConfig.autoPlacerEnabled) {
            val autoPlacer = AutoPlacer(
                Groups.UTILITY_MACHINES,
                Items.AUTO_PLACER,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                Items.AUTO_PLACER_RECIPE
            )
            autoPlacer.register(addon)

            // 5. 绑定放置机研究: 花费经验解锁
            Research(
                NamespacedKey(SlimeEasy.instance, "auto_placer"),
                9002,
                I18n.text("research.auto-placer"),
                AUTO_PLACER_RESEARCH_COST
            ).apply {
                addItems(autoPlacer)
                register()
            }
        }

        // 筛分原料先挂到 Slimefun 磨石，再登记活板门多方块筛子，最后统一绑定研究。
        if (SEConfig.sieveEnabled) {
            val sieveMaterials = listOf(
                SlimefunItem(
                    Groups.UTILITY_MACHINES,
                    Items.SIEVE_DUST,
                    RecipeType.GRIND_STONE,
                    Items.SIEVE_DUST_RECIPE
                ).also { it.register(addon) },
                SlimefunItem(
                    Groups.UTILITY_MACHINES,
                    Items.CRUSHED_NETHERRACK,
                    RecipeType.GRIND_STONE,
                    Items.CRUSHED_NETHERRACK_RECIPE
                ).also { it.register(addon) },
                SlimefunItem(
                    Groups.UTILITY_MACHINES,
                    Items.CRUSHED_END_STONE,
                    RecipeType.GRIND_STONE,
                    Items.CRUSHED_END_STONE_RECIPE
                ).also { it.register(addon) },
                SlimefunItem(
                    Groups.UTILITY_MACHINES,
                    Items.CRUSHED_BLACKSTONE,
                    RecipeType.GRIND_STONE,
                    Items.CRUSHED_BLACKSTONE_RECIPE
                ).also { it.register(addon) }
            )
            val sieve = Sieve(Groups.UTILITY_MACHINES, Items.SIEVE).also { it.register(addon) }
            research(
                "sieve",
                9022,
                I18n.text("research.sieve"),
                SIEVE_RESEARCH_COST,
                *(sieveMaterials + sieve).toTypedArray()
            )
        }

        // 6. 注册苦力怕驱逐方块 (增强工作台配方); 关闭时连同管控监听器一并跳过
        if (SEConfig.creeperWardEnabled) {
            val creeperWard = CreeperWard(
                Groups.UTILITY_MACHINES,
                Items.CREEPER_WARD,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                Items.CREEPER_WARD_RECIPE
            )
            creeperWard.register(addon)

            // 7. 绑定驱逐方块研究: 花费经验解锁
            Research(
                NamespacedKey(SlimeEasy.instance, "creeper_ward"),
                9003,
                I18n.text("research.creeper-ward"),
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
        }

        // 8b. 注册屠夫机器 (增强工作台配方) 与其范围/伤害升级组件; 关闭时连同死亡监听器一并跳过
        if (SEConfig.butcherEnabled) {
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

            research("butcher", 9010, I18n.text("research.butcher"), BUTCHER_RESEARCH_COST, butcher, butcherRangeUp, butcherDamageUp)

            // 8c. 注册屠夫机器掉落 / 经验兜底监听器
            Bukkit.getPluginManager().registerEvents(ButcherDeathListener(), SlimeEasy.instance)
        }

        // 8d. 注册自动点击器 (红石激活 + 一格容积 + 漏斗补料, 增强工作台配方)
        if (SEConfig.autoClickerEnabled) {
            val autoClicker = AutoClicker(
                Groups.UTILITY_MACHINES,
                Items.AUTO_CLICKER,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                Items.AUTO_CLICKER_RECIPE
            )
            autoClicker.register(addon)
            research("auto_clicker", 9018, I18n.text("research.auto-clicker"), AUTO_CLICKER_RESEARCH_COST, autoClicker)
        }

        // 8e. 注册采石场与效率 / 产物升级组件
        if (SEConfig.quarryEnabled) {
            val et = RecipeType.ENHANCED_CRAFTING_TABLE
            val quarry = Quarry(Groups.UTILITY_MACHINES, Items.QUARRY, et, Items.QUARRY_RECIPE)
            quarry.register(addon)

            // 五档效率升级为无自定义行为的普通 Slimefun 物品 (档位由采石场读槽内物品身份解析)
            val effI = registerPlain(addon, Groups.UTILITY_MACHINES, Items.QUARRY_EFFICIENCY_I, Items.QUARRY_EFFICIENCY_I_RECIPE)
            val effII = registerPlain(addon, Groups.UTILITY_MACHINES, Items.QUARRY_EFFICIENCY_II, Items.QUARRY_EFFICIENCY_II_RECIPE)
            val effIII = registerPlain(addon, Groups.UTILITY_MACHINES, Items.QUARRY_EFFICIENCY_III, Items.QUARRY_EFFICIENCY_III_RECIPE)
            val effIV = registerPlain(addon, Groups.UTILITY_MACHINES, Items.QUARRY_EFFICIENCY_IV, Items.QUARRY_EFFICIENCY_IV_RECIPE)
            val effV = registerPlain(addon, Groups.UTILITY_MACHINES, Items.QUARRY_EFFICIENCY_V, Items.QUARRY_EFFICIENCY_V_RECIPE)
            val netherrackUp = registerPlain(addon, Groups.UTILITY_MACHINES, Items.QUARRY_NETHERRACK_UPGRADE, Items.QUARRY_NETHERRACK_UPGRADE_RECIPE)
            val endStoneUp = registerPlain(addon, Groups.UTILITY_MACHINES, Items.QUARRY_END_STONE_UPGRADE, Items.QUARRY_END_STONE_UPGRADE_RECIPE)
            val blackstoneUp = registerPlain(addon, Groups.UTILITY_MACHINES, Items.QUARRY_BLACKSTONE_UPGRADE, Items.QUARRY_BLACKSTONE_UPGRADE_RECIPE)

            research("quarry", 9021, I18n.text("research.quarry"), QUARRY_RESEARCH_COST,
                quarry, effI, effII, effIII, effIV, effV, netherrackUp, endStoneUp, blackstoneUp)
        }

        // 9~11. 注册矿物勘察尺 (普通 + 进阶) 及其左键展示切换监听器; 关闭时整体跳过
        if (SEConfig.surveyRulerEnabled) {
            // 9. 普通勘察尺 (仅工业矿机范围)
            val surveyRuler = SurveyRuler(
                Groups.UTILITY_TOOLS,
                Items.SURVEY_RULER,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                Items.SURVEY_RULER_RECIPE,
                listOf(SurveyTier(I18n.text("names.survey-tier.industrial-miner"), MINER_RANGE))
            )
            surveyRuler.register(addon)

            Research(
                NamespacedKey(SlimeEasy.instance, "survey_ruler"),
                9004,
                I18n.text("research.survey-ruler"),
                SURVEY_RULER_RESEARCH_COST
            ).apply {
                addItems(surveyRuler)
                register()
            }

            // 10. 进阶勘察尺 (进阶 + 普通两种范围, 潜行右键空气切换)
            val advancedSurveyRuler = SurveyRuler(
                Groups.UTILITY_TOOLS,
                Items.ADVANCED_SURVEY_RULER,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                Items.ADVANCED_SURVEY_RULER_RECIPE,
                listOf(
                    SurveyTier(I18n.text("names.survey-tier.advanced-industrial-miner"), ADVANCED_MINER_RANGE),
                    SurveyTier(I18n.text("names.survey-tier.industrial-miner"), MINER_RANGE)
                )
            )
            advancedSurveyRuler.register(addon)

            Research(
                NamespacedKey(SlimeEasy.instance, "advanced_survey_ruler"),
                9005,
                I18n.text("research.advanced-survey-ruler"),
                SURVEY_RULER_RESEARCH_COST
            ).apply {
                addItems(advancedSurveyRuler)
                register()
            }

            // 11. 勘察尺左键监听器 (潜行左键切换展示形式: 聊天栏 / 箱子界面)
            Bukkit.getPluginManager().registerEvents(
                SurveyDisplayListener(),
                SlimeEasy.instance
            )
        }

        // 11b. 注册生长抑制器 (手持工具, 归实用工具)
        if (SEConfig.growthInhibitorEnabled) {
            val growthInhibitor = GrowthInhibitor(
                Groups.UTILITY_TOOLS,
                Items.GROWTH_INHIBITOR,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                Items.GROWTH_INHIBITOR_RECIPE
            )
            growthInhibitor.register(addon)
            research("growth_inhibitor", 9019, I18n.text("research.growth-inhibitor"), GROWTH_INHIBITOR_RESEARCH_COST, growthInhibitor)
        }

        // 工程师护目镜是可穿戴普通物品；扫描与 DH 可选依赖由全服共享显示服务统一管理。
        if (SEConfig.engineerGogglesEnabled) {
            val engineerGoggles = SlimefunItem(
                Groups.UTILITY_TOOLS,
                Items.ENGINEER_GOGGLES,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                Items.ENGINEER_GOGGLES_RECIPE
            ).also { it.register(addon) }
            research(
                "engineer_goggles",
                9025,
                I18n.text("research.engineer-goggles"),
                ENGINEER_GOGGLES_RESEARCH_COST,
                engineerGoggles
            )
        }

        // 11c. 注册战斗挽具 (4 档, 归实用工具) 并启动乐魂作战定时任务; 关闭时不注册也不启动任务
        if (SEConfig.combatHarnessEnabled) {
            fun harness(stack: SlimefunItemStack, recipe: Array<ItemStack?>): SlimefunItem =
                SlimefunItem(Groups.UTILITY_TOOLS, stack, RecipeType.ENHANCED_CRAFTING_TABLE, recipe).also { it.register(addon) }
            val h1 = harness(Items.COMBAT_HARNESS_I, Items.COMBAT_HARNESS_I_RECIPE)
            val h2 = harness(Items.COMBAT_HARNESS_II, Items.COMBAT_HARNESS_II_RECIPE)
            val h3 = harness(Items.COMBAT_HARNESS_III, Items.COMBAT_HARNESS_III_RECIPE)
            val h4 = harness(Items.COMBAT_HARNESS_IV, Items.COMBAT_HARNESS_IV_RECIPE)
            research("combat_harness", 9020, I18n.text("research.combat-harness"), COMBAT_HARNESS_RESEARCH_COST, h1, h2, h3, h4)
            HarnessCombat.start()
        }

        // 12. 注册存储系统
        registerStorage(addon)

        // 13. 注册简易村民
        registerVillager(addon)

        // 14. 注册简易领地及其统一保护模块
        registerTerritory(addon)

        // 所有附属完成多方块登记后再启动扫描，确保首次刷新即可读取完整注册表。
        if (SEConfig.engineerGogglesEnabled) {
            EngineerGogglesDisplay.start(SlimeEasy.instance)
        }
    }

    /** 注册领地物品、研究、菜单输入监听器，并延迟挂接 Slimefun ProtectionModule。 */
    private fun registerTerritory(addon: SlimefunAddon) {
        if (!SEConfig.territoryEnabled) return
        Groups.TERRITORY.register(addon)
        val recipeType = RecipeType.ENHANCED_CRAFTING_TABLE
        val core = TerritoryCore(
            Groups.TERRITORY, Items.TERRITORY_CORE, recipeType, Items.TERRITORY_CORE_RECIPE
        ).also { it.register(addon) }
        val flag = TerritoryFlag(
            Groups.TERRITORY, Items.TERRITORY_FLAG, recipeType, Items.TERRITORY_FLAG_RECIPE
        ).also { it.register(addon) }
        research("territory", 9024, I18n.text("research.territory"), TERRITORY_RESEARCH_COST, core, flag)

        val pluginManager = Bukkit.getPluginManager()
        pluginManager.registerEvents(TerritoryBlockTransactionListener(), SlimeEasy.instance)
        pluginManager.registerEvents(TerritoryProtectionListener(), SlimeEasy.instance)
        pluginManager.registerEvents(TerritoryInputListener(), SlimeEasy.instance)
        TerritoryBoundaryDisplay.start(SlimeEasy.instance)
        TerritoryProtectionBridge.initialize(SlimeEasy.instance)
    }

    /**
     * 注册简易村民: 分类、各物品、研究解锁与相关监听器。
     *
     * 每项独立受 [SEConfig] 开关控制; 关闭项连同其物品、研究与专属监听器一并跳过。
     */
    private fun registerVillager(addon: SlimefunAddon) {
        Groups.VILLAGER.register(addon)
        val et = RecipeType.ENHANCED_CRAFTING_TABLE
        val pm = Bukkit.getPluginManager()

        // 无自定义行为的普通物品 (捕捉器 / 僵尸信号 / 速度升级 / 遗忘药剂)
        fun plain(stack: SlimefunItemStack, recipe: Array<ItemStack?>): SlimefunItem =
            SlimefunItem(Groups.VILLAGER, stack, et, recipe).also { it.register(addon) }

        if (SEConfig.villagerCatcherEnabled) {
            val catcher = plain(VillagerItems.VILLAGER_CATCHER, VillagerItems.VILLAGER_CATCHER_RECIPE)
            research("villager_catcher", 9011, I18n.text("research.villager-catcher"), VILLAGER_CATCHER_RESEARCH_COST, catcher)
            pm.registerEvents(CatcherListener(), SlimeEasy.instance)
        }
        if (SEConfig.zombieSignalEnabled) {
            val zombieSignal = plain(VillagerItems.ZOMBIE_SIGNAL, VillagerItems.ZOMBIE_SIGNAL_RECIPE)
            research("zombie_signal", 9012, I18n.text("research.zombie-signal"), ZOMBIE_SIGNAL_RESEARCH_COST, zombieSignal)
        }
        if (SEConfig.villagerTraderEnabled) {
            val trader = VillagerTrader(
                Groups.VILLAGER, VillagerItems.VILLAGER_TRADER, et, VillagerItems.VILLAGER_TRADER_RECIPE
            ).also { it.register(addon) }
            research("villager_trader", 9013, I18n.text("research.villager-trader"), VILLAGER_TRADER_RESEARCH_COST, trader)
            pm.registerEvents(TraderListener(), SlimeEasy.instance)
        }
        if (SEConfig.ironFarmEnabled) {
            val ironFarm = IronFarm(
                Groups.VILLAGER, VillagerItems.IRON_FARM, et, VillagerItems.IRON_FARM_RECIPE
            ).also { it.register(addon) }
            val speedUpgrade = plain(VillagerItems.IRON_FARM_SPEED_UPGRADE, VillagerItems.IRON_FARM_SPEED_UPGRADE_RECIPE)
            research("iron_farm", 9014, I18n.text("research.iron-farm"), IRON_FARM_RESEARCH_COST, ironFarm, speedUpgrade)
            pm.registerEvents(IronFarmListener(), SlimeEasy.instance)
        }
        if (SEConfig.villagerSchoolEnabled) {
            val school = VillagerSchool(
                Groups.VILLAGER, VillagerItems.VILLAGER_SCHOOL, et, VillagerItems.VILLAGER_SCHOOL_RECIPE
            ).also { it.register(addon) }
            research("villager_school", 9015, I18n.text("research.villager-school"), VILLAGER_SCHOOL_RESEARCH_COST, school)
        }
        if (SEConfig.forgettingPotionEnabled) {
            val forgettingPotion = plain(VillagerItems.FORGETTING_POTION, VillagerItems.FORGETTING_POTION_RECIPE)
            research("forgetting_potion", 9016, I18n.text("research.forgetting-potion"), FORGETTING_POTION_RESEARCH_COST, forgettingPotion)
            pm.registerEvents(ForgettingPotionListener(), SlimeEasy.instance)
        }
        if (SEConfig.villagerHealerEnabled) {
            val healer = VillagerHealer(
                Groups.VILLAGER, VillagerItems.VILLAGER_HEALER, et, VillagerItems.VILLAGER_HEALER_RECIPE
            ).also { it.register(addon) }
            research("villager_healer", 9017, I18n.text("research.villager-healer"), VILLAGER_HEALER_RESEARCH_COST, healer)
        }
    }

    /**
     * 注册存储系统: 分类、存储方块、网络方块、升级组件、研究解锁与相关监听器。
     *
     * 五大板块 (抽屉 / 翻页箱 / 磁盘 / 升级组件 / 网络) 各自独立受 [SEConfig] 开关控制。
     * 共享监听器 (区块卸载清理、经验球拦截) 只要任一存储板块开启即注册。
     */
    private fun registerStorage(addon: SlimefunAddon) {
        Groups.STORAGE.register(addon)
        val et = RecipeType.ENHANCED_CRAFTING_TABLE
        val pm = Bukkit.getPluginManager()

        // 升级组件 (无自定义行为的普通 Slimefun 物品)
        fun plain(stack: SlimefunItemStack, recipe: Array<ItemStack?>): SlimefunItem =
            registerPlain(addon, Groups.STORAGE, stack, recipe)

        val drawerOn = SEConfig.storageDrawerEnabled
        val boxOn = SEConfig.storageBoxEnabled
        val diskOn = SEConfig.storageDiskEnabled
        val upgradeOn = SEConfig.storageUpgradeEnabled
        val networkOn = SEConfig.storageNetworkEnabled

        if (drawerOn) {
            val drawer = Drawer(Groups.STORAGE, StorageItems.DRAWER, et, StorageItems.DRAWER_RECIPE).also { it.register(addon) }
            research("storage_drawer", 9006, I18n.text("research.storage-drawer"), DRAWER_RESEARCH_COST, drawer)
            // 抽屉展示框交互监听器 (仅抽屉需要)
            pm.registerEvents(DrawerListener(), SlimeEasy.instance)
        }
        if (boxOn) {
            val box = PagedBox(Groups.STORAGE, StorageItems.BOX, et, StorageItems.BOX_RECIPE).also { it.register(addon) }
            research("storage_box", 9007, I18n.text("research.storage-box"), BOX_RESEARCH_COST, box)
        }
        if (diskOn) {
            val manager = DiskManager(
                Groups.STORAGE, StorageItems.DISK_MANAGER, et, StorageItems.DISK_MANAGER_RECIPE
            ).also { it.register(addon) }
            val disk1k = plain(StorageItems.DISK_1K, StorageItems.DISK_1K_RECIPE)
            val disk4k = plain(StorageItems.DISK_4K, StorageItems.DISK_4K_RECIPE)
            val disk16k = plain(StorageItems.DISK_16K, StorageItems.DISK_16K_RECIPE)
            val disk64k = plain(StorageItems.DISK_64K, StorageItems.DISK_64K_RECIPE)
            val disk128k = plain(StorageItems.DISK_128K, StorageItems.DISK_128K_RECIPE)
            val disk256k = plain(StorageItems.DISK_256K, StorageItems.DISK_256K_RECIPE)
            research(
                "storage_disks", 9023, I18n.text("research.storage-disks"), DISK_RESEARCH_COST,
                manager, disk1k, disk4k, disk16k, disk64k, disk128k, disk256k
            )
        }
        if (upgradeOn) {
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
            val outputUp = plain(StorageItems.OUTPUT_UPGRADE, StorageItems.OUTPUT_UPGRADE_RECIPE)
            val compressionUp = plain(StorageItems.COMPRESSION_UPGRADE, StorageItems.COMPRESSION_UPGRADE_RECIPE)
            val advancedCompressionUp = plain(
                StorageItems.ADVANCED_COMPRESSION_UPGRADE, StorageItems.ADVANCED_COMPRESSION_UPGRADE_RECIPE
            )
            research("storage_upgrades", 9008, I18n.text("research.storage-upgrades"), STORAGE_UPGRADE_RESEARCH_COST,
                stackI, stackII, stackIII, expUp, magnetUp, voidUp, pageUp, wiseUp, enderWiseUp, extractUp,
                remoteUp, outputUp, compressionUp, advancedCompressionUp)
        }
        if (networkOn) {
            val controller = NetworkController(Groups.STORAGE, StorageItems.CONTROLLER, et, StorageItems.CONTROLLER_RECIPE).also { it.register(addon) }
            val connector = NetworkConnector(Groups.STORAGE, StorageItems.CONNECTOR, et, StorageItems.CONNECTOR_RECIPE).also { it.register(addon) }
            val inputPort = NetworkPort(Groups.STORAGE, StorageItems.INPUT_PORT, et, StorageItems.INPUT_PORT_RECIPE, true).also { it.register(addon) }
            val outputPort = NetworkPort(Groups.STORAGE, StorageItems.OUTPUT_PORT, et, StorageItems.OUTPUT_PORT_RECIPE, false).also { it.register(addon) }
            val remoteTerminal = RemoteTerminal(Groups.STORAGE, StorageItems.REMOTE_TERMINAL, et, StorageItems.REMOTE_TERMINAL_RECIPE).also { it.register(addon) }
            research("storage_network", 9009, I18n.text("research.storage-network"), NETWORK_RESEARCH_COST,
                controller, connector, inputPort, outputPort, remoteTerminal)
        }

        // 共享监听器: 存储缓存区块卸载清理 + 经验球拦截 (任一存储板块开启即需要)
        if (drawerOn || boxOn || diskOn || upgradeOn || networkOn) {
            pm.registerEvents(
                top.maplex.slimeEasy.storage.core.StorageChunkListener(), SlimeEasy.instance
            )
            pm.registerEvents(
                top.maplex.slimeEasy.storage.drawer.MagnetOrbListener(), SlimeEasy.instance
            )
        }
    }

    /** 绑定一个存储系统研究: 指定经验等级成本, 归入若干物品。 */
    private fun research(key: String, id: Int, name: String, cost: Int, vararg items: SlimefunItem) {
        Research(NamespacedKey(SlimeEasy.instance, key), id, name, cost).apply {
            addItems(*items)
            register()
        }
    }

    /** 注册无自定义行为的增强工作台物品。 */
    private fun registerPlain(
        addon: SlimefunAddon,
        group: ItemGroup,
        stack: SlimefunItemStack,
        recipe: Array<ItemStack?>
    ): SlimefunItem = SlimefunItem(group, stack, RecipeType.ENHANCED_CRAFTING_TABLE, recipe)
        .also { it.register(addon) }
}
