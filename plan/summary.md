# 项目初期代码与ARCH.md实现进度对比

## 代码现状总结 vs ARCH.md

### 📊 整体进度

| M# | 里程碑 | 进度 | 说明 |
|----|--------|------|------|
| **M1** | 数据模型 + 存储层 | **~95%** | 模型20个文件全部就位（含额外ContainerSnapshot），存储4个文件完整，JSON序列化/反序列化完全实现 |
| **M2** | 命令系统 + Controller | **~90%** | 6个Controller + 7个命令文件全部实现；`/warehouse`+`/wh` 命令树完整可用 |
| **M3** | 容器交互 + 记忆 | **~50%** | 记忆管理/扫描完成，但 `ContainerInteractor.execute()` 是空桩，缺少Mixin驱动GUI自动化，实际无法搬运物品 |
| **M4** | 高亮渲染 | **~40%** | HighlightManager/Color定义完成，但 `ContainerOutlineRenderer.render()` 是空桩，缺少 WorldRendererMixin 无法渲染到屏幕 |
| **M5** | 路径执行器 | **~20%** | 接口定义 + SimpleWalkExecutor 完成（SimpleWalk也不控制玩家移动），缺 CreativeFlight/Portal/Hybrid 三个执行器 |
| **M6** | UI接口+EventBus | **0%** | 没有任何 EventBus、UI 友好接口或监听系统 |

### 🔴 关键差异 (实际代码 vs ARCH.md 设计)

| 差异项 | ARCH.md 设计 | 实际代码 |
|--------|-------------|---------|
| **包结构** | `src/client/java/` + `src/main/java/` 分离 | **全部在 `src/main/java/` 下**（无client目录） |
| **Mixin层** | 3-4个Mixin + mixins.json | **mixin/目录完全为空**，无mixins.json，fabric.mod.json无mixins条目 |
| **HighlightSubCommand** | 独立的 `command/sub/HighlightSubCommand.java` | **不存在**，功能合并到 `WarehouseSubCommand.java` |
| **PathfindingController** | 使用 `PathExecutor.tick()` 状态机 | **未使用PathExecutor**，自有一套简化版tick循环直接调用 `executeTransfer` |
| **DataStorage<T> 接口** | 存储层实现该接口 | **定义了但无人实现**，各Storage自己定义方法签名 |
| **ContainerSnapshot.java** | 未在模型树中列出 | **真实存在**，被ContainerMemory引用 |
| **Warehouse.rules** | ARCH.md 类图只显示4个字段 | **实际有5个**：多了 `Map<String, ItemRules> rules`（JSON中确实有rules，类图遗漏） |
| **[--pathfinder]** | RunSubCommand接受 `--pathfinder <type>` 参数 | 确实接受，但**PathfindingController完全忽略该参数**，不区分寻路方式 |
| **命令端规则创建** | 应支持所有Selector/Quantifier类型 | **仅支持 IdSelector + CountSelector**，其他类型只能JSON编辑 |

### ✅ 完整实现的部分

- **Model层全20文件** — 全部full实现，包括5个ItemSelector + 4个QuantitySelector + 4个config子包
- **Storage层全4文件** — 包括带自定义类型适配器的 Gson 序列化（多态反序列化完整支持6+4种类型）
- **Controller层全6文件** — 全部full实现 (Selection/Rule/Warehouse/Pathfinding/Container/Highlight)
- **Command层7/8文件** — 除HighlightSubCommand外全部存在且完整
- **Engine核心规则引擎** — RuleApplicator（196行，TransferPlan完整计算逻辑）/ ItemMatcher / QuantityCalculator 全部完成
- **ContainerMemory/Scanner/HighlightManager** — 管理类全实现
- **Util全3文件** — Constants / CoordinateUtils / CommandUtils 全部完成
- **MCWarehouseClient入口** — 命令注册 + 事件接入

### ⚠️ 部分实现 / 空桩

- **ContainerInteractor.execute()** — 空桩 (`// Stub — will be driven by ContainerScreenMixin`)
- **ContainerOutlineRenderer.render()** — 空桩 (`// Stub — WorldRendererMixin will wire up rendering`)
- **SimpleWalkExecutor** — 有状态机但不控制实际移动（只检测距离）

### ❌ 完全未实现

- **所有Mixin** — ContainerScreenMixin / WorldRendererMixin / MultiPlayerGameModeMixin / MinecraftMixin 均不存在
- **mixins.json** — 不存在
- **CreativeFlightExecutor / PortalExecutor / HybridExecutor** — 3个路径执行器未实现
- **PathExecutor集成** — PathfindingController 未接入 PathExecutor 接口
- **EventBus / UI接口** — 未开始

### 📈 总结

项目处于 **M2→M3过渡阶段**。数据模型、存储、命令体系和Controller业务逻辑已经非常扎实，但关键的 **Mixin层（容器GUI自动化+高亮渲染）** 完全缺失，导致M3/M4无法真正跑通。路径执行器方面仅有基础框架。

### src/client/java/ 不存在的原因
**Commit `f4b1c8b`** 是一次大规模重构，包含三件事：

1. **整体移动** `src/client/java/ → src/main/java/` — 40个文件rename，其中32个R100纯移动无内容变动
2. **故意降级/打桩** — `ContainerOutlineRenderer.java` 从56行（有完整 `LevelRenderer.renderLineBox()` 渲染实现）→ 30行（只保留颜色map，render()清空），推测是因为Mixin层还没搭好，先退成桩避免编译报错
3. **API适配** — `TagSelector.java` 从15行（用 `stack.getItemHolder().tags()`，该API在新版mappings中不存在）→ 23行（改用 `stack.is(TagKey.create(...))` 修复编译）
4. **新增命令文件** — `ConfigSubCommand`/`ContainerSubCommand`/`RuleSubCommand`/`RunSubCommand`/`WarehouseSubCommand` 五个新文件，加上 `build.gradle`/`settings.gradle` 的构建配置修正

移动的原因推测：因为 `fabric.mod.json` 声明了 `environment: "client"`，整个mod纯客户端，没必要维持 `src/client/` 和 `src/main/` 的分离结构。同时 `settings.gradle` 在commit "2"才加上 `pluginManagement` 仓库声明 — 说明第一次提交时构建配置是不完整的，commit "2"同时在修构建问题。

---

## 后续开发优先级建议

自然切入点：**先打通 Mixin 层（M3/M4 的 blocker）→ 补上 ContainerInteractor 执行逻辑 → 再接入 PathExecutor**

| 优先级 | 任务 | 涉及里程碑 | 前置依赖 |
|--------|------|-----------|---------|
| **P0** | 搭建 Mixin 基础设施：`mixins.json` + `fabric.mod.json` mixins 声明 + gradle 的 Mixin 配置 | M3/M4 | 无 |
| **P0** | 实现 `ContainerScreenMixin`（Hook 容器 GUI 打开/槽位操作） | M3 | Mixin 基础设施 |
| **P0** | 实现 `WorldRendererMixin`（Hook 渲染管线画高亮轮廓） | M4 | Mixin 基础设施 |
| **P1** | 实现 `ContainerInteractor.execute()` — 通过 Mixin 驱动真正的 GUI 物品移动 | M3 | ContainerScreenMixin |
| **P1** | 实现 `ContainerOutlineRenderer.render()` — 调用 `LevelRenderer.renderLineBox()` 画框 | M4 | WorldRendererMixin |
| **P1** | 实现 `MultiPlayerGameModeMixin` — Hook 交互发包（右键打开容器等） | M3 | Mixin 基础设施 |
| **P2** | `PathfindingController` 接入 `PathExecutor` 接口，让状态机用引擎层的 PathExecutor | M5 | P1 完成 |
| **P2** | `RunSubCommand --pathfinder` 参数真正生效，选择不同 PathExecutor 实现 | M5 | PathfindingController 接入 |
| **P3** | 实现 `CreativeFlightExecutor` / `PortalExecutor` / `HybridExecutor` | M5 | P2 完成 |
| **P3** | 命令端 `RuleSubCommand` 支持更多 Selector/Quantifier 类型（TagSelector / NameSelector / GroupSelector 等） | M2 增强 | 无 |
| **P4** | EventBus 事件系统 + Controller UI 友好接口 | M6 | P0-P3 稳定后 |
| **P4** | `DataStorage<T>` 接口落地（或废弃该接口） | — | 无 |

### 建议的迭代顺序

```
迭代1 (P0): Mixin基础设施 → ContainerScreenMixin → WorldRendererMixin
迭代2 (P1): ContainerInteractor.execute() + ContainerOutlineRenderer.render() + MultiPlayerGameModeMixin
迭代3 (P2): PathfindingController 接入 PathExecutor + --pathfinder 生效
迭代4 (P3): 剩余 3 个 PathExecutor + 命令增强
迭代5 (P4): EventBus / UI 接口
```
