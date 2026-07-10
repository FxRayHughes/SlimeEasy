package top.maplex.slimeEasy.registry

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import top.maplex.slimeEasy.SlimeEasy

/**
 * 物品分类注册中心。
 *
 * 集中管理本附属所有 [ItemGroup], 便于统一维护与查找。
 */
object Groups {

    /**
     * "实用机械" 分类。
     *
     * 图标使用活塞, 直观体现本分类以机械装置为主。
     * NamespacedKey 的命名空间取插件实例, key 使用小写下划线风格。
     */
    val UTILITY_MACHINES: ItemGroup = ItemGroup(
        NamespacedKey(SlimeEasy.instance, "utility_machines"),
        SlimefunItemStack(
            "SE_GROUP_UTILITY_MACHINES",
            Material.PISTON,
            "&b实用机械",
            "",
            "&7各类自动化实用装置"
        )
    )

    /**
     * "实用工具" 分类。
     *
     * 图标使用铜锄, 呼应本分类以手持勘察 / 辅助工具为主。
     */
    val UTILITY_TOOLS: ItemGroup = ItemGroup(
        NamespacedKey(SlimeEasy.instance, "utility_tools"),
        SlimefunItemStack(
            "SE_GROUP_UTILITY_TOOLS",
            Material.COPPER_HOE,
            "&b实用工具",
            "",
            "&7各类手持辅助工具"
        )
    )

    /**
     * "存储系统" 分类。
     *
     * 图标使用木桶, 涵盖抽屉 / 翻页箱 / 存储网络 (控制器·连接器·端口) 与各类升级组件。
     */
    val STORAGE: ItemGroup = ItemGroup(
        NamespacedKey(SlimeEasy.instance, "storage"),
        SlimefunItemStack(
            "SE_GROUP_STORAGE",
            Material.BARREL,
            "&b存储系统",
            "",
            "&7海量存储抽屉、翻页箱与存储网络"
        )
    )

    /**
     * "简易村民" 分类。
     *
     * 图标使用村民刷怪蛋, 涵盖村民捕捉器、僵尸信号、村民交易器、胶囊刷铁机、村民小学与遗忘药剂。
     */
    val VILLAGER: ItemGroup = ItemGroup(
        NamespacedKey(SlimeEasy.instance, "villager"),
        SlimefunItemStack(
            "SE_GROUP_VILLAGER",
            Material.VILLAGER_SPAWN_EGG,
            "&b简易村民",
            "",
            "&7与村民打交道而无需操心保护村民"
        )
    )
}
