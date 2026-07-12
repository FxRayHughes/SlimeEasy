package top.maplex.slimeEasy.config

import top.maplex.slimeEasy.SlimeEasy

/**
 * SlimeEasy 中央配置层。
 *
 * 统一封装插件 config.yml 的读取, 按功能分区暴露**所有数值 / 开关 / 研究等级**。
 * 每个 getter 都以 get() 形式读取内存态 config 对象并带**硬编码兜底默认值**, 因此:
 *
 * - **运行时数值** (伤害 / 范围 / 冷却 / 间隔 / 上限 等): 每次使用即时读取,
 *   [reload] 后无需重启立即生效。
 * - **注册期数据** (功能开关 / 研究等级): 仅在 onEnable 注册时读取一次,
 *   改动后需 **重启服务端** 才能生效。玩家文本由独立的 [I18n] 语言文件管理。
 *
 * 明确**不纳入配置**的项 (改动会损坏存档或打乱既有布局, 保持硬编码):
 * PDC 持久化键 (`se_*`)、编解码分隔符、数据结构版本号、GUI 槽位索引。
 *
 * 时间间隔类沿用既有约定: 以**秒**暴露给玩家, 内部按墙钟时间戳判定, 不受 tick / 区块 / 重载影响。
 */
object SEConfig {

    private val cfg get() = SlimeEasy.instance.config

    /** 重新从磁盘加载 config.yml (供 /se reload 调用)。 */
    fun reload() {
        SlimeEasy.instance.reloadConfig()
    }

    // ==================== 通用读取助手 ====================

    private fun int(path: String, def: Int, min: Int = Int.MIN_VALUE): Int =
        cfg.getInt(path, def).coerceAtLeast(min)

    private fun long(path: String, def: Long, min: Long = Long.MIN_VALUE): Long =
        cfg.getLong(path, def).coerceAtLeast(min)

    private fun double(path: String, def: Double, min: Double = Double.NEGATIVE_INFINITY): Double =
        cfg.getDouble(path, def).coerceAtLeast(min)

    private fun bool(path: String, def: Boolean): Boolean =
        cfg.getBoolean(path, def)

    val language: String get() = cfg.getString("language", "zh_CN") ?: "zh_CN"

    /** 读取一个"秒"配置并转毫秒 (至少 1 秒)。 */
    private fun seconds(path: String, def: Int): Long =
        cfg.getInt(path, def).coerceAtLeast(1).toLong() * 1000L

    // ==================== 机器通用 (破坏机 / 放置机的展示框调频) ====================

    /** 展示框调频的默认间隔 (Slimefun tick 数)。 */
    val machineDefaultInterval: Int get() = int("machine.frequency.default-interval", 1, 1)

    /** 拉杆每旋转一档改变的 tick 步进。 */
    val machineStepPerAngle: Int get() = int("machine.frequency.step-per-angle", 3, 1)

    // ==================== 自动破坏机 / 自动放置机 (仅开关 + 研究) ====================

    val autoBreakerEnabled: Boolean get() = bool("auto-breaker.enabled", true)
    val autoBreakerResearch: Int get() = int("auto-breaker.research-level", 10, 0)

    val autoPlacerEnabled: Boolean get() = bool("auto-placer.enabled", true)
    val autoPlacerResearch: Int get() = int("auto-placer.research-level", 10, 0)

    // ==================== 筛子 ====================

    /** 是否注册筛子、筛分原料及其研究。 */
    val sieveEnabled: Boolean get() = bool("sieve.enabled", true)

    /** 解锁筛子和四种筛分原料所需的经验等级。 */
    val sieveResearch: Int get() = int("sieve.research-level", 8, 0)

    /**
     * 读取某项筛分产物的独立百分比概率。
     *
     * 配置值被限制在 0..100；每次筛选时即时读取，因此 `/se reload` 后立即生效。
     */
    fun sieveChance(input: String, output: String, default: Int): Int =
        int("sieve.chances.$input.$output", default, 0).coerceAtMost(100)

    // ==================== 苦力怕驱逐方块 ====================

    val creeperWardEnabled: Boolean get() = bool("creeper-ward.enabled", true)
    val creeperWardResearch: Int get() = int("creeper-ward.research-level", 10, 0)

    /** 保护半径 (区块): 1 = 以所在区块为中心的 3×3 区块。 */
    val creeperWardProtectRadius: Int get() = int("creeper-ward.protect-radius-chunks", 1, 0)

    /** 主动推离苦力怕的检测半径 (格)。 */
    val creeperWardDetectRadius: Double get() = double("creeper-ward.detect-radius", 48.0, 0.0)

    /** 推离水平力度。 */
    val creeperWardPushStrength: Double get() = double("creeper-ward.push-strength", 0.8, 0.0)

    /** 推离附加的向上分量。 */
    val creeperWardPushUp: Double get() = double("creeper-ward.push-up", 0.2, 0.0)

    /** 单次 tick 最多推离的苦力怕数量。 */
    val creeperWardMaxPushAttempts: Int get() = int("creeper-ward.max-push-attempts", 20, 1)

    /** 保护区缓存 TTL (秒)。 */
    val creeperWardProtectionTtlMillis: Long get() = seconds("creeper-ward.protection-ttl-seconds", 8)

    /** 单次自然生成事件最多回溯扫描的区块数。 */
    val creeperWardMaxScanChunks: Int get() = int("creeper-ward.max-scan-chunks", 16, 1)

    // ==================== 屠夫机器 ====================

    val butcherEnabled: Boolean get() = bool("butcher.enabled", true)
    val butcherResearch: Int get() = int("butcher.research-level", 15, 0)

    /** 基础攻击截面边长 (格); 每级范围升级 +2。 */
    val butcherBaseSpan: Int get() = int("butcher.base-span", 3, 1)

    /** 基础攻击纵深 (格); 每级范围升级 +1。 */
    val butcherBaseDepth: Int get() = int("butcher.base-depth", 1, 1)

    /** 每级伤害升级的线性加成 (基础值的比例)。 */
    val butcherDamagePerLevel: Double get() = double("butcher.damage-per-level", 0.5, 0.0)

    /** 每 1 点饱食度折算的攻击次数。 */
    val butcherAttacksPerNutrition: Int get() = int("butcher.attacks-per-nutrition", 15, 1)

    /** 内部余量缓存上限 (饱食度)。 */
    val butcherMaxSatiety: Int get() = int("butcher.max-satiety", 100, 1)

    /** 范围 / 伤害升级级数上限。 */
    val butcherMaxUpgradeLevel: Int get() = int("butcher.max-upgrade-level", 5, 0)

    // ==================== 自动点击器 ====================

    val autoClickerEnabled: Boolean get() = bool("auto-clicker.enabled", true)
    val autoClickerResearch: Int get() = int("auto-clicker.research-level", 15, 0)

    /** 最小点击间隔 (tick); 0.05 = 每 tick 连点 20 次。 */
    val autoClickerMinInterval: Double get() = double("auto-clicker.min-interval", 0.05, 0.05)

    /** 最大点击间隔 (tick)。 */
    val autoClickerMaxInterval: Double get() = double("auto-clicker.max-interval", 40.0, 0.05)

    /** 默认点击间隔 (tick)。 */
    val autoClickerDefaultInterval: Double get() = double("auto-clicker.default-interval", 4.0, 0.05)

    /** 微调步进 (Shift 点击, tick)。 */
    val autoClickerFineStep: Double get() = double("auto-clicker.fine-step", 0.05, 0.001)

    /** 粗调步进 (普通点击, tick)。 */
    val autoClickerCoarseStep: Double get() = double("auto-clicker.coarse-step", 0.25, 0.001)

    /** 单个 Slimefun tick 内的最多点击次数 (防卡)。 */
    val autoClickerMaxClicksPerTick: Int get() = int("auto-clicker.max-clicks-per-tick", 20, 1)

    /** 安装抽取升级后, 单个 tick 最多补入物品槽的数量。 */
    val autoClickerExtractMaxItemsPerTick: Int get() = int("auto-clicker.extract-max-items-per-tick", 64, 1)

    // ==================== 采石场 ====================

    val quarryEnabled: Boolean get() = bool("quarry.enabled", true)
    val quarryResearch: Int get() = int("quarry.research-level", 12, 0)
    val quarryBaseIntervalTicks: Int get() = int("quarry.base-interval-ticks", 2, 1)
    val quarryBaseOutput: Int get() = int("quarry.base-output", 1, 1)
    val quarryTier1Output: Int get() = int("quarry.efficiency-output.tier-1", 1, 1)
    val quarryTier2Output: Int get() = int("quarry.efficiency-output.tier-2", 6, 1)
    val quarryTier3Output: Int get() = int("quarry.efficiency-output.tier-3", 12, 1)
    val quarryTier4Output: Int get() = int("quarry.efficiency-output.tier-4", 32, 1)
    val quarryTier5Output: Int get() = int("quarry.efficiency-output.tier-5", 64, 1)
    val quarryDropOverflow: Boolean get() = bool("quarry.drop-overflow", false)

    // ==================== 矿物勘察尺 ====================

    val surveyRulerEnabled: Boolean get() = bool("survey-ruler.enabled", true)
    val surveyRulerResearch: Int get() = int("survey-ruler.research-level", 10, 0)

    /** 勘察冷却 (tick)。 */
    val surveyRulerCooldownTicks: Int get() = int("survey-ruler.cooldown-ticks", 100, 0)

    /** 普通工业矿机采掘半径 (7×7 → 3)。 */
    val surveyMinerRange: Int get() = int("survey-ruler.miner-range", 3, 1)

    /** 进阶工业矿机采掘半径 (11×11 → 5)。 */
    val surveyAdvancedMinerRange: Int get() = int("survey-ruler.advanced-miner-range", 5, 1)

    /** 燃料估算: 每单位燃料可驱动的挖掘方块数 (熔炉/机器折算基准)。 */
    val surveyPerBucket: Int get() = int("survey-ruler.blocks-per-bucket", 96, 1)
    val surveyPerRaw: Int get() = int("survey-ruler.blocks-per-raw-fuel", 128, 1)
    val surveyPerFuel: Int get() = int("survey-ruler.blocks-per-fuel", 256, 1)

    // ==================== 工程师护目镜 ====================

    /** 是否注册工程师护目镜及其研究；注册期读取，修改后需重启。 */
    val engineerGogglesEnabled: Boolean get() = bool("engineer-goggles.enabled", true)

    /** 解锁工程师护目镜所需经验等级。 */
    val engineerGogglesResearch: Int get() = int("engineer-goggles.research-level", 12, 0)

    /** 护目镜扫描的三维直线半径；只检查已加载区块，不由显示功能加载新区块。 */
    val engineerGogglesRadius: Int get() = int("engineer-goggles.radius", 16, 1)

    /** 穿戴检测、机器数据和私有全息图刷新的共享周期。 */
    val engineerGogglesRefreshTicks: Long get() = long("engineer-goggles.refresh-ticks", 10L, 1L)

    /** 每轮允许首次建立的多方块空间单元数量；限制主线程扫描尖峰而不限制最终显示数量。 */
    val engineerGogglesMaxNewCellsPerRefresh: Int
        get() = int("engineer-goggles.max-new-cells-per-refresh", 4, 1)

    // ==================== 生长抑制器 ====================

    val growthInhibitorEnabled: Boolean get() = bool("growth-inhibitor.enabled", true)
    val growthInhibitorResearch: Int get() = int("growth-inhibitor.research-level", 10, 0)

    // ==================== 战斗挽具 ====================

    val combatHarnessEnabled: Boolean get() = bool("combat-harness.enabled", true)
    val combatHarnessResearch: Int get() = int("combat-harness.research-level", 20, 0)

    /** 四档挽具每次激光的魔法伤害 (点)。 */
    val harnessDamageI: Double get() = double("combat-harness.damage.tier-1", 5.0, 0.0)
    val harnessDamageII: Double get() = double("combat-harness.damage.tier-2", 10.0, 0.0)
    val harnessDamageIII: Double get() = double("combat-harness.damage.tier-3", 20.0, 0.0)
    val harnessDamageIV: Double get() = double("combat-harness.damage.tier-4", 25.0, 0.0)

    /** 交战半径 (格)。 */
    val harnessRange: Double get() = double("combat-harness.range", 16.0, 0.0)

    /** 两次开火最小间隔 (毫秒)。 */
    val harnessCooldownMillis: Long get() = long("combat-harness.cooldown-millis", 1500L, 0L)

    /** 扫描周期 (tick)。 */
    val harnessPeriodTicks: Long get() = long("combat-harness.period-ticks", 15L, 1L)

    // ==================== 存储系统 ====================

    val storageDrawerEnabled: Boolean get() = bool("storage.drawer.enabled", true)
    val storageDrawerResearch: Int get() = int("storage.drawer.research-level", 8, 0)

    /** 抽屉磁吸半径 (格)。 */
    val storageDrawerMagnetRadius: Double get() = double("storage.drawer.magnet-radius", 6.0, 0.0)

    /** 抽屉存储的物品种类槽位数。 */
    val storageDrawerSlots: Int get() = int("storage.drawer.slots", 32, 1)

    val storageBoxEnabled: Boolean get() = bool("storage.box.enabled", true)
    val storageBoxResearch: Int get() = int("storage.box.research-level", 15, 0)

    /** 翻页箱每页物品种类数。 */
    val storageBoxPageTypes: Int get() = int("storage.box.page-types", 45, 1)

    /** 翻页箱磁吸半径 (格)。 */
    val storageBoxMagnetRadius: Double get() = double("storage.box.magnet-radius", 6.0, 0.0)

    /** 磁盘管理器及六档磁盘的总开关。 */
    val storageDiskEnabled: Boolean get() = bool("storage.disk.enabled", true)

    /** 磁盘存储研究所需经验等级。 */
    val storageDiskResearch: Int get() = int("storage.disk.research-level", 25, 0)

    val storageUpgradeEnabled: Boolean get() = bool("storage.upgrade.enabled", true)
    val storageUpgradeResearch: Int get() = int("storage.upgrade.research-level", 20, 0)

    /** 升级槽位上限。 */
    val storageUpgradeMaxSlots: Int get() = int("storage.upgrade.max-slots", 12, 1).coerceAtMost(13)

    /** 翻页升级的最大页数。 */
    val storageUpgradeMaxPages: Int get() = int("storage.upgrade.max-pages", 5, 1)

    /** 抽取 / 输出过滤名单最大物品种类数 (GUI 上限 27)。 */
    val storageFilterMaxItems: Int get() = int("storage.upgrade.filter-max-items", 27, 1).coerceAtMost(27)

    /** 新过滤器是否默认白名单; false 为默认黑名单。 */
    val storageFilterDefaultWhitelist: Boolean get() = bool("storage.upgrade.filter-default-whitelist", false)

    /** 每个存储容器或主动输入端口每 tick 抽取的最大物品数; 0 为不限。 */
    val storageIoPullMaxItemsPerTick: Int get() = int("storage.io.pull-max-items-per-tick", 0, 0)

    /** 每个存储容器或主动输出端口每 tick 输出的最大物品数; 0 为不限。 */
    val storageIoPushMaxItemsPerTick: Int get() = int("storage.io.push-max-items-per-tick", 0, 0)

    val storageNetworkEnabled: Boolean get() = bool("storage.network.enabled", true)
    val storageNetworkResearch: Int get() = int("storage.network.research-level", 30, 0)

    /** 单个远程终端最多绑定的控制器数量; 0 为不限。 */
    val storageNetworkRemoteTerminalMaxBindings: Int get() =
        int("storage.network.remote-terminal-max-bindings", 0, 0)

    /**
     * 网络扫描半径 (格)。
     *
     * 上限 63: NetworkScan 的键打包每轴仅 7 位 (偏移 + 半径 ∈ [0, 2×半径] 须 ≤127),
     * 超过将导致键碰撞、错误并网。故在此夹取, 防止误配置损坏网络拓扑。
     */
    val storageNetworkScanRadius: Int get() = int("storage.network.scan-radius", 24, 1).coerceAtMost(63)

    // ==================== 简易村民 ====================

    val villagerCatcherEnabled: Boolean get() = bool("villager.catcher.enabled", true)
    val villagerCatcherResearch: Int get() = int("villager.catcher.research-level", 12, 0)

    val zombieSignalEnabled: Boolean get() = bool("villager.zombie-signal.enabled", true)
    val zombieSignalResearch: Int get() = int("villager.zombie-signal.research-level", 8, 0)

    val villagerTraderEnabled: Boolean get() = bool("villager.trader.enabled", true)
    val villagerTraderResearch: Int get() = int("villager.trader.research-level", 18, 0)

    /** 交易器补货间隔 (毫秒)。 */
    val traderRestockMillis: Long get() = seconds("villager.trader.restock-interval-seconds", 30)

    val ironFarmEnabled: Boolean get() = bool("villager.iron-farm.enabled", true)
    val ironFarmResearch: Int get() = int("villager.iron-farm.research-level", 20, 0)

    /** 刷铁机产铁间隔 (毫秒, 未计速度升级)。 */
    val ironProduceMillis: Long get() = seconds("villager.iron-farm.produce-interval-seconds", 20)

    /** 刷铁机每周期消耗的食物饱食度。 */
    val ironFoodPerCycle: Int get() = int("villager.iron-farm.food-per-cycle", 1, 0)

    /** 刷铁机每周期产出的铁锭数量。 */
    val ironPerCycle: Int get() = int("villager.iron-farm.iron-per-cycle", 1, 1)

    /** 刷铁机速度升级级数上限。 */
    val ironSpeedMaxLevel: Int get() = int("villager.iron-farm.speed-upgrade-max-level", 5, 0)

    /** 速度升级每级对间隔的缩短系数。 */
    val ironSpeedStep: Double get() = double("villager.iron-farm.speed-upgrade-step", 0.5, 0.0)

    val villagerSchoolEnabled: Boolean get() = bool("villager.school.enabled", true)
    val villagerSchoolResearch: Int get() = int("villager.school.research-level", 15, 0)

    /** 村民小学转化耗时 (毫秒)。 */
    val schoolConvertMillis: Long get() = seconds("villager.school.convert-seconds", 30)

    val forgettingPotionEnabled: Boolean get() = bool("villager.forgetting-potion.enabled", true)
    val forgettingPotionResearch: Int get() = int("villager.forgetting-potion.research-level", 12, 0)

    val villagerHealerEnabled: Boolean get() = bool("villager.healer.enabled", true)
    val villagerHealerResearch: Int get() = int("villager.healer.research-level", 15, 0)

    /** 村民治愈机治愈耗时 (毫秒)。 */
    val healerConvertMillis: Long get() = seconds("villager.healer.convert-seconds", 30)

    // ==================== 简易的领地 ====================

    /** 是否注册领地分类、物品、保护模块及监听器；切换后需重启。 */
    val territoryEnabled: Boolean get() = bool("territory.enabled", true)

    /** 解锁领地核心与旗帜所需的经验等级。 */
    val territoryResearch: Int get() = int("territory.research-level", 8, 0)
}
