package top.maplex.slimeEasy.storage.network

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow
import org.bukkit.block.Block
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import top.maplex.slimeEasy.config.SEConfig
import top.maplex.slimeEasy.machine.common.MachineProtection
import top.maplex.slimeEasy.storage.core.CargoBufferBlock
import top.maplex.slimeEasy.storage.core.ItemKey
import top.maplex.slimeEasy.storage.upgrade.FaceConfig
import top.maplex.slimeEasy.storage.upgrade.ItemFilter
import top.maplex.slimeEasy.storage.upgrade.UpgradeStore
import top.maplex.slimeEasy.util.locationKey
import java.util.concurrent.ConcurrentHashMap

/**
 * 网络端口的缓冲桥接与主动相邻 IO。
 *
 * 所有方法均由同步的控制器 ticker 或玩家菜单事件调用，约定运行在主线程。主动传输
 * 采用“先从来源暂扣，再写目标，失败回原来源”的事务顺序；只有回退也失败的数量才会
 * 在端口位置掉落，从而在满仓、过滤变化和外部虚拟存储拒收时仍保持物品守恒。
 */
object NetworkPortIO {

    /** 输出端口位置到网络物品轮换起点，避免每 tick 永远优先同一物品类型。 */
    private val outputCursor = ConcurrentHashMap<String, Int>()

    /**
     * 输出缓冲所有权账本：位置到本插件从网络真实取出的物品身份与尚未取走数量。
     *
     * Slimefun 旧 ChestMenu 只处理点击、不处理 InventoryDragEvent；玩家可能用拖拽绕过
     * “只出不进”handler。账本让端口能识别非网络生成的物品或超量增量并立即弹出，
     * 防止它们在模式切换时被错误插入网络。
     */
    private val outputOwned = ConcurrentHashMap<String, Pair<ItemKey, Int>>()

    /** 已完成首次账本恢复的位置；首次见到旧端口时信任其 SQL 持久化缓冲，兼容服务器重启。 */
    private val initializedOutputs = ConcurrentHashMap.newKeySet<String>()

    /** 新放置输出端口没有历史缓冲，预先标记后可把首次出现的外部拖入物识别为非法增量。 */
    fun markPlaced(port: Block, isInput: Boolean) {
        if (!isInput) initializedOutputs.add(port.locationKey())
    }

    /** 控制器每 tick 调用：先维护玩家/货运缓冲，再按模式执行主动输入。 */
    fun serviceInput(network: StorageNetwork, port: Block) {
        sanitizeBuffer(network, port, isInput = true)
        drainInputBuffer(network, port)
        if (PortMode.read(port).activeEnabled) pullActive(network, port)
    }

    /** 控制器每 tick 调用：先维护玩家/货运缓冲，再按模式执行主动输出。 */
    fun serviceOutput(network: StorageNetwork, port: Block) {
        normalizeOutputBuffer(port)
        sanitizeBuffer(network, port, isInput = false)
        fillOutputBuffer(network, port)
        if (PortMode.read(port).activeEnabled) pushActive(network, port)
    }

    /**
     * 清退因过滤变化而变得不合法的缓冲内容。
     *
     * 输入缓冲没有可靠来源面，直接掉落；输出缓冲中的物品已从网络真实扣除，必须优先
     * 插回网络，只有网络也拒收的余量才允许掉落。
     */
    fun sanitizeBuffer(network: StorageNetwork?, port: Block, isInput: Boolean): Boolean {
        val menu = StorageCacheUtils.getMenu(port.location) ?: return false
        val stack = menu.getItemInSlot(NetworkPort.BUFFER_SLOT) ?: return false
        if (stack.type.isAir || filter(isInput).allows(port.location, stack)) return false
        val drops = clearBuffer(network, port, isInput)
        dropAt(port, drops)
        return drops.isNotEmpty()
    }

    /**
     * 从菜单移除全部缓冲物，并返回最终需要掉落的物品堆。
     *
     * 调用者可在模式切换时立即掉落，也可在破坏处理器中加入 Slimefun 的 drops 列表，
     * 因而本方法本身不生成实体，避免端口破坏时重复掉落。
     */
    fun clearBuffer(network: StorageNetwork?, port: Block, isInput: Boolean): List<ItemStack> {
        if (!isInput) normalizeOutputBuffer(port)
        val menu = StorageCacheUtils.getMenu(port.location) ?: return emptyList()
        val stack = menu.getItemInSlot(NetworkPort.BUFFER_SLOT)
        if (!isInput) outputOwned.remove(port.locationKey())
        if (stack == null) return emptyList()
        if (stack.type.isAir || stack.amount <= 0) return emptyList()
        menu.replaceExistingItem(NetworkPort.BUFFER_SLOT, null)
        if (isInput || network == null) return listOf(stack.clone())
        val key = ItemKey.of(stack) ?: return listOf(stack.clone())
        return split(key, network.insert(key, stack.amount.toLong()))
    }

    /** 在端口中心生成清退物；输入来源未知或事务回退失败时统一走此出口。 */
    fun dropAt(port: Block, items: Collection<ItemStack>) {
        val location = port.location.toCenterLocation()
        for (item in items) {
            if (!item.type.isAir && item.amount > 0) port.world.dropItemNaturally(location, item)
        }
    }

    /** 方块破坏时释放位置相关的公平性游标。 */
    fun clearRuntime(port: Block) {
        outputCursor.remove(port.locationKey())
        outputOwned.remove(port.locationKey())
        initializedOutputs.remove(port.locationKey())
    }

    /** 输入缓冲按过滤规则送入网络；满仓余量留在槽内等待后续 tick。 */
    private fun drainInputBuffer(network: StorageNetwork, port: Block) {
        val menu = StorageCacheUtils.getMenu(port.location) ?: return
        val stack = menu.getItemInSlot(NetworkPort.BUFFER_SLOT) ?: return
        if (stack.type.isAir || stack.amount <= 0 || !ItemFilter.EXTRACT.allows(port.location, stack)) return
        val key = ItemKey.of(stack) ?: return
        val leftover = network.insert(key, stack.amount.toLong())
        if (leftover != stack.amount.toLong()) {
            menu.replaceExistingItem(
                NetworkPort.BUFFER_SLOT,
                stack.clone().apply { amount = leftover.toInt() }.takeIf { leftover > 0 }
            )
        }
    }

    /** 输出缓冲为空时从允许的网络物品中轮换取出一堆，供玩家或货运实际拿走。 */
    private fun fillOutputBuffer(network: StorageNetwork, port: Block) {
        val menu = StorageCacheUtils.getMenu(port.location) ?: return
        if (menu.getItemInSlot(NetworkPort.BUFFER_SLOT)?.type?.isAir == false) return
        val entries = rotatedEntries(network, port).filter { ItemFilter.OUTPUT.allows(port.location, it.first.template) }
        val (key, amount) = entries.firstOrNull() ?: return
        val requested = minOf(amount, key.vanillaMaxStack.toLong())
        val extracted = network.extract(key, requested)
        if (extracted > 0) {
            menu.replaceExistingItem(NetworkPort.BUFFER_SLOT, key.toDisplay(extracted.toInt()))
            outputOwned[port.locationKey()] = key to extracted.toInt()
        }
    }

    /**
     * 对照所有权账本结算玩家/货运取走量，并弹出通过拖拽等路径写入的非网络物品。
     * 合法取走无需回写网络，因为填充缓冲时已经真实扣除；这里只维护剩余所有权数量。
     */
    private fun normalizeOutputBuffer(port: Block) {
        val menu = StorageCacheUtils.getMenu(port.location) ?: return
        val locationKey = port.locationKey()
        val owned = outputOwned[locationKey]
        val current = menu.getItemInSlot(NetworkPort.BUFFER_SLOT)
        if (initializedOutputs.add(locationKey) && owned == null) {
            // BlockMenu 内容由 Slimefun SQL 保存；重启后的首次观察必须把旧输出缓冲恢复为合法所有权，
            // 否则每次重启都会把尚未取走的网络物品错误弹出。
            if (current != null && !current.type.isAir) {
                ItemKey.of(current)?.let { outputOwned[locationKey] = it to current.amount }
            }
            return
        }
        if (owned == null) {
            if (current != null && !current.type.isAir) {
                menu.replaceExistingItem(NetworkPort.BUFFER_SLOT, null)
                dropAt(port, listOf(current.clone()))
            }
            return
        }
        val (key, ownedAmount) = owned
        if (current == null || current.type.isAir) {
            outputOwned.remove(locationKey)
            return
        }
        if (!key.matches(current)) {
            menu.replaceExistingItem(NetworkPort.BUFFER_SLOT, null)
            outputOwned.remove(locationKey)
            dropAt(port, listOf(current.clone()))
            return
        }
        if (current.amount > ownedAmount) {
            val surplus = current.amount - ownedAmount
            menu.replaceExistingItem(NetworkPort.BUFFER_SLOT, key.toDisplay(ownedAmount))
            dropAt(port, listOf(key.toDisplay(surplus)))
        } else {
            outputOwned[locationKey] = key to current.amount
        }
    }

    /** 从启用面相邻库存主动抽取；每个端口独立消耗自己的单 tick 预算。 */
    private fun pullActive(network: StorageNetwork, port: Block) {
        var budget = budget(SEConfig.storageIoPullMaxItemsPerTick)
        sourceLoop@
        for (face in FaceConfig.EXTRACT.faces(port.location)) {
            if (budget <= 0) break
            val sourceBlock = port.getRelative(face)
            val target = targetOf(network, port, sourceBlock) ?: continue
            when (target) {
                is Target.Vanilla -> {
                    for (slot in 0 until target.inventory.size) {
                        if (budget <= 0) break@sourceLoop
                        val stack = target.inventory.getItem(slot) ?: continue
                        if (stack.type.isAir || stack.amount <= 0 || !ItemFilter.EXTRACT.allows(port.location, stack)) continue
                        val key = ItemKey.of(stack) ?: continue
                        val requested = minOf(stack.amount.toLong(), budget).toInt()
                        target.inventory.setItem(slot, stack.clone().apply { amount -= requested }.takeIf { it.amount > 0 })
                        val networkLeft = network.insert(key, requested.toLong())
                        val moved = requested.toLong() - networkLeft
                        if (networkLeft > 0) rollbackVanilla(target.inventory, slot, key, networkLeft, port)
                        budget -= moved
                    }
                }
                is Target.Virtual -> {
                    val storage = target.logic.storageAt(target.block)
                    var changed = false
                    for ((key, amount) in storage.entries()) {
                        if (budget <= 0) break
                        if (amount <= 0 || !ItemFilter.EXTRACT.allows(port.location, key.template)) continue
                        val requested = minOf(amount, budget, key.vanillaMaxStack.toLong())
                        val taken = storage.extract(key, requested, simulate = false)
                        if (taken <= 0) continue
                        changed = true
                        val networkLeft = network.insert(key, taken)
                        val moved = taken - networkLeft
                        if (networkLeft > 0) {
                            target.logic.prepareForInsert(target.block, key.template)
                            val rollbackLeft = storage.insert(key.template, networkLeft, simulate = false)
                            dropAt(port, split(key, rollbackLeft))
                        }
                        budget -= moved
                    }
                    if (changed) target.logic.saveStorage(target.block, storage)
                }
                is Target.Slimefun -> {
                    // 输入端口只读取标准货运接口声明的 WITHDRAW 槽，绝不遍历机器全部菜单槽。
                    val slots = target.menu.preset.getSlotsAccessedByItemTransport(
                        target.menu, ItemTransportFlow.WITHDRAW, null
                    )
                    for (slot in slots) {
                        if (budget <= 0) break@sourceLoop
                        val stack = target.menu.getItemInSlot(slot) ?: continue
                        if (stack.type.isAir || stack.amount <= 0 || !ItemFilter.EXTRACT.allows(port.location, stack)) continue
                        val key = ItemKey.of(stack) ?: continue
                        val requested = minOf(stack.amount.toLong(), budget).toInt()
                        target.menu.consumeItem(slot, requested, false)
                        val networkLeft = network.insert(key, requested.toLong())
                        val moved = requested.toLong() - networkLeft
                        if (networkLeft > 0) {
                            val rollback = target.menu.pushItem(key.toDisplay(networkLeft.toInt()), *slots)
                            if (rollback != null) dropAt(port, listOf(rollback))
                        }
                        budget -= moved
                    }
                }
            }
        }
    }

    /** 从网络轮换取物并推入启用面目标；目标余量优先插回网络，最终失败才掉落。 */
    private fun pushActive(network: StorageNetwork, port: Block) {
        var budget = budget(SEConfig.storageIoPushMaxItemsPerTick)
        val targets = FaceConfig.OUTPUT.faces(port.location).mapNotNull { face ->
            targetOf(network, port, port.getRelative(face))
        }
        if (targets.isEmpty()) return
        for ((key, snapshotAmount) in rotatedEntries(network, port)) {
            if (budget <= 0) break
            if (snapshotAmount <= 0 || !ItemFilter.OUTPUT.allows(port.location, key.template)) continue
            var available = snapshotAmount
            for (target in targets) {
                if (budget <= 0 || available <= 0) break
                val requested = minOf(available, budget, key.vanillaMaxStack.toLong())
                val extracted = network.extract(key, requested)
                if (extracted <= 0) break
                val rejected = when (target) {
                    is Target.Vanilla -> target.inventory.addItem(key.toDisplay(extracted.toInt()))
                        .values.sumOf { it.amount }.toLong()
                    is Target.Virtual -> {
                        val storage = target.logic.storageAt(target.block)
                        target.logic.prepareForInsert(target.block, key.template)
                        val left = storage.insert(key.template, extracted, simulate = false)
                        if (left != extracted) target.logic.saveStorage(target.block, storage)
                        left
                    }
                    is Target.Slimefun -> {
                        // 输出端口按当前物品查询动态 INSERT 槽，兼容过滤节点等精细货运协议。
                        val transfer = key.toDisplay(extracted.toInt())
                        val slots = target.menu.preset.getSlotsAccessedByItemTransport(
                            target.menu, ItemTransportFlow.INSERT, transfer
                        )
                        if (slots.isEmpty()) extracted
                        else target.menu.pushItem(transfer, *slots)?.amount?.toLong() ?: 0L
                    }
                }
                val moved = extracted - rejected
                if (rejected > 0) dropAt(port, split(key, network.insert(key, rejected)))
                budget -= moved
                available -= extracted
            }
        }
    }

    /**
     * 将相邻方块解析为原版容器、SlimeEasy 虚拟存储或标准 Slimefun 菜单目标。
     * 当前网络成员必须排除，经验模式不存物品也必须排除；保护检查在读取任何库存前完成。
     */
    private fun targetOf(network: StorageNetwork, port: Block, block: Block): Target? {
        if (!MachineProtection.canInteract(port, block)) return null
        if (network.members.any { it.first == block } ||
            network.inputPorts.any { it == block } || network.outputPorts.any { it == block }
        ) return null
        val slimefun = StorageCacheUtils.getSlimefunItem(block.location)
        if (slimefun is CargoBufferBlock) {
            if (UpgradeStore.resolve(block.location).hasExpStorage) return null
            return Target.Virtual(block, slimefun)
        }
        if (slimefun is InventoryBlock) {
            val menu = StorageCacheUtils.getMenu(block.location) ?: return null
            return Target.Slimefun(menu)
        }
        // 未实现标准库存接口的 Slimefun 方块仍不可直接读取底层原版库存，避免绕过机器协议。
        if (slimefun != null) return null
        val holder = block.getState(false) as? InventoryHolder ?: return null
        return Target.Vanilla(holder.inventory)
    }

    /** 把原版来源的网络拒收量优先放回原槽，再放回同一来源库存，最后才在端口掉落。 */
    private fun rollbackVanilla(inventory: Inventory, slot: Int, key: ItemKey, amount: Long, port: Block) {
        var left = amount.toInt()
        val current = inventory.getItem(slot)
        if (current == null || current.type.isAir) {
            val put = minOf(left, key.vanillaMaxStack)
            inventory.setItem(slot, key.toDisplay(put))
            left -= put
        } else if (key.matches(current)) {
            val room = current.maxStackSize - current.amount
            val put = minOf(left, room)
            if (put > 0) {
                inventory.setItem(slot, current.clone().apply { this.amount += put })
                left -= put
            }
        }
        if (left <= 0) return
        val rejected = inventory.addItem(key.toDisplay(left)).values.sumOf { it.amount }.toLong()
        dropAt(port, split(key, rejected))
    }

    /** 返回以端口游标为起点的网络快照，并把下一 tick 起点前移一类。 */
    private fun rotatedEntries(network: StorageNetwork, port: Block): List<Pair<ItemKey, Long>> {
        val entries = network.aggregate()
        if (entries.isEmpty()) return emptyList()
        val locationKey = port.locationKey()
        val start = (outputCursor[locationKey] ?: 0).mod(entries.size)
        outputCursor[locationKey] = (start + 1).mod(entries.size)
        return entries.drop(start) + entries.take(start)
    }

    private fun filter(isInput: Boolean): ItemFilter = if (isInput) ItemFilter.EXTRACT else ItemFilter.OUTPUT

    private fun budget(configured: Int): Long = if (configured <= 0) Long.MAX_VALUE else configured.toLong()

    /** Long 数量按物品原版堆叠上限拆分，避免构造非法超量 ItemStack。 */
    private fun split(key: ItemKey, amount: Long): List<ItemStack> {
        if (amount <= 0) return emptyList()
        val result = ArrayList<ItemStack>()
        var left = amount
        while (left > 0) {
            val part = minOf(left, key.vanillaMaxStack.toLong()).toInt()
            result.add(key.toDisplay(part))
            left -= part
        }
        return result
    }

    private sealed interface Target {
        data class Vanilla(val inventory: Inventory) : Target
        data class Virtual(val block: Block, val logic: CargoBufferBlock) : Target
        data class Slimefun(val menu: BlockMenu) : Target
    }
}
