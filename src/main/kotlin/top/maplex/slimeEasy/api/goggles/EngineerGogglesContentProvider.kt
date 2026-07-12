package top.maplex.slimeEasy.api.goggles

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.block.Block
import org.bukkit.entity.Player

/**
 * 单次护目镜目标渲染的只读上下文。
 *
 * 实例只在当前主线程刷新期间有效，扩展不得跨 tick 保存 [viewer]、[block] 或其它引用。
 */
interface EngineerGogglesDisplayContext {
    /** 本次私有全息图的唯一接收玩家。 */
    val viewer: Player

    /** 普通方块本身或已匹配多方块结构的中心方块。 */
    val block: Block

    /** Slimefun 注册表中与目标对应的物品。 */
    val slimefunItem: SlimefunItem

    /** 目标是否来自已匹配的 Slimefun 多方块结构。 */
    val isMultiblock: Boolean
}

/**
 * 单次护目镜展示的可变内容。
 *
 * [title] 和 [details] 使用已经完成颜色转换的 Legacy 文本；扩展应使用自己的 I18n 服务生成文本。
 */
interface EngineerGogglesDisplayContent {
    /** 第一行标题，默认是 [EngineerGogglesDisplayContext.slimefunItem] 的本地化名称。 */
    var title: String

    /** 标题下方按顺序展示的状态、能源及扩展文本。 */
    val details: MutableList<String>

    /** 设为 false 可隐藏本轮目标；后续提供器或事件监听器仍可重新启用。 */
    var visible: Boolean
}

/**
 * 高性能护目镜内容提供器。
 *
 * 提供器按注册顺序在 Bukkit 事件之前同步执行，适合依赖 SlimeEasy 的附属为自身物品追加稳定内容。
 * 实现不得阻塞、异步访问上下文或直接操作 DecentHolograms。
 */
fun interface EngineerGogglesContentProvider {
    /** 修改当前目标的展示内容；异常会被 SlimeEasy 隔离并按提供器仅记录一次。 */
    fun provide(context: EngineerGogglesDisplayContext, content: EngineerGogglesDisplayContent)
}
