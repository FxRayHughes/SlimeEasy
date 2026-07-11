package top.maplex.slimeEasy.storage.core

import org.bukkit.block.Block
import top.maplex.slimeEasy.storage.upgrade.UpgradeType

/**
 * 可安装升级组件的宿主 (存储容器 / 自动点击器等)。
 *
 * [UpgradeMenu] 只依赖本接口的三个回调 (安装校验 / 变更通知 / 卸载容量校验),
 * 从而与具体宿主类型解耦 —— 存储容器 ([CargoBufferBlock]) 与自动点击器均可复用同一
 * 升级 GUI 与 [top.maplex.slimeEasy.storage.upgrade.UpgradeStore] 存储管道。
 *
 * 三个方法均有默认实现 (放行 / 空操作 / 允许), 宿主按需覆写。
 */
interface UpgradeHost {

    /**
     * 升级变更前的业务校验。
     *
     * @param install true = 安装; false = 卸下
     * @return null 表示允许; 非 null 为拒绝原因 (展示给玩家)
     */
    fun rejectUpgradeChange(block: Block, type: UpgradeType, install: Boolean): String? = null

    /** 升级组件变更后的回调 (安装 / 卸下后由 [UpgradeMenu] 调用)。 */
    fun onUpgradesChanged(block: Block) {}

    /**
     * 判断在仅保留 [remainingStackMultiplier] 倍率后, 现存内容是否仍装得下
     * (仅卸下容量类升级时触发)。默认恒允许。
     */
    fun capacityAllowsRemoval(block: Block, remainingStackMultiplier: Double): Boolean = true
}
