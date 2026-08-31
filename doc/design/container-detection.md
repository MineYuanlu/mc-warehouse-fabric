# 容器检测系统

> 原 PDD §8。章节号保持不变——代码注释中的「PDD §8.x」引用按本文件定位。

## 8.1 接口

```java
interface ContainerDetector {
    String id();

    /** 打开前预判：目标方块（含方块实体类型）是否属于此容器类型 */
    boolean matchesBlock(BlockInWorld pos);

    /** 打开后校验：组合判定（方块实体类型 + Screen 标题 + 槽位数） */
    boolean matches(Screen screen, ContainerOpenContext ctx);

    /** 扫描当前打开 Screen 的容器侧内容，返回快照（含槽位能力，§8.2） */
    ContainerSnapshot scan(Screen screen);

    /** 解析多格容器：给定一组坐标，返回合并后的 ContainerInfo */
    ContainerInfo resolveMultiBlock(BlockPos[] positions);
}
```

- **身份识别必须组合判定**：仅凭 Screen 类不可靠（箱子/木桶/陷阱箱共用同类 Screen），须叠加方块实体类型与标题/槽位数
- **标题要素语义**：「标题」是**开屏包标题 ↔ 预检 BE DisplayName 的一致性校验**，而非固定默认名匹配——26.1 服务端发包即 `provider.getDisplayName()`（与 BE 同源），天然抗铁砧改名；非 Nameable BE（末影箱）跳过该要素
- **resolveMultiBlock 约定**：非多格容器类型对 `positions.length > 1` 应返回 null（拒绝裁剪语义，不产出默认模板）
- 粗粒度布尔 `canInput(Screen)` / `canOutput(Screen)` 不在接口中——无法表达熔炉这类混合槽位容器，由 §8.2 槽位能力模型取代

## 8.2 槽位能力模型

机器类容器的槽位不是均质的（熔炉的输入/燃料/输出），没有槽位级元数据，「满/空」聚合与放入计划都会算错。

```java
/** 单个槽位的能力描述，由 Detector.scan 产出；默认全 GENERIC + 双 true */
record SlotInfo(SlotRole role, boolean canTakeFrom, boolean canPutTo) {}

enum SlotRole { GENERIC, MACHINE_INPUT, MACHINE_FUEL, MACHINE_OUTPUT, SPECIAL }
```

引擎约束：

- 放入计划只允许使用 `canPutTo=true` 的槽位；取出只用 `canTakeFrom=true`
- 「容器满/空」聚合、`QuantityContext.slotCount/freeSlots` 按角色口径过滤
- `MACHINE_FUEL` 槽位的放入还须经物品可燃性二次过滤（引擎内置常识表 + Detector 可覆盖；
  实现注记：可熔炼判定用客户端同步的 RecipePropertySet（与服务端菜单同源），
  可燃判定用 FuelValues，过滤语义完整镜像原版 quickMove 路由——
  非「可熔炼且非燃料」类物品在需求生成期即跳过，避免 quickMove no-op 逐击超时）

内置角色表：

| 容器                               | 角色                                                                    |
| ---------------------------------- | ----------------------------------------------------------------------- |
| 箱子族/潜影盒/末影箱/漏斗/投掷器等 | 全 GENERIC                                                              |
| 熔炉系                             | slot0=MACHINE_INPUT、slot1=MACHINE_FUEL、slot2=MACHINE_OUTPUT（仅可取） |
| 酿造台                             | 3 药水位=MACHINE_INPUT、材料位=SPECIAL、粉末位=SPECIAL                  |

## 8.3 交互方式 SPI（ContainerInteraction）— 扩展点

打开/关闭/搬移动作的**物理实现**抽象。原版 GUI 点击只是默认实现——这是未来接入非 GUI 交互通道（如 AE 存储网络终端直连 API）的关键接口：

```java
interface ContainerInteraction {
    String id();
    /** 发起打开（异步；等待/校验由 interaction-protocol.md §6.1 协议层负责，实现不关心时序） */
    void requestOpen(ContainerHandle handle);
    void requestClose(ContainerHandle handle);
    /** 整堆移出槽位 → 背包；返回是否已发起（成败由 §6.2 对账判定） */
    boolean quickMoveToPlayer(ContainerHandle handle, int slot);
    /** 背包 → 整堆移入槽位 */
    boolean quickMoveToContainer(ContainerHandle handle, int slot);

    // ---- 点击原语层：精确数量能力的物理基础，语义见 interaction-protocol.md §6.2 ----

    /** 是否支持点击原语；false 时引擎降级为整堆粒度 */
    boolean supportsExactAmount();
    boolean pickupAll(ContainerHandle handle, int slot);
    boolean pickupHalf(ContainerHandle handle, int slot);
    boolean placeOne(ContainerHandle handle, int slot);
    boolean putBackHeld(ContainerHandle handle, int slot);
    /** QUICK_CRAFT 拖拽协议：把光标堆均分到多个槽位（一次发包序列） */
    boolean dragDistribute(ContainerHandle handle, int[] slots);
}
```

- 内置 `VanillaGuiInteraction`：`useItemOn` 打开 + `menu.clicked` 实现全部原语
- 精确数量算法在**协议层**只实现一份（interaction-protocol.md §6.2），参数化于原语——交互插件提供原语即可获得双向精确搬运能力，无需重写算法
- 语义级方法（quickMoveTo\*）是原语之上的便捷封装，仅支持整堆的通道只需实现它们
- `ContainerHandle` = 一次已建立的容器会话（pos + 当前 Screen/Menu 引用 + 身份信息）
- 协议层的等待/对账/超时逻辑对一切实现复用不变——插件换交互方式时无需重写协议

## 8.4 容器打开与交互流程

见 interaction-protocol.md（运行时交互协议）。关键约束：所有操作必须在客户端打开 UI 后进行，且每次操作需服务端对账确认后才能推进。

## 8.5 内置实现

| 容器类型           | 检测方式                                                     | 特殊处理                   |
| ------------------ | ------------------------------------------------------------ | -------------------------- |
| 箱子/大箱子/陷阱箱 | matchesBlock 方块实体 + GenericContainerScreen（GENERIC_9x1..9x6 菜单族，槽位数取 9 的正倍数守卫——27/54 为常规值，主身份是 BE+菜单族+标题；大箱双格菜单标题放宽仅对 54 格） | 大箱子自动检测双格并关联   |
| 木桶               | 方块实体识别 + 同屏类                                        | 27 格                      |
| 漏斗/投掷器/发射器 | 方块实体 + 特定槽位数                                        | —                          |
| 熔炉/高炉/烟熏炉   | FurnaceScreen                                                | 三格特殊（§8.2 角色表）    |
| 酿造台             | BrewingStandScreen                                           | 5 格（§8.2 角色表）        |
| 潜影盒             | ShulkerBoxScreen                                             | 27 格，视为普通容器        |
| 末影箱             | 方块实体 + 标题                                              | 27 格，缓存键附 playerUUID |

**玩家背包**作为搬运过程的媒介容器，不与 `ContainerInfo` 对应，口径见 transport-engine.md §5.3（36 格主背包）。

## 8.6 容器物品（潜影盒/储物袋）处理

**默认行为**：将所有容器物品视为普通物品，不拆解其内部内容。潜影盒作为一个整体物品单元被搬运。

**扩展方向（未实现）**：

- 拆出模式：将潜影盒放在地上，打开取出/放入内部物品，再回收空盒
- 整箱模式：保持现状，整体搬运
- 通过规则优先级在两种模式间选择
- 需要处理落地位置、掉落物安全性、多类物品按规则分流等复杂逻辑

## 8.7 Mixin 注入点

实际注册于 `yuanlu-warehouse.client.mixins.json` 的全部 client mixin：

| 目标类                | 注入点                  | 用途                                                                                   |
| --------------------- | ----------------------- | -------------------------------------------------------------------------------------- |
| `AbstractContainerScreen` | `onClose()` (HEAD)   | 容器 UI 关闭时自动刷新 ContainerMemory（校验会话绑定，data-model.md §3.8）             |
| `ClientPacketListener` | `handleOpenScreen`     | 开屏包捕获：syncId 门控的打开确认（interaction-protocol.md §6.1）+ 标记模式两段式捕获 + 手动开箱刷新 F2 ② 信号（transport-engine.md §5.4） |
| `ClientPacketListener` | `handleContainerClose` | 关屏包：丢弃未配对的开屏候选（防止残留界面误绑下一次会话）                             |
| `ClientPacketListener` | `handleBlockEvent`     | 方块事件：箱子/末影箱/潜影盒开合动画信号——手动开箱刷新 F2 ③ 验证（transport-engine.md §5.4） |
| `LevelRenderer`       | `renderLevel` (HEAD)    | per-frame Gizmos 收集窗口事件钩子（`UiHooks.firePerFrameWorld`）——UI 世界高亮渲染管线，见 ui-highlight.md §7 |
| `MultiPlayerGameMode` | `useItemOn`             | 玩家右键点击方块捕获：手动开箱刷新 F2 ① 信号（transport-engine.md §5.4）               |

所有 Mixin 均为 client-only，只 fire 事件/记录信号，不含业务逻辑。
