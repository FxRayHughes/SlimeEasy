package top.maplex.slimeEasy.storage.core

import org.bukkit.block.Block
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 库存变更通知总线。
 *
 * 任何存储方块 [CargoBufferBlock.saveStorage] 落盘后都会 [fire] 一次; 打开中的
 * GUI (翻页箱 / 网络终端) 订阅后据此**实时重绘**, 无论改动来自本界面、别的玩家、
 * 货运端口、磁铁, 还是成员容器自身的界面 —— 保证多处视图数据一致。
 *
 * 监听器约定在主线程被调用且自身开销小 (仅重绘已打开界面)。
 */
object StorageChangeBus {

    private val listeners = CopyOnWriteArrayList<(Block) -> Unit>()

    /** 订阅变更 (进程内长期有效; GUI 单例在首次使用时订阅一次)。 */
    fun subscribe(listener: (Block) -> Unit) {
        listeners.add(listener)
    }

    /** 广播某方块库存已变更。 */
    fun fire(block: Block) {
        for (l in listeners) l(block)
    }
}
