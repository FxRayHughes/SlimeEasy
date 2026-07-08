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
}
