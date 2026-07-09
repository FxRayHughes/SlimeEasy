package top.maplex.slimeEasy.storage.core

import org.bukkit.inventory.ItemStack

/**
 * 物品身份主键。
 *
 * 存储网络的路由索引 (Map/Set) 以本类为键区分"同物品不同 NBT"。
 * 内部持有一个数量恒为 1 的只读模板, [equals]/[hashCode] 直接委托给
 * Bukkit [ItemStack] 的实现 —— 后者已正确纳入 Material 与全部 meta
 * (显示名、lore、附魔、自定义模型、Slimefun id 等) 的比较, 无需自行拆解。
 *
 * @property template 数量为 1 的物品模板 (防御性拷贝, 外部不可变更本键)
 */
class ItemKey private constructor(val template: ItemStack) {

    /** 该物品的原版最大堆叠上限 (1..99), 供容量换算使用。 */
    val vanillaMaxStack: Int
        get() = template.maxStackSize

    /** 生成一个指定数量的展示物拷贝 (数量将被夹取到 1..99, 仅作视觉)。 */
    fun toDisplay(amount: Int): ItemStack = template.clone().apply {
        this.amount = amount.coerceIn(1, 99)
    }

    /** 判断给定物品是否与本键同类 (忽略数量)。 */
    fun matches(other: ItemStack?): Boolean =
        other != null && template.isSimilar(other)

    override fun equals(other: Any?): Boolean =
        this === other || (other is ItemKey && template.isSimilar(other.template))

    override fun hashCode(): Int {
        // ItemStack.hashCode 含 amount, 而本键 amount 恒为 1, 故稳定且与 equals 一致
        return template.hashCode()
    }

    companion object {
        /**
         * 由任意物品构造主键; 传入 null 或 AIR 返回 null。
         *
         * 会克隆并把数量归一化为 1, 使同物品不同数量映射到同一键。
         */
        fun of(item: ItemStack?): ItemKey? {
            if (item == null || item.type.isAir) return null
            val unit = item.clone().apply { amount = 1 }
            return ItemKey(unit)
        }
    }
}
