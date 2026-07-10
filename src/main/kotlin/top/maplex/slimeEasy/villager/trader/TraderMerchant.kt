package top.maplex.slimeEasy.villager.trader

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.MerchantInventory
import top.maplex.slimeEasy.villager.core.VillagerData

/**
 * 村民交易器的"虚拟商人"。
 *
 * 交易走 Bukkit [org.bukkit.inventory.Merchant], 玩家不直接接触实体村民 —— 故"无需操心保护村民"。
 * 打开时由装配村民的 [VillagerData.recipes] 构建商人; 关闭时从商人回读各交易 uses 并回存, 保持库存进度。
 */
object TraderMerchant {

    /**
     * 依装配村民的交易列表打开虚拟商人界面。
     *
     * [Bukkit.createMerchant] 与 [Player.openMerchant] 在当前 Paper 标为 deprecated, 但为
     * 打开虚拟商人的唯一标准 API 且功能正常, 无非弃用替代, 故显式抑制告警。
     */
    @Suppress("DEPRECATION")
    fun open(player: Player, data: VillagerData) {
        val merchant = Bukkit.createMerchant(Component.text("村民交易器"))
        // recipes 为 VillagerData 中已解码的新对象; openMerchant 前必须先 setRecipes
        merchant.recipes = data.recipes
        player.openMerchant(merchant, true)
    }

    /**
     * 从一个交易界面回读交易进度并回存到方块。
     *
     * 以方块当前装配村民为基准, 仅替换交易列表 (含更新后的 uses), 保持职业 / 类型 / 等级不变。
     */
    fun syncBack(block: Block, inventory: MerchantInventory) {
        val base = TraderStore.getVillager(block) ?: return
        val updated = VillagerData(
            professionKey = base.professionKey,
            typeKey = base.typeKey,
            level = base.level,
            experience = base.experience,
            adult = base.adult,
            recipes = inventory.merchant.recipes
        )
        TraderStore.setVillager(block, updated)
    }
}
