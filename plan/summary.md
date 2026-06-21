# 项目代码与 ARCH.md 实现进度对比 (2026-06-21 更新)

## 代码现状总结 vs ARCH.md

### 📊 整体进度

| M# | 里程碑 | 进度 | 说明 |
|----|--------|------|------|
| **M1** | 数据模型 + 存储层 | **~95%** | 模型20个文件全部就位，存储4个文件完整，JSON序列化/反序列化完全实现 |
| **M2** | 命令系统 + Controller | **~90%** | 6个Controller + 7个命令文件全部实现；命令树完整可用 |
| **M3** | 容器交互 + 记忆 | **~70%** | 🟡 execute() 已完整实现，ContainerScreenMixin 已存在并注册，但旧版 `ClickType` API 导致编译失败 |
| **M4** | 高亮渲染 | **~60%** | 🟡 render() 已完整实现，HighlightManager 完成，但 MC 26.1 渲染 API 变更导致编译失败，缺 WorldRendererMixin |
| **M5** | 路径执行器 | **~25%** | 🔴 PathfindingController 已接入 PathExecutor 接口，但仅 SimpleWalkExecutor 可用且不控制玩家移动 |
| **M6** | UI接口+EventBus | **0%** | ⚪ 没有任何 EventBus、UI 友好接口或监听系统 |

### 🔴 关键差异 (实际代码 vs ARCH.md 设计)

| 差异项 | ARCH.md 设计 | 实际代码 |
|--------|-------------|---------|
| **包结构** | `src/client/java/` + `src/main/java/` 分离 | **全部在 `src/main/java/` 下** |
| **Mixin 基础设施** | 无 mixins.json，Mixin 待实现 | **已就位**：`mc-warehouse.mixins.json` + `fabric.mod.json` mixins 声明已配齐 |
| **Mixin 完成度** | 3个 Mixin 均未实现 | **2/3 已实现**：`ContainerScreenMixin` + `MultiPlayerGameModeMixin`，仅缺 `WorldRendererMixin` |
| **ContainerInteractor** | execute()/applyPlan() 是空桩 | **execute() 已完整实现**（调用 `menu.clicked()` 执行 `QUICK_MOVE`），但使用已移除的 `ClickType` API |
| **ContainerOutlineRenderer** | render() 是空桩 | **render() 已完整实现**（调用 `LevelRenderer.renderLineBox()`），但使用已移除的旧版 API |
| **PathfindingController** | 未接入 PathExecutor | **已接入**：使用 `executor.tick()` 状态机处理 MOVING/ARRIVED/FAILED/DONE |
| **HighlightSubCommand** | 独立的 `HighlightSubCommand.java` | **不存在**，功能合并到 `WarehouseSubCommand.java` |
| **DataStorage<T> 接口** | 存储层实现该接口 | **定义了但无人实现**，各 Storage 自己定义方法签名 |
| **ContainerSnapshot.java** | 未在模型树中列出 | **真实存在**，被 ContainerMemory 引用 |
| **ContainerInfo.mode** | 字段名 `mode` | **字段名 `ruleMode`**，另含内部枚举 `RuleMode` 和 `defaultMode()` 静态方法 |
| **[--pathfinder]** | RunSubCommand 接受参数 | 确实接受并传入 `PathfindingController`，但 switch 仅 `default -> SimpleWalkExecutor`，始终忽略该参数 |
| **命令端规则创建** | 应支持所有 Selector/Quantifier 类型 | **仅支持 IdSelector + CountSelector**，其他类型只能 JSON 编辑 |
| **Mixin `compatibilityLevel`** | 无要求 | 设为 `JAVA_21`，与项目目标 Java 25 不完全一致（但向下兼容） |
| **ContainerController.onScreenClosed()** | 应记录记忆 | 捕获快照后**直接返回**，未实际更新记忆 |

### ✅ 完整实现的部分（无编译问题）

- **Model层全20文件** — 全部完整实现，包括5个ItemSelector + 4个QuantitySelector + 4个config子包
- **Storage层全4文件** — 包括带自定义类型适配器的 Gson 序列化（多态反序列化完整支持6+4种类型）
- **Controller层全6文件** — 全部完整实现 (Selection/Rule/Warehouse/Pathfinding/Container/Highlight)
- **Command层7/8文件** — 除HighlightSubCommand外全部存在且完整，命令树与设计基本一致
- **Engine核心规则引擎** — RuleApplicator（196行，TransferPlan完整计算逻辑）/ ItemMatcher / QuantityCalculator 全部完成
- **ContainerMemory/Scanner/HighlightManager** — 管理类全实现
- **Util全3文件** — Constants / CoordinateUtils / CommandUtils 全部完成
- **MCWarehouseClient入口** — 命令注册 + 事件接入
- **Mixin基础设施** — `mc-warehouse.mixins.json` + `fabric.mod.json` mixins声明已就位
- **ContainerScreenMixin** — 注入 `onClose` 到 `AbstractContainerScreen`
- **MultiPlayerGameModeMixin** — 注入 `useItemOn` 到 `MultiPlayerGameMode`

### ⚠️ 部分实现 / 需API迁移

- **ContainerInteractor.execute()** — 逻辑完整但使用已移除的 `ClickType` API（需改为 `ContainerInput`）
- **ContainerOutlineRenderer.render()** — 逻辑完整但使用已移除的 API（`RenderType.LINES`、`LevelRenderer.renderLineBox()`、`Camera.getPosition()`）
- **MCWarehouseClient** — `WorldRenderEvents.AFTER_ENTITIES` 注册失败（Fabric API 版本问题）
- **SimpleWalkExecutor** — 有完整状态机但不控制实际移动（只检测距离）
- **ContainerController.onScreenClosed()** — 捕获快照后未更新记忆
- **PathfindingController.onBlockInteraction()** — 方法体为空

### ❌ 完全未实现

- **WorldRendererMixin** — 不存在（渲染改由 `WorldRenderEvents` 事件驱动，不再需要此 Mixin）
- **CreativeFlightExecutor / PortalExecutor / HybridExecutor** — 3个路径执行器未实现
- **EventBus / UI接口** — 未开始

### 🏗️ 构建状态 (已修复 ✅)

**构建已通过**。8个编译错误全部修复：

| # | 文件 | 错误 | 修复方案 |
|---|------|------|---------|
| 1 | `ContainerInteractor.java` | `ClickType` 不存在 | 改为 `ContainerInput` + `ContainerInput.QUICK_MOVE` |
| 2 | `ContainerOutlineRenderer.java` | 多处 API 不兼容 (`RenderType`, `renderLineBox`, `camera.getPosition`) | 整体改用 MC 26.1 **Gizmos API**：`Gizmos.cuboid()` + `GizmoStyle.stroke()` |
| 3 | `MCWarehouseClient.java` | `WorldRenderEvents` 在 Fabric API 26.1 中已移除 | 删除旧注册逻辑，改为 `WorldRendererMixin` |
| 4 | 新增 `WorldRendererMixin` | 无（新文件） | 注入 `LevelRenderer.collectPerFrameGizmos()`（@At("RETURN")），调用 `ContainerOutlineRenderer.renderGizmos()` |

### 📈 总结

项目处于 **M2→M3 过渡阶段**，相比上次评估的重大变化：

1. **Mixin 层全部就位**：3个Mixin全部实现并注册（新增 `WorldRendererMixin`），不再缺失
2. **ContainerInteractor 和 ContainerOutlineRenderer 已有完整逻辑**：不再是空桩，已适配 MC 26.1 API
3. **构建已通过**：无编译错误
4. **核心矛盾**：从"构建失败/API不兼容" → "核心交互逻辑仍需完善"（`onBlockInteraction` 为空的、路径执行器不控制移动等）

### 📈 后续开发优先级建议（更新版）

| 优先级 | 任务 | 涉及里程碑 | 前置依赖 |
|--------|------|-----------|---------|
| **P1** | 修复 `ContainerController.onScreenClosed()` — 实际更新记忆 | M3 | 无 |
| **P1** | 实现 `PathfindingController.onBlockInteraction()` — 拦截手动交互 | M3 | 无 |
| **P1** | 命令端 `RuleSubCommand` 支持更多 Selector/Quantifier 类型 | M2 | 无 |
| **P2** | `PathfindingController` 支持切换 `PathExecutor` 类型（switch 添加分支） | M5 | 无 |
| **P3** | 实现 `CreativeFlightExecutor` / `PortalExecutor` / `HybridExecutor` | M5 | P2 |
| **P4** | EventBus 事件系统 + Controller UI 友好接口 | M6 | 稳定后 |
| **P4** | `DataStorage<T>` 接口落地（或废弃） | — | 无 |
