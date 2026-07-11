package top.maplex.slimeEasy.machine.quarry

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Container
import org.bukkit.block.data.Directional
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.storage.core.CargoBufferBlock

/**
 * 采石场的世界判定与产出逻辑。
 *
 * 采石场本体为**观察者方块**, 沿用本项目统一约定 (见 [top.maplex.slimeEasy.machine.butcher.ButcherLogic.facingOf]):
 * `观察者脸朝向` = 其"附着"的方块方向。生产条件:
 *
 * 1. 脸朝向那格是 **圆石** (`COBBLESTONE`);
 * 2. 该圆石的 6 个正交邻居中**同时**存在**岩浆**与**水** (不必是源头, 任意水位即可)。
 *
 * 满足时按档位产出当前选择的材料 (默认圆石, 可升级为地狱岩 / 末地石), 并推送到
 * 采石场自身 6 邻的容器:
 * - **原版容器** ([Container]): 直接 `inventory.addItem`;
 * - **本插件存储方块** ([CargoBufferBlock] 抽屉 / 翻页箱): 走其虚拟库存插入。
 *
 * 采石场无内部缓冲, 放不下的部分**直接丢弃** (不散落地面), 与"无容积"设定一致。
 *
 * 所有方法约定在主线程调用 (读方块数据 / 写容器)。
 */
object QuarryLogic {

    /** 6 个正交面 (上下东南西北)。 */
    private val FACES = arrayOf(
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    )

    /**
     * 采石场"附着"的圆石方块; 不满足 (脸朝向非定向 / 目标非圆石) 返回 null。
     *
     * 观察者脸朝向那格即附着方向, 与屠夫 / 点击器语义一致。
     */
    fun attachedCobblestone(quarry: Block): Block? {
        val facing = (quarry.blockData as? Directional)?.facing ?: return null
        val target = quarry.getRelative(facing)
        return if (target.type == Material.COBBLESTONE) target else null
    }

    /**
     * 该圆石是否处于"可生产"状态: 6 邻正交中同时相邻岩浆与水 (任意水位, 含流动)。
     *
     * 水与岩浆分别识别其源头与流动态材质, 只要各命中至少一个即成立。
     */
    fun isProducing(cobblestone: Block): Boolean {
        var hasLava = false
        var hasWater = false
        for (face in FACES) {
            when (cobblestone.getRelative(face).type) {
                Material.LAVA -> hasLava = true
                Material.WATER -> hasWater = true
                else -> {}
            }
            if (hasLava && hasWater) return true
        }
        return false
    }

    /**
     * 向采石场 6 邻的容器输出 [amount] 个 [material]; 放不下的部分丢弃。
     *
     * 逐个邻居尝试, 直到全部放入或邻居遍历完。原版容器与本插件存储方块分别处理。
     */
    fun output(quarry: Block, material: Material, amount: Int) {
        var remaining = amount
        for (face in FACES) {
            if (remaining <= 0) break
            val neighbor = quarry.getRelative(face)
            val cargo = cargoBufferAt(neighbor)
            remaining -= when {
                cargo != null -> insertToCargo(cargo, neighbor, material, remaining)
                neighbor.state is Container -> insertToVanilla(neighbor, material, remaining)
                else -> 0
            }
        }
        if (remaining > 0 && SEConfig.quarryDropOverflow) {
            while (remaining > 0) {
                val amount = minOf(remaining, material.maxStackSize)
                quarry.world.dropItemNaturally(quarry.location.toCenterLocation(), ItemStack(material, amount))
                remaining -= amount
            }
        }
    }

    /** 邻居若为本插件存储方块 (抽屉 / 翻页箱) 返回其逻辑, 否则 null。 */
    private fun cargoBufferAt(block: Block): CargoBufferBlock? {
        if (!StorageCacheUtils.hasBlock(block.location)) return null
        val sfId = StorageCacheUtils.getBlock(block.location)?.sfId ?: return null
        return SlimefunItem.getById(sfId) as? CargoBufferBlock
    }

    /**
     * 向本插件存储方块的虚拟库存插入指定材料; 返回实际放入数量。
     *
     * 与 [top.maplex.slimeEasy.storage.network.StorageNetwork.insert] 一致:
     * 插入前调用 [CargoBufferBlock.prepareForInsert] 校准容量, 有变动才回写。
     */
    private fun insertToCargo(logic: CargoBufferBlock, block: Block, material: Material, amount: Int): Int {
        val template = ItemStack(material)
        val storage = logic.storageAt(block)
        logic.prepareForInsert(block, template)
        val left = storage.insert(template, amount.toLong(), simulate = false)
        val accepted = amount - left.toInt()
        if (accepted > 0) logic.saveStorage(block, storage)
        return accepted
    }

    /**
     * 向原版容器插入指定材料; 返回实际放入数量。
     *
     * 用库存快照模拟, 只放得下的部分才真正写入 (避免溢出洒落)。
     */
    private fun insertToVanilla(block: Block, material: Material, amount: Int): Int {
        val container = block.state as? Container ?: return 0
        val inventory = container.inventory
        // addItem 返回未放入的残余; 一次最多放一组避免超堆
        var remaining = amount
        var accepted = 0
        while (remaining > 0) {
            val put = minOf(remaining, material.maxStackSize)
            val leftover = inventory.addItem(ItemStack(material, put))
            val notPlaced = leftover.values.sumOf { it.amount }
            accepted += put - notPlaced
            remaining -= put
            if (notPlaced > 0) break // 容器已满, 停止
        }
        return accepted
    }
}
