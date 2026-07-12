package top.maplex.slimeEasy.storage.drawer

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.core.StorageChangeBus

/**
 * 抽屉的经验存储 (经验存储升级启用时)。
 *
 * 以 long 形式将经验**点数**保存在 BlockData (键 [DATA_KEY])，取出交付方式保存在
 * [PAYOUT_MODE_KEY]。展示层复用 [DrawerDisplay]，以经验瓶为图标、存量文字显示点数。
 */
object DrawerExp {

    private const val DATA_KEY = "se_drawer_exp"

    /**
     * 取出交付方式的持久化键；缺失时必须视为直接给予，以兼容升级前创建的经验容器。
     */
    private const val PAYOUT_MODE_KEY = "se_exp_payout_mode"

    /** 经验展示图标身份 (经验瓶)。 */
    private val EXP_ICON: ItemKey = ItemKey.of(ItemStack(Material.EXPERIENCE_BOTTLE))!!

    /** 读取当前存储的经验点数。 */
    fun get(block: Block): Long =
        StorageCacheUtils.getData(block.location, DATA_KEY)?.toLongOrNull() ?: 0L

    /** 增加经验点数并刷新展示。 */
    fun add(block: Block, points: Long) {
        if (points <= 0) return
        set(block, get(block) + points)
    }

    /**
     * 吸入经验 (磁铁 / 生成拦截): 依智者系升级概率**可能翻倍**后入库。
     *
     * 与手动存入 ([deposit]) 区分: 只有"吸入"走本方法, 才享受智者翻倍。
     */
    fun addAbsorbed(block: Block, points: Long) {
        if (points <= 0) return
        val chance = top.maplex.slimeEasy.storage.upgrade.UpgradeStore.resolve(block.location).wiseDoubleChance
        val finalPoints = if (chance > 0.0 && Math.random() < chance) points * 2 else points
        add(block, finalPoints)
    }

    /** 玩家存入其全部经验。 */
    fun deposit(block: Block, player: Player) {
        val points = ExpUtil.total(player)
        if (points <= 0) return
        ExpUtil.clear(player)
        add(block, points.toLong())
    }

    /** 读取该经验容器的取出交付方式；旧容器默认直接给予。 */
    fun payoutMode(block: Block): ExperiencePayoutMode = ExperiencePayoutMode.fromStored(
        StorageCacheUtils.getData(block.location, PAYOUT_MODE_KEY)
    )

    /** 切换并持久化该容器的取出交付方式，返回切换后的模式。 */
    fun togglePayoutMode(block: Block): ExperiencePayoutMode {
        val next = payoutMode(block).next()
        StorageCacheUtils.setData(block.location, PAYOUT_MODE_KEY, next.storedValue)
        StorageChangeBus.fire(block)
        return next
    }

    /**
     * 玩家取出经验。
     *
     * @param points 期望取出的点数; 实际取出不超过库存
     * @return 实际取出的点数
     */
    fun withdraw(block: Block, player: Player, points: Int): Int {
        val stored = get(block)
        val give = minOf(stored, points.toLong()).toInt()
        if (give <= 0) return 0
        ExperiencePayout.give(player, give, payoutMode(block))
        set(block, stored - give)
        return give
    }

    /** 按"等级数"取出: 依玩家当前等级换算所需点数, 再取出 (不超库存)。 */
    fun withdrawLevels(block: Block, player: Player, levels: Int) {
        val want = ExpUtil.pointsForLevels(player.level, levels)
        if (want <= 0) return
        withdraw(block, player, want)
    }

    private fun set(block: Block, value: Long) {
        StorageCacheUtils.setData(block.location, DATA_KEY, value.toString())
        DrawerDisplay.update(block, if (value > 0) EXP_ICON else null, value)
        StorageChangeBus.fire(block) // 广播变更, 供打开中的经验界面实时重绘 (磁铁后台吸入等)
    }

    /** 按当前存量重绘展示 (展示实体重建后调用)。 */
    fun refreshDisplay(block: Block) {
        val value = get(block)
        DrawerDisplay.update(block, if (value > 0) EXP_ICON else null, value)
    }
}
