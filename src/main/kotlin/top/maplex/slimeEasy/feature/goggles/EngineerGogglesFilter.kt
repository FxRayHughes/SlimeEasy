package top.maplex.slimeEasy.feature.goggles

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineProcessHolder
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy

/**
 * 护目镜的互斥功能分类。
 *
 * 每个展示目标只归入一个功能，过滤时因此可用“分类启用 AND 功能启用”的稳定语义，避免同时属于
 * 多个功能时出现一项关闭却仍被另一项放行的歧义。
 */
internal enum class EngineerGogglesFunction(val filterKey: String, val icon: Material) {
    MULTIBLOCK("multiblock", Material.CRAFTING_TABLE),
    GENERATOR("generator", Material.DAYLIGHT_DETECTOR),
    TEMPLATE_MACHINE("template-machine", Material.BLAST_FURNACE),
    RECIPE_MACHINE("recipe-machine", Material.CRAFTER),
    CONSUMER("consumer", Material.FURNACE),
    CAPACITOR("capacitor", Material.REDSTONE_BLOCK),
    CONNECTOR("connector", Material.REPEATER),
    OTHER("other", Material.SLIME_BALL);

    companion object {
        /** 多方块优先单独分类，其余方块按 Slimefun 能源组件类型归类。 */
        fun of(target: EngineerGogglesTarget): EngineerGogglesFunction {
            if (target.blockData == null) return MULTIBLOCK
            if (target.item is EnergyNetProvider) return GENERATOR
            if (target.item is AContainer) return TEMPLATE_MACHINE
            if (target.item is RecipeDisplayItem) return RECIPE_MACHINE
            val component = target.item as? EnergyNetComponent ?: return OTHER
            return when (component.energyComponentType) {
                EnergyNetComponentType.GENERATOR -> GENERATOR
                EnergyNetComponentType.CONSUMER -> CONSUMER
                EnergyNetComponentType.CAPACITOR -> CAPACITOR
                EnergyNetComponentType.CONNECTOR -> CONNECTOR
                else -> OTHER
            }
        }
    }
}

/** 可被公共 API 验证的机器工作状态；无法查询的附属实现必须归入 [UNKNOWN]。 */
internal enum class EngineerGogglesWorkState(val filterKey: String, val icon: Material) {
    WORKING("working", Material.LIME_DYE),
    IDLE("idle", Material.RED_DYE),
    UNKNOWN("unknown", Material.GRAY_DYE);

    companion object {
        /**
         * 优先读取公共 [MachineProcessHolder] 的当前操作，覆盖标准模板机、发电机和实现同一协议的附属机器；
         * 未暴露处理器的发电组件再以当前输出判断，其它实现保持未知，避免猜测私有状态。
         */
        fun of(target: EngineerGogglesTarget): EngineerGogglesWorkState {
            val data = target.blockData ?: return UNKNOWN
            if (!data.isDataLoaded) return UNKNOWN
            (target.item as? MachineProcessHolder<*>)?.let { machine ->
                return if (machine.machineProcessor.getOperation(target.blockLocation) != null) WORKING else IDLE
            }
            (target.item as? EnergyNetProvider)?.let { provider ->
                val output = runCatching {
                    provider.getGeneratedOutputLong(target.blockLocation, data as ASlimefunDataContainer)
                }.getOrNull() ?: return UNKNOWN
                return if (output > 0L) WORKING else IDLE
            }
            return UNKNOWN
        }
    }
}

/**
 * 持久化在单个工程师护目镜上的过滤协议。
 *
 * 五个 PDC 字段都保存“禁用集合”而非“启用集合”，因此旧护目镜和未来新增的 Slimefun 分类/功能默认可见。
 * 集合成员均为不含换行的稳定键，使用换行分隔可避免 NamespacedKey 中的冒号与物品 ID 冲突。
 */
internal object EngineerGogglesFilter {

    /** Slimefun ItemGroup NamespacedKey 的禁用集合持久化键；修改会丢失既有过滤偏好。 */
    private const val DISABLED_GROUPS_KEY = "engineer_goggles_disabled_groups"

    /** [EngineerGogglesFunction.filterKey] 的禁用集合持久化键。 */
    private const val DISABLED_FUNCTIONS_KEY = "engineer_goggles_disabled_functions"

    /** 潜行右键单独隐藏的 Slimefun 物品 ID 集合持久化键。 */
    private const val HIDDEN_ITEMS_KEY = "engineer_goggles_hidden_items"

    /** SlimefunAddon 名称的禁用集合持久化键。 */
    private const val DISABLED_ADDONS_KEY = "engineer_goggles_disabled_addons"

    /** [EngineerGogglesWorkState.filterKey] 的禁用集合持久化键。 */
    private const val DISABLED_STATES_KEY = "engineer_goggles_disabled_states"

    private val disabledGroupsKey by lazy { NamespacedKey(SlimeEasy.instance, DISABLED_GROUPS_KEY) }
    private val disabledFunctionsKey by lazy { NamespacedKey(SlimeEasy.instance, DISABLED_FUNCTIONS_KEY) }
    private val hiddenItemsKey by lazy { NamespacedKey(SlimeEasy.instance, HIDDEN_ITEMS_KEY) }
    private val disabledAddonsKey by lazy { NamespacedKey(SlimeEasy.instance, DISABLED_ADDONS_KEY) }
    private val disabledStatesKey by lazy { NamespacedKey(SlimeEasy.instance, DISABLED_STATES_KEY) }

    /** 一次性读取的不可变过滤快照，供同一轮所有目标复用。 */
    data class Snapshot(
        val disabledGroups: Set<String>,
        val disabledFunctions: Set<String>,
        val hiddenItems: Set<String>,
        val disabledAddons: Set<String>,
        val disabledStates: Set<String>
    ) {
        /**
         * 分类、功能、附属、状态和单项隐藏五层均通过时才允许显示目标。
         * 工作状态由刷新轮次共享层传入，避免多个佩戴者对同一机器重复查询处理器。
         */
        fun allows(target: EngineerGogglesTarget, workState: EngineerGogglesWorkState): Boolean =
            target.item.itemGroup.key.toString() !in disabledGroups &&
                EngineerGogglesFunction.of(target).filterKey !in disabledFunctions &&
                target.item.id !in hiddenItems &&
                target.item.addon.name !in disabledAddons &&
                workState.filterKey !in disabledStates
    }

    /** 从物品 PDC 读取过滤快照；无数据的旧护目镜默认显示全部。 */
    fun read(item: ItemStack): Snapshot = Snapshot(
        readSet(item, disabledGroupsKey),
        readSet(item, disabledFunctionsKey),
        readSet(item, hiddenItemsKey),
        readSet(item, disabledAddonsKey),
        readSet(item, disabledStatesKey)
    )

    /** 切换某 Slimefun 分类并返回切换后是否显示。 */
    fun toggleGroup(item: ItemStack, group: String): Boolean = toggle(item, disabledGroupsKey, group)

    /** 切换某功能分类并返回切换后是否显示。 */
    fun toggleFunction(item: ItemStack, function: EngineerGogglesFunction): Boolean =
        toggle(item, disabledFunctionsKey, function.filterKey)

    /** 切换某 Slimefun 物品类型并返回切换后是否显示。 */
    fun toggleItem(item: ItemStack, itemId: String): Boolean = toggle(item, hiddenItemsKey, itemId)

    /** 切换某附属的全部目标并返回切换后是否显示。 */
    fun toggleAddon(item: ItemStack, addon: String): Boolean = toggle(item, disabledAddonsKey, addon)

    /** 切换某工作状态并返回切换后是否显示。 */
    fun toggleState(item: ItemStack, state: EngineerGogglesWorkState): Boolean =
        toggle(item, disabledStatesKey, state.filterKey)

    /** 清除所有过滤字段，恢复默认全部显示。 */
    fun reset(item: ItemStack) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.remove(disabledGroupsKey)
        meta.persistentDataContainer.remove(disabledFunctionsKey)
        meta.persistentDataContainer.remove(hiddenItemsKey)
        meta.persistentDataContainer.remove(disabledAddonsKey)
        meta.persistentDataContainer.remove(disabledStatesKey)
        item.itemMeta = meta
    }

    private fun toggle(item: ItemStack, key: NamespacedKey, value: String): Boolean {
        require('\n' !in value) { "Engineer goggles filter keys cannot contain line breaks" }
        val values = readSet(item, key).toMutableSet()
        val enabled = if (value in values) {
            values.remove(value)
            true
        } else {
            values.add(value)
            false
        }
        writeSet(item, key, values)
        return enabled
    }

    private fun readSet(item: ItemStack, key: NamespacedKey): Set<String> {
        val raw = item.itemMeta?.persistentDataContainer?.get(key, PersistentDataType.STRING).orEmpty()
        return raw.lineSequence().filter(String::isNotBlank).toSet()
    }

    private fun writeSet(item: ItemStack, key: NamespacedKey, values: Set<String>) {
        val meta = item.itemMeta ?: return
        if (values.isEmpty()) {
            meta.persistentDataContainer.remove(key)
        } else {
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, values.sorted().joinToString("\n"))
        }
        item.itemMeta = meta
    }
}
