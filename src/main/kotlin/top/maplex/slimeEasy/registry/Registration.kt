package top.maplex.slimeEasy.registry

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.api.researches.Research
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.feature.ward.CreeperSpawnListener
import top.maplex.slimeEasy.feature.ward.CreeperWard
import top.maplex.slimeEasy.machine.breaker.AutoBreaker
import top.maplex.slimeEasy.machine.placer.AutoPlacer

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

    /**
     * 执行全部注册。
     *
     * @param addon 本附属实例, 用于将内容归属到本插件
     */
    fun registerAll(addon: SlimefunAddon) {
        // 1. 注册物品组
        Groups.UTILITY_MACHINES.register(addon)

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

        // 8. 注册苦力怕生成拦截监听器
        Bukkit.getPluginManager().registerEvents(
            CreeperSpawnListener(),
            SlimeEasy.instance
        )
    }
}
