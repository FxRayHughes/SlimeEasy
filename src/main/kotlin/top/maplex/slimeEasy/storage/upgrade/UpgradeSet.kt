package top.maplex.slimeEasy.storage.upgrade

/**
 * 已安装升级的解算结果 (只读值对象)。
 *
 * 由容器持有的一组 [UpgradeType] 归纳出对外的能力与容量倍率, 供存储逻辑查询。
 *
 * @property types 已安装的升级类型集合 (去重; 能力/容量判定用)
 * @property pageExpansionCount 翻页扩容组件数量 (可叠装, 单独计数)
 */
class UpgradeSet(val types: Set<UpgradeType>, val pageExpansionCount: Int = 0) {

    /** 容量倍率: 所有堆叠升级的倍率**连乘** (无堆叠升级则为 1.0)。 */
    val capacityMultiplier: Double =
        types.filter { it.isStack }.fold(1.0) { acc, t -> acc * t.multiplier }

    /** 是否启用经验存储 (容器改存经验)。 */
    val hasExpStorage: Boolean get() = UpgradeType.EXP_STORAGE in types

    /** 是否启用磁铁 (吸附附近掉落物 / 经验球)。 */
    val hasMagnet: Boolean get() = UpgradeType.MAGNET in types

    /** 是否启用虚空 (命中销毁表的物品入库前湮灭)。 */
    val hasVoid: Boolean get() = UpgradeType.VOID in types

    /** 是否启用抽取 (每 tick 从相邻六向的漏斗 / 箱子等容器主动提取物品入库)。 */
    val hasExtract: Boolean get() = UpgradeType.EXTRACT in types

    /** 是否启用输出 (每 tick 把库存物品主动推送到相邻六向的容器)。 */
    val hasOutput: Boolean get() = UpgradeType.OUTPUT in types

    /** 是否启用远程 (容器远程接入某控制器网络; 控制器坐标存于升级物品 PDC)。 */
    val hasRemote: Boolean get() = UpgradeType.REMOTE in types

    /** 翻页箱页数: 基础 1 页, 每个扩容组件 +1, 封顶 [MAX_PAGES]。 */
    val boxPages: Int get() = (1 + pageExpansionCount).coerceIn(1, MAX_PAGES)

    /**
     * 吸入经验时"翻倍"的合成概率 (智者系升级)。
     *
     * 多个智者升级独立触发, 命中任一即翻倍: 概率 = 1 - ∏(1 - 各自概率)。
     * 例: 同时装智者(20%)+末影智者(50%) → 1-(0.8×0.5)=60%。无智者升级则 0。
     */
    val wiseDoubleChance: Double
        get() {
            var miss = 1.0
            for (t in types) if (t.wiseChance > 0.0) miss *= (1.0 - t.wiseChance)
            return 1.0 - miss
        }

    // 单槽容量 = 物品原版堆叠 × capacityMultiplier, 由 VirtualStorage.cellCapacity 直接计算,
    // 无需本类再提供 capacityFor。

    companion object {
        /** 翻页箱最大页数。实时读取配置。 */
        val MAX_PAGES: Int get() = top.maplex.slimeEasy.config.SEConfig.storageUpgradeMaxPages
    }
}
