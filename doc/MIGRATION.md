# 代码迁移指南

## 概述

本文档指导如何将旧项目（`mc-warehouse-old`）中已验证的代码迁移到新项目（`mc-warehouse`）。新项目是旧项目的重构版本，包名从 `bid.yuanlu.mcwarehouse` 变更为 `bid.yuanlu.mc.warehouse`，架构和设计在 PDD.md 中有详细描述。

## 迁移前准备

1. 旧项目路径：`<旧项目根目录>`（下文以 `$OLD` 代指）
2. 新项目路径：`<新项目根目录>`（下文以 `$NEW` 代指）
3. 确保已阅读 `$NEW/doc/PDD.md`，理解新架构设计

## 可迁移的代码模块

以下模块可直接迁移（仅需改包名和 import）：

### 1. 物品选择器 (ItemSelector 体系)

| 旧文件                                                           | 新包路径                               | 迁移方式         |
| ---------------------------------------------------------------- | -------------------------------------- | ---------------- |
| `$OLD/src/client/java/.../model/rule/ItemSelector.java`          | `api/item/ItemSelector.java`           | 直接复制，改包名 |
| `$OLD/src/client/java/.../model/selector/IdSelector.java`        | `impl/selector/IdSelector.java`        | 直接复制，改包名 |
| `$OLD/src/client/java/.../model/selector/TagSelector.java`       | `impl/selector/TagSelector.java`       | 直接复制，改包名 |
| `$OLD/src/client/java/.../model/selector/NameSelector.java`      | `impl/selector/NameSelector.java`      | 直接复制，改包名 |
| `$OLD/src/client/java/.../model/selector/NbtSelector.java`       | `impl/selector/NbtSelector.java`       | 直接复制，改包名 |
| `$OLD/src/client/java/.../model/selector/CompositeSelector.java` | `impl/selector/CompositeSelector.java` | 直接复制，改包名 |

**注意事项**：

- 接口方法签名无需修改（`matches(ItemStack)`）
- `CompositeSelector` 的 `op` 枚举保持 `AND`/`OR`/`NOT`
- 确保 `ItemSelector` 接口移动到 `api/` 包
- **NbtSelector 语义差异**：旧实现是 `componentsPatch.toString().contains(value)` 字符串包含匹配（并非旧 ARCH.md 声称的"NBT 子集匹配"）。新 PDD §3.5 已如实注记该语义；是否更名/升级待调研。迁移时保留现行为，并补单元测试固化
- 每个选择器需配套 `SelectorCodec` 注册到 Registry（新 PDD §9.2），否则无法参与 JSON 序列化

### 2. 数量选择器 (QuantitySelector 体系)

| 旧文件                                                             | 新包路径                               | 迁移方式                       |
| ------------------------------------------------------------------ | -------------------------------------- | ------------------------------ |
| `$OLD/src/client/java/.../model/rule/QuantitySelector.java`        | `api/item/QuantitySelector.java`       | **接口重设计，参考语义后重写** |
| `$OLD/src/client/java/.../model/quantifier/CountSelector.java`     | `impl/quantity/CountSelector.java`     | 参考后改造（签名变更）         |
| `$OLD/src/client/java/.../model/quantifier/GroupSelector.java`     | `impl/quantity/GroupSelector.java`     | 参考后改造（签名变更）         |
| `$OLD/src/client/java/.../model/quantifier/FillSlotsSelector.java` | `impl/quantity/FillSlotsSelector.java` | 参考后改造（签名变更）         |
| `$OLD/src/client/java/.../model/quantifier/PercentSelector.java`   | `impl/quantity/PercentSelector.java`   | 参考后改造（签名变更）         |

**注意事项**：

- **接口已重构**（新 PDD §3.6）：旧的双方法 `computeTargetQuantity(int currentCount, int totalSlots, int maxStackSize)` + `isSatisfied(...)` 废弃，改为单一方法
  `int computeTargetAmount(QuantityContext ctx)`——只算「目标总量」，不再返回 per-slot 分布
- 槽位落位由独立的 **SlotAllocator** 扩展点承担（内置 `FirstFitAllocator`，无旧代码对应，需新写）
- 四个 quantifier 实现按新公式迁移：count→value；group→value×maxStackSize；fill_slots→max(0,slotCount-value)×maxStackSize；percent→slotCount×maxStackSize×value%
- 同样需要配套 codec 注册

### 3. 坐标工具类

| 旧文件                                               | 新包路径                    | 迁移方式                   |
| ---------------------------------------------------- | --------------------------- | -------------------------- |
| `$OLD/src/client/java/.../util/CoordinateUtils.java` | `util/CoordinateUtils.java` | 直接复制，改包名           |
| `$OLD/src/client/java/.../util/Constants.java`       | `util/Constants.java`       | 参考后重写，常量值可能不同 |

**注意事项**：

- `CoordinateUtils.toAbsolute()` / `toRelative()` 逻辑直接复用

### 4. 数据模型类

| 旧文件                                                | 新包路径                           | 迁移方式                                                |
| ----------------------------------------------------- | ---------------------------------- | ------------------------------------------------------- |
| `$OLD/src/client/java/.../model/ContainerType.java`   | `api/container/IOType.java`        | 重命名枚举，去掉 IGNORE 之前的旧值，保留新 PDD 的四个值 |
| `$OLD/src/client/java/.../model/ContainerInfo.java`   | `api/container/ContainerInfo.java` | 参考后重写，结构有变化（新增 cacheType/priority/label） |
| `$OLD/src/client/java/.../model/Warehouse.java`       | `api/warehouse/Warehouse.java`     | 参考后重写，结构有变化（新增 anchor map/rules map）     |
| `$OLD/src/client/java/.../model/rule/ItemRule.java`   | `api/item/ItemRule.java`           | 直接复制，改包名                                        |
| `$OLD/src/client/java/.../model/rule/ItemRules.java`  | `api/item/ContainerRule.java`      | 重命名，结构基本相同                                    |
| `$OLD/src/client/java/.../model/ContainerMemory.java` | `core/cache/ContainerMemory.java`  | 参考后重写                                              |

**注意事项**：

- 字段名映射：`negate → negative`、`quantifier → quantity`（新 PDD §3.4）
- `Warehouse.active` **不迁移**：激活态改为 `WarehouseManager.activeWarehouseId` 运行时持有（旧项目该字段持久化但从未被读取）
- `ContainerInfo` 新增 cacheType/priority/label；priority.soft 为 hard 相同时的次级排序键
- 缓存键从裸 BlockPos 升级为 `(worldId, dimId, canonicalPos [, playerUUID])`，快照排除玩家背包区槽位（新 PDD §3.8）

### 5. 配置持久化 (Gson 适配器)

| 旧文件                                                   | 新包路径                            | 迁移方式                   |
| -------------------------------------------------------- | ----------------------------------- | -------------------------- |
| `$OLD/src/client/java/.../storage/WarehouseStorage.java` | `core/config/WarehouseStorage.java` | 参考后重写，数据模型有变化 |
| `$OLD/src/client/java/.../storage/DataStorage.java`      | `core/config/DataStorage.java`      | 参考后重写                 |

**关键迁移点**：

- Gson 的 `ItemSelectorAdapter` 和 `QuantitySelectorAdapter` 代码可直接复用；但 v0.2 起多态分发表来自 Registry 的 `SelectorCodec` 注册（内置实现同样走注册），不再是硬编码 instanceof 链
- 文件路径从 `mc-warehouse/` 改为 `yuanlu-warehouse/`，且位于标准 `config/` 目录下
- 序列化结构需适配新 PDD 的 JSON 格式（anchors 两级嵌套、pos 相对坐标、schemaVersion 字段）
- `WorldConfigStorage` 的 sp/mp 双通道按服务器地址分层思想已被新 `worlds[worldId].dimensions[dimId]` 两级配置吸收（新 PDD §11.4）；`PathfinderDataStorage` 对应新的 `pathfinders/` 目录（二阶段消费）

## 需要参考后重写的模块

以下模块逻辑可参考，但需要根据新 PDD 重新设计：

### 1. 传输引擎 (TransportEngine)

| 旧文件                                                           | 参考方式                                                                                    |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `$OLD/src/client/java/.../controller/PathfindingController.java` | 参考其状态机逻辑（roundHadAction/roundHadNewExplore 追踪）、容器遍历方式、TransferPlan 生成 |

**差异**：

- 旧项目状态机顺序：OUTPUT → TEMP → INPUT
- 新项目状态机顺序：GET_TEMP → GET_INPUT → PUT_OUTPUT → PUT_TEMP
- 旧项目按距离排序，新项目按硬优先级排序

### 2. 容器交互

| 旧文件                                                                  | 参考方式                         |
| ----------------------------------------------------------------------- | -------------------------------- |
| `$OLD/src/client/java/.../engine/container/ContainerInteractor.java`    | 参考打开/扫描/操作容器的具体实现 |
| `$OLD/src/client/java/.../engine/container/ContainerMemoryManager.java` | 参考内存管理逻辑                 |
| `$OLD/src/client/java/.../engine/container/ContainerScanner.java`       | 参考容器扫描逻辑                 |

**注意事项**：

- `ContainerInteractor` 中的 `clicked()` 调用方式可直接复用
- 新项目需增加缓存类型判断

### 3. 规则引擎

| 旧文件                                                         | 参考方式         |
| -------------------------------------------------------------- | ---------------- |
| `$OLD/src/client/java/.../engine/rule/RuleApplicator.java`     | 参考规则应用逻辑 |
| `$OLD/src/client/java/.../engine/rule/ItemMatcher.java`        | 参考物品匹配逻辑 |
| `$OLD/src/client/java/.../engine/rule/QuantityCalculator.java` | 参考数量计算逻辑 |

### 4. 命令系统

| 旧文件                                                   | 参考方式                         |
| -------------------------------------------------------- | -------------------------------- |
| `$OLD/src/client/java/.../command/WarehouseCommand.java` | 参考命令注册和 Fork 实现         |
| `$OLD/.../command/sub/*.java` (6个文件)                  | 参考各子命令的解析和参数处理逻辑 |

**注意事项**：

- 旧项目使用 `com.mojang.brigadier` 命令框架，新项目同样使用
- 命令名和参数有变化，需按 PDD §8 重新实现

### 5. Mixin

| 旧文件                                                         | 参考方式                          |
| -------------------------------------------------------------- | --------------------------------- |
| `$OLD/src/client/java/.../mixin/ContainerScreenMixin.java`     | 直接复用，改包名和 mixin 配置路径 |
| `$OLD/src/client/java/.../mixin/WorldRendererMixin.java`       | 直接复用，改包名（一阶段不实现）  |
| `$OLD/src/client/java/.../mixin/MultiPlayerGameModeMixin.java` | 直接复用，改包名（一阶段不实现）  |

## 不需要迁移的模块

以下模块在新项目中有不同的设计，不需要迁移：

| 旧模块                               | 原因                                                           |
| ------------------------------------ | -------------------------------------------------------------- |
| `controller/*.java` (6个 Controller) | 新项目采用更扁平的结构，命令直接调用 API，不经过 Controller 层 |
| `engine/highlight/*.java`            | 一阶段不实现高亮                                               |
| `event/WarehouseEventBus.java`       | 新项目有独立的事件总线设计                                     |
| `model/config/*.java`                | 新项目的配置结构不同                                           |
| `storage/WorldConfigStorage.java`    | 合并到统一的 config 管理                                       |
| `storage/ModConfigStorage.java`      | 合并到统一的 config 管理                                       |
| `storage/PathfinderDataStorage.java` | 一阶段不需要                                                   |

## 迁移步骤建议

### 第一阶段：基础设施

1. 复制所有 ItemSelector 和 QuantitySelector 的实现（改包名）
2. 复制 CoordinateUtils（改包名）
3. 复制 ContainerScreenMixin（改包名，调整 mixin 配置路径）
4. 实现 ContainerMemory 和数据模型类

### 第二阶段：核心引擎

1. 实现 WarehouseManager（CRUD）
2. 实现 ContainerInteractor（打开/扫描/操作）
3. 实现 RuleEngine
4. 实现 TransportEngine 状态机

### 第三阶段：命令与集成

1. 实现 /wh 命令系统
2. 实现配置持久化（Gson）
3. 实现事件总线基础版本
4. 集成测试

## 包名变更对照

| 旧包名                                     | 新包名                                          |
| ------------------------------------------ | ----------------------------------------------- |
| `bid.yuanlu.mcwarehouse`                   | `bid.yuanlu.mc.warehouse`                       |
| `bid.yuanlu.mcwarehouse.model`             | `bid.yuanlu.mc.warehouse.api`                   |
| `bid.yuanlu.mcwarehouse.model.rule`        | `bid.yuanlu.mc.warehouse.api.item`              |
| `bid.yuanlu.mcwarehouse.model.selector`    | `bid.yuanlu.mc.warehouse.impl.selector`         |
| `bid.yuanlu.mcwarehouse.model.quantifier`  | `bid.yuanlu.mc.warehouse.impl.quantity`         |
| `bid.yuanlu.mcwarehouse.engine.container`  | `bid.yuanlu.mc.warehouse.core.engine.container` |
| `bid.yuanlu.mcwarehouse.engine.rule`       | `bid.yuanlu.mc.warehouse.core.engine.rule`      |
| `bid.yuanlu.mcwarehouse.engine.pathfinder` | `bid.yuanlu.mc.warehouse.api.navigation`        |
| `bid.yuanlu.mcwarehouse.storage`           | `bid.yuanlu.mc.warehouse.core.config`           |
| `bid.yuanlu.mcwarehouse.command`           | `bid.yuanlu.mc.warehouse.command`               |
| `bid.yuanlu.mcwarehouse.controller`        | （不迁移）                                      |
| `bid.yuanlu.mcwarehouse.mixin`             | `bid.yuanlu.mc.warehouse.mixin`                 |
| `bid.yuanlu.mcwarehouse.util`              | `bid.yuanlu.mc.warehouse.util`                  |
| `bid.yuanlu.mcwarehouse.event`             | `bid.yuanlu.mc.warehouse.core.event`            |

## 验证

迁移完成后，运行以下命令验证：

```bash
cd $NEW
./gradlew build                    # 编译 + JUnit + JAR
./gradlew runClient                # 启动 Minecraft 手动测试
```
