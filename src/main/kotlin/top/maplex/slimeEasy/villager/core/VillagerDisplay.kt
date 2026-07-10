package top.maplex.slimeEasy.villager.core

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.persistence.PersistentDataType
import top.maplex.slimeEasy.SlimeEasy

/**
 * 方块内嵌"小生物"展示层 (交易器 / 刷铁机共用)。
 *
 * 用**真实生物实体**(村民 / 僵尸 / 铁傀儡本体) 作展示 —— 而非 block/item/text Display 实体
 * (它们无法呈现村民等生物模型)。每只展示生物统一被"钉死"为纯装饰:
 * 关闭 AI 与感知、无敌、静音、无重力、不可碰撞、持久化, 并用 SCALE 尺寸属性缩小。
 *
 * 每个展示实体以一个 [dataKey] 记录 UUID 到方块 BlockData; 一个方块可挂多只展示 (不同 key)。
 * 所有方法须在主线程调用。
 */
object VillagerDisplay {

    /**
     * SCALE (尺寸) 属性: MC 1.20.5 加入、1.21.2 由 `generic.scale` 更名为 `scale`。
     * 运行时按 key 容错查找, 不硬编码枚举名; 取不到则缩放降级为默认大小 (功能不受阻)。
     */
    private val scaleAttribute: Attribute? by lazy { resolveScaleAttribute() }

    /** 展示实体的持久化标记键: 供捕捉器 / 遗忘药剂识别并跳过展示实体, 避免误捕误改。 */
    val DISPLAY_KEY: NamespacedKey = NamespacedKey(SlimeEasy.instance, "se_display_marker")

    /** 某实体是否为本插件的展示实体 (纯装饰, 不可被捕捉 / 遗忘)。 */
    fun isDisplay(entity: Entity): Boolean =
        entity.persistentDataContainer.has(DISPLAY_KEY, PersistentDataType.BYTE)

    /** 成年村民 / 僵尸的近似原始身高 (格), 用于把缩放后的展示实体在方块内垂直居中。 */
    private const val DEFAULT_BASE_HEIGHT = 1.95

    /**
     * 生成一只展示生物 (若同 key 已存在先清除), 记录 UUID 到 BlockData。
     *
     * 展示实体的坐标锚点是**脚部**。为使缩放后的小实体整体嵌入单个方块内 (而非脚踩方块中层、
     * 上半身冒出顶部), 脚部 y 置于**方块底面**并按缩放后身高做垂直居中:
     * `y = 方块底面 + (1 - 原始身高 × scale) / 2`。水平方向可用 [dx] / [dz] 在方块内偏移 (多实体并排)。
     *
     * @param baseHeight 该实体的近似原始身高 (格); 铁傀儡等偏高实体需传入更大值
     * @param dx / dz 相对方块中心的水平偏移 (格)
     * @param configure 生成后的额外配置 (如设定村民职业外观)
     */
    fun <T : LivingEntity> spawn(
        block: Block,
        dataKey: String,
        clazz: Class<T>,
        scale: Double,
        baseHeight: Double = DEFAULT_BASE_HEIGHT,
        dx: Double = 0.0,
        dz: Double = 0.0,
        configure: (T) -> Unit = {}
    ): T {
        remove(block, dataKey)
        // 脚部落在方块底面, 再抬升 (1 - 缩放后身高)/2 使实体在方块内垂直居中
        val offsetY = ((1.0 - baseHeight * scale) / 2.0).coerceAtLeast(0.0)
        val at = block.location.add(0.5 + dx, offsetY, 0.5 + dz)
        val entity = block.world.spawn(at, clazz) { decorate(it, scale) }
        configure(entity)
        StorageCacheUtils.setData(block.location, dataKey, entity.uniqueId.toString())
        return entity
    }

    /** 依 BlockData 记录的 UUID 解析展示实体; 缺失 / 已卸载返回 null。 */
    fun get(block: Block, dataKey: String): Entity? {
        val raw = StorageCacheUtils.getData(block.location, dataKey)
        if (raw.isNullOrEmpty()) return null
        val uuid = runCatching { java.util.UUID.fromString(raw) }.getOrNull() ?: return null
        return Bukkit.getEntity(uuid)
    }

    /** 移除某展示实体并清除其 BlockData 记录。 */
    fun remove(block: Block, dataKey: String) {
        get(block, dataKey)?.remove()
        StorageCacheUtils.setData(block.location, dataKey, "")
    }

    /** 把实体钉死为纯装饰: 关 AI / 感知、无敌、静音、无重力、不可碰撞、持久化、按 scale 缩小。 */
    private fun decorate(entity: LivingEntity, scale: Double) {
        entity.setAI(false)
        (entity as? Mob)?.isAware = false
        entity.isInvulnerable = true
        entity.isSilent = true
        entity.setGravity(false)
        entity.isPersistent = true
        entity.isCollidable = false
        // 持久化标记为展示实体, 防止玩家用捕捉器 / 遗忘药剂对其操作
        entity.persistentDataContainer.set(DISPLAY_KEY, PersistentDataType.BYTE, 1)
        scaleAttribute?.let { attr ->
            runCatching { entity.getAttribute(attr)?.baseValue = scale }
        }
    }

    private fun resolveScaleAttribute(): Attribute? =
        runCatching { Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale")) }.getOrNull()
            ?: runCatching { Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale")) }.getOrNull()
}
