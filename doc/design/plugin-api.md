# 插件系统 (Plugin API)

> 原 PDD §9 + UI-PDD §11（插件 UI 预留位并入本章）。章节号保持不变——代码注释中的「PDD §9.x」引用按本文件定位。

## 9.1 注册入口

```java
interface WarehousePlugin {
    void register(WarehouseRegistry registry);
}
```

通过 Fabric 的 `entrypoint` 机制加载：

```json
// fabric.mod.json (附属mod)
"entrypoints": {
    "warehouse-plugin": ["com.example.MyWarehousePlugin"]
}
```

## 9.2 WarehouseRegistry — 接口本体

```java
public interface WarehouseRegistry {
    // ---- 能力实现注册（重复 id 抛 IllegalArgumentException，加载期快速失败）----
    void registerDetector(ContainerDetector detector);
    void registerNavigator(Navigator navigator);
    void registerWorldIdentifier(WorldIdentifier identifier);
    void registerInteraction(ContainerInteraction interaction);
    void registerSlotAllocator(SlotAllocator allocator);
    void registerAgentPlanner(AgentPlanner planner);

    // ---- 序列化 codec 注册：解决「插件自定义 selector 无法持久化」----
    void registerItemSelectorCodec(SelectorCodec<? extends ItemSelector> codec);
    void registerQuantitySelectorCodec(SelectorCodec<? extends QuantitySelector> codec);
}

/** JSON 多态编解码：type 即 JSON "type" 字段值，全局唯一 */
public interface SelectorCodec<T> {
    String type();
    JsonObject toJson(T value);
    T fromJson(JsonObject json);
}
```

- **注册时机**：插件 entrypoint 调用期间（客户端初始化线程），早于任何功能使用；此后注册表冻结，运行期注册一律拒绝
- 内置实现由模组自身经同一机制注册（自食其力，保证 API 完备性）
- Gson TypeAdapter 按 codec 注册表分发（configuration.md §11.2）

## 9.3 扩展点总表

| 扩展点     | 接口                        | 说明                                                  |
| ---------- | --------------------------- | ----------------------------------------------------- |
| 容器检测器 | `ContainerDetector`         | 识别新类型容器（如 AE 存储网络、其他 mod 的特殊容器） |
| 交互方式   | `ContainerInteraction`      | 非 GUI 的物品搬移通道                                 |
| 物品选择器 | `ItemSelector` (+codec)     | 新的物品匹配方式                                      |
| 数量选择器 | `QuantitySelector` (+codec) | 新的数量控制方式                                      |
| 槽位分配器 | `SlotAllocator`             | 槽位落位策略                                          |
| 寻路器     | `Navigator`                 | 新的寻路算法                                          |
| 世界标识器 | `WorldIdentifier`           | 服务器内 world 划分的识别手段（服务端推送为内置实现） |
| 仓库规划器 | `AgentPlanner`              | AI/规则引擎自动规划仓库配置                           |
| 事件订阅   | `WarehouseEvents.*`         | 监听引擎状态/数据变化（transport-engine.md §5.9）     |
| HUD 区块（v1 预留） | `registerHudElement(HudWidgetFactory)` | 插件向 HUD 注册自定义区块（与内置区块同等渲染纪律）；前置条件：L1 稳定 + 元素树可序列化 |
| 屏幕扩展（v1 预留） | `registerScreenContributor` | 插件向主屏追加页签/详情侧栏区块；前置条件同上 + 布局契约冻结 |

> 两个 v1 预留注册点暂只定义不实现（UI 引擎现为内部实现，未进 `api/`）。预留期间插件若需要 UI 反馈，继续用聊天栏（`WarehouseEvents.ERROR` 桥）。

## 9.4 AgentPlanner 接口

```java
interface AgentPlanner {
    String id();
    void plan(Warehouse warehouse, PlanningContext context);
    // 可增删改查仓库所有配置：物品选择、容器设置、寻路设置等
}
```

LLM API 作为一种潜在内置实现（未实现）：密钥等敏感配置独立文件、调用异步化、默认关闭；附属 mod 也可通过此接口接入规则引擎等。

## 9.5 规则引擎 (Rule Engine)

规则引擎不属于"插件"，而是传输引擎的核心子模块，但附属 mod 可以通过注册新的 `ItemSelector`/`QuantitySelector` 来扩展规则能力。

**核心职责**：

```
输入：容器快照(ContainerSnapshot) + 容器规则(ContainerRule[]) + 玩家背包内容
输出：TransferPlan（包含所有可执行的操作）

处理流程：
  1. 对于容器中的每个槽位：
     a. 按容器 rules 数组顺序遍历关联的 ContainerRule 及其 ItemRule[]
        （首条命中生效，data-model.md §3.7）
     b. 用 ItemSelector.matches() 判断槽位物品是否匹配
     c. 匹配后，用 QuantitySelector.computeTargetAmount() 计算目标总量
     d. delta = target - current 确定方向与数量
  2. 放入方向的 delta 经 SlotAllocator 分配到具体 canPutTo 槽位，
     并受容器剩余空间约束；取出方向同理分配 canTakeFrom 槽位
  3. 对于反选规则（negative=true），先正常匹配再取反结果
  4. 合并所有结果，生成 TransferPlan
```

**TEMP 容器的特殊规则**：

TEMP 容器同时参与取出和放入两个阶段，其行为取决于当前阶段：

- `GET_TEMP` 阶段：按 transport-engine.md §5.3 双策略取出（保守/精确模式）
- `PUT_TEMP` 阶段：放入背包中所有无法放入 OUTPUT 的多余物品
