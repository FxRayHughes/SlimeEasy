package top.maplex.slimeEasy.api.goggles

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 所有注册式内容提供器执行后触发的同步 Bukkit 事件。
 *
 * 监听器可修改 [content] 或取消本次目标展示；事件对象及其上下文均不得在当前调用结束后保存。
 */
class EngineerGogglesDisplayEvent(
    /** 当前玩家与 Slimefun 目标的只读上下文。 */
    val context: EngineerGogglesDisplayContext,
    /** 已包含内置状态、能源数据及提供器结果的可变内容。 */
    val content: EngineerGogglesDisplayContent
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** Bukkit 事件注册协议要求的静态处理器列表入口。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS

        /** 无监听器时跳过事件分配与 PluginManager 调用，避免大量目标产生无效开销。 */
        internal fun hasListeners(): Boolean = HANDLERS.registeredListeners.isNotEmpty()
    }
}
