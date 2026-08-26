# Yuanlu Warehouse — 一阶段实施计划

> 依据 `doc/PDD.md` v0.3。**每层完成、验证并提交前更新本文件的进度表。**
> 状态：⬜ 未开始 · 🔄 进行中 · ✅ 完成

## 已定决策（2026-08-26 与作者讨论定稿）

| # | 事项 | 决策 |
| - | ---- | ---- |
| D1 | 阶段执行模型 | 基于缓存构建阶段访问队列（未探索容器 + 缓存显示可 IO 的容器）；逐箱「开箱→扫描→本箱 moves 滚动模拟→当场执行→settle 重扫→关屏→下一箱」；`TransferPlan` 为阶段级概念聚合，随容器处理累积。已回写 PDD §5.3 / §3.9（L0） |
| D2 | 不限量 quantity × OUTPUT | 按 PDD §3.7 **严格拒载**（配置加载期 + 命令设置期校验），不做 MVP 式静默矫正。`ItemRule.quantity == null` 表示不限量（∞ 语义） |
| D3 | 点击对账实现 | 纯客户端 tick 轮询：点击前记录 menu stateId + 目标槽预期增量，逐 tick 观察 stateId 变化且内容相符 / 预测被拒的本地回滚；超时判失败。零网络包 mixin。开屏确认用单一 `ClientPacketListener.handleOpenScreen` mixin 捕获 containerId 作会话身份键 |
| D4 | 交付节奏 | 逐层 commit（本文件随每次提交更新）；JVM 单测随层走；E2E gametest 在 L8 / L10 / L11 三个里程碑集中补齐 |

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
| L2 | impl 选择器×5+codec、数量选择器×4+codec、FirstFitAllocator、CoordinateUtils | JVM 单测 | ⬜ | |
| L3 | 世界标识 Single/Multiplayer + WorldSessionTracker 会话切换 | JVM 单测 | ⬜ | |
| L4 | 配置持久化 ModConfig/WorldConfig/仓库 JSON IO（schemaVersion/原子写/冲突拒载）/codec 分发 | JVM 单测 | ⬜ | |
| L5 | WarehouseManagerImpl CRUD + 激活态 | JVM 单测 | ⬜ | |
| L6 | CacheKey + 三级缓存 + TTL + 自愈失效 + Screen.onClose Mixin | JVM 单测 | ⬜ | |
| L7 | 内置 Detector（箱族/木桶/潜影盒/末影箱/漏斗族/熔炉系/酿造台）+ 槽位角色表 + resolveMultiBlock | JVM 单测 | ⬜ | |
| L8 | VanillaGuiInteraction 六原语 + ContainerSession 协议层（开屏握手/逐击对账/双向精确算法/settle 重扫）+ handleOpenScreen Mixin | gametest① | ⬜ | |
| L9 | RuleApplicator（首条命中/negative/∞语义/滚动模拟/聚合容量预检/selector×IOType 校验） | JVM 单测 | ⬜ | |
| L10 | NoOpNavigator + TransportEngineImpl（状态机/防振荡双机制/轮次追踪/异常表/RunReport/WarehouseEvents） | gametest② | ⬜ | |
| L11 | `/wh` 命令全量 + 标记模式 + i18n（en_us+zh_cn）+ 入口装配 | gametest③ | ⬜ | |

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
