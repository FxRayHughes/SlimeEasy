package top.maplex.slimeEasy.territory

import io.github.thebusybiscuit.slimefun4.libraries.dough.skins.PlayerHead
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import top.maplex.slimeEasy.config.I18n
import top.maplex.slimeEasy.storage.core.GuiItems
import java.util.UUID

/**
 * 领地管理箱子界面。
 * 54格主界面与45格分页区域是固定槽位协议，按钮必须返回 false 防止图标被取走。
 */
internal object TerritoryMenu {
    /** 主界面固定为六行，所有功能入口槽位均以该尺寸为布局协议。 */
    private const val SIZE = 54
    /** 确认类子菜单固定把危险操作放在中央，返回按钮放在底行中央。 */
    private const val CONFIRM_SLOT = 22
    private const val BACK_SLOT = 49
    /** 旗帜编辑器上方展示当前样式，清空按钮位于右下；样式导入只响应玩家背包点击。 */
    private const val FLAG_PREVIEW_SLOT = 13
    private const val FLAG_CLEAR_SLOT = 33
    /** 成员与在线邀请列表共享前五行；底行固定为上一页、返回/邀请和下一页协议。 */
    private val MEMBER_SLOTS = (0 until 45).toList()

    fun open(player: Player, territory: Territory) {
        val menu = base(I18n.text("menus.territory.title"))
        add(menu, 13, icon(Material.LODESTONE, "menus.territory.overview",
            "owner" to ownerName(territory),
            "chunks" to territory.chunks.size,
            "flags" to territory.flags.size,
            "maxFlags" to Territory.MAX_FLAGS))
        if (canBrowseMembers(player, territory)) {
            add(menu, 20, GuiItems.localized(Material.PLAYER_HEAD, "menus.territory.members")) {
                openMembers(player, territory, 0)
            }
        } else if (player.uniqueId in territory.members) {
            add(menu, 20, GuiItems.localized(Material.PLAYER_HEAD, "menus.territory.my-permissions")) {
                openMemberPermissions(player, territory, player.uniqueId)
            }
        }
        if (TerritoryService.canManage(player, territory, TerritoryManagement.PERMISSIONS)) {
            add(menu, 22, GuiItems.localized(Material.OAK_DOOR, "menus.territory.visitors")) {
                openVisitorPermissions(player, territory)
            }
            add(menu, 21, icon(Material.WRITABLE_BOOK, "menus.territory.default-permissions",
                "actions" to territory.defaultMemberActions.size,
                "management" to territory.defaultMemberManagement.size,
                "managementMax" to TerritoryManagement.entries.size)) {
                openDefaultPermissions(player, territory)
            }
        }
        if (TerritoryService.canManage(player, territory, TerritoryManagement.SETTINGS)) {
            add(menu, 24, GuiItems.localized(Material.LEVER, "menus.territory.settings")) {
                openSettings(player, territory)
            }
        }
        add(menu, 30, icon(Material.WHITE_BANNER, "menus.territory.chunks", "count" to territory.chunks.size)) {
            openChunks(player, territory, 0)
        }
        if (TerritoryService.canManage(player, territory, TerritoryManagement.FLAGS)) {
            add(menu, 31, icon(Material.LOOM, "menus.territory.flag-editor",
                "patterns" to territory.flagPatterns.size)) {
                openFlagEditor(player, territory)
            }
        }
        if (player.uniqueId == territory.owner || TerritoryService.isAdmin(player)) {
            add(menu, 32, GuiItems.localized(Material.ENDER_EYE, "menus.territory.transfer")) {
                TerritoryInputListener.promptTransfer(player, territory)
            }
            add(menu, 40, GuiItems.localized(Material.BARRIER, "menus.territory.disband")) {
                openDisband(player, territory)
            }
        }
        menu.open(player)
    }

    private fun openMembers(player: Player, territory: Territory, page: Int) {
        val menu = base(I18n.text("menus.territory.member-title"))
        val members = territory.members.keys.sortedBy { Bukkit.getOfflinePlayer(it).name.orEmpty().lowercase() }
        val pageMembers = members.drop(page * MEMBER_SLOTS.size).take(MEMBER_SLOTS.size)
        if (pageMembers.isEmpty()) {
            add(menu, 22, GuiItems.localized(Material.GRAY_DYE, "menus.territory.no-members"))
        }
        pageMembers.forEachIndexed { index, member ->
            add(menu, MEMBER_SLOTS[index], playerHead(member, territory.members.getValue(member))) {
                openMemberPermissions(player, territory, member)
            }
        }
        if (page > 0) add(menu, 45, GuiItems.PREV_PAGE) { openMembers(player, territory, page - 1) }
        if ((page + 1) * MEMBER_SLOTS.size < members.size) add(menu, 53, GuiItems.NEXT_PAGE) {
            openMembers(player, territory, page + 1)
        }
        if (TerritoryService.canManage(player, territory, TerritoryManagement.MEMBERS)) {
            add(menu, 49, GuiItems.localized(Material.LIME_DYE, "menus.territory.add-member")) {
                openInviteCandidates(player, territory.id, 0)
            }
        }
        add(menu, 48, GuiItems.localized(Material.ARROW, "menus.territory.back")) { open(player, territory) }
        menu.open(player)
    }

    /**
     * 展示此刻仍可被邀请的在线玩家；每次翻页都重新取快照并夹紧页码，避免上下线造成空页。
     * 隐身玩家按 Bukkit 可见性过滤，不能借领地菜单泄露其在线状态。
     */
    private fun openInviteCandidates(player: Player, territoryId: UUID, requestedPage: Int) {
        val territory = managedTerritory(player, territoryId) ?: return
        val candidates = inviteCandidates(player, territory)
        val lastPage = if (candidates.isEmpty()) 0 else (candidates.size - 1) / MEMBER_SLOTS.size
        val page = requestedPage.coerceIn(0, lastPage)
        val menu = base(I18n.text("menus.territory.invite-list-title"))
        val pageCandidates = candidates.drop(page * MEMBER_SLOTS.size).take(MEMBER_SLOTS.size)
        if (pageCandidates.isEmpty()) {
            add(menu, CONFIRM_SLOT, GuiItems.localized(Material.GRAY_DYE, "menus.territory.no-invite-candidates"))
        }
        pageCandidates.forEachIndexed { index, target ->
            add(menu, MEMBER_SLOTS[index], invitePlayerHead(target, "menus.territory.invite-player")) {
                openInviteConfirmation(player, territory.id, target.uniqueId, page)
            }
        }
        if (page > 0) add(menu, 45, GuiItems.PREV_PAGE) {
            openInviteCandidates(player, territory.id, page - 1)
        }
        if ((page + 1) * MEMBER_SLOTS.size < candidates.size) add(menu, 53, GuiItems.NEXT_PAGE) {
            openInviteCandidates(player, territory.id, page + 1)
        }
        add(menu, BACK_SLOT, GuiItems.localized(Material.ARROW, "menus.territory.back")) {
            openMembers(player, territory, 0)
        }
        menu.open(player)
    }

    /**
     * 玩家头颅只负责选中目标，实际发送邀请必须再点中央确认按钮；确认时重新解析所有状态，
     * 防止菜单打开后发生下线、入会、领地解散或成员管理权被收回。
     */
    private fun openInviteConfirmation(player: Player, territoryId: UUID, targetId: UUID, returnPage: Int) {
        val territory = managedTerritory(player, territoryId) ?: return
        val target = currentInviteTarget(player, territory, targetId, returnPage) ?: return
        val menu = base(I18n.text("menus.territory.invite-confirm-title", "player" to target.name))
        add(menu, 13, invitePlayerHead(target, "menus.territory.invite-selected"))
        add(menu, CONFIRM_SLOT, GuiItems.localized(
            Material.LIME_CONCRETE,
            "menus.territory.invite-confirm",
            "player" to target.name
        )) {
            val currentTerritory = managedTerritory(player, territoryId) ?: return@add
            val currentTarget = currentInviteTarget(player, currentTerritory, targetId, returnPage) ?: return@add
            val result = TerritoryInvitations.inviteMember(player, currentTerritory, currentTarget)
            TerritoryMessages.send(player, result)
            if (result.wasApplied()) {
                openMembers(player, currentTerritory, 0)
            } else {
                openInviteCandidates(player, currentTerritory.id, returnPage)
            }
        }
        add(menu, BACK_SLOT, GuiItems.localized(Material.ARROW, "menus.territory.back")) {
            openInviteCandidates(player, territory.id, returnPage)
        }
        menu.open(player)
    }

    private fun openMemberPermissions(player: Player, territory: Territory, target: UUID) {
        if (target !in territory.members) {
            player.closeInventory()
            TerritoryMessages.send(player, TerritoryService.Result.NOT_MEMBER)
            return
        }
        val menu = base(I18n.text("menus.territory.permission-title", "player" to playerName(target)))
        val canEditActions = TerritoryService.canManage(player, territory, TerritoryManagement.PERMISSIONS)
        val canEditManagement = player.uniqueId == territory.owner || TerritoryService.isAdmin(player)
        TerritoryAction.entries.forEachIndexed { index, action ->
            val allowed = territory.members[target]?.actions?.contains(action) == true
            add(menu, 10 + index + if (index >= 4) 5 else 0,
                permissionIcon(action.name, allowed, canEditActions), actionClick@{
                if (!canEditActions) return@actionClick
                val result = TerritoryService.toggleAction(player, territory, target, action)
                TerritoryMessages.send(player, result)
                if (result.wasApplied()) openMemberPermissions(player, territory, target)
            })
        }
        TerritoryManagement.entries.forEachIndexed { index, permission ->
            val allowed = territory.members[target]?.management?.contains(permission) == true
            add(menu, 28 + index, managementIcon(permission.name, allowed, canEditManagement), managementClick@{
                if (!canEditManagement) return@managementClick
                val result = TerritoryService.toggleManagement(player, territory, target, permission)
                TerritoryMessages.send(player, result)
                if (result.wasApplied()) openMemberPermissions(player, territory, target)
            })
        }
        if (TerritoryService.canManage(player, territory, TerritoryManagement.MEMBERS)) {
            add(menu, 40, GuiItems.localized(Material.RED_DYE, "menus.territory.remove-member")) {
                openRemoveMember(player, territory, target)
            }
        }
        add(menu, BACK_SLOT, GuiItems.localized(Material.ARROW, "menus.territory.back")) {
            if (canBrowseMembers(player, territory)) openMembers(player, territory, 0) else open(player, territory)
        }
        menu.open(player)
    }

    /** 移除成员会即时撤销权限并可能驱离玩家，因此必须经过独立确认界面。 */
    private fun openRemoveMember(player: Player, territory: Territory, target: UUID) {
        val menu = base(I18n.text("menus.territory.remove-title", "player" to playerName(target)))
        add(menu, CONFIRM_SLOT, GuiItems.localized(Material.RED_CONCRETE, "menus.territory.remove-confirm",
            "player" to playerName(target))) {
            val result = TerritoryService.removeMember(player, territory, target)
            TerritoryMessages.send(player, result)
            if (result.wasApplied()) {
                player.sendMessage(I18n.text("messages.territory.member-removed", "player" to playerName(target)))
                Bukkit.getPlayer(target)?.sendMessage(I18n.text(
                    "messages.territory.you-were-removed", "owner" to ownerName(territory)
                ))
                openMembers(player, territory, 0)
            }
        }
        add(menu, BACK_SLOT, GuiItems.localized(Material.ARROW, "menus.territory.back")) {
            openMemberPermissions(player, territory, target)
        }
        menu.open(player)
    }

    private fun openVisitorPermissions(player: Player, territory: Territory) {
        val menu = base(I18n.text("menus.territory.visitor-title"))
        TerritoryAction.entries.forEachIndexed { index, action ->
            val allowed = action in territory.visitorActions
            add(menu, 10 + index + if (index >= 4) 5 else 0, permissionIcon(action.name, allowed, true)) {
                val result = TerritoryService.toggleAction(player, territory, null, action)
                TerritoryMessages.send(player, result)
                if (result.wasApplied()) openVisitorPermissions(player, territory)
            }
        }
        add(menu, 49, GuiItems.localized(Material.ARROW, "menus.territory.back")) { open(player, territory) }
        menu.open(player)
    }

    /** 新成员默认权限独立于现有成员；行为权限可委派管理，管理权限仍只允许主人修改。 */
    private fun openDefaultPermissions(player: Player, territory: Territory) {
        val menu = base(I18n.text("menus.territory.default-title"))
        val canEditActions = TerritoryService.canManage(player, territory, TerritoryManagement.PERMISSIONS)
        val canEditManagement = player.uniqueId == territory.owner || TerritoryService.isAdmin(player)
        TerritoryAction.entries.forEachIndexed { index, action ->
            val allowed = action in territory.defaultMemberActions
            add(menu, 10 + index + if (index >= 4) 5 else 0,
                permissionIcon(action.name, allowed, canEditActions), actionClick@{
                if (!canEditActions) return@actionClick
                val result = TerritoryService.toggleDefaultAction(player, territory, action)
                TerritoryMessages.send(player, result)
                if (result.wasApplied()) openDefaultPermissions(player, territory)
            })
        }
        TerritoryManagement.entries.forEachIndexed { index, permission ->
            val allowed = permission in territory.defaultMemberManagement
            add(menu, 28 + index, managementIcon(permission.name, allowed, canEditManagement), managementClick@{
                if (!canEditManagement) return@managementClick
                val result = TerritoryService.toggleDefaultManagement(player, territory, permission)
                TerritoryMessages.send(player, result)
                if (result.wasApplied()) openDefaultPermissions(player, territory)
            })
        }
        add(menu, BACK_SLOT, GuiItems.localized(Material.ARROW, "menus.territory.back")) { open(player, territory) }
        menu.open(player)
    }

    /**
     * 旗帜编辑器直接读取玩家背包中被点击的旗帜，并始终取消背包点击，避免模板被移动或消耗。
     * 服务层更新旗帜颜色材质与 Banner TileState 图案，同时保留朝向及按坐标绑定的 Slimefun 身份。
     */
    private fun openFlagEditor(player: Player, territory: Territory) {
        val menu = base(I18n.text("menus.territory.flag-editor-title"))
        add(menu, FLAG_PREVIEW_SLOT, flagPreview(territory))
        // base() 默认锁定玩家背包；此界面只开放读取样式，处理器返回 false 仍禁止任何物品移动。
        menu.setPlayerInventoryClickable(true)
        menu.addPlayerInventoryClickHandler { _, _, item, _ ->
            val template = item?.itemMeta as? BannerMeta
            val baseColor = item?.type?.let(::bannerColor)
            if (template == null || baseColor == null) {
                player.sendMessage(I18n.text("messages.territory.flag-template-required"))
            } else {
                applyFlagDesign(player, territory, baseColor, template.patterns)
            }
            false
        }
        add(menu, FLAG_CLEAR_SLOT, GuiItems.localized(Material.BARRIER, "menus.territory.flag-clear")) {
            applyFlagDesign(player, territory, DyeColor.WHITE, emptyList())
        }
        add(menu, BACK_SLOT, GuiItems.localized(Material.ARROW, "menus.territory.back")) { open(player, territory) }
        menu.open(player)
    }

    /** 将服务层同步数量反馈给操作者；写盘失败时仍展示内存中已经应用的样式。 */
    private fun applyFlagDesign(
        player: Player,
        territory: Territory,
        baseColor: DyeColor,
        patterns: List<org.bukkit.block.banner.Pattern>
    ) {
        val update = TerritoryService.updateFlagDesign(player, territory, baseColor, patterns)
        TerritoryMessages.send(player, update.result)
        if (update.result.wasApplied()) {
            player.sendMessage(I18n.text(
                "messages.territory.flags-updated", "updated" to update.updated, "total" to update.total
            ))
            openFlagEditor(player, territory)
        }
    }

    private fun flagPreview(territory: Territory): ItemStack =
        GuiItems.localized(bannerMaterial(territory.flagBaseColor), "menus.territory.flag-preview",
            "patterns" to territory.flagPatterns.size).apply {
            editMeta(BannerMeta::class.java) { it.patterns = territory.flagPatterns }
        }

    private fun bannerColor(material: Material): DyeColor? {
        val colorName = material.name.removeSuffix("_WALL_BANNER").removeSuffix("_BANNER")
        return runCatching { DyeColor.valueOf(colorName) }.getOrNull()
    }

    private fun bannerMaterial(color: DyeColor): Material =
        Material.matchMaterial("${color.name}_BANNER") ?: Material.WHITE_BANNER

    private fun openSettings(player: Player, territory: Territory) {
        val menu = base(I18n.text("menus.territory.settings-title"))
        add(menu, 20, toggleIcon(Material.OAK_DOOR, "entry", territory.allowStrangerEntry)) {
            val result = TerritoryService.toggleEntry(player, territory)
            TerritoryMessages.send(player, result)
            if (result.wasApplied()) openSettings(player, territory)
        }
        add(menu, 24, toggleIcon(Material.FEATHER, "flight", territory.allowFlight)) {
            val result = TerritoryService.toggleFlight(player, territory)
            TerritoryMessages.send(player, result)
            if (result.wasApplied()) openSettings(player, territory)
        }
        add(menu, 49, GuiItems.localized(Material.ARROW, "menus.territory.back")) { open(player, territory) }
        menu.open(player)
    }

    /** 3×3旗帜会产生大量认领区块，因此区块列表必须分页，不能静默截断第45项之后的数据。 */
    private fun openChunks(player: Player, territory: Territory, page: Int) {
        val menu = base(I18n.text("menus.territory.chunk-title"))
        val chunks = territory.chunks.sortedWith(compareBy<TerritoryChunk> { it.x }.thenBy { it.z })
        chunks.drop(page * MEMBER_SLOTS.size).take(MEMBER_SLOTS.size).forEachIndexed { slot, chunk ->
            add(menu, slot, icon(if (chunk == territory.core.chunk) Material.LODESTONE else Material.WHITE_BANNER,
                "menus.territory.chunk", "x" to chunk.x, "z" to chunk.z))
        }
        if (page > 0) add(menu, 45, GuiItems.PREV_PAGE) { openChunks(player, territory, page - 1) }
        if ((page + 1) * MEMBER_SLOTS.size < chunks.size) {
            add(menu, 53, GuiItems.NEXT_PAGE) { openChunks(player, territory, page + 1) }
        }
        if (TerritoryService.canManage(player, territory, TerritoryManagement.CHUNKS)) {
            add(menu, 50, GuiItems.localized(Material.COMPASS, "menus.territory.expansion-mode")) {
                TerritorySessions.selectExpansion(player.uniqueId, territory.id)
                player.closeInventory()
                player.sendMessage(I18n.text("messages.territory.expansion-selected"))
            }
        }
        add(menu, 48, GuiItems.localized(Material.ARROW, "menus.territory.back")) { open(player, territory) }
        menu.open(player)
    }

    private fun openDisband(player: Player, territory: Territory) {
        val menu = base(I18n.text("menus.territory.disband-title"))
        add(menu, 22, GuiItems.localized(Material.TNT, "menus.territory.disband-confirm")) {
            if (player.uniqueId == territory.owner || TerritoryService.isAdmin(player)) {
                val result = TerritoryService.disband(territory)
                TerritoryMessages.send(player, result)
                if (result.wasApplied()) {
                    player.closeInventory()
                    player.sendMessage(I18n.text("messages.territory.disbanded"))
                }
            }
        }
        add(menu, 49, GuiItems.localized(Material.ARROW, "menus.territory.back")) { open(player, territory) }
        menu.open(player)
    }

    private fun base(title: String): ChestMenu = ChestMenu(title).apply {
        setSize(SIZE)
        setEmptySlotsClickable(false)
        setPlayerInventoryClickable(false)
    }

    private fun add(menu: ChestMenu, slot: Int, item: ItemStack, action: (() -> Unit)? = null) {
        menu.addItem(slot, item) { _, _, _, _ -> action?.invoke(); false }
    }

    private fun icon(material: Material, key: String, vararg placeholders: Pair<String, Any?>): ItemStack =
        GuiItems.localized(material, key, *placeholders)

    private fun permissionIcon(permission: String, enabled: Boolean, editable: Boolean): ItemStack =
        icon(if (enabled) Material.LIME_DYE else Material.GRAY_DYE,
            "menus.territory.${if (editable) "permission" else "permission-readonly"}",
            "permission" to I18n.raw("names.territory.action.${permission.lowercase().replace('_', '-')}"),
            "state" to state(enabled))

    private fun managementIcon(permission: String, enabled: Boolean, editable: Boolean): ItemStack =
        icon(if (enabled) Material.LIME_CONCRETE else Material.GRAY_CONCRETE,
            "menus.territory.${if (editable) "management" else "management-readonly"}",
            "permission" to I18n.raw("names.territory.management.${permission.lowercase()}"),
            "state" to state(enabled))

    private fun toggleIcon(material: Material, setting: String, enabled: Boolean): ItemStack =
        icon(
            material,
            "menus.territory.setting",
            "setting" to I18n.raw("names.territory.setting.$setting"),
            "state" to state(enabled)
        )

    /** 成员头像同样走 Slimefun 的 PlayerHead 工具，避免菜单内存在两套皮肤构造路径。 */
    private fun playerHead(player: UUID, access: TerritoryMember): ItemStack =
        PlayerHead.getItemStack(Bukkit.getOfflinePlayer(player)).apply {
            editMeta { meta ->
                meta.displayName(Component.text(playerName(player)).decoration(TextDecoration.ITALIC, false))
                meta.lore(listOf(I18n.component("menus.territory.member-summary",
                    "actions" to access.actions.size, "management" to access.management.size,
                    "managementMax" to TerritoryManagement.entries.size)))
            }
        }

    /** 保留 PlayerHead 写入的皮肤，只替换身份感知菜单所需的本地化名称与 Lore。 */
    private fun invitePlayerHead(player: Player, key: String): ItemStack = PlayerHead.getItemStack(player).apply {
        val display = I18n.componentDisplay(key, "player" to player.name)
        editMeta { meta ->
            meta.displayName(display.name)
            if (display.lore.isNotEmpty()) meta.lore(display.lore)
        }
    }

    /** 候选集只包含操作者可见、尚未加入且不是领地主人的其他在线玩家。 */
    private fun inviteCandidates(player: Player, territory: Territory): List<Player> =
        Bukkit.getOnlinePlayers().asSequence()
            .filter { target ->
                target.uniqueId != player.uniqueId && target.uniqueId != territory.owner &&
                    target.uniqueId !in territory.members && player.canSee(target)
            }
            .sortedBy { it.name.lowercase() }
            .toList()

    /** 打开邀请相关界面前统一重验领地存在性及成员管理权限。 */
    private fun managedTerritory(player: Player, territoryId: UUID): Territory? {
        val territory = TerritoryService.byId(territoryId)
        if (territory == null) {
            player.closeInventory()
            TerritoryMessages.send(player, TerritoryService.Result.NOT_FOUND)
            return null
        }
        if (!TerritoryService.canManage(player, territory, TerritoryManagement.MEMBERS)) {
            player.closeInventory()
            TerritoryMessages.send(player, TerritoryService.Result.NO_PERMISSION)
            return null
        }
        return territory
    }

    /** 目标下线或变为不可见时不泄露状态；已加入时返回成员结果并刷新候选列表。 */
    private fun currentInviteTarget(
        player: Player,
        territory: Territory,
        targetId: UUID,
        returnPage: Int
    ): Player? {
        val target = Bukkit.getPlayer(targetId)
        if (target == null || !player.canSee(target)) {
            player.sendMessage(I18n.text("messages.territory.player-not-online"))
            openInviteCandidates(player, territory.id, returnPage)
            return null
        }
        if (target.uniqueId == territory.owner || target.uniqueId in territory.members) {
            TerritoryMessages.send(player, TerritoryService.Result.ALREADY_MEMBER)
            openInviteCandidates(player, territory.id, returnPage)
            return null
        }
        return target
    }

    private fun state(enabled: Boolean): String =
        I18n.raw("names.territory.state.${if (enabled) "enabled" else "disabled"}")
    private fun canBrowseMembers(player: Player, territory: Territory): Boolean =
        player.uniqueId == territory.owner || TerritoryService.isAdmin(player) ||
            TerritoryService.canManage(player, territory, TerritoryManagement.MEMBERS) ||
            TerritoryService.canManage(player, territory, TerritoryManagement.PERMISSIONS)
    private fun ownerName(territory: Territory): String = playerName(territory.owner)
    private fun playerName(player: UUID): String = Bukkit.getOfflinePlayer(player).name ?: player.toString()
}
