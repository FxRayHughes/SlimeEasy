package top.maplex.slimeEasy.territory

import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction
import org.bukkit.Location
import org.bukkit.DyeColor
import org.bukkit.block.banner.Pattern
import java.util.UUID

/** 世界与区块坐标组成的无损领地索引键。 */
internal data class TerritoryChunk(val world: UUID, val x: Int, val z: Int) {
    fun adjacentTo(other: TerritoryChunk): Boolean =
        world == other.world && kotlin.math.abs(x - other.x) + kotlin.math.abs(z - other.z) == 1

    /** 返回以当前区块为中心的正方形覆盖区；领地旗帜固定使用半径1，即3×3区块。 */
    fun square(radius: Int): Set<TerritoryChunk> = buildSet {
        require(radius >= 0) { "radius must not be negative" }
        for (offsetX in -radius..radius) {
            for (offsetZ in -radius..radius) add(TerritoryChunk(world, x + offsetX, z + offsetZ))
        }
    }

    companion object {
        fun of(location: Location): TerritoryChunk =
            TerritoryChunk(requireNotNull(location.world).uid, location.blockX shr 4, location.blockZ shr 4)
    }
}

/**
 * 可持久化的方块坐标。
 * 世界 UUID 与完整 Int 坐标属于存档协议，不能改成截断后的短整型键。
 */
internal data class TerritoryBlock(val world: UUID, val x: Int, val y: Int, val z: Int) {
    val chunk: TerritoryChunk get() = TerritoryChunk(world, x shr 4, z shr 4)

    companion object {
        fun of(location: Location): TerritoryBlock = TerritoryBlock(
            requireNotNull(location.world).uid,
            location.blockX,
            location.blockY,
            location.blockZ
        )
    }
}

/** 六项行为权限与 Slimefun Protection API 的 Interaction 一一对应。 */
internal enum class TerritoryAction(val interaction: Interaction) {
    BREAK_BLOCK(Interaction.BREAK_BLOCK),
    PLACE_BLOCK(Interaction.PLACE_BLOCK),
    INTERACT_BLOCK(Interaction.INTERACT_BLOCK),
    ATTACK_PLAYER(Interaction.ATTACK_PLAYER),
    ATTACK_ENTITY(Interaction.ATTACK_ENTITY),
    INTERACT_ENTITY(Interaction.INTERACT_ENTITY);

    companion object {
        fun of(interaction: Interaction): TerritoryAction = entries.first { it.interaction == interaction }
    }
}

/** 管理能力独立于行为权限；只有主人可以授予这些能力，避免管理者自我提权。 */
internal enum class TerritoryManagement {
    MEMBERS,
    PERMISSIONS,
    CHUNKS,
    FLAGS,
    SETTINGS
}

/** 单个成员的行为与管理授权。 */
internal data class TerritoryMember(
    val actions: MutableSet<TerritoryAction> = DEFAULT_ACTIONS.toMutableSet(),
    val management: MutableSet<TerritoryManagement> = mutableSetOf()
) {
    companion object {
        val DEFAULT_ACTIONS: Set<TerritoryAction> = TerritoryAction.entries
            .filterNot { it == TerritoryAction.ATTACK_PLAYER }
            .toSet()

        fun formerOwner(): TerritoryMember = TerritoryMember(
            TerritoryAction.entries.toMutableSet(),
            TerritoryManagement.entries.toMutableSet()
        )
    }
}

/**
 * 一个核心及其旗帜构成的持久化聚合根。
 * [chunks] 是权限查询的唯一真相源，[flags] 记录每个扩展区块的实体锚点；默认成员模板只在
 * 加入时复制。[flagBaseColor] 与 [flagPatterns] 是现有和未来旗帜共同继承的白名单化外观协议；
 * 不保存完整 ItemStack，避免把名称、Lore、PDC 或其它插件元数据注入所有领地旗帜。
 */
internal data class Territory(
    val id: UUID,
    var owner: UUID,
    val core: TerritoryBlock,
    val chunks: MutableSet<TerritoryChunk>,
    val flags: MutableMap<TerritoryChunk, TerritoryBlock>,
    val members: MutableMap<UUID, TerritoryMember> = mutableMapOf(),
    val defaultMemberActions: MutableSet<TerritoryAction> = TerritoryMember.DEFAULT_ACTIONS.toMutableSet(),
    val defaultMemberManagement: MutableSet<TerritoryManagement> = mutableSetOf(),
    val visitorActions: MutableSet<TerritoryAction> = mutableSetOf(),
    var flagBaseColor: DyeColor = DyeColor.WHITE,
    val flagPatterns: MutableList<Pattern> = mutableListOf(),
    var allowStrangerEntry: Boolean = true,
    var allowFlight: Boolean = false,
    var locked: Boolean = false
) {
    /** 根据核心与所有旗帜中心重新计算真实认领并集，重叠的3×3覆盖只计入一次。 */
    fun expectedChunks(): Set<TerritoryChunk> = buildSet {
        addAll(core.chunk.square(CORE_CHUNK_RADIUS))
        flags.keys.forEach { addAll(it.square(FLAG_CHUNK_RADIUS)) }
    }

    companion object {
        /** 每个领地最多35面扩展旗帜；重叠覆盖不会减少或增加旗帜计数。 */
        const val MAX_FLAGS = 35
        /** 核心默认认领以所在区块为中心的3×3区域，与旗帜保持一致的覆盖语义。 */
        const val CORE_CHUNK_RADIUS = 1
        /** 每面旗帜以所在区块为中心向四周扩展一格，组成固定3×3覆盖。 */
        const val FLAG_CHUNK_RADIUS = 1
        /** 限制异常物品注入的图案层数，原版生存旗帜通常不超过6层。 */
        const val MAX_FLAG_PATTERNS = 20
    }
}
