# 项目进度总结

## 项目状态

- 构建通过 ✅
- 可以进游戏测试部分功能（数据管理：创建仓库、选择区域、添加容器、创建规则、列出信息、数据持久化）
- 核心自动化功能不能正常工作（不会自动移动玩家、不会渲染高亮、不会合理搬运物品、规则无法绑定到容器）

## ARCH.md 变化

从 d340b38 (Init) 到 ff2328d8 (当前版本)，主要变化：

| 区域                 | 变化                                                                           |
| -------------------- | ------------------------------------------------------------------------------ |
| **§1.2 包结构**      | 扩展为"源集与包结构"，明确 `src/client/java/` 全部代码 + `src/main/java/` 为空 |
| **controller 包**    | 新增 `HighlightController`                                                     |
| **model 包**         | 新增 `ContainerSnapshot`，新增 `ContainerMemory` 描述                          |
| **Mixin 标注**       | 从纯描述改为 "3/3 已完成 ✅"，标注注入方法和具体 API                           |
| **§4.1 路径执行器**  | 新增实现状态段落（硬编码为 SimpleWalk、不控制移动）                            |
| **§4.2 容器交互**    | 新增说明：`execute()` 已实现，`ClickType`→`ContainerInput`，构建通过           |
| **§4.3 高亮**        | 新增说明：Gizmos API 重构完成，`WorldRendererMixin` 新建，构建通过             |
| **§2.1 Warehouse**   | 新增 `Map<String, ItemRules> rules` 字段                                       |
| **§八 技术决策**     | 新增"源集分离"行                                                               |
| **§九 源集分离说明** | 新增完整一节：`splitEnvironmentSourceSets()` 配置详解                          |
| **§里程碑**          | 从纯规划表变为进度跟踪表，M1~M6 全部标注百分比和状态图标                       |

---

## 当前代码进度（探索结果综合）

**`./gradlew build` ✅ 通过，不会崩溃。**

但核心自动化**还不能用**。可以把它当成"仓库数据管理工具"先测试，不能当成"自动搬运 mod"用。

### ✅ 可以测试的部分（数据管理工具）

```
/warehouse warehouse create test      → 创建并激活仓库，锚点设为当前位置
/warehouse select pos1 / pos2          → 设置选择区域
/warehouse container add INPUT          → 扫描区域内的容器并加入仓库
/warehouse rule create myrule          → 创建规则组
/warehouse rule add myrule --id minecraft:diamond --count 64
/warehouse warehouse list              → 列出仓库
/warehouse container list              → 列出容器
/warehouse config show                 → 查看配置
```

**所有数据都会保存到 `<game-dir>/mc-warehouse/` 的 JSON 文件中，重启后还在。**

### ❌ 还不能用的核心功能（1个关键阻塞）

| 阻塞项               | 严重程度 | 说明                                                                                                                          |
| -------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------- |
| **不会自动移动玩家** | **P0**   | `SimpleWalkExecutor` 只检测距离（2格内算到达），从不发送移动输入/数据包。跑 `/warehouse run` 后你需要**手动走到每个容器旁边** |

### ✅ 最近修复
- **高亮渲染已修复**：`HighlightController.showWarehouse()` 调用 `HighlightManager.setWarehouseHighlights()`，`hideAll()` 同时清空 Manager。`/warehouse warehouse show` 现可正常渲染容器高亮。
- **物品操作 tick 间隔已修复**：`ContainerInteractor` 新增 `startExecution() + tick()` 状态机，每 `speed` tick 执行一次移动。`PathfindingController` 在交互期间暂停路径执行，交互完成后再推进到下一目标容器。
- **规则绑定命令已添加**：`/warehouse container rules add <name>` 和 `remove <name>`，玩家看向目标容器即可绑定/解绑规则组。

### 其他小问题

- `onScreenClosed()`（Mixin 注入容器关闭时）捕获了快照后什么都不做
- `MultiPlayerGameModeMixin` 注入的 `onBlockInteraction()` 是空方法
- 命令端 rule parser 只支持 `--id/--count/--negate`，不支持 tag/name/nbt/fill/group/percent

---

## 结论和建议

**现在可以先进游戏做一次"数据管理"测试**，验证这些链路：

1. 命令是否正常注册和响应
2. 区域选择是否能正确扫描容器
3. 仓库/容器/规则的数据是否正确写入 JSON
4. 数据重启后是否加载正常

**但不建议现在做"自动搬运"测试**，因为 `/warehouse run` 需要玩家手动走到每个容器旁边，且搬运逻辑因为没有绑定的规则而不生效。

如果你想继续开发，优先级最高的事是：

1. **让 `SimpleWalkExecutor` 真正控制移动**（输入模拟或发包）

---

# 附录

## 可用命令

```
/warehouse warehouse create <name>     → Creates + activates warehouse at player pos
/warehouse warehouse list               → Lists warehouses
/warehouse warehouse activate <name>    → Activates warehouse
/warehouse warehouse deactivate         → Deactivates
/warehouse warehouse delete <name>      → Deletes warehouse
/warehouse select pos1                  → Sets selection corner 1
/warehouse select pos2                  → Sets selection corner 2
/warehouse select expand <dir> <amount> → Expands selection
/warehouse select show                  → Shows selection bounds
/warehouse container add <type>         → Scans selection, adds containers
/warehouse container remove             → Removes containers in selection
/warehouse container list               → Lists containers in active warehouse
/warehouse container type <type>        → Changes type of selected containers
/warehouse container mode <mode>        → Changes mode of selected containers
/warehouse container info               → Info for block player is looking at
/warehouse container memory show        → Shows cached snapshot for container
/warehouse container memory clear       → Clears all cached snapshots
/warehouse container rules add <name>   → Binds rule group to looked-at container
/warehouse container rules remove <name> → Unbinds rule group from looked-at container
/warehouse rule create <name>           → Creates rule group
/warehouse rule delete <name>           → Deletes rule group
/warehouse rule list                    → Lists rule groups
/warehouse rule show <name>             → Shows rules in group
/warehouse rule add <name> <args>       → Adds rule (only --id/--count/--negate)
/warehouse rule remove <name> <index>   → Removes rule by index
/warehouse rule edit <name> <index>     → Edits rule by index
/warehouse config show                  → Shows world config JSON
/warehouse config set interaction.speed <n>
/warehouse config reload                → Reloads config
```
