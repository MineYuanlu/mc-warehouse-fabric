# 寻路系统 (Navigation)

> 原 PDD §7。章节号保持不变——代码注释中的「PDD §7.x」引用按本文件定位。

## 7.1 接口

```java
interface Navigator {
    String id();
    PathResult start(Goal goal);       // 开始寻路；单目标，内部不维护目标队列
    PathStatus tick();                 // 每 tick 更新状态，由引擎在游戏 tick 中调用
    void cancel();
}

PathResult {
    boolean success;
    String messageKey;                 // 给玩家的提示（i18n key）
}

enum PathStatus {
    MOVING,      // 正在移动中
    ARRIVED,     // 已到达目标
    FAILED,      // 寻路失败（无法到达）
    CANCELLED    // 被取消
}

Goal {
    WorldDimPos target;                // 目标坐标（跨维度：见下）
    double acceptableDistance;         // 可接受的距离（格数）
    @Nullable Direction faceHint;      // 到达后的朝向建议（引擎按方块中心计算传入，供开容器用）
}
```

- **跨维度语义**：target 含 WorldDim，Navigator 必须自行处理维度切换（如经传送门）；NoOp 实现直接提示玩家自行前往
- **重试协议归引擎**：FAILED 后由引擎决定重试（同一 Goal 最多 navRetryMax 次，每次重新调用 start()）；Navigator 不做内部重试、不持有目标队列——避免「控制器索引 ↔ 执行器内部队列」双账本脱钩（design-decisions.md §15.10）

## 7.2 默认实现： NoOpNavigator

- `start()`: 输出 i18n 提示「请前往: (worldId, dim, x, y, z)」，返回 `PathResult(success=true)`
- `tick()`: 检测玩家位置，同维度且距目标 < acceptableDistance 返回 `ARRIVED`，否则返回 `MOVING`
- 由玩家自行前往目标，引擎不做任何自动移动

## 7.3 扩展寻路器（未内置，经 Registry 注册）

| 寻路器                 | 方式       | 说明                               |
| ---------------------- | ---------- | ---------------------------------- |
| SimpleWalkExecutor     | 走路       | 模拟 WASD 按键，向目标移动         |
| CreativeFlightExecutor | 创造飞行   | 在创造模式下直接飞行               |
| PortalExecutor         | 地狱门交通 | 利用地狱门缩短距离                 |
| ElytraExecutor         | 鞘翅飞行   | 使用烟花火箭推进                   |
| CommandExecutor        | 指令传送   | 使用 `/tp` 指令（需 OP 权限）      |
| HybridExecutor         | 组合       | 自动检测游戏模式，选择最佳寻路方式 |

**寻路配置持久化**（消费方为实现上述扩展时的配置读取，格式先行预留）：`config/yuanlu-warehouse/pathfinders/<id>.json`

```json
{
  "type": "hybrid",
  "allowFlight": true,
  "allowPortal": true,
  "warpCommands": ["/tp", "/home"],
  "preferredRoutes": [
    { "fromDim": "minecraft:overworld", "toDim": "minecraft:the_nether" }
  ]
}
```

运行时覆盖：`/wh start --pathfinder <id>`；默认取 `servers[server].dimensions[dim].pathfinder`（configuration.md §11.4）。

寻路模块设计为独立库，可被其他 mod 复用。
