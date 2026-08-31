# 核心数据模型

> 原 PDD §3。章节号保持不变——代码注释中的「PDD §3.x」引用按本文件定位。

## 3.1 仓库 (Warehouse)

```java
Warehouse {
    id: String                          // 唯一标识
    anchors: Map<WorldDim, Pos>         // 每个 (worldId, dimId) 的基准点（WorldDim 见 world-identity.md §4.5，JSON 嵌套结构见 configuration.md §11.3）
    containers: List<ContainerInfo>
    rules: Map<String, ContainerRule>   // 本仓库内定义的规则（key = ruleId）
}
```

- 仓库可跨 world、跨维度，每个 `(serverId, worldName, dimId)` 有一个基准点；服务器维度是会话相对的（运行时按当前 serverId 解析，天然跨游戏复用）
- 容器的 pos 存为**相对坐标**（相对于同 `(world,dim)` 的 anchor），便于整体偏移
- `rules` 字段使得规则可以在仓库级别定义，也可在全局共享（见 configuration.md §11）；全局与内嵌规则 id 冲突 = 加载报错拒载（configuration.md §11.3）
- **激活态不入仓**：`active` 字段不持久化。激活态是运行时概念，由 `WarehouseManager.activeWarehouseId` 内存持有（见 design-decisions.md §15.2）

## 3.2 容器信息 (ContainerInfo)

```java
ContainerInfo {
    pos: WorldDimPos[]            // (worldName, dimId, x, y, z)，支持多格容器；
                                  // pos[0] 为 canonical pos（缓存键与日志的主标识）
    ioType: IOType                // INPUT | OUTPUT | TEMP | IGNORE
    ruleMode: RuleMode            // WHITELIST | BLACKLIST
    rules: String[]               // 引用的 ContainerRule id
    cacheType: CacheType          // NONE | MEMORY | DISK
    priority: Priority            // hard：主排序键（降序遍历）；soft：hard 相同时的次级排序键
    label: String                 // 可选标签，用于高亮显示和日志
}
```

**IOType 枚举**：

| 类型   | 含义                                        | 取出 | 放入 | 默认 ruleMode |
| ------ | ------------------------------------------- | ---- | ---- | ------------- |
| INPUT  | 输入容器（物资产出点）                      | ✅   | ❌   | BLACKLIST     |
| OUTPUT | 输出容器（存储/机器输入）                   | ❌   | ✅   | WHITELIST     |
| TEMP   | 中转容器                                    | ✅   | ✅   | BLACKLIST     |
| IGNORE | 跳过（不出现在搬运流程中，仅用于高亮/标记） | ❌   | ❌   | —             |

IOType 是**封闭枚举**（有意决策）：它是用户意图标注；容器的真实物理能力由 Detector 的槽位能力模型表达（container-detection.md §8.2），二者正交。

**多格容器关联机制**：一个 `ContainerInfo.pos` 可包含多个坐标，这些坐标被视为**同一个逻辑容器**的多格（如大箱子 2 格、用户选中的多个末影箱关联为同一容器）。识别器（`ContainerDetector.resolveMultiBlock`）负责自动检测多格关系；用户也可手动将多个坐标归入同一个 `ContainerInfo.pos` 数组。搬运时，引擎会依次打开每个坐标的 UI 并操作，但逻辑上视为同一个容器（共享缓存、共享规则判定），缓存以 canonical pos（pos[0]）为键。

## 3.3 容器规则 (ContainerRule)

```java
ContainerRule {
    id: String                    // 唯一标识
    itemRules: ItemRule[]         // 物品规则列表（有序）
}
```

## 3.4 物品规则 (ItemRule)

```java
ItemRule {
    selector: ItemSelector        // 匹配物品
    negative: boolean             // 反选
    quantity: QuantitySelector    // 数量控制
}
```

## 3.5 物品选择器 (ItemSelector) — 接口

```java
public interface ItemSelector {
    /** 判断给定的物品堆是否匹配此选择器（只看物品本身与组件，不看数量） */
    boolean matches(ItemStack stack);
}
```

| 内置实现          | JSON 示例                                           | 匹配逻辑              |
| ----------------- | --------------------------------------------------- | --------------------- |
| IdSelector        | `{"type":"id","value":"minecraft:diamond"}`         | 物品 ID 精确匹配      |
| TagSelector       | `{"type":"tag","value":"minecraft:logs"}`           | 物品标签匹配          |
| NameSelector      | `{"type":"name","value":"钻石","fuzzy":true}`       | 显示名称模糊/精确匹配 |
| NbtSelector       | `{"type":"nbt","value":"{...}"}`                    | 见下方注记            |
| CompositeSelector | `{"type":"composite","op":"AND","selectors":[...]}` | AND/OR/NOT 组合       |

**CompositeSelector 的 op 取值**：`AND`（全部匹配）、`OR`（任一匹配）、`NOT`（全部不匹配，即取反嵌套）。

> **NbtSelector 注记**：名称沿用习惯叫法。MC 1.20.5+ 物品数据已是 DataComponents，26.x 无 NBT；其实际语义为**组件序列化文本包含匹配**（`toString().contains(value)`，并非「NBT 子集匹配」）。是否更名（如 ComponentMatchSelector）及升级为结构化组件子集匹配，见 design-decisions.md §15.11 待定项。插件作者请勿依赖其内部表示形式。

## 3.6 数量选择器与槽位分配

数量选择器只回答「目标总量是多少」，具体落到哪些槽位由独立的 SlotAllocator 决定——「算多少」与「放哪格」职责分离（design-decisions.md §15.8）。

```java
public interface QuantitySelector {
    /**
     * 计算匹配物品在该容器中的目标总量。
     * delta = target - current 由引擎推导方向：
     *   正数需放入，负数需取出，0 表示刚好满足。
     */
    int computeTargetAmount(QuantityContext ctx);
}

/**
 * 数量计算的上下文。
 * slotCount/freeSlots 为容器侧参与判定的全部槽位口径
 * （canTakeFrom || canPutTo，见 container-detection.md §8.2），不做方向细分。
 */
record QuantityContext(
    int currentTotal,     // 容器中匹配物品的当前总量
    int slotCount,        // 参与判定的槽位总数
    int freeSlots,        // 其中空槽数
    int maxStackSize      // 该物品最大堆叠数
) {}
```

| 内置实现          | JSON 示例                         | 目标总量                                                                 |
| ----------------- | --------------------------------- | ------------------------------------------------------------------------ |
| CountSelector     | `{"type":"count","value":64}`     | `value`                                                                  |
| GroupSelector     | `{"type":"group","value":3}`      | `value × maxStackSize`                                                   |
| FillSlotsSelector | `{"type":"fill_slots","value":5}` | 占满除 `value` 个空位外的槽位 ≈ `max(0, slotCount-value) × maxStackSize` |
| PercentSelector   | `{"type":"percent","value":75}`   | `slotCount × maxStackSize × value%`（按容量比例取整）                    |

统一公式天然给出方向语义：同一 selector 在 OUTPUT 上表现为「填充上限」，在 INPUT 上表现为「保留下限」（取出多余部分）。

**槽位分配器 (SlotAllocator)** — 扩展点：

```java
public interface SlotAllocator {
    String id();
    /**
     * 把「对 item 的 amount 个增删」分配到具体槽位。
     * toContainer=true 放入（只用 canPutTo 槽位），false 取出（只用 canTakeFrom 槽位）。
     */
    List<SlotAllocation> allocate(ContainerSnapshot snapshot,
                                  ItemStack item, int amount, boolean toContainer);
}

record SlotAllocation(int slot, int count) {}
```

- 内置 `FirstFitAllocator`：先并入已有同类不满堆（按槽序），再依次填充空槽
- 通过 Registry 注册，全局配置 `slotAllocator` 选择当前实现（configuration.md §11.4）

## 3.7 规则判定语义

**判定顺序即优先级**：

- 对容器的每个槽位物品，按容器 `rules` 数组顺序遍历关联的 `ContainerRule`，再按其 `itemRules` 列表顺序遍历——**首条命中的 ItemRule 生效，其余忽略**
- `negative=true`：先求反匹配结果，再走命中流程
- **WHITELIST** 下无任何 ItemRule 命中 → 物品不允许放入（target=0）
- **BLACKLIST** 下无任何 ItemRule 命中 → 物品不受限：取出方向 target=∞（尽量清空）、放入方向 target=∞（尽量塞满）
- 多个 delta 汇总后交由引擎推导操作（放入容量受目标容器剩余空间约束）

**selector×IOType 合法性校验**：部分数量语义与容器类型组合非法——例如「无限量」语义禁止用于 OUTPUT（否则所有物品涌入单箱，破坏分类目标）。QuantitySelector 实现可声明不兼容的 IOType 集合（接口默认方法，缺省返回空集表示全部兼容），配置加载与命令设置时校验，非法组合报错拒载。

**默认规则**：

| 容器类型 | ruleMode  | 规则 | 行为                                   |
| -------- | --------- | ---- | -------------------------------------- |
| INPUT    | BLACKLIST | 空   | 允许任意物品取出，尽量清空             |
| TEMP     | BLACKLIST | 空   | 允许任意物品取出和放入（杂项箱）       |
| OUTPUT   | WHITELIST | 空   | 默认不允许任何物品放入（必须明确配置） |
| IGNORE   | —         | —    | 跳过，不参与搬运                       |

## 3.8 容器内存 (ContainerMemory)

```java
ContainerMemory {
    key: CacheKey                 // (worldId, dimId, canonicalPos [, playerUUID])
    snapshot: ContainerSnapshot   // 最后一次扫描的快照
    cacheType: CacheType
    lastAccessTime: long
    explored: boolean             // 是否已被扫描过
}

ContainerSnapshot {
    slots: Map<Integer, ItemStack>      // slotIndex → 物品堆（空位不包含）
    slotInfos: Map<Integer, SlotInfo>   // 槽位能力（来自 Detector，container-detection.md §8.2）
    title: String                       // 容器 UI 标题（用于识别）
    slotCount: int                      // 容器侧总槽位数
}
```

**正确性约束**：

- **缓存键必须含 worldId 与 dimId**——裸 BlockPos 键会跨维度串数据；末影箱类因玩家而异的容器附加 playerUUID 维度；多格容器用 canonicalPos
- **快照只含容器侧槽位**：依据 Menu 中槽位的 container 归属判定，玩家背包/盔甲/副手一律不进入快照（玩家背包区混入快照会导致规则计算错乱）
- **DISK 缓存按 worldId 分目录**，切换世界时不加载其它世界的缓存文件
- **自愈机制**：任何基于缓存的预检被现实否定（点击无响应、内容与预期不符）→ 立即失效该容器缓存并强制重扫。缓存因此只是性能优化，不是正确性依赖（transport-engine.md §5.5、design-decisions.md §15.7）

**内存生命周期**：

- 当玩家打开一个容器时，引擎通过 Mixin 拦截 `Screen.onClose()` 自动创建/刷新 `ContainerMemory`；**写入前必须校验正在关闭的 Screen 与哪个容器会话绑定**（打开时建立映射），禁止用「上一个交互坐标」猜测——防止玩家手动开关无关 GUI 造成缓存投毒
- 缓存是惰性的：引擎需要某个容器的内容时，先检查是否有有效缓存；有则直接使用，无则尝试打开容器扫描
- 缓存有效性判断：
  - `NONE`：只在当前搬运轮次内有效，轮次结束后清除
  - `MEMORY`：随游戏 Session 生命周期，worldId 变化或退出世界/服务器时清除
  - `DISK`：持久化到文件系统，重启后重新加载；当用户主动打开容器时刷新

## 3.9 传输计划 (TransferPlan)

**注意**：`TransferPlan` 不是持久化数据模型，而是传输引擎运行时产生的**一次操作计划**，用于指导引擎执行具体操作。

```java
/** 一次搬运操作的计划：包含多个物品移动指令 */
TransferPlan {
    direction: PlanDirection       // TO_PLAYER | TO_CONTAINER
    moves: List<ItemMove>
}

/** 单个物品移动指令 */
ItemMove {
    targetPos: WorldDimPos         // 目标容器坐标
    targetSlot: int                // 目标槽位（-1 表示自动分配）
    item: ItemStack                // 要移动的物品
    amount: int                    // 移动数量
    priority: int                  // 执行优先级（同一 plan 内的排序）
}

/** 计划方向 —— 决定操作方向 */
enum PlanDirection {
    TO_PLAYER,     // 从容器 → 玩家背包（取出）
    TO_CONTAINER   // 从玩家背包 → 容器（放入）
}
```

引擎的每个阶段（GET_TEMP / GET_INPUT / PUT_OUTPUT / PUT_TEMP）都对应一个阶段级 `TransferPlan`：以单容器会话为粒度滚动生成并立即执行 `ItemMove`（transport-engine.md §5.3），计划对象随阶段推进累积，用于事件上报、日志与调试预览。`PlanDirection` 使得同一段执行代码可以处理取出和放入两种操作，避免重复逻辑。

> `amount` 支持任意精确数量的双向搬运，算法见 interaction-protocol.md §6.2。
