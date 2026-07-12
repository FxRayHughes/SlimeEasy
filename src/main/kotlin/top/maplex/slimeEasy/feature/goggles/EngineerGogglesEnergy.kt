package top.maplex.slimeEasy.feature.goggles

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer
import top.maplex.slimeEasy.config.I18n
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 把 Slimefun 公共能源 API 转成护目镜文本，并保存相邻采样以计算真实净电量变化。
 *
 * 净变化无法拆分同一 tick 内先耗电再补电的两个过程，因此文本明确使用“净流入/净流出”。
 */
internal class EngineerGogglesEnergy {

    private data class ChargeSample(val charge: Long, val sampledAt: Long)

    private val previous = HashMap<String, ChargeSample>()

    /** 读取当前目标可验证的能源字段，并把本次储能值纳入下一轮净流量采样。 */
    fun lines(target: EngineerGogglesTarget, now: Long, seen: MutableSet<String>): List<String> {
        val data = target.blockData ?: return emptyList()
        val component = target.item as? EnergyNetComponent ?: return emptyList()
        val result = ArrayList<String>()

        if (!data.isDataLoaded) {
            StorageCacheUtils.requestLoad(data)
            result += I18n.text("holograms.engineer-goggles.energy-loading")
            return result
        }

        val container = data as ASlimefunDataContainer
        val capacity = runCatching { component.capacityLong }.getOrDefault(0L)
        if (capacity > 0L) {
            runCatching { component.getChargeLong(target.blockLocation, container) }
                .onSuccess { charge ->
                    result += I18n.text(
                        "holograms.engineer-goggles.energy",
                        "charge" to charge,
                        "capacity" to capacity
                    )
                    result += flowLine(target.key, charge, now)
                    seen += target.key
                }
        }

        (target.item as? AContainer)?.let { machine ->
            val consumption = machine.energyConsumption
            if (consumption > 0) {
                result += I18n.text(
                    "holograms.engineer-goggles.consumption",
                    "amount" to consumption
                )
            }
        }

        (target.item as? EnergyNetProvider)?.let { provider ->
            runCatching { provider.getGeneratedOutputLong(target.blockLocation, container) }
                .onSuccess { output ->
                    result += I18n.text("holograms.engineer-goggles.generation", "amount" to output)
                }
        }

        return result
    }

    /** 删除本轮已离开所有佩戴者扫描范围的采样，防止长期保留已拆除机器的位置。 */
    fun retain(seen: Set<String>) {
        previous.keys.retainAll(seen)
    }

    private fun flowLine(key: String, charge: Long, now: Long): String {
        val old = previous.put(key, ChargeSample(charge, now))
            ?: return I18n.text("holograms.engineer-goggles.flow-sampling")
        val elapsed = (now - old.sampledAt).coerceAtLeast(1L)
        val perSecond = ((charge - old.charge) * 1000.0 / elapsed).roundToLong()
        return when {
            perSecond > 0L -> I18n.text("holograms.engineer-goggles.flow-in", "amount" to perSecond)
            perSecond < 0L -> I18n.text("holograms.engineer-goggles.flow-out", "amount" to abs(perSecond))
            else -> I18n.text("holograms.engineer-goggles.flow-idle")
        }
    }
}
