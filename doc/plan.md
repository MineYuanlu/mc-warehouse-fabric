# Yuanlu Warehouse — 一阶段实施计划

> 依据 `doc/PDD.md` v0.3。**每层完成、验证并提交前更新本文件的进度表。**
> 状态：⬜ 未开始 · 🔄 进行中 · ✅ 完成

## 已定决策（2026-08-26 与作者讨论定稿）

| # | 事项 | 决策 |
| - | ---- | ---- |
| D1 | 阶段执行模型 | 基于缓存构建阶段访问队列（未探索容器 + 缓存显示可 IO 的容器）；逐箱「开箱→扫描→本箱 moves 滚动模拟→当场执行→settle 重扫→关屏→下一箱」；`TransferPlan` 为阶段级概念聚合，随容器处理累积。已回写 PDD §5.3 / §3.9（L0） |
| D2 | 不限量 quantity × OUTPUT | 按 PDD §3.7 **严格拒载**（配置加载期 + 命令设置期校验），不做 MVP 式静默矫正。`ItemRule.quantity == null` 表示不限量（∞ 语义） |
| D3 | 点击对账实现 | ~~纯客户端 tick 轮询 stateId~~ **（L8 实测修订）**：26.1 客户端 `menu.getStateId()` 不随服务端包更新，不可作对账信号。实际采用：**受监视槽位/光标内容变化 + 2 tick 稳定窗口**——变化后未被回滚即确认；回到点击前状态 = 预测被拒（CLICK_CORRECTED）；超时 = CLICK_TIMEOUT。通道无关，插件交互实现免费复用。开屏确认仍用 `ClientPacketListener.handleOpenScreen` mixin 捕获 containerId |
| D3a | 点击发包 | **必须经 `MultiPlayerGameMode.handleContainerInput`**（26.1 名称）——`menu.clicked` 只是本地预测不发包（E2E 实测教训） |
| D4 | 交付节奏 | 逐层 commit（本文件随每次提交更新）；JVM 单测随层走；E2E gametest 在 L8 / L10 / L11 三个里程碑集中补齐 |
| D5 | L11 复审修复决策（2026-08-28 定稿） | ① 多人 worldId 改 `mp:<host>:<port>`，**破坏性变更不迁移**（pre-release 无存量）；② 探索类异常**保留累计 exploreFailMax** 口径，回写 PDD §5.5；③ Detector 标题要素按「开屏标题 ↔ BE DisplayName 一致性」实现（26.1 同源精确等式，抗改名，非 Nameable 跳过）；④ 缓存种子与手动关箱回写**按 PDD 实现**（真实扫描非伪造，红线指操作依据须经对账） |

## 技术锚点（MC 26.1 已验证）

| 用途 | API |
| ---- | --- |
| 点击原语 | `AbstractContainerMenu.clicked(int slot, int button, ContainerInput, Player)`；`ContainerInput.{PICKUP, QUICK_MOVE, QUICK_CRAFT, …}` |
| 对账观测 | menu stateId 变化 + 槽位内容比对（纯轮询，见 D3） |
| 开屏握手 | `ClientboundOpenScreenPacket.getContainerId()/getType()/getTitle()` → mixin `ClientPacketListener.handleOpenScreen` |
| 打开方块 | `MultiPlayerGameMode.useItemOn(LocalPlayer, InteractionHand, BlockHitResult)` |
| 世界标识 | 单机存档目录名 / `ServerData.ip`；维度 id；坐标统一 `Identifier`（`net.minecraft.resources`） |
| 命令/事件 | Fabric client command v2 `ClientCommandRegistrationCallback`、`ClientTickEvents`、Fabric `Event<T>` |

**反模式红线**（PDD §15.12）：全程 tick 驱动单线程，禁 worker 线程与 `Thread.sleep` 节流；槽位归属一律用 `Slot.container` 判定，禁止 `chest.length + (i<9 ? i+27 : i-9)` 式索引算术；盲发点击不逐击对账禁止；聚合摘要只做预检，操作依据永远是对账后的槽位级快照。

## 分层计划与进度

| 层 | 内容 | 测试 | 状态 | 提交 |
| -- | ---- | ---- | ---- | ---- |
| L0 | PDD §5.3/§3.9 执行模型勘误回写 + 本计划文档 | — | ✅ | `doc: PDD 执行模型勘误 + 一阶段实施计划` |
| L1 | `api/` 全量接口/记录/枚举（container/item/world/navigation/interaction/plugin/warehouse/transport） | 编译通过 | ✅ | `feat(api): L1 核心 API 全量定义` |
| L2 | impl 选择器×5+codec、数量选择器×4+codec、FirstFitAllocator、CoordinateUtils | JVM 单测 | ✅ | `feat(impl): L2 选择器/数量选择器/分配器实现与单测` |
| L3 | 世界标识 Single/Multiplayer + WorldSessionTracker 会话切换 | JVM 单测 | ✅ | `feat(world): L3 世界标识与会话追踪` |
| L4 | 配置持久化 ModConfig/WorldConfig/仓库 JSON IO（schemaVersion/原子写/冲突拒载）/codec 分发 | JVM 单测 | ✅ | `feat(config): L4 配置持久化` |
| L5 | WarehouseManagerImpl CRUD + 激活态 | JVM 单测 | ✅ | `feat(warehouse): L5 仓库管理` |
| L6 | CacheKey + 三级缓存 + TTL + 自愈失效 + Screen.onClose Mixin | JVM 单测 | ✅ | `feat(cache): L6 容器内存与三级缓存` |
| L7 | 内置 Detector（箱族/木桶/潜影盒/末影箱/漏斗族/熔炉系/酿造台）+ 槽位角色表 + resolveMultiBlock | JVM 单测 | ✅ | `feat(container): L7 容器检测` |
| L8 | VanillaGuiInteraction 六原语 + ContainerSession 协议层（开屏握手/逐击对账/双向精确算法/settle 重扫）+ handleOpenScreen Mixin | gametest① | ✅ | `feat(protocol): L8 运行时交互协议` |
| L9 | RuleApplicator（首条命中/negative/∞语义/滚动模拟/聚合容量预检/selector×IOType 校验） | JVM 单测 | ✅ | `feat(rule): L9 规则引擎` |
| L10 | NoOpNavigator + TransportEngineImpl（状态机/防振荡双机制/轮次追踪/异常表/RunReport/WarehouseEvents） | gametest② | ✅ | `feat(engine): L10 传输引擎` |
| L11 | `/wh` 命令全量 + 标记模式 + i18n（en_us+zh_cn）+ 入口装配 | gametest③ | ✅ | `feat(command): L11 命令系统与入口装配` |


**L2 补充记录**
- `SelectorCodec` 增加 `implType()` 方法：序列化按「实例类型→codec」分发所必需（PDD §9.2 三方法之外的必要扩展，插件多写一行返回类字面量）
- 新增 `core/registry/SelectorCodecs` 分发核心（CompositeSelector 嵌套编码依赖；type 字段由分发层统一写入，codec 只产出载荷）
- **MC 26.1 测试基建关键修复**：`Bootstrap.bootStrap()` 后必须执行 `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS` 组件绑定（否则 `new ItemStack` 抛 "Components not bound yet"）；初始化器会引用数据包侧标签/动态注册表（damage_type/is_fire、trim_material 等），测试环境用宽松 Provider（缺失标签→空 Named 集、缺失注册表→空表、缺失元素→stand-alone 占位 Holder）完成绑定
- test 源集需显式依赖 `sourceSets.client.output`（split sources 下默认只挂 main）


**L7 补充记录**
- MC 26.1：`GenericContainerScreen` 已更名 `ContainerScreen`；身份判定以 MenuType+BE 类型+槽位数为主（不依赖 Screen 类）
- `ContainerDetector` 增加 `playerScoped()` 默认方法（末影箱缓存键附 playerUUID 所需，PDD §3.8）
- 双箱合并用 `ChestBlock.getConnectedBlockPos`；快照槽位边界以 `Slot.container instanceof Inventory` 判定


**L8 补充记录（E2E 实测教训）**
- `menu.clicked()` 仅本地预测不发包；点击必须经 `MultiPlayerGameMode.handleContainerInput(containerId, slot, button, ContainerInput, player)`（26.1 更名，旧名 handleInventoryMouseClick）
- 客户端 `AbstractContainerMenu.getStateId()` 在 26.1 不随服务端 SetContent/SetSlot 更新——对账信号改为「受监视槽位+光标内容变化 + 2 tick 稳定窗口」（见 D3）
- gametest 断言不可用 JUnit（production runtime 无 junit）——用 AssertionError 抛出
- gametest 源集需显式加 `gametestImplementation` junit + client output


**L10 补充记录（E2E 实测教训）**
- 内置实现注册（探测器/交互/导航器/分配器/codec）在 L11 入口装配前由 gametest 引导内联
- `continueRun` 依赖 suspend 记录完整 sessionInfo（sessionKey/sessionInfo 三元组都要落）——坏容器探测失败发生在 beginOpen 早段时尤其注意
- 出口条件补充（§5.2 v0.2「未知≠满足」的推广）：任何类目存在未探索容器 → 出口①②一律不满足；已 skip 的容器不算未知
- itemsMoved 是取/放双段累计（INPUT 取 60 + OUTPUT 放 60 = 120），不是净搬运量
- gametest 的 runOnServer 异步派发：断言服务端状态需标志位轮询等待回调完成；关箱后客户端 BlockEntity 不含容器内容（只有 Menu 同步），只能从服务端读


**L11 补充记录（E2E 实测教训）**
- `ContainerOpenContext` 紧构造器要求 pos/block 双非空——标记模式采集必须用目标绝对坐标 + 客户端 level 的 BlockInWorld 构造，传 null 会让全部 Detector 组合判定 NPE
- 26.1 动作栏/聊天分离：`LocalPlayer.sendOverlayMessage`（动作栏）/`sendSystemMessage`（聊天）；`displayClientMessage` 已不存在
- `MultiPlayerGameMode.useItemOn` 打开容器 → `handleOpenScreen` mixin（HEAD 时 screen 未挂上）→ 标记模式用「开屏包置 pendingCapture + 下帧 tick 消费」两段式采集，最后真实扫描再 onClose
- Brigadier 字面量参数不能含冒号——`rule add <id> <selector...>` 用单个 greedy 参数自行分词（支持裸 JSON 选择器与 `--quantity count:64` 尾巴）
- 跨仓库搬运经 manager 的临时覆盖视图实现（overlay 不落盘、pop 恢复激活态），引擎零改动复用标准状态机
- gametest 命令冒烟：独立 dispatcher + FabricClientCommandSource stub（computeOnClient 同步执行）；`/warehouse` 别名经 redirect 注册

## 复审修复批次（L0–L11 遗留问题，2026-08-28 完成）

> L11 完成后的复审发现 16 项前次遗留 + 4 项 L11 新增缺口。除 #7（对账语义，D3 已闭环）外全部修复，分 10 个提交（B1–B10）+ 文档回写（B11）。

| 批 | 内容 | 提交 |
| -- | ---- | ---- |
| B1 | `api/transport/TransportEngine` 接口（命令层脱离具体类）；`start(pathfinderOverride)` 一次性寻路器参数化，删 `pathfinderOnce` 死字段 | `refactor(api)` |
| B2 | transfer overlay 随 RUN_FINISHED 自动回收（SUSPENDED 不发 RUN_FINISHED，continue 路径天然保留） | `fix(command)` |
| B3 | containerId 门控恢复（开屏包身份键，绑定/发包双重校验）；rule add 校验回滚加固 | `fix(protocol)` |
| B4 | **DISK 缓存闭环**：remember 落盘 / getValid·isExplored 回源回填 / invalidate 双清 / 原子写；playerScoped 缓存键（DetectorResolver 共用）；标记模式缓存种子；手动关箱回写（closeScanSink） | `feat(cache)` |
| B5 | MACHINE_FUEL 可燃性过滤（acceptsPut SPI + RecipePropertySet/FuelValues，镜像原版 quickMove 路由）；标题一致性校验；resolveMultiBlock 非多格拒多坐标 + MarkMode 双箱合并 | `feat(container)` |
| B6 | NbtSelector 匹配完整组件表（含恰为默认值的组件） | `fix(selector)` |
| B7 | 交互时序接入 `interactionSpeed(w,d)`（§11.4）与 `interactionJitterPercent`（§6.4） | `feat(protocol)` |
| B8 | 多人 worldId → `mp:<host>:<port>`（D5①，破坏性变更） | `feat(world)` |
| B9 | 仓库/全局规则 schemaVersion 不符不再静默（errors/warn） | `fix(config)` |
| B10 | dragDistribute -999 注释勘误 + 单槽快路径（1 包替代三连包） | `fix(protocol)` |
| B11 | PDD §5.5/§8.1/§8.2/§10.1 勘误 + 本表 | `doc` |

**技术锚点补录（26.1 反编译验证，refs/dep-src）**
- 开屏包标题来源：`ServerPlayer.openMenu` 直接发 `provider.getDisplayName()`；原版容器 BE 自身即 provider——标题一致性校验是精确等式
- 可熔炼判定：客户端同步 `RecipePropertySet`（`level.recipeAccess().propertySet(FURNACE_INPUT)`，与服务端菜单同源）；可燃：`level.fuelValues().isFuel(stack)`
- 原版熔炉 quickMove 路由：canSmelt→输入槽、isFuel→燃料槽、两者皆非→**拒绝**（no-op）——这正是放入需求必须前置过滤的原因
- `RUN_FINISHED` 仅在 state→DONE 时发出；SUSPENDED 挂起不发——overlay 自动回收据此设计

### gametest 里程碑覆盖（§13 映射）

- **gametest①（L8）**：容器打开协议（成功/超时/UI 身份不匹配）、点击逐击对账、精确数量双向存取断言
- **gametest②（L10）**：状态机整轮循环、出口条件与未知态、缓存自愈、SUSPENDED 三种恢复路径、RunReport 五档
- **gametest③（L11）**：命令冒烟、标记模式右键注册/移除

## 验证命令

```bash
./gradlew build                                            # 每层：编译 + JUnit + JAR
./gradlew runProductionClientGameTest                      # 里程碑：真实启动 MC E2E
./gradlew runProductionClientGameTestUniversal \
  -PuniversalJar="$(find build/libs -name 'yuanlu-warehouse-*.jar' ! -name '*-sources.jar' | head -1)"
```
