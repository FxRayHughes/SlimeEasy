package top.maplex.slimeEasy.machine.butcher

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.data.Directional
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.SlimeEasy
import top.maplex.slimeEasy.config.SEConfig

/**
 * 屠夫机器的世界操作逻辑: 目标收集、伤害计算、范围横扫、食物折算、耐久消耗。
 *
 * 所有方法均需在主线程调用 (涉及实体与方块数据修改)。
 * 数值 (截面 / 纵深 / 伤害系数 / 折算 / 上限) 实时读取 [SEConfig], /se reload 后即时生效。
 */
object ButcherLogic {

    /** 基础攻击截面边长 (格); 每级范围升级 +2。 */
    private val baseSpan: Int get() = SEConfig.butcherBaseSpan

    /** 基础攻击纵深 (格); 每级范围升级 +1。 */
    private val baseDepth: Int get() = SEConfig.butcherBaseDepth

    /** 每级伤害升级的线性加成 (基础值的比例)。 */
    private val damagePerLevel: Double get() = SEConfig.butcherDamagePerLevel

    /** 每 1 点饱食度折算的攻击次数。 */
    val ATTACKS_PER_NUTRITION: Int get() = SEConfig.butcherAttacksPerNutrition

    /** 内部余量缓存上限 (饱食度)。机器最多预吞这么多饱食度的食物。 */
    val MAX_SATIETY: Int get() = SEConfig.butcherMaxSatiety

    /** 内部余量缓存上限 (换算为攻击次数) = [MAX_SATIETY] × [ATTACKS_PER_NUTRITION]。 */
    val MAX_FUEL: Long get() = MAX_SATIETY.toLong() * ATTACKS_PER_NUTRITION

    /** 命中怪物时写入的所有者标记键 (供死亡监听器识别本机击杀)。 */
    val KEY_KILLER = NamespacedKey(SlimeEasy.instance, "butcher_killer")

    /**
     * 常见食物的饱食度 (nutrition) 映射表。
     *
     * 手写而非依赖版本 API: 不同 Minecraft 版本的食物组件访问方式差异较大,
     * 固定映射稳定且可控。未列出的物品视为非食物 (返回 0)。
     */
    private val NUTRITION: Map<Material, Int> = mapOf(
        Material.ROTTEN_FLESH to 4, Material.BREAD to 5, Material.COOKED_BEEF to 8,
        Material.COOKED_PORKCHOP to 8, Material.COOKED_MUTTON to 6, Material.COOKED_CHICKEN to 6,
        Material.COOKED_RABBIT to 5, Material.COOKED_COD to 5, Material.COOKED_SALMON to 6,
        Material.BEEF to 3, Material.PORKCHOP to 3, Material.CHICKEN to 2, Material.MUTTON to 2,
        Material.APPLE to 4, Material.GOLDEN_APPLE to 4, Material.GOLDEN_CARROT to 6,
        Material.CARROT to 3, Material.POTATO to 1, Material.BAKED_POTATO to 5, Material.BEETROOT to 1,
        Material.MELON_SLICE to 2, Material.SWEET_BERRIES to 2, Material.COOKIE to 2,
        Material.PUMPKIN_PIE to 8, Material.MUSHROOM_STEW to 6, Material.RABBIT_STEW to 10,
        Material.BEETROOT_SOUP to 6, Material.DRIED_KELP to 1
    )

    /**
     * 各武器材质的基础攻击力 (含徒手基准, 即原版"攻击伤害"总值)。
     *
     * 涵盖木/石/铜/铁/金/钻/下界合金各档剑与斧, 以及三叉戟与重锤。未列出的武器
     * (如未来新增材质) 由 [fallbackDamage] 按类型给合理默认值, 不会被拒绝。
     */
    private val WEAPON_DAMAGE: Map<Material, Double> = mapOf(
        Material.WOODEN_SWORD to 4.0, Material.GOLDEN_SWORD to 4.0, Material.STONE_SWORD to 5.0,
        Material.COPPER_SWORD to 5.0, Material.IRON_SWORD to 6.0, Material.DIAMOND_SWORD to 7.0,
        Material.NETHERITE_SWORD to 8.0,
        Material.WOODEN_AXE to 7.0, Material.STONE_AXE to 9.0, Material.COPPER_AXE to 9.0,
        Material.IRON_AXE to 9.0, Material.DIAMOND_AXE to 9.0, Material.NETHERITE_AXE to 10.0,
        Material.TRIDENT to 9.0, Material.MACE to 6.0
    )

    /**
     * 判断某物品是否为可接受的武器。
     *
     * 按材质名后缀识别 (`_SWORD` / `_AXE`) 及重锤 / 三叉戟, 而非硬编码枚举 —— 铜剑、
     * 以及未来任何新档位武器都能自动被接受, 不会因遗漏映射而被静默拒绝。
     */
    fun isWeapon(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        val name = item.type.name
        return name.endsWith("_SWORD") || name.endsWith("_AXE") ||
            name == "MACE" || name == "TRIDENT"
    }

    /** 未在 [WEAPON_DAMAGE] 中列出的武器的默认攻击力 (按类型合理估值)。 */
    private fun fallbackDamage(material: Material): Double {
        val name = material.name
        return when {
            name.endsWith("_AXE") -> 9.0
            name == "TRIDENT" -> 9.0
            name == "MACE" -> 6.0
            else -> 4.0 // 剑类默认 (木剑基准)
        }
    }

    /** 判断某物品是否为附魔书。 */
    fun isBook(item: ItemStack?): Boolean =
        item != null && item.type == Material.ENCHANTED_BOOK

    /** 判断某物品是否为范围升级组件 (按 Slimefun ID 识别)。 */
    fun isRangeUpgrade(item: ItemStack?): Boolean = matchesId(item, "SE_BUTCHER_RANGE_UPGRADE")

    /** 判断某物品是否为伤害升级组件 (按 Slimefun ID 识别)。 */
    fun isDamageUpgrade(item: ItemStack?): Boolean = matchesId(item, "SE_BUTCHER_DAMAGE_UPGRADE")

    /** 物品的 Slimefun ID 是否等于给定值。 */
    private fun matchesId(item: ItemStack?, id: String): Boolean {
        if (item == null || item.type.isAir) return false
        return io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getByItem(item)?.id == id
    }

    /** 某物品的饱食度 (非食物返回 0)。 */
    fun nutritionOf(material: Material): Int = NUTRITION[material] ?: 0

    /** 观察者脸朝向即攻击方向; 非定向方块返回 null。 */
    fun facingOf(machine: Block): org.bukkit.block.BlockFace? =
        (machine.blockData as? Directional)?.facing

    /**
     * 收集攻击区域内的合法目标: facing **正前方**的扁平 N×N 截面 + 纵深内的非玩家生物。
     *
     * - 截面边长 N = [BASE_SPAN] + 2×[rangeLevel] (3×3 → 5×5 → 7×7 …);
     * - 纵深 = [BASE_DEPTH] + [rangeLevel] (格), 从机器正前一格起向前延伸。
     *
     * 排除玩家 (含所有者) 与盔甲架 (ArmorStand 属 LivingEntity 但非攻击目标)。
     */
    fun collectTargets(
        machine: Block, face: org.bukkit.block.BlockFace, rangeLevel: Int
    ): List<LivingEntity> {
        val halfSpan = (baseSpan + 2 * rangeLevel) / 2.0
        val depth = (baseDepth + rangeLevel).toDouble()
        val halfDepth = depth / 2.0
        // 中心沿 facing 外推 (机器面 +0.5 到区域中点)
        val offset = 0.5 + halfDepth
        val center = machine.location.toCenterLocation().add(
            face.modX * offset, face.modY * offset, face.modZ * offset
        )
        // 沿 facing 轴用纵深半径, 其余轴用截面半宽
        val rx = if (face.modX != 0) halfDepth else halfSpan
        val ry = if (face.modY != 0) halfDepth else halfSpan
        val rz = if (face.modZ != 0) halfDepth else halfSpan
        return machine.world.getNearbyEntities(center, rx, ry, rz)
            .filterIsInstance<LivingEntity>()
            .filter {
                it !is Player &&
                    it.type != org.bukkit.entity.EntityType.ARMOR_STAND &&
                    // 排除已驯服宠物 (玩家的狼/猫/鹦鹉等), 避免误杀
                    !((it as? org.bukkit.entity.Tameable)?.isTamed ?: false)
            }
    }

    /**
     * 以武器 (合并附魔书附魔) 计算基础攻击伤害, 再按伤害升级级数线性放大。
     *
     * = (武器基础攻击力 + 锋利加成) × (1 + [DAMAGE_PER_LEVEL] × [damageLevel])。
     * 锋利加成 0.5×level + 0.5, 与原版一致; 附魔书的锋利也计入。
     */
    fun computeDamage(weapon: ItemStack, book: ItemStack?, damageLevel: Int): Double {
        val base = WEAPON_DAMAGE[weapon.type] ?: fallbackDamage(weapon.type)
        val sharp = maxOf(
            weapon.getEnchantmentLevel(Enchantment.SHARPNESS),
            book?.let { effectiveLevel(it, Enchantment.SHARPNESS) } ?: 0
        )
        val sharpBonus = if (sharp > 0) 0.5 * sharp + 0.5 else 0.0
        return (base + sharpBonus) * (1.0 + damagePerLevel * damageLevel)
    }

    /** 附魔书上某附魔的等级 (存储附魔取 storedEnchants)。 */
    fun effectiveLevel(book: ItemStack, ench: Enchantment): Int {
        val meta = book.itemMeta as? org.bukkit.inventory.meta.EnchantmentStorageMeta
            ?: return book.getEnchantmentLevel(ench)
        return meta.getStoredEnchantLevel(ench)
    }

    /** 武器与附魔书合并后的火焰附加等级 (>0 表示点燃)。 */
    fun fireAspectLevel(weapon: ItemStack, book: ItemStack?): Int = maxOf(
        weapon.getEnchantmentLevel(Enchantment.FIRE_ASPECT),
        book?.let { effectiveLevel(it, Enchantment.FIRE_ASPECT) } ?: 0
    )

    /**
     * 生成"武器 + 附魔书附魔"合并后的物品副本 (数量 1)。
     *
     * 把附魔书 (storedEnchants) 的附魔叠加到武器副本上, 取两者较高等级。假玩家
     * 手持此副本攻击时, 原版据手持物品自然应用抢夺缩放掉落与锋利伤害, 无需另算。
     */
    fun effectiveWeapon(weapon: ItemStack, book: ItemStack?): ItemStack {
        val result = weapon.clone().apply { amount = 1 }
        val stored = (book?.itemMeta as? org.bukkit.inventory.meta.EnchantmentStorageMeta)?.storedEnchants ?: return result
        for ((ench, lvl) in stored) {
            val cur = result.getEnchantmentLevel(ench)
            if (lvl > cur) result.addUnsafeEnchantment(ench, lvl)
        }
        return result
    }

    /**
     * 对区域内目标执行一次范围横扫伤害。
     *
     * 优先路径: 取本世界假玩家, 令其手持 [effective] 武器, 以其为伤害来源 —— 原版
     * 据此认定"玩家击杀", 经验 / 稀有掉落 / 抢夺缩放全套自然生效。
     * 降级路径 (假玩家不可用): 用 [org.bukkit.damage.DamageSource] PLAYER_ATTACK 造成
     * 伤害, 并给目标打标记, 由 [ButcherDeathListener] 在死亡时补经验。
     *
     * 抢夺无需单独传递: 已由 [effectiveWeapon] 合并进假玩家手持武器, 原版据此自然
     * 缩放掉落。仅写入 [KEY_KILLER] (owner) 标记供死亡监听器识别; 未被打死的目标在
     * 攻击后清除标记, 使标记只留在真正被本机击杀的怪身上。
     *
     * @param dmg 每个目标承受的伤害值
     * @param fire 火焰附加等级 (>0 则点燃, 每级 4 秒)
     */
    fun performSweep(
        machine: Block, targets: List<LivingEntity>, effective: ItemStack,
        dmg: Double, fire: Int, ownerUuid: String
    ) {
        val fake = FakePlayerFactory.get(machine.world)
        fake?.inventory?.setItemInMainHand(effective)
        // 把假玩家挪到机器中心, 使原版击退方向从机器算起 (而非其出生点)
        if (fake != null) FakePlayerFactory.positionAt(fake, machine.location.toCenterLocation())
        val source = if (fake == null) {
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build()
        } else null
        for (t in targets) {
            val pdc = t.persistentDataContainer
            pdc.set(KEY_KILLER, org.bukkit.persistence.PersistentDataType.STRING, ownerUuid)
            if (fake != null) t.damage(dmg, fake) else t.damage(dmg, source!!)
            if (fire > 0) t.fireTicks = maxOf(t.fireTicks, fire * 80)
            // 未被打死的目标清除标记, 避免其日后被他物击杀时误补经验
            if (t.isValid && !t.isDead) pdc.remove(KEY_KILLER)
        }
    }
}
