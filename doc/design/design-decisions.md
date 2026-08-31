# 设计决策记录

> 原 PDD §15 + UI-PDD §16 合并。章节号保持不变——代码注释中的「PDD §15.x」引用按本文件定位；「UI-PDD Dx」编号亦保持。

## 主设计决策（原 PDD §15）

### 15.1 为什么状态机顺序是 GET_TEMP → GET_INPUT → PUT_OUTPUT → PUT_TEMP？

1. **先取 TEMP**：TEMP 容器中的物品通常是上一轮搬运的多余物品，优先清空 TEMP 可以最大化利用背包空间
2. **再取 INPUT**：从 INPUT 获取新物品
3. **先放 OUTPUT**：优先将物品放入规则定义的 OUTPUT 容器
4. **再放 TEMP**：所有 OUTPUT 放不下或不符合规则的多余物品放入 TEMP

### 15.2 为什么激活态不入仓、业务代码整体放 src/client？

- 把 `active` 持久化进 JSON 却从不读取、实际激活态在内存——两处状态必然漂移。明确：激活是运行时单选，WarehouseManager 内存持有。
- 本模组纯客户端。splitEnvironmentSourceSets 下混放极易误引 client-only 类（Screen/渲染）；全部业务收拢到 `src/client` 后边界天然清晰，main 只留 ModInitializer 空壳作为未来服务端增强的挂载点。

### 15.3 为什么 TEMP 既参与取出又参与放入？

TEMP 是中转容器，设计为"杂项箱"。当 OUTPUT 规则明确时，TEMP 中可能正好有 OUTPUT 需要的物品（比如玩家之前手动放入的），所以优先从 TEMP 取出。当 OUTPUT 放不下时，多余的物品回到 TEMP。

### 15.4 为什么使用 TransferPlan 而非直接操作？

TransferPlan 将「计算」和「执行」分离：可以在执行前预览操作、在执行失败时精准恢复、便于日志记录和调试。

### 15.5 为什么必须「服务端对账后才能推进/快照」？

客户端 `menu.clicked()` 是本地预测：服务器可能拒绝或修正。前身实现的三个缺陷同根同源——盲发 click 不验证、GUI 被外部关闭当作成功、快照记录的是预测态。对策统一为 interaction-protocol.md §6 协议：对账成功才推进计划、才写缓存；对账前的 Screen 关闭一律视为异常。

### 15.6 为什么需要防振荡双机制？

- 无「INPUT 条件准入」时：取出物品后发现无处可放，下一轮又取——空转死循环；
- 无「TEMP 双策略」时：从 TEMP 取出的物品 OUTPUT 放不下又放回 TEMP——往复振荡。
  二者是该领域特有的收敛保障，属于必实现的正确性逻辑而非优化。（教训来自前身实现实测。）

### 15.7 为什么缓存必须带世界标识且要有自愈？

裸 BlockPos 缓存键会跨维度串数据；MEMORY/DISK 的「内容不变」假设在多人服/漏斗环境下随时失效。自愈机制把缓存从正确性依赖降级为性能优化：预检错了只是白跑一趟，到达重扫即可自愈，绝不产生错误操作。

### 15.8 为什么 QuantitySelector 简化为总量 + SlotAllocator？

per-slot 进出签名把「算多少」（选择器职责）与「放哪格」（分配职责）耦合在一个接口里：每个数量选择器都被迫处理槽位布局，无法独立演进。分离后选择器只返回一个 int，落位策略（FirstFit 等）独立成扩展点，双方各自简单。

### 15.9 为什么事件系统改用 Fabric Event&lt;T&gt;？

固定方法接口每加一个事件就破坏一次 API，且只有模组自己能发事件。类型化 Event 天然向后兼容、支持插件订阅，与 Fabric 生态一致。

### 15.10 为什么 Navigator 单目标、重试归引擎？

前身实现中控制器维护 targetIndex、执行器内部又持有目标队列，两条账各自推进，在早退/失败路径上错位（到达 A 却操作 B）。新接口收敛为单 Goal：队列只在引擎一侧，Navigator 只对单个 Goal 负责。

### 15.11 待定项 / 后续专项

| 事项                         | 说明                                                                                           |
| ---------------------------- | ---------------------------------------------------------------------------------------------- |
| NbtSelector 更名与语义升级   | 是否更名 ComponentMatchSelector、是否做结构化组件子集匹配，调研其它 mod 做法后决定             |
| KeepCountSelector            | 「保持扫描时数量」的数量选择器（前身 COUNT_LIST 语义，实战验证过）                             |
| OUTPUT 驱逐异类（clear）     | OUTPUT 容器主动取出不属于自身清单的物品（前身 clear 标志验证）                                 |
| where 模式                   | 手持物品高亮所有含该物品的容器（区分规则命中/缓存命中色；高亮系统的实用用例）                  |
| GO_BACK                      | 搬运完成后寻路返回起点（Navigator 目标的趣味用例）                                             |
| 点击式框选（选区）           | 输入拦截 Mixin 方案（attack/useBlock）与选区状态机                                             |
| watch 持续监控模式           | DONE 后监听 INPUT 变化自动重启搬运（前身 Hack 即此形态）                                       |
| TransportEngine 阶段序列泛化 | 四阶段固定 → GET(sourceSet)/PUT(destSet) 策略序列；`transfer` 命令的 IOType 临时覆盖可被其取代 |
| 高亮状态色                   | HAS_SPACE/FULL/UNKNOWN 与运行时联动                                                            |

### 15.12 Wurst7 MVP 对照记录

本设计有两个真实运行过的前身实现，位于 `refs/libs/Wurst7/src/main/java/net/wurstclient/`：

- **WarehouseHack.java**——watch 模式雏形：范围内持续整理，无配置无寻路，打磨出了精确存取的折半算法与聚合容量模型
- **WarehouseCmd.java**——一次性编排雏形：配置驱动的多容器搬运（weight 优先级 / ioType 数量语义 / sign 标记 / template 模板 / summary 高亮 / Wurst 寻路集成），是本 PDD 传输引擎的直系原型

**吸收的验证结论**：

1. 精确数量双向算法可行且高效（存入折半 O(log n)+O(R)；取出半取/全取+放回；QUICK_CRAFT 多槽分发）→ interaction-protocol.md §6.2
2. 聚合容量模型（每 itemId 总量/占用槽/空槽三标量）足以支撑快速预检 → transport-engine.md §5.3
3. syncId 门控的同步包握手是可靠的打开确认与会话身份方案 → interaction-protocol.md §6.1
4. 右键标记录入复用开箱协议、边际成本低 → transport-engine.md §5.7 标记模式
5. 配置按服务器地址隔离目录、缓存按维度失效 → world-identity.md、configuration.md §11.3
6. selector×IOType 存在非法组合需校验（ALL×OUTPUT）→ data-model.md §3.7

**不继承的反模式**（实现期红线）：

- worker 线程 + `Thread.sleep` 节流 + Future 桥接主线程（连关屏都要调度回主线程）——坚持 tick 驱动单线程
- `chest.length + (i<9 ? i+27 : i-9)` 槽位索引算术——只对原版布局成立，必须用 Slot.container 归属判定
- 盲发点击不逐击对账（MVP 靠事后重扫兜底收敛）——新协议要求逐击对账
- 聚合缓存当运行时数据源（每次 run 仍须真开箱）——聚合摘要只做预检，操作依据永远是对账后的槽位级快照

## UI 设计决策（原 UI-PDD §16）

### D1 为什么三层 + SPI 兼容层，而不是"直接用原版框架"？
universal jar = 一份字节码跑全部支持版本；26.1→26.2 已证明 client GUI API 的高挥发性（GuiGraphicsExtractor、Hud、StagedVertexBuffer 等变动）。把挥发的绘制/挂载面收进 `ui/mc/` 单包，版本升级 = 只修一个包；L1（元素树/事件/绑定）是十年稳定的概念层，L3 只依赖语义。详见调研报告（research/ui-research.md）§4/§6。

### D2 为什么不做声明式语言（HTML/CSS 式）？
LDLib2 的 XML/LSS 服务于"给外部用户写 UI"的产品需求；本 mod 无此需求（插件 UI 预留也只需要树序列化）。语言层的解析器/schema/工具链是双倍维护成本，且失去编译期检查。**树才是稳定契约**：远期需要时在 L1 加 `toJson/fromJson` 即可（D3 的前置）。

### D3 元素树可序列化作为插件 UI 扩展的前置
预留注册点（plugin-api.md §9.3）未来开放时，插件以数据（而非代码）描述 UI 片段，规避"插件冻结期 api/ 变更"风险。当前只保证 L1 元素树无 MC client 类型泄漏，使序列化可行。

### D4 为什么坚持"resize 全量重建"而不做增量布局？
vanilla 约定（`rebuildWidgets`）+ Create/LDLib2 共同验证；本 mod 屏幕规模小（个位数），全量重建成本可忽略；增量布局（Taffy 脏驱动）是为 LDLib2 级别的 UI 规模服务的，属于过度设计（调研 §3.3 评价）。

### D5 为什么不用 Flexbox/Taffy/Yoga？
同 D4。L1 的 Row/Column/Grid/Anchor 覆盖本 mod 全部布局形态（列表/表单/网格/HUD 角落），纯 Java 可 JUnit；布局器是可替换的 `Layout` 策略。**增补**：实机反馈（固定宽度内容折断、列表无法利用大窗口）催生了轻量 flex 语义——`grow` 主轴权重 + `ScrollElement` 滚动视口（ui-engine.md §3.4），仍为零依赖自研（约百行）；完整 Flexbox（shrink/wrap/justify/交叉轴对齐）与 Taffy 级约束求解依然不做。

### D6 为什么样式只做主题 token，不做级联/选择器？
StyleBag 级联（LDLib2）解决的是"四大样式来源冲突"，本 mod 样式来源只有"主题"一个；token 直连 + 元素 classes/id 保留查询能力。若后续做主题切换或插件自定义皮肤，再评估加"来源优先级"薄层。

### D7 为什么高亮/选区不需要专用 mixin？
26.1 `LevelRenderer.renderLevel` 内即处于 Gizmos per-frame 收集窗口（公开 API，try-with-resources），Gizmos 覆盖线框/填充/文字/穿墙全部需求。现有 `LevelRendererMixin` 仅在 renderLevel HEAD fire 一个事件钩子（不含渲染逻辑）。自定义 RenderPipeline（Wurst7 路线）仅在 Gizmos 表达不了的效果时才引入，且归入 L2。

### D8 为什么 HUD 纯只读、不放任何交互控件？
vanilla 不向 HUD 分发鼠标/键盘输入（MouseHandler 只派发给当前 Screen）；接管需要 mixin MouseHandler/Keyboard 抢先（IPN/EMI 路线），跨版本风险高一档且与其它 mod 的兼容面大。HUD 是**只读信息面板**（用户明确要求）；其位置/内容配置经 HUD 设置屏（ui-screens.md §6.2，普通 Screen，输入走 L1 事件系统）完成。若后续确需 HUD 按钮类交互，经 `OverlayHost` 设计评审后单独立项。

### D9 为什么 SelectionState 上移到 core/selection/？
命令层与 UI 必须共享同一选区状态（`/wh select pos1` 与快捷键设角互通），否则两个前端状态漂移。它本质是业务状态而非命令解析产物；`command/` 改为调用 core（与 manager/engine 同级）。

### D10 为什么 i18n key 新开 `ui.wh.*` 前缀而非复用 `commands.wh.*`？
命令文案（语法说明式）与 UI 文案（按钮/标签式）粒度和措辞习惯不同；强行复用会让两边的改动互相牵制。状态/档位类共用 key（`wh.state.*`/`wh.grade.*`）保持一致。

### D11 为什么"构建时求解 + 全量重建"能覆盖多尺寸/多比例/多分辨率窗口？
（ui-engine.md §3.7 的决策记录）三重保障：①引擎只工作在 GUI 缩放坐标系，物理分辨率/DPI 由 vanilla 吸收，GUI Scale 的本质只是"可视区大小变化"；②布局器是流式的（Column/Grid/Anchor 对父级约束求解），不存在写死的屏幕坐标，宽高比变化自然适应；③resize/窗口变化触发全量重建（vanilla 约定），HUD 走每帧尺寸比对+下帧重建（LDLib2 模式）。这与 Create（居中重算）、EMI（缓存重算）、MaLiLib（init 现算）的实践一致——mod 生态没有任何一家做运行期百分比/DPI 缩放，本引擎也不做。代价：极小可视区（高 GUI Scale）下靠滚动消化而非缩放，属可接受取舍。

### D12 为什么 HUD 设置做成 Screen 而不是"HUD 上的编辑模式"？
同一原因链：HUD 无输入（D8）→ 编辑交互必须发生在 Screen 里 → 干脆让设置屏显示实时 HUD 预览并在其上编辑（拖拽/开关/排序都是 Screen 内常规事件）。额外收益：同一 `HudRoot` 元素在 HudHost 与设置屏画布两处渲染，天然验证了 L1"同树多挂载"的设计假设，为插件 HUD 区块提供实现样板。

### D13 为什么规则条目结构化编辑走 SelectorCodecs 注册表而不是 UI 自建类型表？
类型清单 = `SelectorCodecs.itemTypeNames()/quantityTypeNames()` 注册序枚举——内置与插件 codec 自动出现，UI 不维护第二份类型表；校验 = JSON round-trip（`itemFromJson`），与命令解析同一口径（严格校验），UI 与命令的语义漂移在机制上不可能发生。插件类型的 label 缺省字面量展示（无 i18n 键也不破坏可用性）。

### D14 为什么 expand 快捷键选「按压沿视线主轴 1 格」而不是「按住+滚轮 grab」？
滚轮拦截需要新增 MouseHandler 注入点（第 5 个业务 mixin，成为跨版本维护面）；按压语义已覆盖「快捷键方向性扩展」的核心需求（潜行反向 = 收缩），精确多格扩展由选区面板次数+方向按钮承接，命令层 `select expand <count> <dir>` 保持完整能力。grab 语义若未来确需，作为独立 mixin 提案评审。

### D15 为什么 HUD 设置的模拟容器用原版贴图分段 blit 而不是程序化绘制？
贴靠对齐的前提是尺寸与真实容器 GUI 一致——原版 `generic_54` 等贴图按 256 网格 UV 分段 blit（body 段 rows*18+17 + 背包段 96px）即得像素级一致；程序化绘制要么近似（对齐偏差）要么复刻原版绘制逻辑（维护成本）。为此给 L1 端口加了 `UiDraw.blit`（纹理路径 + UV 子区域），仍是版本无关签名。

### D16 为什么「打开原版容器界面时 HUD 继续显示」留待后续？
需要 GameRenderer 级挂载（OverlayHost）或容器 Screen 渲染注入，z 序与拖拽配置需独立设计；HUD 定位需求已由设置屏模拟容器参照（D15）解决——用户可在无容器的设置屏里按真实容器尺寸完成贴靠，运行时收益增量小、跨版本风险高一档，故不因扩容顺手做掉。
