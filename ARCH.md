# MC Warehouse 架构设计文档

## 一、总体架构

### 1.1 分层架构

采用 MVC 模式完全分层，命令与未来 UI 共享 Controller 层：

```
┌─────────────────────────────────────────────────┐
│                    View 层                        │
│  ┌──────────────┐  ┌──────────────────────────┐  │
│  │  Command/CMD  │  │  UI (未来实现)           │  │
│  │  /warehouse   │  │  Screen / HUD / Overlay  │  │
│  │  /wh          │  │                          │  │
│  └──────┬───────┘  └──────────┬───────────────┘  │
├─────────┼─────────────────────┼───────────────────┤
│         └─────────┬───────────┘                   │
│                   ▼                               │
│  ┌──────────────────────────────────────────────┐ │
│  │              Controller 层                    │ │
│  │  SelectionController / RuleController /       │ │
│  │  WarehouseController / PathfindingController  │ │
│  │  ContainerController / HighlightController    │ │
│  └──────────────────────┬───────────────────────┘ │
├─────────────────────────┼─────────────────────────┤
│                         ▼                         │
│  ┌──────────────────────────────────────────────┐ │
│  │              Service / Engine 层              │ │
│  │  RuleEngine / PathExecutor / ContainerMemory  │ │
│  │  ContainerInteractor / HighlightManager       │ │
│  └──────────────────────┬───────────────────────┘ │
├─────────────────────────┼─────────────────────────┤
│                         ▼                         │
│  ┌──────────────────────────────────────────────┐ │
│  │              Data / Storage 层                │ │
│  │  DataStorage / WarehouseStorage / WorldConfig │ │
│  │  JSON Serializer / File I/O                   │ │
│  └──────────────────────────────────────────────┘ │
├───────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────┐ │
│  │              Mixin 层                         │ │
│  │  ContainerScreenMixin / WorldRenderMixin     │ │
│  │  MultiPlayerGameModeMixin / MinecraftMixin    │ │
│  └──────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

### 1.2 包结构

```
bid.yuanlu.mcwarehouse
├── MCWarehouseClient.java              # Mod 入口
│
├── controller                          # Controller 层 (命令/UI 共享)
│   ├── SelectionController.java        # 区域选择
│   ├── RuleController.java             # 物品规则 CRUD
│   ├── WarehouseController.java        # 仓库管理（激活/显示）
│   ├── PathfindingController.java      # 寻路/搬运流程编排
│   └── ContainerController.java        # 容器记忆/交互
│
├── command                             # View: 命令层
│   ├── WarehouseCommand.java           # /warehouse 命令入口注册
│   └── sub/
│       ├── SelectSubCommand.java       # /warehouse select
│       ├── RuleSubCommand.java         # /warehouse rule
│       ├── ContainerSubCommand.java    # /warehouse container
│       ├── RunSubCommand.java          # /warehouse run
│       ├── HighlightSubCommand.java    # /warehouse highlight
│       └── ConfigSubCommand.java       # /warehouse config
│
├── engine                              # Service/Engine 层
│   ├── rule
│   │   ├── ItemMatcher.java            # 匹配引擎
│   │   ├── QuantityCalculator.java     # 数量计算引擎
│   │   └── RuleApplicator.java         # 规则应用引擎（计算哪些物品该放/该拿）
│   ├── container
│   │   ├── ContainerInteractor.java    # 容器GUI自动化操作
│   │   ├── ContainerMemoryManager.java # 容器记忆管理（纯内存）
│   │   └── ContainerScanner.java       # 扫描区域内容器
│   ├── pathfinder
│   │   ├── PathExecutor.java           # 路径执行器接口
│   │   └── executors/
│   │       ├── SimpleWalkExecutor.java   # 步行寻路
│   │       ├── CreativeFlightExecutor.java # 创造飞行
│   │       ├── PortalExecutor.java      # 跨维度传送门
│   │       └── HybridExecutor.java      # 可配置的复合执行器
│   └── highlight
│       ├── HighlightManager.java       # 高亮管理器
│       └── ContainerOutlineRenderer.java # WorldRenderer 渲染
│
├── model                               # 数据模型
│   ├── Warehouse.java                  # 仓库定义
│   ├── ContainerInfo.java              # 容器信息
│   ├── ContainerType.java              # INPUT / OUTPUT / RELAY / IGNORE
│   ├── ContainerMemory.java            # 容器内容记忆
│   ├── rule
│   │   ├── ItemRule.java               # 单条规则 = Selector + Quantifier
│   │   ├── ItemRules.java              # 命名规则组，包含多个 ItemRule
│   │   ├── ItemSelector.java           # 物品选择器（接口）
│   │   └── QuantitySelector.java       # 数量选择器（接口）
│   ├── selector
│   │   ├── IdSelector.java
│   │   ├── NbtSelector.java
│   │   ├── NameSelector.java
│   │   ├── TagSelector.java
│   │   └── CompositeSelector.java
│   ├── quantifier
│   │   ├── CountSelector.java          # 精确个数
│   │   ├── GroupSelector.java          # N 组
│   │   ├── FillSlotsSelector.java      # 填充到剩余N格
│   │   └── PercentSelector.java        # 百分比
│   └── config
│       ├── WorldConfig.java            # 世界配置
│       └── PathfinderConfig.java       # 寻路器配置
│
├── storage                             # 持久化
│   ├── DataStorage.java                # 存储接口
│   ├── WarehouseStorage.java           # 仓库数据读写
│   ├── WorldConfigStorage.java         # world.json 读写
│   └── PathfinderDataStorage.java      # 路径数据读写
│
├── mixin                               # Mixin 注入
│   ├── ContainerScreenMixin.java       # 容器 GUI 操作
│   ├── WorldRendererMixin.java         # 高亮渲染
│   └── MultiPlayerGameModeMixin.java   # 交互发包
│
└── util
    ├── CoordinateUtils.java            # 坐标转换（相对↔绝对）
    ├── Constants.java                  # 常量
    └── CommandUtils.java               # 命令工具
```

---

## 二、数据模型

### 2.1 仓库定义 (Warehouse)

```java
public class Warehouse {
    String name;                        // 仓库名称（唯一标识）
    BlockPos anchor;                    // 基准点（绝对坐标）
    boolean active;                     // 是否激活（用于搬运）
    List<ContainerInfo> containers;     // 容器列表
}
```

### 2.2 容器信息 (ContainerInfo)

```java
public class ContainerInfo {
    BlockPos relativePos;               // 相对 anchor 的偏移
    ContainerType type;                 // INPUT | OUTPUT | RELAY | IGNORE
    RuleMode mode;                      // WHITELIST | BLACKLIST
    List<String> rulesNames;            // 引用的 ItemRules 名称列表
}
```

- **INPUT（搬出容器/输入端）**：只能从中取出物品，按规则尽可能清空
- **OUTPUT（搬入容器/输出端）**：只能放入物品，按规则尽可能填满
- **RELAY（中继容器/暂存端）**：可存可取，倾向清空并送往 OUTPUT
- **IGNORE（忽略）**：被标记但不参与搬运

**默认 RuleMode**：以容器类型自动推断
- INPUT → `BLACKLIST`（规则外的物品也能取出，尽可能清空）
- OUTPUT → `WHITELIST`（只放入规则允许的物品）
- RELAY → `BLACKLIST`（规则外的物品也能处理，倾向清空）

### 2.3 物品规则体系

```
ItemRules (命名规则组)
├── name: String
└── rules: List<ItemRule>

ItemRule (单条规则)
├── selector: ItemSelector    # 匹配什么物品
├── negate: boolean           # 反转匹配结果（默认 false）
└── quantifier: QuantitySelector  # 控制数量/容量
```

#### ItemSelector 接口

```java
public interface ItemSelector {
    boolean matches(ItemStack stack);
}
```

内置实现：

| 选择器 | JSON 示例 | 匹配逻辑 |
|--------|-----------|----------|
| `IdSelector` | `{"type":"id","value":"minecraft:diamond"}` | 物品 ID 精确匹配 |
| `TagSelector` | `{"type":"tag","value":"minecraft:logs"}` | 物品标签匹配 |
| `NameSelector` | `{"type":"name","value":"钻石","fuzzy":true}` | 显示名称模糊/精确匹配 |
| `NbtSelector` | `{"type":"nbt","value":"{...}"}` | NBT 子集匹配 |
| `CompositeSelector` | `{"type":"composite","op":"AND","selectors":[...]}` | AND/OR/NOT 组合 |

#### QuantitySelector 接口

```java
public interface QuantitySelector {
    /**
     * 计算匹配该 selector 的物品在容器中应保留的目标数量
     * @param currentCount  当前容器中该物品的数量
     * @param totalSlots    容器总槽位数
     * @param maxStackSize  该物品的最大堆叠数
     * @return 应保留的目标数量（调用方计算 delta = target - current，正数需放入，负数需取出）
     */
    int computeTargetQuantity(int currentCount, int totalSlots, int maxStackSize);
    
    /**
     * 判断当前数量是否已满足规则要求
     */
    boolean isSatisfied(int currentCount, int totalSlots, int maxStackSize);
}
```

内置实现：

| 选择器 | JSON 示例 | 含义 |
|--------|-----------|------|
| `CountSelector` | `{"type":"count","value":64}` | 保持该物品不少于/不超过 64 个 |
| `GroupSelector` | `{"type":"group","value":3}` | 保持 3 组（<物品最大堆叠数> * 3） |
| `FillSlotsSelector` | `{"type":"fill_slots","value":5}` | 填充到只剩 5 个空格 |
| `PercentSelector` | `{"type":"percent","value":75}` | 填充到容器 75% 容量 |

### 2.4 容器记忆 (ContainerMemory)

纯内存存储，退出游戏即消失。每次打开容器时刷新。

```java
public class ContainerMemory {
    Map<BlockPos, ContainerSnapshot> snapshots;
    
    // 打开容器时拍照
    void snapshot(BlockPos pos, ContainerSnapshot data);
    
    // 清空记忆
    void clear();
    void clear(BlockPos pos);
    
    // 查询
    @Nullable ContainerSnapshot get(BlockPos pos);
}
```

---

## 三、存储设计

### 3.1 目录结构

```
游戏运行目录/
└── mc-warehouse/
    ├── config/
    │   ├── worlds.json                # 世界配置（全局）
    │   └── mod.json                   # 模组全局配置
    ├── warehouses/
    │   ├── <name>/
    │   │   └── data.json              # 仓库定义（容器 + 规则定义合并）
    │   └── ...
    └── pathfinder/
        ├── simple_walk/
        │   └── default.json
        ├── hybrid/
        │   └── nether_highway.json
        └── ...
```

### 3.2 worlds.json 结构

```json
{
  "sp": {
    "<world_uuid>": {
      "dimensions": {
        "<dimension_id>": {
          "warehouses": {
            "<warehouse_name>": { "enable": true }
          }
        }
      },
      "interaction": { "speed": 2 },
      "pathfinders": ["hybrid", "simple_walk"]
    }
  },
  "mp": {}
}
```

层级路径说明：
- `sp.<world_id>.dimensions.<dimension_id>.warehouses.<name>` — 维度级仓库开关
- `sp.<world_id>.interaction` — 世界级交互配置
- `sp.<world_id>.pathfinders` — **世界级**寻路算法（非维度级）

### 3.3 data.json 结构（合并 containers + rules）

```json
{
  "name": "main_base",
  "anchor": { "x": 100, "y": 64, "z": 200 },
  "containers": [
    {
      "pos": { "x": 5, "y": 0, "z": 3 },
      "type": "OUTPUT",
      "mode": "WHITELIST",
      "rules": ["wool_x64", "common_tools"]
    }
  ],
  "rules": [
    {
      "name": "wool_x64",
      "items": [
        {
          "selector": { "type": "tag", "value": "minecraft:wool" },
          "quantifier": { "type": "group", "value": 64 }
        }
      ]
    },
    {
      "name": "common_tools",
      "items": [
        {
          "selector": { "type": "id", "value": "minecraft:pickaxe" },
          "quantifier": { "type": "fill_slots", "value": 2 }
        }
      ]
    }
  ]
}
```

---

## 四、核心系统设计

### 4.1 路径执行器 (PathExecutor)

#### 接口定义

```java
public interface PathExecutor {
    /** 设置目标点集合 */
    void setTargets(Collection<Vec3> targets);
    
    /** 每帧调用，返回当前状态 */
    Status tick();
    
    /** 获取已到达的目标点（消费式） */
    @Nullable Vec3 pollArrived();
    
    /** 是否还有待前往的目标点 */
    boolean hasRemaining();
    
    /** 重置执行器 */
    void reset();
    
    enum Status { MOVING, ARRIVED, FAILED, DONE }
}
```

- `MOVING`：正在行进中（上一步执行成功但尚未到达）
- `ARRIVED`：到达了一个目标点（调用方需通过 `pollArrived()` 获取并开始容器交互）
- `FAILED`：无法继续前进（超时/卡住/路径不可达）
- `DONE`：所有目标点均已到达，任务完成

#### 外部编排流程

```
1. Controller.setTargets(容器坐标列表)
2. Controller 进入主循环（每 tick 调用一次）:
   a. status = executor.tick()
   b. 如果 MOVING → 等待下一 tick
   c. 如果 ARRIVED:
      - arrived = executor.pollArrived()
      - 开启容器交互（ContainerInteractor）
      - 交互完成后，继续下一 tick
   d. 如果 FAILED:
      - 重试次数 < 最大重试 → 重新 setTargets 后继续
      - 超过重试次数 → 提示用户并中止
   e. 如果 DONE → 结束
3. 所有目标点均完成 → 结束
```

#### 执行器类型

| 执行器 | 说明 | 配置数据位置 |
|--------|------|-------------|
| `SimpleWalkExecutor` | 单纯行走/跳跃，适合生存模式 | `pathfinder/simple_walk/default.json` |
| `CreativeFlightExecutor` | 创造模式飞行寻路 | `pathfinder/creative_fly/default.json` |
| `PortalExecutor` | 跨维度传送门寻路 | `pathfinder/portal/default.json` |
| `HybridExecutor` | 可配置的组合执行器，支持多种移动方式+固定路线+命令传送 | `pathfinder/hybrid/<name>.json` |

`HybridExecutor` 的配置示例：

```json
{
  "type": "hybrid",
  "allowFlight": true,
  "allowPortal": true,
  "warpCommands": ["/warp hub", "/tpa <player>"],
  "preferredRoutes": [
    {
      "from": "overworld",
      "to": "nether",
      "path": "pathfinder/routes/overworld_to_nether.json"
    }
  ]
}
```

### 4.2 容器交互 (ContainerInteractor)

#### Mixin 驱动的 GUI 自动化

```java
public class ContainerInteractor {
    /** 交互速度 (tick) */
    int speed;
    
    /** 对一个容器执行搬运（调用方在 ARRIVED 后调用） */
    void execute(ContainerInfo info, ContainerSnapshot actual);
    
    /** 计算输入/输出方案 */
    TransferPlan calculatePlan(ContainerInfo info, ContainerSnapshot actual);
    
    /** 执行具体的物品移动（通过 Mixin 操作 GUI 槽位） */
    void applyPlan(TransferPlan plan);
}
```

**搬运逻辑：**

1. 打开容器后，对比记忆快照与当前内容的差异（如果有新变化则更新记忆）
2. 根据容器类型和规则计算 TransferPlan：
   - **INPUT（默认黑名单）**：遍历容器内物品，匹配规则 → 规则匹配的按 quantifier 保留，其余全部取出
   - **OUTPUT（默认白名单）**：遍历规则 → 检查缺失物品 → 从玩家背包中放入规则允许的物品
   - **RELAY（默认黑名单）**：双向计算，匹配规则的按 quantifier 保留，其余全部清出，再按规则从背包补入
3. 交互速度由 `config.worlds.interaction.speed` 控制

### 4.3 高亮系统 (HighlightManager)

通过 WorldRenderer Mixin 实现 BlockPos 级别的轮廓渲染。

```java
public class HighlightManager {
    // 添加高亮
    void addHighlight(BlockPos pos, HighlightType type);
    
    // 批量设置（仓库所有容器）
    void setWarehouseHighlights(Warehouse warehouse);
    
    // 清空高亮
    void clearAll();
    
    enum HighlightType {
        INPUT_OUTLINED,    // 输入端颜色
        OUTPUT_OUTLINED,   // 输出端颜色
        RELAY_OUTLINED,    // 暂存端颜色
        IGNORE_OUTLINED,   // 忽略颜色
        HAS_SPACE,         // 有空间
        FULL,              // 已满
        UNKNOWN            // 未知内容
    }
}
```

### 4.4 容器记忆管理 (ContainerMemoryManager)

```java
public class ContainerMemoryManager {
    /** 记录容器快照 */
    void snapshot(BlockPos pos, ContainerSnapshot data);
    
    /** 获取快照 */
    @Nullable ContainerSnapshot get(BlockPos pos);
    
    /** 清空 */
    void clearAll();
    void clear(BlockPos pos);
}
```

---

## 五、命令体系

### 5.1 命令入口

注册 `/warehouse` 和 `/wh` 两个命令别名，指向同一套逻辑。

### 5.2 命令树

```
/warehouse
├── select
│   ├── pos1                    # 设置选择区域第一点/第二点
│   ├── pos2
│   ├── expand <n>              # 向某个方向扩展
│   └── show                    # 显示/隐藏选区
│
├── container
│   ├── add [type] [rules...]   # 添加当前选择区域的容器
│   ├── remove                  # 移除选择容器
│   ├── list                    # 列出所有标记
│   ├── type <type>             # 切换容器类型
│   ├── mode <mode>             # 切换白名单/黑名单
│   ├── info                    # 查看容器详情
│   └── memory
│       ├── show                # 查看记忆
│       └── clear               # 清空记忆
│
├── rule
│   ├── list                    # 列出所有规则组
│   ├── create <name>           # 创建规则组
│   ├── delete <name>           # 删除规则组
│   ├── add <name>              # 为规则组添加规则
│   ├── remove <name> <index>   # 删除规则组内规则
│   ├── edit <name> <index>     # 修改规则
│   └── show <name>             # 显示规则组详情
│
├── warehouse
│   ├── create <name>           # 创建仓库
│   ├── delete <name>           # 删除仓库
│   ├── list                    # 列出仓库
│   ├── activate <name>         # 激活仓库（用于搬运）
│   ├── deactivate              # 取消激活
│   ├── show                    # 高亮显示仓库所有容器
│   └── hide                    # 隐藏高亮
│
├── run                         # 开始搬运（单次执行）
│   └── [--pathfinder <type>]   # 指定寻路方式
│
├── config
│   ├── show                    # 查看配置
│   ├── set <key> <value>       # 设置配置
│   └── reload                  # 重载配置文件
│
└── help                        # 帮助
```

### 5.3 命令层与 Controller 层的边界

命令层只做：
- 参数解析与校验
- Controller 方法调用
- 结果展示（反馈给玩家）

Controller 层承担所有业务逻辑：
- 不与 Minecraft 玩家直接交互
- 返回结构化结果
- 未来 UI 层直接调用相同 Controller

---

## 六、搬运流程（核心数据流）

### 6.1 一次完整的「自动搬运」

```
用户执行 /warehouse run
    │
    ▼
PathfindingController
    │ 1. 获取当前激活仓库的容器列表
    │ 2. 筛选非 IGNORE 类型的容器
    │ 3. 按容器类型排序优先级：
    │    OUTPUT → INPUT → RELAY
    │    （先清空背包放输出端，再从输入端取物，最后中继端中转分发）
    │ 4. 将坐标送入 PathExecutor.setTargets()
    │
    ▼
PathExecutor.tick() 循环
    │
    ├── ARRIVED ──→ ContainerInteractor
    │                   │ 读取容器记忆 + 规则
    │                   │ 计算 TransferPlan
    │                   │ 打开容器 GUI 执行
    │                   │ 交互完成后 → snapshot()
    │                   └──→ 继续 tick 前往下个目标
    │
    ├── FAILED ──→ 重试逻辑
    │
    └── DONE ────→ 提示用户搬运完成
```

### 6.2 TransferPlan 计算

对于一个容器，TransferPlan 包含两组操作：

```
输入容器 (INPUT) ：
    规则模式: BLACKLIST（默认）
    操作: 遍历物品 → 匹配规则 → 规则内物品按 quantifier 保留，多余的取出
          （默认黑名单意味着没有规则限制的物品全部取出，尽可能清空）

输出容器 (OUTPUT)：
    规则模式: WHITELIST（默认）
    操作: 遍历规则 → 检查缺失数量 → 从背包取出规则允许的物品放入
          （默认白名单意味着没有规则允许的东西不会放入）

暂存容器 (RELAY)：
    规则模式: BLACKLIST（默认）
    操作: 双向计算
      1. 先移除不符合规则/超额物品到背包
      2. 再根据规则补入（从背包）
      默认黑名单意味着：规则匹配的物品按 quantifier 保留，其余全部清出
```

---

## 七、UI 接口预留

### 7.1 Controller 层接口

所有 Controller 方法设计为 UI 友好形式：

```java
public class WarehouseController {
    /** 返回结构化数据，而非直接发送消息 */
    List<WarehouseInfo> listWarehouses();
    Result activateWarehouse(String name);
    WarehouseDetail getDetail(String name);
}

public class RuleController {
    List<ItemRulesInfo> listRules();
    ItemRulesDetail getRuleDetail(String name);
    Result createRule(String name);
    Result addRuleItem(String name, ItemSelector selector, QuantitySelector quantifier);
}
```

### 7.2 事件系统（为 UI 准备的观察者模式）

```java
public interface WarehouseEventBus {
    // 仓库状态变更
    void onWarehouseActivated(Warehouse warehouse);
    void onWarehouseDeactivated();
    
    // 搬运进度
    void onTransferProgress(ContainerInfo current, TransferPlan plan);
    void onTransferComplete(Warehouse warehouse);
    void onTransferError(String message);
    
    // 容器记忆更新
    void onContainerMemoryUpdated(BlockPos pos, ContainerSnapshot snapshot);
}
```

未来 UI 层只需监听 EventBus 即可实时响应状态变化。

---

## 八、关键技术决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 模组加载器 | Fabric | 已确定，使用 26.1+ 无混淆映射 |
| 数据序列化 | Gson | 轻量，适配无混淆环境 |
| 配置格式 | JSON | 人类可读，Fabric 生态原生兼容 |
| 高亮方式 | WorldRenderer Mixin | 纯客户端，性能可控 |
| 容器操作 | ContainerScreen Mixin | 纯客户端模拟用户操作 |
| 坐标系统 | 相对坐标 + 基准点 | 方便整体平移，复用仓库配置 |
| 规则匹配 | 单方法接口 + 策略选择器 | 简洁，易扩展 |
| 寻路 | Executor 模式 + 状态机 | 支持多种移动方式，可扩展 |

---

## 九、里程碑建议

| 阶段 | 内容 | 产出 |
|------|------|------|
| **M1** | 数据模型 + 存储层 | 可创建/编辑/保存仓库和规则 |
| **M2** | 命令系统 + Controller | 完整的 `/warehouse` 命令 |
| **M3** | 容器交互 + 记忆 | 可执行容器内物品搬运 |
| **M4** | 高亮渲染 | 容器可视化 |
| **M5** | 路径执行器 | 完整的自动搬运流程 |
| **M6** | 优化 + UI 接口 | EventBus + 为 UI 做好准备 |
