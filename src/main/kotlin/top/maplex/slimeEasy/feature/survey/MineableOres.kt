package top.maplex.slimeEasy.feature.survey

import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag
import org.bukkit.Material

/**
 * 工业矿机可挖矿石判定。
 *
 * 口径与 Slimefun [io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.miner.IndustrialMiner]
 * 的 canMine 保持一致: 命中 [SlimefunTag.INDUSTRIAL_MINER_ORES] 或 [SlimefunTag.DEEPSLATE_ORES] 即视为可挖。
 *
 * 说明: 深板岩矿石默认开启, 故纳入判定; 远古残骸默认关闭 (canMineAncientDebris=false),
 * 故不纳入 —— 与矿机开箱即用的行为一致。进阶矿机与普通矿机的可挖矿石种类相同
 * (前者仅继承后者的矿石集合), 差异体现在挖掘范围与精准采集, 因此本判定对两者通用。
 */
object MineableOres {

    /** 该材质是否属于工业矿机 (含进阶) 默认可挖的矿石。 */
    fun isMineable(material: Material): Boolean =
        SlimefunTag.INDUSTRIAL_MINER_ORES.isTagged(material) ||
            SlimefunTag.DEEPSLATE_ORES.isTagged(material)
}
