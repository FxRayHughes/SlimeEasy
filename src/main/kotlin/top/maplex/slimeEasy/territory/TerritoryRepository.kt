package top.maplex.slimeEasy.territory

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.DyeColor
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.block.banner.Pattern
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * `territories.yml` 持久化入口。
 * 存档键、枚举名和版本号均属于长期协议；写入使用临时文件替换，避免崩溃留下半份 YAML。
 */
internal class TerritoryRepository(private val plugin: JavaPlugin) {

    private val file = File(plugin.dataFolder, FILE_NAME)
    /** 旧版本成功读取后要求服务层在关闭前写回当前格式，避免每次启动重复迁移。 */
    var requiresMigration: Boolean = false
        private set

    /**
     * 从版本化 YAML 恢复领地；语法错误或未知版本必须向上抛出。
     * 不能使用 `loadConfiguration`，因为它会吞掉解析异常并返回空配置，随后可能覆盖原存档。
     */
    fun load(): MutableList<Territory> {
        requiresMigration = false
        if (!file.exists()) return mutableListOf()
        val yaml = YamlConfiguration().apply { load(file) }
        val schemaVersion = yaml.getInt(SCHEMA_VERSION_KEY, -1)
        require(schemaVersion in MIN_SUPPORTED_SCHEMA..SCHEMA_VERSION) {
            "Unsupported territory schema version: ${yaml.get(SCHEMA_VERSION_KEY)}"
        }
        requiresMigration = schemaVersion < SCHEMA_VERSION
        val root = yaml.getConfigurationSection(ROOT) ?: return mutableListOf()
        return root.getKeys(false).mapNotNullTo(mutableListOf()) { rawId ->
            val section = root.getConfigurationSection(rawId) ?: return@mapNotNullTo null
            val parsedId = runCatching { UUID.fromString(rawId) }.getOrNull()
            val id = parsedId ?: UUID.nameUUIDFromBytes("corrupt-territory:$rawId".toByteArray(StandardCharsets.UTF_8))
            if (parsedId == null) {
                plugin.logger.warning("Territory id '$rawId' is invalid; loading recoverable chunks as locked")
                return@mapNotNullTo readLockedTerritory(id, section)
            }
            runCatching { readTerritory(id, section, schemaVersion) }
                .onFailure { plugin.logger.warning("Unable to load territory $rawId: ${it.message}") }
                .getOrElse { readLockedTerritory(id, section) }
        }
    }

    /** 将完整快照写入临时文件后原子替换正式存档。 */
    fun save(territories: Collection<Territory>) {
        plugin.dataFolder.mkdirs()
        val yaml = YamlConfiguration()
        yaml.set(SCHEMA_VERSION_KEY, SCHEMA_VERSION)
        territories.sortedBy { it.id.toString() }.forEach { territory -> writeTerritory(yaml, territory) }
        val temporary = file.toPath().resolveSibling("$FILE_NAME.tmp")
        Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8)
        runCatching {
            Files.move(
                temporary,
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.recoverCatching {
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
    }

    private fun readTerritory(id: UUID, section: ConfigurationSection, schemaVersion: Int): Territory {
        val core = decodeBlock(requireNotNull(section.getString("core")))
        val flags = section.getStringList("flags").map(::decodeBlock).associateByTo(mutableMapOf()) { it.chunk }
        val chunks = if (schemaVersion >= 4) {
            section.getStringList("chunks").mapTo(mutableSetOf(), ::decodeChunk)
        } else {
            // 版本1-3的核心只认领单区块；迁移时按核心与旗帜各自3×3规则重建完整并集。
            buildSet {
                addAll(core.chunk.square(Territory.CORE_CHUNK_RADIUS))
                flags.keys.forEach { addAll(it.square(Territory.FLAG_CHUNK_RADIUS)) }
            }.toMutableSet()
        }
        val members = mutableMapOf<UUID, TerritoryMember>()
        section.getConfigurationSection("members")?.getKeys(false)?.forEach { rawPlayer ->
            val member = requireNotNull(section.getConfigurationSection("members.$rawPlayer"))
            members[UUID.fromString(rawPlayer)] = TerritoryMember(
                enumSet(member.getStringList("actions"), TerritoryAction.entries),
                enumSet(member.getStringList("management"), TerritoryManagement.entries)
            )
        }
        return Territory(
            id = id,
            owner = UUID.fromString(requireNotNull(section.getString("owner"))),
            core = core,
            chunks = chunks,
            flags = flags,
            members = members,
            defaultMemberActions = if (schemaVersion >= 2 && section.contains(DEFAULT_ACTIONS_KEY)) {
                enumSet(section.getStringList(DEFAULT_ACTIONS_KEY), TerritoryAction.entries)
            } else {
                TerritoryMember.DEFAULT_ACTIONS.toMutableSet()
            },
            defaultMemberManagement = if (schemaVersion >= 2) {
                enumSet(section.getStringList(DEFAULT_MANAGEMENT_KEY), TerritoryManagement.entries)
            } else {
                mutableSetOf()
            },
            visitorActions = enumSet(section.getStringList("visitor-actions"), TerritoryAction.entries),
            flagBaseColor = if (schemaVersion >= 2) {
                DyeColor.valueOf(section.getString(FLAG_BASE_COLOR_KEY, DyeColor.WHITE.name)!!)
            } else {
                DyeColor.WHITE
            },
            flagPatterns = if (schemaVersion >= 2) {
                section.getStringList(FLAG_PATTERNS_KEY).mapTo(mutableListOf(), ::decodePattern)
            } else {
                mutableListOf()
            },
            allowStrangerEntry = section.getBoolean("allow-stranger-entry", true),
            allowFlight = section.getBoolean("allow-flight", false),
            locked = section.getBoolean("locked", false)
        )
    }

    /**
     * 必填或坐标字段损坏时尽量保留仍可解析的区块，并强制锁定为仅管理员可访问。
     * 这样单个字段错误不会把原本受保护的整片区域直接暴露出来。
     */
    private fun readLockedTerritory(id: UUID, section: ConfigurationSection): Territory? {
        val chunks = section.getStringList("chunks").mapNotNullTo(mutableSetOf()) {
            runCatching { decodeChunk(it) }.getOrNull()
        }
        val flags = section.getStringList("flags").mapNotNull {
            runCatching { decodeBlock(it) }.getOrNull()
        }.associateByTo(mutableMapOf()) { it.chunk }
        val core = section.getString("core")?.let { runCatching { decodeBlock(it) }.getOrNull() }
            ?: flags.values.firstOrNull()
            ?: chunks.firstOrNull()?.let { TerritoryBlock(it.world, it.x shl 4, 0, it.z shl 4) }
            ?: return null
        val owner = runCatching { UUID.fromString(section.getString("owner")) }.getOrNull()
            ?: UUID.nameUUIDFromBytes("corrupt-owner:$id".toByteArray(StandardCharsets.UTF_8))
        chunks += core.chunk
        chunks += flags.keys
        return Territory(
            id = id,
            owner = owner,
            core = core,
            chunks = chunks,
            flags = flags,
            allowStrangerEntry = false,
            allowFlight = false,
            locked = true
        )
    }

    private fun writeTerritory(yaml: YamlConfiguration, territory: Territory) {
        val path = "$ROOT.${territory.id}"
        yaml.set("$path.owner", territory.owner.toString())
        yaml.set("$path.core", encode(territory.core))
        yaml.set("$path.chunks", territory.chunks.map(::encode).sorted())
        yaml.set("$path.flags", territory.flags.values.map(::encode).sorted())
        yaml.set("$path.$DEFAULT_ACTIONS_KEY", territory.defaultMemberActions.map { it.name }.sorted())
        yaml.set("$path.$DEFAULT_MANAGEMENT_KEY", territory.defaultMemberManagement.map { it.name }.sorted())
        yaml.set("$path.visitor-actions", territory.visitorActions.map { it.name }.sorted())
        yaml.set("$path.$FLAG_BASE_COLOR_KEY", territory.flagBaseColor.name)
        yaml.set("$path.$FLAG_PATTERNS_KEY", territory.flagPatterns.map(::encode))
        yaml.set("$path.allow-stranger-entry", territory.allowStrangerEntry)
        yaml.set("$path.allow-flight", territory.allowFlight)
        yaml.set("$path.locked", territory.locked)
        territory.members.toSortedMap(compareBy<UUID> { it.toString() }).forEach { (player, member) ->
            yaml.set("$path.members.$player.actions", member.actions.map { it.name }.sorted())
            yaml.set("$path.members.$player.management", member.management.map { it.name }.sorted())
        }
    }

    private fun encode(chunk: TerritoryChunk): String = "${chunk.world};${chunk.x};${chunk.z}"
    private fun encode(block: TerritoryBlock): String = "${block.world};${block.x};${block.y};${block.z}"
    private fun encode(pattern: Pattern): String = "${pattern.color.name};${pattern.pattern.key}"

    private fun decodeChunk(value: String): TerritoryChunk {
        val parts = value.split(';')
        require(parts.size == 3) { "Invalid chunk key" }
        return TerritoryChunk(UUID.fromString(parts[0]), parts[1].toInt(), parts[2].toInt())
    }

    private fun decodeBlock(value: String): TerritoryBlock {
        val parts = value.split(';')
        require(parts.size == 4) { "Invalid block key" }
        return TerritoryBlock(UUID.fromString(parts[0]), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
    }

    /** 旗帜图案使用颜色枚举名和完整注册表键，避免 PatternType 顺序变化破坏存档。 */
    private fun decodePattern(value: String): Pattern {
        val parts = value.split(';')
        require(parts.size == 2) { "Invalid banner pattern" }
        val color = DyeColor.valueOf(parts[0])
        val key = requireNotNull(NamespacedKey.fromString(parts[1])) { "Invalid banner pattern key" }
        val type = requireNotNull(Registry.BANNER_PATTERN[key]) { "Unknown banner pattern: $key" }
        return Pattern(color, type)
    }

    private fun <E : Enum<E>> enumSet(values: List<String>, entries: List<E>): MutableSet<E> {
        val byName = entries.associateBy(Enum<E>::name)
        return values.mapTo(mutableSetOf()) { value ->
            requireNotNull(byName[value]) { "Unknown enum value: $value" }
        }
    }

    companion object {
        /** 动态存档文件名，发布后不得修改，否则已有领地无法恢复。 */
        private const val FILE_NAME = "territories.yml"
        /** YAML 根节点名称，属于持久化路径协议。 */
        private const val ROOT = "territories"
        /** 新成员默认行为权限键，缺失时迁移为历史安全默认值。 */
        private const val DEFAULT_ACTIONS_KEY = "default-member-actions"
        /** 新成员默认管理权限键；历史存档迁移时保持为空，防止意外提权。 */
        private const val DEFAULT_MANAGEMENT_KEY = "default-member-management"
        /** 旗帜图案层列表键，与独立底色键共同恢复完整旗帜外观。 */
        private const val FLAG_PATTERNS_KEY = "flag-patterns"
        /** 旗帜底色键与图案层共同组成完整模板，新旗帜也必须继承。 */
        private const val FLAG_BASE_COLOR_KEY = "flag-base-color"
        /** 存档版本键是拒绝误读未来格式的兼容性协议。 */
        private const val SCHEMA_VERSION_KEY = "schema-version"
        /** 版本4把核心默认覆盖从单区块升级为3×3，并要求 chunks 保存完整覆盖并集。 */
        private const val SCHEMA_VERSION = 4
        /** 版本1-3可迁移默认权限、旗帜样式及核心/旗帜3×3覆盖。 */
        private const val MIN_SUPPORTED_SCHEMA = 1
    }
}
