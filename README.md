# SlimeEasy

一个基于 [Slimefun](https://github.com/Slimefun/Slimefun4) 的 Paper 附属插件, 提供若干实用的自动化机械与防护装置。

## 环境要求

| 项 | 版本 |
|------|------|
| 服务端 | Paper 26.2+ |
| Java | 25 |
| 前置插件 | Slimefun4 |

## 下载

最新开发版随 `main` 分支自动构建发布 (滚动更新, 地址固定不变):

```
https://github.com/FxRayHughes/SlimeEasy/releases/download/latest/SlimeEasy-1.0-SNAPSHOT-all.jar
```

将 jar 放入服务器 `plugins/` 目录, 与 Slimefun 一同加载即可。

## 内容一览

所有内容归入 Slimefun 指南中的 **实用机械** 分类, 均需研究解锁 (各 10 级经验)。

### 自动破坏机

以**普通活塞**驱动, 自动破坏活塞推杆朝向的方块并收入机器箱子。

- 工具挖掘: 箱子第 0 格放挖掘类工具时, 以该工具挖掘, 生效时运 / 精准采集等附魔
- 耐久消耗: 遵循原版**耐久 (Unbreaking)** 附魔的免损概率, 工具耗尽后清空该格并回退徒手挖掘
- 容量保护: 掉落物无法完全放入箱子时, 本次不破坏, 避免物品溢出丢失

**合成** (增强工作台): 涂蜡铜箱子居中, 四周环绕普通活塞, 底部红石块 + 铁块。

### 自动放置机

以**粘性活塞**驱动, 自动将机器箱子中的方块放置到活塞推杆朝向的位置。

**合成** (增强工作台): 涂蜡铜箱子居中, 四周环绕粘性活塞, 底部红石块 + 铁块。

### 苦力怕驱逐方块

绿色地毯外观。放置后, **其所在区块及周围一圈 (共 3×3 区块)** 成为受保护区:

- **禁止自然生成**: 保护区内不再自然生成苦力怕 (拦截自然 / 刷怪笼 / 区块生成等, 放行指令与刷怪蛋)
- **持续驱逐**: 已进入的苦力怕沿"离开全部相连保护区的最近方向"被推出; 多个方块保护区重叠时方向一致, 不会把苦力怕挤在重叠带
- **禁止爆炸**: 保护区内苦力怕无法引爆 (双层拦截: 引爆预备 + 实际爆炸)
- **卡死兜底**: 苦力怕被连续推离约 10 秒仍无法离开 (被墙 / 坑卡住等) 时直接移除

保护随方块存在而持续, 破坏后失效; 登记为内存 TTL 自愈式, 服务器重启后自动重建。

**合成** (增强工作台): 铁剑居中, 八格仙人掌环绕。

## 从源码构建

```bash
./gradlew build
```

产物位于 `build/libs/SlimeEasy-<version>-all.jar` (shadow 打包, 已内置 Kotlin 运行时)。

本地起测试服 (由 run-paper 提供):

```bash
./gradlew runServer
```

## 项目结构

```
src/main/kotlin/top/maplex/slimeEasy/
├── SlimeEasy.kt              插件主类 (入口)
├── SlimeEasyAddon.kt         Slimefun 附属身份
├── machine/
│   ├── common/               机器共享层
│   │   ├── PistonSupport      活塞方向 / 目标方块定位
│   │   ├── FrequencyResolver  相邻红石块调速
│   │   └── BlockEffect        破坏 / 放置音效与粒子
│   ├── breaker/              自动破坏机
│   └── placer/               自动放置机
├── feature/ward/             苦力怕驱逐方块
│   ├── CreeperWard            方块本体 + ticker (续期 + 驱逐 + 兜底移除)
│   ├── ProtectedChunks        受保护区块 TTL 登记 + 全局出口方向
│   └── CreeperControlListener 生成拦截 + 爆炸拦截
└── registry/                 物品 / 分类 / 研究 / 配方注册
```

## 持续集成

每次推送触发 GitHub Actions 构建; `main` 分支构建成功后自动更新 `latest` 滚动发布 (预发布)。特性分支仅构建, 不发布。
