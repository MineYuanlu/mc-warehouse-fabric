# UI 业务组件与 HUD（L3）

> 原 UI-PDD §5–§6。章节号保持不变——代码注释中的「UI-PDD §5.x / §6.x」引用按本文件定位。
> L1/L2 引擎层见 [ui-engine.md](ui-engine.md)；高亮渲染见 [ui-highlight.md](ui-highlight.md)。

## 5. L3 业务组件（ui/app/）

**总目标**：用户不执行任何命令即可获得 `/wh` 的全部等价能力（能力映射见 research/ui-research.md §5）。UI 与命令是同一能力的两个前端——UI 数据全部来自 `api/` 接口 + `WarehouseEvents` 事件，写操作直接调 manager/engine（与 `WhCommands` 各 Group 相同的调用序列，不经过命令层）；不存在 UI 独占的能力，命令层保留。

### 5.1 仓库管理主屏 `WarehouseManagerScreen`

单 Screen 两页签（`TabView` 语义，元素树内实现）。**布局**：全部主屏走 `ScreenScaffold` 全屏脚手架——根唯一子级 `grow(1)` 满铺整个屏幕（无固定外框），铺 `Theme.bgScreen` 半透明黑底（普通屏叠加在 MC 模糊背景之上）；内容 = padding(10) Column：页头（固定）→ body（`grow(1)` 撑满）→ 底部操作行（固定）。列表/详情侧栏 `grow(1)` 瓜分剩余宽度（声明宽为最小值）并内嵌 `ScrollElement`（条目超出即滚轮滚动，ui-engine.md §3.4）。

**页签 A：仓库列表**

| 区块 | 内容 | 交互 |
|---|---|---|
| 列表 | 每仓库一行：名称、容器数、规则数 | 点击选中；双击 = 激活（世界/维度标记待补，见 todos） |
| 操作条 | [新建] [激活] [删除] [重载配置] | 激活当前仓库按钮置绿（Create 互斥指示灯模式）；删除需二次确认对话框（危险色） |
| 详情侧栏 | 选中仓库概览：anchor、容器明细表（IOType 徽标/规则/缓存/优先级）；`RunReport` 最近一次结果在引擎页状态卡渲染 | 容器行点击 → 弹出编辑浮层（改 type/mode、绑/解规则、清缓存，等价 `container type/mode/rules/memory`） |

**页签 B：引擎控制**

- 状态卡：`TransportState` + 动作级进度（PROGRESS 事件两粒度分别渲染，transport-engine.md §5.9）+ `RunReport` 最近一次结果；进度条（ui-engine.md §3.6 数值滚动）待接线（见 todos）。
- 控制按钮组：[开始]/[暂停]/[继续]/[重启]/[终止]——按状态机合法迁移禁用不合法项（等价 `start/stop/continue/restart/abort`；disabled 项的 tooltip 说明待补，见 todos）。
- 跨仓库搬运入口（等价 `transfer`）：src/dst 双循环选择器（`CycleSelector`，与"互斥按钮组"同族的循环式控件）+ 开始/停止。
- 引擎页补全项：pathfinder 选择（`--pathfinder` 覆盖）、运行状态详情、anchor 设定、容器增删、标记模式 Modal（带完整参数开启/退出，参数经 `lastConfigured` 记忆供快捷键沿用）。

数据源：`WarehouseManager`（读 `list()/active()`）+ `WarehouseEvents`（TRANSPORT_STATE/PROGRESS/RUN_FINISHED/WAREHOUSE_CHANGED → Presenter `Value`）。

### 5.2 选区与批量操作

复用 `SelectionState`（`core/selection/`，命令层与 UI 共用同一状态——design-decisions.md D9）：

- **世界交互**（HUD 快捷键驱动，见 ui-interaction.md §8）：设 pos1/pos2（脚下/准星 `--look` 等价）、扩展角 2、显示/清除。
- **共享操作核心**：`core/selection/SelectionOps`——inBox 判定 / countInBox / expand / dominantAxis（视线主轴），
  命令层 `SelectGroup.inBox` 与选区面板同一实现（消除双实现漂移）；`~` 相对坐标解析同样抽取为
  `util/RelativeCoords` 共用（`CommandSupport.coord` 委托）。
- **选区盒渲染**：`WorldHighlighter` 画三轴三色线框 + 角块高亮 + 半透明面（Litematica `renderSelectionBox` 分层配色语义：主体 X 红/Y 绿/Z 蓝；角块当前统一 accent 青色，pos1 红/pos2 蓝双色区分待补，见 todos），见 ui-highlight.md §7。
- **选区面板**（Screen）：pos1/pos2 三种设定形态（脚下 / 准星 / XYZ 输入框支持 `~` 相对语法）；
  expand 行 = 次数输入 + 六方向按钮（等价 `select expand <count> <dir>`）；信息行 = world/dim、体积、
  选区内容器数；批量操作（`set-type`：IOType 互斥组 + mode 回落默认、`set-rule` 加/解、`set-cache`）+ [应用到选区]。
- 标记模式（`MarkMode`）：HUD 快捷键开关（等价 `container mark`），开启时 HUD 显示当前 session（type/rule/template）与操作提示；仓库页「标记模式」Modal 可带完整参数开启/退出；配合容器高亮辨识待标记目标。

### 5.3 规则编辑

- **双栏布局**：左列规则 CRUD（新建 id 校验、引用数>0 禁删 + tooltip——等价 `rule list/create/delete`）；
  右列选中规则的条目列表 + 结构化编辑器。
- **条目列表**：序号 + 结构化摘要（`!name(fuzzy):stone ×64 count` 形态，由 codec toJson 派生）+ tooltip 展示
  完整 JSON；双击载入编辑器、↑↓ 重排（UI 便捷项，语义无变化）、× 删除（等价 `rule remove`）。
- **结构化编辑器**（等价 `rule add` 的表单形态）：选择器类型循环选择（清单来自 `SelectorCodecs.itemTypeNames()`
  注册表枚举）→ 按类型出参数字段（id/tag/name/nbt 文本框，name 附 fuzzy/exact checkbox，composite 走原始
  JSON）；取反 checkbox；数量 = 不限量/count/group/fill_slots/percent 循环选择 + 数值框。
  **即时校验** = 构造 JsonObject → `SelectorCodecs.itemFromJson/quantityFromJson` round-trip，错误行内红色；
  **保存** = `WhCommands.upsertRuleEntryDirect`（与命令同一严格校验 + 回滚 + 落盘路径）。
- **物品选择网格** `ItemGridElement`：玩家背包 36 格快照 9 列（原版 18px 槽位），点击物品把注册 id 填进
  id 选择器参数；Modal 弹出。插件注册的额外 codec 类型自动出现在类型清单（label 兜底字面量）。
- **命令语法兜底**：tail 语法输入保留为 Modal 形态（复用 `WhCommands.addRuleEntryDirect`），
  服务高级用户与粘贴既有命令的场景。

### 5.4 Presenter 与共享数据组件

HUD 元素（详见 §6）与高亮渲染（详见 ui-highlight.md §7）都是 L3 组件，共享 ui-engine.md §3.3 的 `Value` 绑定机制。当前唯一的 Presenter 是 `HudPresenter`（订阅 WarehouseEvents → `Value` 更新，同时喂 HUD 与引擎页状态卡——"跨 UI 持续显示"即由此达成）；其余屏（仓库/选区/规则/世界/配置）数据量小、无跨屏复用需求，直接读 manager/mapper（打开时拉取 + 操作后刷新），不设 Presenter 中间层——屏多了再按需抽取。

### 5.5 世界映射页与配置页（等价 world/config 两组命令）

- **世界页** `WorldScreens`：会话身份卡（serverId / 当前 worldId / 激活 worldName / 当前维度，等价 `world info`）；
  worldName→worldId 映射列表（`*` 标激活名，等价 `world list`），每行 [绑当前会话]（快捷换绑恢复入口）与
  [绑 worldId…] / [重命名] Modal（等价 `world bind/rename`）；服务端报告维度列表。
  写操作直接经 `WorldNameMapper`（与命令 WorldGroup 同一调用序列）。
- **配置页** `ConfigScreens`：11 个配置项类型化控件——bool=checkbox、int=NumberField（范围钳制）、
  slotAllocator=注册表下拉（`WarehouseRegistryImpl.allocators()`）、reachLimit=double 文本框。
  开关/枚举即改即存，数值/文本**失焦（BLUR）落盘**（避免逐键写盘）；落盘路径与 `config set` 一致
  （`ConfigIO.saveModConfig`）；[重载配置] 等价 `/wh reload`。
- 导航：`ScreenHeader` 扩为七页（WAREHOUSE / ENGINE / RULES / SELECTION / WORLD / CONFIG / HUD_SETTINGS）。

---

## 6. HUD 设计

**交互模型**：HUD 是常规意义的抬头显示（Heads-Up Display）——**纯只读信息面板，明确不做任何按键/鼠标交互**（design-decisions.md D8）。它只显示信息；其配置经"HUD 设置屏"（一个普通 Screen，见 §6.2）完成。

### 6.1 内容

（可由配置逐项开关，`HudAlignment` 四角对齐，默认左上）：

| 区块 | 数据源 | 说明 |
|---|---|---|
| 仓库行 | `WarehouseManager.active()` | 当前仓库名 + 容器数；无激活仓库时提示"打开 UI" |
| 状态行 | `TRANSPORT_STATE` | `TransportState` 中文化（运行时长待补，见 todos） |
| 进度行 | `PROGRESS`（动作级）+ 轮次计数 + ITEM_MOVED 累计 | 动作描述（搬移/扫描/取物/放置）；微型进度条待接线（见 todos） |
| 选区行 | `SelectionState` | 未设时隐藏；`pos1 (x,y,z) → pos2 (x,y,z)  WxHxD` |
| 标记行 | `MarkMode.Session` | 标记模式中显示 session 参数（type/rule）；templateId 与操作提示待补（见 todos） |
| 报告行 | `RUN_FINISHED` | 结束后 8s 内显示 `RunGrade` + 摘要（160 tick 计时由 HUD 侧自持） |
| 快捷键提示 | ui-interaction.md §8 表 | 仅在选区/标记模式或按下 alt 时显示（待实现，见 todos） |

**渲染纪律**（AppleSkin 模式）：无数据区块直接跳过；区块内容每 tick 刷新字符串、经 `Value.set` 的等值去重避免无谓重排（tickCounter 键缓存为其等价实现）；文字行数裁剪上限（`maxLines`，默认 8）；每角共享一个半透明背景框（角落包装面板，区块按 order 纵向排布其中）。

### 6.2 HUD 设置屏（编辑模式）

HUD 本体永不接收输入；对其位置与内容的调整经一个 passthrough Screen 进行（MaLiLib/Wurst 的 HUD 编辑思路）：

- 入口：仓库管理主屏"HUD 设置"按钮（或快捷键）→ `openScreenKeepHud`（`hudPassthrough=true`：跳过 MC 模糊/菜单背景，世界透出）。
- **z 序**：HUD 层在 `extractGui` 更早 stratum，必被 Screen 盖住——passthrough 屏打开期间 `Mc261HudHost` 不在 HUD 层渲染，改由 `Mc261ScreenHost` 在自身内容提取后**代渲染 HUD**：设置中的 HUD 永远显示在面板之上，被面板遮住也能先拖走。
- 编辑交互：屏幕中央面板逐区块开关（checkbox）、行拖拽排序（捕获阶段，越行高换位，滑条上起拖除外）、滑条调文字缩放（0.1 步进，`SliderElement`：拖动实时预览 + 标签就地刷新，**不整屏重建**——重建首帧中央面板闪现左上角曾是缺陷根源；落盘推迟到 DRAG_END/CLICK）；**按住 HUD 本体拖拽 = 平移该角组**——按 `HudLayout` 布局期记录的每角真实 bounds（±2px 余量，区块全关即失效）抓取（捕获阶段抢占，面板行不干扰），附近空白退回象限猜测；方向键 1px 微调。中央面板定位 = `OverlayLayer` 根级 `grow(1)` 满铺 + `Center` 布局器布局期居中钳制（不依赖 20Hz tick）。
- 跟手性：拖拽增量在 double 累加器中保留小数残量（GUI Scale≥2 时单帧位移不足 1px，直接取整会整体卡顿），逐像素跟手；offset 钳在 `[0, 屏-8px]`，组永远留在屏内可再抓取。
- 持久化：`config/yuanlu-warehouse-hud.json`：`{ blocks: { "<id>": { enabled, corner, offsetX, offsetY, order, maxLines, scale } } }`，拖拽中仅写内存、START/END 落盘。
- **模拟容器参照与贴靠**：中央面板「模拟容器」行（启用 + 预设循环选择，会话级不落盘）——
  `ContainerGhostElement` 按原版 GUI 尺寸与贴图分段 blit 渲染常见容器预设（单箱27/双箱54/潜影盒/投掷器/熔炉/
  背包段，generic_54 body+背包段 UV 拼接；`UiDraw.blit` 纹理区域端口），屏幕居中、zIndex -1 不拦输入；
  拖拽 HUD 组时以容器矩形为 snap 目标——`HudLayout.snapDelta` 四向边缘最小修正（阈值 6px，纯函数可 JUnit），
  吸附成功沿容器被对齐的边画 1px accent 引导线。用途：把 HUD 拖到不遮挡容器界面的位置/贴靠容器边缘（design-decisions.md D15）。
- 实现要点：拖拽命中/移动全部是 Screen 内常规输入事件，走 L1 事件系统（DRAG_*），无跨版本风险。
