---
name: slimefun-item
description: 在 SlimeEasy（纯 Paper + Kotlin，无 TabooLib）中新增或修改 Slimefun 物品、机器、工具、配方、分类、研究与交互行为，并正确使用项目独立 I18n、Slimefun 权限/保护 API。涉及 SlimefunItem、SlimefunItemStack、ItemGroup、Research、ItemUseHandler、EntityInteractHandler、BlockTicker 或玩家可见文本时使用。
---

# SlimeEasy Slimefun 内容开发

## 项目基线

- 使用 Paper 26.2、Java 25、Kotlin 与 Slimefun 2026.x，不引入 TabooLib。
- 在 `paper-plugin.yml` 中保持插件 `load: POSTWORLD`，并声明 Slimefun `load: BEFORE`、`required: true`、`join-classpath: true`。
- 在 `SlimeEasy.onEnable` 中先 `saveDefaultConfig()`、再 `I18n.load()`，之后才能首次访问 `Groups`、`Items` 等会构造 `SlimefunItemStack` 的对象。
- 不要在 bootstrap、loader 或插件启用前构造 `SlimefunItemStack`；当前 API 会抛出 `PrematureCodeException`。

## API 真相源

先从当前源码或编译 jar 查证签名，不凭旧教程或记忆实现：

- Slimefun 源码：`E:/Code/Minecraft-Outsourcing/self/Slimefun4/src/main/java/`
- Slimefun 编译 jar：`E:/Code/SlimeFun/SlimeEasy/libs/Slimefun-2026.1.jar`
- 项目现有实现：`E:/Code/SlimeFun/SlimeEasy/src/main/kotlin/top/maplex/slimeEasy/`
- Paper API：Gradle 缓存中的 `io.papermc.paper/paper-api`

重点类的当前包名：

```kotlin
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.api.researches.Research
import io.github.thebusybiscuit.slimefun4.core.handlers.EntityInteractHandler
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
```

`BlockTicker` 当前不在 `core.handlers`；覆盖新签名时使用 `SlimefunBlockData`，不要使用已弃用的 `Config` 签名。

## 文件职责

- `registry/Groups.kt`：分类图标与 `ItemGroup`。
- `registry/Items.kt`、`StorageItems.kt`、`VillagerItems.kt`：物品模板和 3×3 配方数据。
- `registry/Registration.kt`：功能开关、实例注册、研究与监听器总入口。
- `machine/`、`feature/`、`storage/`、`villager/`：行为实现。
- `config/SEConfig.kt`：功能开关、研究等级与运行参数。
- `config/I18n.kt`：独立语言服务。
- `src/main/resources/lang/zh_CN.yml`：所有玩家可见中文。

## I18n 规则

禁止在 Kotlin 中新增玩家可见中文，也不要把名称或 Lore 写入 `config.yml`。

- `I18n.raw(key)`：保留 `&` 颜色码。用于 `SlimefunItemStack` 的名称和 Lore；该构造器会自行调用 `ChatColor.translateAlternateColorCodes('&', ...)`。
- `I18n.rawList(key)`：读取 Slimefun Lore 列表或 YAML `|-` 多行块。
- `I18n.text(key)`：转换为 `§` Legacy 字符串。用于 Bukkit/Slimefun 仍接收 `String` 的菜单、聊天和物品元数据 API。
- `I18n.component(key)`：用于接收 Adventure `Component` 的 Paper API。
- 动态参数使用 `{name}`，调用时传入 `"name" to value`。
- 新增按功能和组件分层的语义化键；禁止 `items-001`、`menu-023` 等数字键。
- 物品和 UI 节点统一使用一个基础键，并在其下定义 `name`、`lore`。

推荐语言结构：

```yaml
items:
  se-example-machine:
    name: "&e示例机器"
    lore: |-
      &7第一行说明
      &7当前产量: &f{amount}
menus:
  example-machine:
    status:
      name: "&b运行状态"
      lore: |-
        &7状态: {status}
        &7产量: &f{amount}
research:
  example-machine: "示例机器"
messages:
  example-machine:
    no-permission: "&c你没有权限使用该机器。"
```

物品模板：

```kotlin
val EXAMPLE: SlimefunItemStack = SEText.localized(
    EXAMPLE_ID,
    Material.OBSERVER,
    "items.se-example-machine",
    "amount" to SEConfig.exampleAmount
)
```

UI 图标使用同一模式：

```kotlin
GuiItems.localized(
    Material.PAPER,
    "menus.example-machine.status",
    "status" to status,
    "amount" to amount
)
```

`SEText.localized` 与 `GuiItems.localized` 分别读取基础键下的 `name/lore`，不要在业务代码逐行拼装翻译。

语言文件缺失文件或键时由 `I18n` 回退到 jar 内置 `zh_CN.yml`。`/se reload` 会重载配置和语言；物品、分类、研究及已缓存界面文本仍需重启才能更新。

## 注册流程

1. 在对应 registry 文件定义大写唯一 ID、物品模板和 `Array<ItemStack?>` 配方；空位使用 `null`。
2. 仅在新分类确有必要时向 `Groups.kt` 添加 `ItemGroup`，分类图标文本同样走 `I18n.raw`。
3. 有行为的内容继承 `SlimefunItem`，在 `preRegister()` 中添加 handler；纯物品直接注册 `SlimefunItem`。
4. 在 `Registration.registerAll(addon)` 内受 `SEConfig` 开关控制地注册分类、物品、研究和监听器。
5. 同步 `config.yml`、`SEConfig.kt`、`lang/zh_CN.yml` 与 README 功能说明。

注册示例：

```kotlin
val item = ExampleMachine(
    Groups.UTILITY_MACHINES,
    Items.EXAMPLE,
    RecipeType.ENHANCED_CRAFTING_TABLE,
    Items.EXAMPLE_RECIPE
).also { it.register(addon) }

Research(
    NamespacedKey(SlimeEasy.instance, "example_machine"),
    9022,
    I18n.text("research.example-machine"),
    SEConfig.exampleResearch
).apply {
    addItems(item)
    register()
}
```

`NamespacedKey` 是研究主标识。当前构造器仍要求数字 ID，但数字 ID API 已弃用；为兼容现有研究配置，继续分配未占用且不重复的递增 ID。

## Handler 正确签名

### 手持物品右键

```kotlin
override fun preRegister() {
    addItemHandler(ItemUseHandler { event ->
        event.cancel()
        val player = event.player
        val heldItem = event.item
        val clickedBlock = event.clickedBlock.orElse(null)
    })
}
```

`event.item` 不返回 null，无物品时为空气物品；`clickedBlock` 是 `Optional<Block>`；手信息从 `event.hand` 读取。

### 右键实体

```kotlin
addItemHandler(EntityInteractHandler { event, item, offHand ->
    if (offHand) return@EntityInteractHandler
    event.isCancelled = true
    val target = event.rightClicked
})
```

### 方块 ticker

```kotlin
addItemHandler(object : BlockTicker() {
    override fun tick(block: Block, item: SlimefunItem, data: SlimefunBlockData) {
        // tick 逻辑
    }

    override fun isSynchronized(): Boolean = true
})
```

读取或修改世界、方块、实体、菜单和容器时必须同步执行。只有完全线程安全且不触碰 Bukkit 世界状态的逻辑才考虑返回 `false`。

## 权限与保护

- 玩家使用已有 Slimefun 方块或进行远程绑定/访问前，调用 `Slimefun.getPermissionsService().hasPermission(player, slimefunItem)`；拒绝时可用 Slimefun 自身本地化消息 `messages.no-permission`。
- 自动破坏、放置或攻击必须通过 `Slimefun.getProtectionManager().hasPermission(...)` 检查对应 `Interaction`，不要绕过 WorldGuard、GriefPrevention 等保护插件。
- 没有玩家上下文的机器应在放置时记录 owner，再以 owner 身份检查目标位置。优先复用 `MachineProtection`。
- 网络控制器识别与使用权限优先复用 `NetworkControllerAccess`，不要只判断方块类型或 Slimefun ID。
- handler 中涉及原版交互时正确取消事件：`PlayerRightClickEvent.cancel()`；Bukkit 实体事件使用 `event.isCancelled = true`。

## 常见约束

- 主类不要直接实现 `SlimefunAddon`；继续使用独立 `SlimeEasyAddon`，避免 `JavaPlugin.getName()` 的 final 签名冲突。
- 物品 ID 必须全大写，分类和研究的 `NamespacedKey` 使用小写下划线。
- 方块材质才能挂 `BlockTicker`，实现 `NotPlaceable` 的物品不能挂 ticker。
- 冷却优先使用 `player.setCooldown(itemStack, ticks)`，让原版冷却状态成为唯一真相源。
- 矿石集合优先使用 `SlimefunTag`，但展示名必须走 `I18n`，不要用 `Material.name` 直接展示给中文玩家。
- 新增运行参数时同时加入 `config.yml` 与 `SEConfig` getter；PDC 键、数据版本和 GUI 槽位等存档/布局协议保持代码常量。

## 验证

1. 扫描新增 Kotlin 运行时字符串，确认没有玩家可见中文残留。
2. 校验所有新增 I18n 键存在、占位符名称一致且没有未使用键。
3. 运行 `gradlew.bat build --no-daemon`；Windows 环境不要假定存在 Bash。
4. 不启动服务端；未进行服务器运行验证时明确标注“运行时未实测”。
5. 提交时不要包含 `.agents` 以外的代理临时文件；仅在用户明确要求提交 Skill 时提交 `.agents/skills/slimefun-item/`。
