---
name: slimeeasy-goggles-extension
description: 为依赖 SlimeEasy 的 Paper/Slimefun 附属接入工程师护目镜公开扩展 API。涉及 EngineerGogglesApi、EngineerGogglesContentProvider、EngineerGogglesDisplayEvent、自定义结构目标注册，或需要为附属物品追加、修改、过滤、隐藏护目镜显示内容以及实施 SlimeBotania 等附属的依赖倒转时使用。
---

# SlimeEasy 工程师护目镜扩展

## 目标与边界

- 让附属插件依赖 SlimeEasy，并通过公开 API 扩展护目镜内容；禁止让 SlimeEasy 反向依赖具体附属。
- 只操作 `top.maplex.slimeEasy.api.goggles` 下的公开类型，不引用 `feature.goggles`、空间索引、目标缓存或 DecentHolograms 类型。
- 由 SlimeEasy 负责目标扫描、玩家私有显示、筛选、定位和全息图生命周期；附属只提供自身领域数据和文本。只有目标不进入 Slimefun 原生方块/多方块注册表时，附属才额外提供常数时间的结构成员解析函数。
- 在修改前检查 SlimeEasy 当前源码。仓库存在 `.codegraph/` 时先运行 `codegraph explore` 查询相关符号，不凭旧文档或记忆猜测签名。

## 接入流程

1. 确认目标附属的构建系统、插件描述文件、I18n 服务、主类启停入口及 Slimefun 物品 ID 规则。
2. 将 SlimeEasy 作为 `compileOnly` 依赖，不把 SlimeEasy 或 DecentHolograms 打进附属 jar。依赖坐标或本地 jar 路径必须从当前工程配置查证。
3. 在 `paper-plugin.yml` 的服务端依赖中声明 SlimeEasy `load: BEFORE`、`required: true`、`join-classpath: true`。只有附属不安装 SlimeEasy 也必须完整工作时，才改为可选依赖并把所有 API 引用隔离到不会被提前类加载的适配器中。
4. 优先注册 `EngineerGogglesContentProvider`；仅在需要 Bukkit 监听优先级、取消语义或松耦合集成时监听 `EngineerGogglesDisplayEvent`。原生扫描无法发现的世界结构再额外调用 `registerTargetProvider`，不要为已能显示的普通方块或原生多方块重复注册目标。
5. 在附属启用且 I18n 已加载后注册，在关闭时主动注销。SlimeEasy 会在插件禁用事件中兜底清理，但附属仍应对自己的生命周期负责。
6. 同步更新附属的构建配置、`paper-plugin.yml`、语言文件、README 和 DESIGN；不要修改 SlimeEasy 去识别附属的具体类或 ID。

## Provider 模式

Provider 在主线程按注册顺序、先于 Bukkit 事件执行，适合每次刷新都要生成的稳定内容。

```kotlin
private val gogglesProvider = EngineerGogglesContentProvider { context, content ->
    if (!context.slimefunItem.id.startsWith("SB_")) return@EngineerGogglesContentProvider

    content.details += botaniaI18n.text(
        "goggles.material-flower.mana",
        "mana" to readMana(context.block)
    )
}

override fun onEnable() {
    EngineerGogglesApi.registerProvider(this, gogglesProvider)
}

override fun onDisable() {
    EngineerGogglesApi.unregisterProvider(this, gogglesProvider)
}
```

- 用精确 Slimefun ID、附属维护的 ID 集合、命名空间或自身类型判断目标；不要仅按原版 `Material` 猜测归属。
- `context.block` 是普通机器方块或多方块中心，`context.isMultiblock` 用于区分两种语义。
- `content.title` 是第一行；`content.details` 是标题后的有序可变列表；`content.visible = false` 会隐藏本轮目标。
- 后注册的 Provider 或事件监听器仍可改回 `visible = true`。若需要不可被其他扩展覆盖的最终隐藏，使用事件并选择合适优先级。
- `EngineerGogglesApi.API_VERSION` 表示公开协议能力版本；自定义目标提供器要求版本不低于 `2`，启用时应检查并给出明确日志。

## 自定义结构目标

只有设备刻意不进入 Slimefun 的持久方块或原生多方块注册表、导致内容 Provider 永远收不到回调时，才注册目标提供器：

```kotlin
private val targetProvider = Function<Block, Collection<Block>> { center ->
    findMachineMembers(center) ?: emptyList()
}

override fun onEnable() {
    check(EngineerGogglesApi.API_VERSION >= 2) { "Engineer goggles target API v2 is required" }
    EngineerGogglesApi.registerTargetProvider(
        this,
        myMachineItem,
        setOf(Material.CAULDRON),
        1,
        targetProvider
    )
}

override fun onDisable() {
    EngineerGogglesApi.unregisterTargetProvider(this, targetProvider)
}
```

- `centerMaterials` 是逻辑中心的候选材质集合，SlimeEasy 先按材质索引，只有匹配中心才调用解析函数。
- 返回集合必须包含中心和本次匹配的全部真实成员；空集合表示不匹配。瞄准模式与潜行右键过滤只认可该集合，不把半径内其它方块视为成员。
- `structureReach` 是中心到任一成员在 X/Y/Z 各轴上的最大距离，必须声明最小真实值；它同时决定局部缓存失效和右键反查上界。
- 解析只能检查已加载中心及少量邻块，不得遍历世界、区块、实体或注册表；跨区块成员必须先用 `World.isChunkLoaded` 判断，不能隐式加载区块。
- SlimeEasy 会验证成员同世界、区块已加载、中心存在和距离边界，并把结果转换成相对坐标；附属不得跨 tick 保存传入或返回的 `Block`。
- 注册、注销必须使用同一个 `Function` 实例。插件关闭有兜底清理，但附属仍应主动注销内容与目标两类 Provider。

## Event 模式

事件在内置内容和全部 Provider 之后同步触发，适合最终调整、跨插件监听或取消显示。

```kotlin
@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
fun onGogglesDisplay(event: EngineerGogglesDisplayEvent) {
    if (!owns(event.context.slimefunItem)) return
    event.content.details += botaniaI18n.text("goggles.material-flower.status")
}
```

- 调用 `event.isCancelled = true` 会隐藏目标并清理已有显示。
- `ignoreCancelled = true` 表示尊重更早监听器的取消；需要审计或显式恢复时才使用 `false`。
- 不要同时用 Provider 和 Event 重复追加同一行。Provider 是依赖 SlimeEasy 的附属的默认选择。

## 文本与数据规则

- 所有玩家可见文本由附属自己的 I18n 服务生成，Kotlin 中不得硬编码中文或把 SlimeEasy 的语言键当成附属协议。
- `title` 与 `details` 接收已完成颜色转换的 Legacy 文本；占位符先由附属 I18n 展开。
- 读取附属自己持久化的真实数据，不反射 SlimeEasy、Slimefun EnergyNet 或其他插件私有实现。
- 不伪造无法可靠取得的速率、工作状态或容量；未知信息应省略，而不是显示“未知”。
- 保持每一行语义稳定且简短。护目镜会按最终行数定位全息图，扩展无需自行调整高度。

## 生命周期与性能约束

- 内容 Provider、目标 Provider 和 Event 都在 Bukkit 主线程的高频路径执行：只做常数时间判断和轻量读取，不执行文件、数据库、网络 I/O，不扫描世界、区块或注册表。
- 昂贵结果应由附属在自身事件或 ticker 中维护缓存，Provider 只读取快照；缓存键优先使用世界 UUID 与方块坐标，方块移除、区块卸载和插件关闭时清理。
- 不异步访问 `viewer`、`block` 或 `slimefunItem`，也不跨 tick 保存 `context`、`content` 或事件对象。
- 不创建调度任务来延迟修改 `content`；回调返回后本轮内容即视为完成。
- 不向玩家广播内容，不创建实体、Action 或命中箱，私有性和点击穿透由 SlimeEasy 的显示后端保证。

## 完成检查

1. 确认依赖方向只有“附属 -> SlimeEasy”，SlimeEasy 源码中没有附属类、jar 或 ID 的主动适配。
2. 确认内容与目标注册/注销分别使用同一个 Provider 实例，重复启用不会累积注册；原生可发现目标没有被重复注册。
3. 确认玩家文本全部走附属 I18n，键和占位符完整，未知数据不会生成无意义行。
4. 确认回调没有阻塞操作、全局扫描、异步 Bukkit 访问或内部 API 引用。
5. 检查 `git diff --check` 及目标项目允许的静态检查。只有用户明确要求构建或编译时才运行 Gradle/Maven；否则交付时注明未执行构建。
6. 说明尚未进行的服务器实测，并给出佩戴者私有显示、过滤隐藏、卸下清理和插件重载的运行时检查点。
