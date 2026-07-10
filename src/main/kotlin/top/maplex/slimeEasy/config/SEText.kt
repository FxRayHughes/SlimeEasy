package top.maplex.slimeEasy.config

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import top.maplex.slimeEasy.SlimeEasy

/**
 * 物品文本 (名称 / Lore) 配置层。
 *
 * 所有 Slimefun 物品的显示名与描述统一以 `items.<ID>.name` 与 `items.<ID>.lore` 暴露到 config.yml,
 * 颜色码沿用 `&` 记法。首次运行 (配置缺失) 时以代码内默认值**自动写入配置**, 之后即可自由改文本。
 *
 * 由于 Slimefun 物品在注册后即冻结, 修改文本需 **重启服务端** 生效 (仅 [SEConfig] 的运行时数值支持热重载)。
 *
 * 用法: 在物品定义处调用 [stack] 替代直接 new SlimefunItemStack, 传入 ID / 材质 / 默认名 / 默认 Lore 行。
 */
object SEText {

    private val cfg get() = SlimeEasy.instance.config

    /** 本轮是否有默认值被自动写入, 需在注册末尾 [flush] 保存。 */
    private var dirty = false

    /**
     * 构造一个文本可配置的 [SlimefunItemStack]。
     *
     * @param id        Slimefun 全局唯一 ID
     * @param material  物品材质
     * @param name      默认显示名 (含 `&` 颜色码)
     * @param lore      默认 Lore 行 (含 `&` 颜色码)
     */
    fun stack(id: String, material: Material, name: String, vararg lore: String): SlimefunItemStack {
        val finalName = resolveName(id, name)
        val finalLore = resolveLore(id, lore.toList())
        return SlimefunItemStack(id, material, finalName, *finalLore.toTypedArray())
    }

    /** 读取配置名称; 缺失则写入默认值。 */
    private fun resolveName(id: String, def: String): String {
        val path = "items.$id.name"
        if (!cfg.isSet(path)) {
            cfg.set(path, def)
            dirty = true
            return def
        }
        return cfg.getString(path, def) ?: def
    }

    /** 读取配置 Lore; 缺失则写入默认值。 */
    private fun resolveLore(id: String, def: List<String>): List<String> {
        val path = "items.$id.lore"
        if (!cfg.isSet(path)) {
            cfg.set(path, def)
            dirty = true
            return def
        }
        return cfg.getStringList(path)
    }

    /** 若本轮写入过默认文本, 持久化到磁盘 (onEnable 注册完成后调用一次)。 */
    fun flush() {
        if (dirty) {
            SlimeEasy.instance.saveConfig()
            dirty = false
        }
    }
}
