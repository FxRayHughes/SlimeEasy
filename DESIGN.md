# SlimeEasy 设计说明

## 配置与语言边界

- `config.yml` 只保存功能开关、研究等级和运行参数，不保存面向玩家的文案。
- `lang/<language>.yml` 按功能和组件层级保存物品、分类、研究、菜单、消息与展示名称，不使用扁平数字键。
- 物品和 UI 图标统一使用包含 `name` 与 `lore` 的基础节点；Lore 采用 YAML `|-` 多行块。
- Kotlin 代码只引用稳定的语义基础键；动态值通过具名 `{placeholder}` 注入，颜色码统一使用 `&`。
- `SEText.localized` 与 `GuiItems.localized` 分别封装 Slimefun 物品和 Adventure UI 图标构造。
- 自定义语言缺失文件或键时回退到插件内置 `zh_CN.yml`，避免升级后出现空文本。
- `/se reload` 同时重载配置与语言。后续读取的菜单和消息更新；Slimefun 注册阶段冻结的物品、分类、研究及已缓存界面文本需重启生效。

## 依赖边界

项目直接使用 Paper、Adventure 与 Slimefun API，不依赖 TabooLib。语言加载由 `I18n` 独立完成，避免把框架配置能力引入业务模块。

## 磁盘存储边界

- 磁盘管理器使用雕纹书架的六个原生书槽作为安装状态与方块外观的唯一真相源，独立 ChestMenu 只展示镜像并执行受校验的安装 / 拆卸。
- 磁盘物品 PDC 只保存不可变 UUID 与格式版本；库存明细以 UUID 为主键写入 Slimefun UniversalData，由服务器当前配置的 SQLite、MySQL 或 PostgreSQL 后端持久化。
- 管理器仅在内存中聚合六张磁盘，不在方块 BlockData 保存第二份内容；翻页箱继续使用 `StorageCacheUtils` 方块数据，同样由 Slimefun SQL 后端持久化。
- 物品分类沿用 `ItemKey` 的完整 ItemStack meta/NBT 相等性。容量按八分之一字节整数单位计算，严格实现 `种类数 × x × 8 + 物品数 / 8`，并单盘限制 64 种。
- 存储网络通过 `CargoBufferBlock` 的可覆盖持久化钩子访问管理器；管理器不注册隐藏货运菜单，避免其 0/1 槽覆盖雕纹书架真实书槽。

### 安全与限制

- UniversalData 查询同时校验 UUID 对应记录的 Slimefun ID 与磁盘规格，阻止篡改 UUID 后覆盖其它通用数据。
- 非空磁盘在容量策略入口被拒绝，避免递归存储和数据规模指数膨胀；空磁盘允许作为普通物品存储。
- Slimefun 权限服务控制管理器 UI 访问；已打开界面在每次热插拔前重新校验方块身份，避免方块破坏后向原坐标写入。
- 被永久销毁或通过管理指令复制的 UUID 无法可靠感知：前者可能留下孤立 UniversalData，后者会让副本指向同一逻辑磁盘。这是“物品仅携带轻量 UUID”模型的已知限制。

## 变更历史

### 2026-07-12 - 新增 SQL 后端物品存储磁盘

**变更内容**: 新增磁盘管理器与六档磁盘，扩展虚拟库存容量策略和存储基类持久化钩子，并以 Slimefun UniversalData 保存可移动磁盘内容。

**变更理由**: 大量完整 ItemStack 数据不适合写入书本 PDC；UniversalData 能按 UUID 跨方块查询并复用 Slimefun 已配置的 SQL 后端。

**影响范围**: 存储物品注册、存储网络成员扫描、虚拟库存容量判断、磁盘 UI、配置、语言与存储文档。

**决策依据**: 翻页箱已通过 Slimefun 方块数据落入同一 SQL 基础设施；磁盘因可移动而选择 UUID 作用域的 UniversalData，而非坐标作用域 BlockData 或插件自建 SQLite。

### 2026-07-11 - 玩家文本迁移到独立 i18n

**变更内容**: 把 Kotlin 中的玩家可见中文统一迁移到 `lang/zh_CN.yml`，并移除物品文本自动写入 `config.yml` 的旧机制。

**变更理由**: 统一语言资源入口，支持后续新增语言并避免配置与文本双轨漂移。

**影响范围**: 物品与分类注册、研究、菜单、命令消息、功能交互提示和展示名称。
