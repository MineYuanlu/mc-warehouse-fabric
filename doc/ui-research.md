# UI 层前置调研报告（ui-research）

> 目的：为自研 UI 引擎设计提供依据。结论先行——**MC 26.1 官方已提供"提取式渲染 + Gizmos 高亮 + Fabric HudElement + 布局器"的完整地基，自研引擎的合理规模是"轻量元素树 + 事件冒泡 + 数据绑定 + 声明式设置"，布局/绘制/高亮/HUD 全部站在 vanilla 与 Fabric API 之上，而不是重造**。
>
> 本报告区分两类内容：**设计思想**（跨版本可借鉴）与 **API 细节**（以 `refs/dep-src/minecraft/` 26.1 反编译为准）。参考 mod 多面向 1.21.x 或更早 API，API 细节不可直接照抄。

---

## 目录

1. [调研范围与获取方式](#1-调研范围与获取方式)
2. [MC 26.1 官方 API 基线](#2-mc-261-官方-api-基线)
3. [参考项目逐节分析](#3-参考项目逐节分析)
4. [横向对比与设计思想提炼](#4-横向对比与设计思想提炼)
5. [零命令等价 UI 能力映射](#5-零命令等价-ui-能力映射)
6. [UI 引擎建议方向](#6-ui-引擎建议方向)

---

## 1. 调研范围与获取方式

全部参考源码在本地（均 gitignore，不入库）：

| 来源 | 版本 | MC | License | 本地位置 | 获取方式 |
|---|---|---|---|---|---|
| Minecraft 本体 | 26.1 | 26.1 | — | `refs/dep-src/minecraft/` | `python tools/gen_refs.py mc`（fernflower，无混淆） |
| Wurst7 | v7.55 | **26.2** | GPL-3 | `refs/mods/Wurst7/` | `git clone --depth 1` |
| Create | 6.0.11 | 1.21.1 | MIT+FAQ | `refs/mods/Create/` | `git clone --depth 1` |
| LDLib2 | 2.2.37 | 1.21.1 | LGPL-3.0 | `refs/mods/LDLib2/` | `git clone --depth 1 -b 1.21` |
| Litematica | 0.26.13 | 1.21.11 | LGPL-3 | `refs/dep-src/litematica/` | Modrinth jar + fernflower |
| MaLiLib | 0.27.18 | 1.21.11 | LGPL-3 | `refs/dep-src/malilib/` | 同上 |
| IPN | 2.2.6 | 1.21.11 | MIT | `refs/dep-src/ipn/` | 同上（mixin/GUI 层保留原名，核心被 proguard 混淆） |
| libIPN | 6.6.3 | 1.21.1 | MIT | `refs/dep-src/libipn/` | 同上 |
| EMI | 1.1.24 | 1.21.1 | MIT | `refs/dep-src/emi/` | 同上 |
| AppleSkin | 3.0.8 | 1.21.11 | MIT | `refs/dep-src/appleskin/` | 同上 |
| Neat | 1.21-47 | 1.21.1 | LGPL-3 | `refs/dep-src/neat/` | 同上 |

- **LDLib2 是 NeoForge 专属**（README 明确不支持 Fabric），只做设计思想参考，禁止抄码（LGPL）；Wurst7 为 GPL-3 同理。
- Wurst7 已适配 MC 26.2，是唯一与本 mod 同代 API 的参考，其渲染/注入代码的 API 用法可信度最高。
- 反编译源码中旧版本 mod 的 MC 类是 intermediary 名（`class_437`=Screen、`class_332`=GuiGraphics、`class_465`=AbstractContainerScreen 等）。

---

## 2. MC 26.1 官方 API 基线

> 来源：`refs/dep-src/minecraft/net/minecraft/client/...`（下文简写 `client/...`）。这一节是设计 UI 引擎的**地基事实**，优先级最高。

### 2.1 全局架构变化：提取式渲染（extract render state）

26.1 的 GUI 已从"即时渲染"重构为**提取-渲染两阶段**：

- 旧 `GuiGraphics` **不存在**，2D 绘制入口是 `client/gui/GuiGraphicsExtractor.java`（926 行），每帧由 `GameRenderer.extractGui(...)` 新建。
- `Renderable` 接口的方法是 `extractRenderState(...)` 而非 `render(...)`。提取出来的都是不可变 render state，**不能跨帧保存绘制对象引用**。
- 2D 变换用 JOML `Matrix3x2fStack`（`graphics.pose()`），不再是 3D PoseStack；HUD 类仍叫 `client/gui/Gui.java`（不叫 Hud），**26.1 已移除 `LayeredDraw`**。
- 每个 draw 调用固定当前 pose 快照；`fill/blit/text` 全部把 pose 快照存入 `GuiRenderState`。

### 2.2 Screen 生命周期（26.1）

`client/gui/screens/Screen.java`：

- `init(width,height)` 由 `Minecraft.setScreen` / resize 调用；首次走 `init()` + `setInitialFocus()`，resize 走 `repositionElements()` → `rebuildWidgets()`（clearWidgets → init 全量重建）。**"init() 全量重建"约定仍在**。
- 渲染固定顺序：`extractRenderStateWithTooltipAndSubtitles` = `nextStratum()` → `extractBackground()` → `nextStratum()` → `extractRenderState()`（遍历 renderables，插入序）→ `extractDeferredElements()`（tooltip 在最后保证置顶）。
- `addRenderableWidget`（画+收输入）/`addRenderableOnly`（只画）/`addWidget`（只收输入）三表分离；`AbstractContainerEventHandler.getChildAt` 命中转发 + 完整 Tab/方向键焦点导航算法已内建。
- 事件签名已事件对象化：`mouseClicked(MouseButtonEvent, boolean)`、`keyPressed(KeyEvent)`、`charTyped(CharacterEvent)`（`net.minecraft.client.input.*`）。
- 打开自定义 Screen：直接 `minecraft.setScreen(new MyScreen(...))`，无注册、无服务端 menu（`MenuScreens` 只在接服务端 menu 时才相关）。
- tooltip：`WidgetTooltipHolder.refreshTooltipForNextRenderPass` + `graphics.setTooltipForNextFrame(...)` 暂存通道，永远在最上层。

### 2.3 绘制 API（GuiGraphicsExtractor 关键签名）

```
nextStratum() / blurBeforeThisStratum()      // 分层（blur 每帧仅一次）
pose(): Matrix3x2fStack                       // pushMatrix/translate/rotate/scale
enableScissor(x0,y0,x1,y1) / disableScissor() // 栈内自动求交集
fill / fillGradient / outline / horizontalLine / verticalLine
blit(RenderPipeline, Identifier, x, y, u, v, w, h, texW, texH[, color])
blitSprite(RenderPipeline, spriteId, x, y, w, h[, color/alpha])  // 自动处理 NineSlice 元数据
text / centeredText / textWithWordWrap / textWithBackdrop
item(ItemStack, x, y[, seed]) / itemDecorations(Font, stack, x, y)   // 物品图标（含方块物品）
entity(EntityRenderState, scale, ...)          // PiP 3D 小窗
requestCursor(CursorType)                      // 鼠标光标
```

### 2.4 vanilla 布局器与现成控件（可直接复用，不重写）

`client/gui/layouts/`：`LinearLayout`（vertical/horizontal + spacing）、`GridLayout`（+ RowHelper）、`EqualSpacingLayout`、`FrameLayout`（`centerInRectangle`/`alignInRectangle`）、`HeaderAndFooterLayout`（配 `AbstractScrollArea` 做滚动页）。约定：`init()` 里 addChild → `arrangeElements()` → `visitChildren(this::addRenderableWidget)`。

现成控件：`Button/EditBox/CycleButton/Checkbox/AbstractScrollArea/AbstractSelectionList/MultiLineEditBox/StringWidget/ImageWidget/Tooltip`。

### 2.5 HUD 扩展点（Fabric API 新版）

vanilla `Gui.extractRenderState` 是单体硬编码方法，本体无注册点。**注入点在 Fabric API**（依赖 `fabricApiVersion=0.155.2+26.1.2`，旧的 `HudRenderCallback`/`HudLayerRegistrationCallback` 已移除）：

```java
// net.fabricmc.fabric.api.client.rendering.v1.hud
HudElementRegistry.addLast(Identifier id, HudElement);      // 也有 addFirst / attachElementBefore/After
interface HudElement { void extractRenderState(GuiGraphicsExtractor, DeltaTracker); }
VanillaHudElements.HOTBAR / CROSSHAIR / CHAT / ...            // 锚点 id
```

签名与 vanilla 提取模型完全一致——**HUD 层的官方正路**。

### 2.6 世界空间高亮：Gizmos 体系（26.1 新正式 API）

包 `net/minecraft/gizmos/` + `client/renderer/gizmos/`。**PDD 计划的 `WorldRenderer.collectPerFrameGizmos` mixin 注入已不再必要——官方 API 直接开放**：

```java
// 静态门面，ThreadLocal 收集器
Gizmos.cuboid(AABB|BlockPos, GizmoStyle)          // 盒子
Gizmos.line(start, end, argb[, width=3]) / arrow / rect / point / circle
Gizmos.billboardText(String, Vec3, TextGizmo.Style)  // 世界内文字（随相机旋转）
// 样式
GizmoStyle.stroke(argb[, width]) / fill(argb) / strokeAndFill(stroke, width, fill)  // 0=无
// 属性
props.setAlwaysOnTop()   // 独立 late_debug pass，先 clearDepth 再画 → 穿墙置顶
props.persistForMillis(int) / props.fadeOut()
```

提交作用域：`levelRenderer.collectPerFrameGizmos()` 返回 `Gizmos.TemporaryCollection`（try-with-resources，**窗口内任何代码提交都有效**，不需要 mixin）；每 tick 版本 `Minecraft.collectPerTickGizmos()` 配 `persistForMillis` 可做跨帧存活。深度测试正常组渲染在半透明地形之前，alwaysOnTop 组在独立 pass。

**盒子高亮最短路径**：`try (var g = mc.levelRenderer.collectPerFrameGizmos()) { Gizmos.cuboid(aabb, GizmoStyle.strokeAndFill(0xFFFF0000, 2.5f, 0x40FF0000)); }`。

### 2.7 vanilla 缺口（自研引擎要补的胶水）

1. **通用嵌套元素树**：vanilla 树深仅 2 层（Screen→widget），无任意嵌套容器、无 capture/bubble 冒泡（命中即停）、无 z-index——需要自研 Node 基类。
2. **样式/主题/动画**：只有 `WidgetSprites` 三态贴图与 `fadeWidgets`；hover 过渡、主题色、程序化圆角需自建。
3. **HUD（非 Screen）收输入**：MouseHandler 只派发给 `minecraft.screen`；HUD 上的可点击控件需要 mixin 接管或 Fabric screen event。
4. **每帧任意绘制入口**（无 Screen 时）：走 `HudElementRegistry`。

---

## 3. 参考项目逐节分析

### 3.1 Wurst7（MC 26.2）——世界空间渲染配方 + 轻量 ClickGUI

**定位**：唯一同代（26.x）参考；ESP 渲染与"高亮世界中的容器"需求 1:1 对应。

**ESP 渲染核心链**（关键类均在 `src/main/java/net/wurstclient/`）：

| 文件 | 内容 |
|---|---|
| `util/RenderUtils.java` | 3D 绘制原语：`drawOutlinedBox(es)`/`drawSolidBox(es)`/`drawTracers`，基于 AABB+顶点填充 |
| `WurstRenderLayers.java` | 自定义 26.x `RenderType`（`net.minecraft.client.renderer.rendertype` 包）：`getLines(depthTest)`/`getQuads(depthTest)` |
| `WurstShaderPipelines.java` | 自定义 `RenderPipeline`；**深度测试开关 = `withDepthStencilState(DepthStencilState.DEFAULT)` vs `Optional.empty()`** |
| `util/WurstBufferSource.java` | 替代已删除的 `MultiBufferSource`，包装 `StagedVertexBuffer` |
| `hacks/ChestEspHack.java` + `hacks/chestesp/*` | 容器 ESP：每 tick 收集 BlockEntity → 14 个分组（每组 Checkbox+Color）→ 半透明填充+描边 |

**关键模式**（可跨版本照抄思路）：
- **相机坐标相减而非 W2S 投影**：`LevelRendererMixin` 在 `LevelRenderer.render` RETURN 处把当帧 ModelView 矩阵传入事件；hack 层把每个盒子顶点先减相机位置（`applyRenderOffset`），交 vanilla 管线完成投影。另有 `applyRegionalRenderOffset` 按区块减偏移防 float 精度抖动。
- **线宽是顶点属性**：26.x 里 `VertexConsumer.setLineWidth(2)` + `POSITION_COLOR_NORMAL_LINE_WIDTH` 顶点格式（不再是 `RenderSystem.lineWidth`）。
- **26.2 已知的版本漂移**（26.1→26.2 迁移警示）：`GuiGraphics`→`GuiGraphicsExtractor`、`Gui`→`Hud`、`MultiBufferSource` 已删（`StagedVertexBuffer` 替代）、`guiRenderState.up()` 分层调用几乎每处 fill/text 之间都要调。
- 事件解耦：`EventManager` 静态 fire + hack enable/disable 时 add/remove listener，mixin 只 fire 事件不含业务。

**ClickGUI**（`clickgui/`）：薄壳 Screen（72 行）转发输入 → 自建 Window/Component 层（核心约 2800 行），手写命中测试、拖拽、滚动、窗口置顶、JSON 布局持久化、pinned-window（设置窗可钉在 HUD）。`Setting.getComponent()` 模式：**每个 Setting 声明自己的 UI 组件，Feature 构造器里 `addSetting(new SliderSetting(...))` 即完成声明，SettingsWindow 零手写装配**。

**评价**：渲染配方整套可搬思路（尤其 Gizmos 未覆盖的自定义管线场景）；`Setting.getComponent()` 声明式设置值得吸收；ClickGUI 自建层是历史包袱（要悬浮+拖拽布局），仓库配置屏用 vanilla Screen 体系更省；每 tick 全量重建 boxes 需改为事件驱动增量。

### 3.2 Create / Catnip —— 交互设计宝库

**定位**：UI 引擎本体在 Catnip 库（`net.createmod.catnip.gui`，maven `maven.createmod.net` 有 sources jar；**1.16 时代的 FlexScreen 已删除**，现版无声明式布局引擎）。

**架构**：`AbstractSimiScreen extends vanilla Screen`——"每次 init 全量重建 + `setWindowSize` 偏移居中"约定；widget 用 `withCallback(lambda)` 回调（非事件总线）；`TickableGuiEventListener` 每帧动画广播；`NavigatableSimiScreen` 有屏幕导航栈 + FBO 缩放转场。

**最值得吸收的交互设计**（与版本/加载器无关）：
1. **互斥 IconButton 组 + green 指示灯**（`content/logistics/filter/FilterScreen.java`）：白/黑名单切换不是 toggle 控件，而是成对按钮、当前项置绿；三值模式（白名单 OR/AND/黑名单）三个按钮一排。**这正是仓库规则模式切换的模板**。
2. **Ghost 槽 + 每 tick `ItemStack.matches` 检测驱动刷新**（`AttributeFilterScreen` + `foundation/gui/menu/GhostItemMenu.java`）："点物品选择但不拿走"的标准答案。
3. **`BoxElement` 纯顶点色渐变面板**（Catnip `gui/element/BoxElement.java`）：无贴图、参数化颜色、一个 BufferBuilder 画完（外圈背景+内圈上下双色渐变边框），**移植成本≈0**，适合列表条目/卡片。
4. **HEADER/BODY/FOOTER 三段拼接**做高度可变窗口（`StockKeeperRequestScreen`，1830 行的物品网格+搜索+购物车范本）：比 9-patch 简单。
5. **搜索防抖 + `@`/`#` 前缀语法**、ScrollInput 滚轮数值输入（shift 大步长+音调反馈）。
6. `ValueSettingsScreen`：hover 吸附 + 松开确认 + `GLFW.glfwSetCursorPos` 锁鼠标的轻量方块配置交互。

**不可直接用**：Catnip 依赖 NeoForge 注入与 1.21.1 GL 状态；`AllGuiTextures` enum 硬编码贴图维护性一般（建议 record/程序化绘制替代）。

### 3.3 LDLib2 ModularUI —— 自研引擎的"参考答案"（设计思想）

**定位**：NeoForge 专属不可依赖，但它是单 mod 可学习的最完整现代 UI 框架（1.21.1）。核心目录 `com/lowdragmc/lowdraglib2/gui/`。

**核心架构**（思想级摘要）：
1. **单一 `UIElement` 树（DOM 思路）**：`gui/ui/UIElement.java`（2381 行）——parent+children、`id`/`classes`/伪类用保留 class（`__hovered__`/`__focused__`/`__disabled__`）、`hitTest` 逆变换+矩形命中+zIndex 降序。
2. **布局引擎是第三方 Taffy（Rust taffy 的 Java 移植，Flexbox+Grid）**：布局样式是纯数据 `TaffyStyle`，元素只 `markDirty(nodeId)`；`ModularUI.calculateStyleAndLayout()` **脏驱动**（styleEngine.requireCalculate() || taffyTree.isDirty() 才算，循环上限 10 防死锁），渲染每帧只读 Layout 结果。
3. **DOM 三阶段事件**（`gui/ui/event/UIEventDispatcher.java`，共 217 行，可直接照抄思路）：`UIEvent`（type/target/phase/坐标/按键/stopPropagation）+ capture（根→父）→ AT_TARGET → bubble（父→根）；click/dblclick(300ms)/enter/leave/drag 全家在 Widget 层合成一次，元素端零负担；`ModularUIWidget` 是唯一 GuiEventListener 适配器（MC Screen 只是 64 行的壳）。
4. **StyleBag 槽位竞标**：`StyleSlot(property, origin, specificity, sourceOrder, value)`，`compareTo` 只有 5 行——以 `StyleOrigin` 优先级（DEFAULT<STYLESHEET<INLINE<ANIMATION<IMPORTANT）解决"默认样式/主题/内联/动画"四大来源冲突，比完整 CSS 引擎简单一个量级。
5. **数据绑定三层**：`IDataProvider`（getValue+registerListener）/`IObserver`/`IDataSource`（双向）；`BindableUIElement.bindDataSource` 时 ITickable source 挂 TICK 事件 20Hz 驱动；**监听器推送而非脏标记**。
6. **HUD 复用同一渲染管线**：`gui/hud/ModularHudLayer.java` 仅 62 行——`getModularUI()` + 分辨率变化时 re-init，然后直接 `mui.getWidget().render(...)`（鼠标参数传 MAX_VALUE）。**HUD 与 Screen 同构**。
7. **渲染层**：`IGuiTexture` 极小接口（`draw(...)`）+ **`SDFRectTexture` 程序化圆角/描边**（一个 QUAD + fragment shader，uniform 传四角半径/填充/边框色，支持 Oklab 插值动画）——远胜切图。
8. 每帧一次 `refreshHoveredElement` 缓存 hover（输入事件只查缓存）。

**对单 mod 的取舍判断**：
- **舍弃**：C↔S 同步整套（UISyncManager/SyncValue/RPC/UIEvent 服务端转发——纯客户端完全不需要，这是 LDLib2 最庞大的一块）、KubeJS 集成、可视化编辑器、UITemplate 网络传输、完整 CSS 级联（specificity/NOT 选择器/scoped stylesheet）、多 OS 窗口。
- **吸收**：元素树+Taffy 思想（或只取 Flexbox 语义自写简化 solver）、DOM 事件三阶段、StyleBag 极简级联、IGuiTexture+SDF 程序化绘制、hover 缓存、单一 Widget 适配器壳、BindableUIElement 推送绑定、HUD=Screen 同构复用。

### 3.4 MaLiLib / Litematica —— overlay 渲染 + 配置 GUI + 选区交互

**定位**：世界空间 overlay（选区框/高亮）与声明式 hotkey/配置框架的最成熟参考。注意其 GUI 渲染深度绑定 1.21.6+ 管线（`GuiContext extends GuiGraphics`、frame-graph pass 注入），API 不可搬，**模式可搬**。

**MaLiLib 架构要点**（`fi/dy/masa/malilib/`）：
1. **RenderEventHandler 分发器 + IRenderer 宽接口**（`event/RenderEventHandler.java`）：一个静态注册表把 overlay-HUD / world-last / tooltip-last 等钩子从 mixin 解耦，mod 只需 2 个注入点 + dispatcher。
2. **RenderContext + MaLiLibPipelines 管线常量表**（`render/`）：`DEBUG_LINES_*_LEQUAL_DEPTH`（线框）/ `*_NO_DEPTH_NO_CULL`（穿墙）/ `POSITION_COLOR_TRANSLUCENT_LEQUAL_DEPTH(_OFFSET)`（半透明面，相机在盒内换 OFFSET 防裁剪）——三档管线覆盖全部高亮需求。
3. **RenderUtils 盒绘制签名**（2193 行工具库）：`renderAreaOutlineNoCorners(pos1,pos2,三轴三色)`（主体 12 棱按 XYZ 分三色、跳过角点棱）+ `renderAreaSides(半透明六面)` + `renderBlockOutline(角块单画)` + `expand=0.001` 防共面 z-fighting——**直接对应本 mod 框选（两角点）需求**。
4. **On-demand 渲染调度**（`render/on_demand/*Renderer` + 不可变 `*RenderState`）：逻辑线程 schedule → 渲染线程统一绘制，解决跨线程高亮请求；`WallOverlayRenderer` 的墙面等距线阵可做货架分格。
5. **KeybindMulti + KeybindSettings**：声明式热键（held-modifier、context=INGAME/GUI、activateOn、cancel 消费、overlaps 冲突检测）——"按住 modifier + 滚轮调选区"交互的基础设施。
6. **InfoHud renderer 注册表**（`render/infohud/InfoHud.java` + `IInfoHudRenderer`）：可插拔 HUD 文本行/自绘元素，含 `HudAlignment` 四角定位与缩放——进度/状态 HUD 的排版一次到位。
7. **GuiBase 配置屏骨架**：绝对坐标 + buttons/widgets/textFields 分表 + 悬停层最后画 + 列表"只实例化可见行" + 屏内 MessageRenderer 消息条 + "打开后 100ms 吞掉首字符"细节。

**Litematica 模式**：`OverlayRenderer.renderSelectionBox` 的 BoxType 分层配色（选中/未选中/放置/角点）+ KELLY_COLORS 调色板轮转 + placement 颜色按哈希缓存；数据-渲染分离（`SelectionManager/Box` 纯数据 + 自定义射线 HitType 枚举）；**GrabbedElement 拖拽模型**（grab→每帧 moveElement→release+滚轮调距，CORNERS/EXPAND 双模式）比"两点依次放置"好用得多。

### 3.5 IPN / libIPN —— 容器界面覆盖层（场景最贴近）

**定位**：在**别人的**容器 Screen 上叠加 UI + 操作格子的标杆。IPN 本体核心被混淆但 mixin/GUI/inventory 层保留了原始包名（`org.anti_ad.mc.ipnext.*`），恰好是所需部分。

**关键实现**：
1. **覆盖层注入三段式**（`mixin/MixinContainerScreen.java` → `gui/inject/ContainerScreenEventHandler.java` 单例分发）：`drawBackground` 后（画在物品之下，做底色块）→ `drawForeground` 后（画在物品之上，做角标 sprite）→ GameRenderer postRender（点击高亮/tooltip）。注入后 `pushMatrix + translate(-x,-y)` 回到容器相对坐标——上层代码全部用 slot 相对坐标。
2. **格子坐标系统**：`ingame/VanillaAccessorsKt`（mixin accessor 拿 HandledScreen 的 x/y/背景宽高私有字段 + slot.x/y）→ `屏幕坐标 = containerBounds.topLeft + slot.topLeft`。
3. **AreaType 区域抽象**（`inventory/AreaTypes.java`）：可组合的 slot 集合（`itemStorage`/`playerHotbar`/`+`/`-` 运算）+ `isRectangular` 网格识别 + ContainerTypes 容器分类注册表——**"标记哪些格子属于输入/输出区"的直接对应物**。
4. **协议层点击**（`inventory/ContainerClicker.java`）：直接调 `ClientPlayerInteractionManager.clickSlot`（不碰鼠标）；**沙盒预演**（`ContainerSandbox` 虚拟容器模拟光标拾取放置 → `diffcalculator` 生成最少点击序列）→ 批量分帧执行 + 格子白色高亮反馈。（本 mod 已有等价 engine/container 层，沙盒预演思想可对照。）
5. **输入拦截管线**（libipn `common/mixin/MixinKeyboard|MixinMouse` HEAD-cancel + `GlobalInputHandler` 两级链 + `isInputFieldActive` 守卫）：容器内搜索框/快捷键不干扰 vanilla 的前提。
6. **自绘 tooltip 队列**（`TooltipsManager` 静态队列 + 每帧末 renderAll）、聊天 `listenLog` 反馈、音效反馈。
7. libipn 配置框架：`ConfigDeclarationBuilder` DSL（key=i18n key）+ 分类列表 + 右上角搜索过滤 + `ConfigXxxWidget` 全家。

**注意**：IPN 注入控件不注册进 vanilla Screen.children，鼠标事件全靠 Keyboard/Mouse mixin 抢先——自研时应优先 `addSelectableChild` 与 vanilla 事件共存（26.1 有完整焦点导航）。

### 3.6 EMI —— 物品网格与搜索的工程范本

**定位**："仓库清单"视图（物品索引+搜索+分类）的直接参考。`dev/emi/`。

1. **分页而非无限滚动**（`screen/EmiScreenManager.java`，2043 行）：`ScreenSpace(tx,ty,tw,th + int[] widths + pageSize)`，渲染只画 `stacks[startIndex .. +pageSize)`，命中检测是整数除法 `(mx-tx)/18`——几千物品只画一屏。滚轮 `scrollAcc` 亚像素累积。
2. **批量渲染**（`screen/StackBatcher.java`）：整页物品烘进 `Map<RenderLayer, VertexBuffer>` 一次上 GPU，每帧只平移矩阵重放；失败的 stack 回退即时模式。（单屏几十个时不需要；>500 才值得。）
3. **搜索三件套**（`search/EmiSearch.java`）：预构建小写前缀索引（SearchTree bake 一次）→ 每字符**后台线程搜索 + worker 代际淘汰**（只留最新 worker，处理 1024 个检查一次，比防抖简单可靠）→ `volatile List` 原子换结果。token 语法：`@mod`/`#tag`/`$tooltip`/`-`取反/`/regex/`/`"phrase"`。
4. **索引构建**（`registry/EmiStackList.java`）：按分组建 `IndexGroup`，`suppressedBy` 抑制重复，后台线程构建、主线程只读不可变 List。
5. **叠加绘制与互操作**：mixin 到 `HandledScreen` 的 drawBackground 后/drawForeground 后（z 抬 200/300/400 分层）；GLFW 层 Mouse/Keyboard mixin 拦截输入（宿主 Screen 零改动）；`EmiScreenBase.of(screen)` 判定可挂性（Bounds 为空完全不渲染不拦输入）。
6. 点击路由 `stackInteraction`：一个 `Function<EmiBind,Boolean>` 同时服务鼠标/键盘，按绑定优先级链路由。

### 3.7 AppleSkin / Neat —— 轻量 HUD 模式

**共同点**：无框架、命令式直接画；**精确注入到 vanilla 已有绘制点之后**而非独立全屏层；开关短路 + tick 级缓存 + 无每帧分配。

- **AppleSkin**（`squeek/appleskin/`）：mixin 注入 InGameHud 的 hunger/health 具体方法（天然拿到原版偏移与 partialTick）；`HeldFoodCache` 按 guiTick 键缓存（一 tick 算一次，帧内零开销）；**动画用 client-tick 步进 alpha（0.125F/tick 折返）而非每帧重算布局**；只画增量图标（底图 0.25 alpha + 前景 flashAlpha 三层结构）；绘制前发事件允许其它 mod 取消。
- **Neat**（`vazkii/neat/`）：生物血条画在**世界空间**（`LevelRenderer.renderEntity` 之后注入），billboard 靠"translate 插值位置 → rotate(相机 yaw) → rotate(180°) → scale(0.0267)"变换链而非手动投影；自定义 RenderType + MultiBufferSource 顶点；血色按 `hslColor(hp/max)` 色相梯度；`shouldShowPlate` 十几条前置短路（距离→视线→类型→黑名单）。配置：cloth-config AutoConfig 注解 + ModMenu 一行生成配置屏。
- **对本 mod 的启示**：传输进度条可套 AppleSkin 三层结构；进度/状态挂在世界方块上方可套 Neat billboard 变换（但 26.1 有 `Gizmos.billboardText`，多数场景一行搞定）。

---

## 4. 横向对比与设计思想提炼

| 维度 | vanilla 26.1 | Wurst7 | Create/Catnip | LDLib2 | MaLiLib | IPN | EMI |
|---|---|---|---|---|---|---|---|
| 元素模型 | 2 层树+焦点导航 | 自建 Window/Component | SimiWidget lambda 回调 | UIElement DOM 树 | 绝对坐标分表 | Widget 树（注入式） | 无（过程式+分页） |
| 布局 | layouts 包 | 无 | init 全量重建 | **Taffy Flexbox 脏驱动** | 无 | Flex/Anchor | 网格+分页+避让 |
| 事件 | 命中即停 | Screen 转发自建层 | 回调 | **DOM capture/bubble** | 分表短路链 | GLFW mixin 抢先 | GLFW mixin 抢先 |
| 样式 | WidgetSprites 三态 | 代码常量 | 代码常量+BoxElement | **StyleBag 级联+LSS+SDF shader** | 代码常量 | DSL+i18n | 代码常量 |
| 数据绑定 | 无 | 无 | 每 tick 检测 | **监听器推送** | config 暂存 | 每 tick | tick 缓存 |
| 世界空间 | **Gizmos API** | 自定义 RenderType/Pipeline | 无 | 无 | RenderContext+管线表 | 无 | 无 |
| HUD | Gui 硬编码 + Fabric HudElement | GUIRenderEvent mixin | 无 | **HUD=Screen 同构** | IRenderer 注册表 | InGameHud 注入 | — |
| 覆盖别人 Screen | — | — | — | — | InventoryOverlay | **三段注入** | drawBackground/Foreground 注入 |

**提炼出的五条核心思想**（自研引擎的设计准则）：

1. **提取式渲染是 26.x 的现实**：一切 UI 都实现 `extractRenderState(GuiGraphicsExtractor,...)`；提取阶段只读数据、无副作用；tooltip 走 `setTooltipForNextFrame`；stratum 管层级。
2. **数据单向流：事件 → 状态缓存 → 每帧提取**。所有参考的成功 mod 都不是"数据变化→立即重绘"，而是"逻辑线程更新不可变状态 → 渲染帧提取"（MaLiLib on-demand RenderState、AppleSkin guiTick 缓存、EMI volatile List 原子替换）。这天然匹配本 mod 已有的 `WarehouseEvents` 事件驱动架构。
3. **引擎三件套的最小集**：元素树（嵌套+命中+zIndex）+ 事件冒泡（capture/bubble，217 行量级）+ 数据绑定（IDataProvider/IObserver 两接口，监听器推送）。样式/动画是加分项不是地基。
4. **世界空间交给 Gizmos，Screen 内交给 vanilla**：高亮不需要自定义 RenderPipeline（除非要特殊效果），线框/填充/文字/穿墙全是现成 API；不要重犯 MaLiLib/EMI 的 GLFW mixin 抢输入（能用 vanilla 焦点系统就别抢）。
5. **性能三板斧**：只画可见（分页/裁剪/短路）、tick 级缓存（guiTick 键）、增量动画（tick 步进 alpha）。

---

## 5. 零命令等价 UI 能力映射

对照 `doc/commands.md` 命令总表，给出每条命令的 UI 形态。原则：**读操作进 Screen/HUD，写操作中"世界相关"的走世界交互（准星/选区/标记模式），"配置相关"的走 Screen**。

| 命令 | UI 等价物 | 形态 | 参考范式 |
|---|---|---|---|
| `help` | 不需要（UI 自解释）；保留 `wh.report` 输出 | — | — |
| `list` / `list <name>` | 仓库管理屏：仓库列表（卡片/列表条目）→ 详情 | Screen | MaLiLib GuiListBase + Create BoxElement |
| `create <name>` | 列表屏"新建"按钮 → 名字输入对话框 | Screen | vanilla EditBox |
| `remove` | 详情屏删除按钮（二次确认对话框） | Screen | — |
| `use` | 列表条目"激活"按钮（当前项 green 指示） | Screen | Create FilterScreen 互斥按钮组 |
| `status` | **HUD 常驻状态行**（激活仓库名/引擎状态/进度）+ Screen 概览页 | HUD+Screen | MaLiLib InfoHud + AppleSkin 缓存模式 |
| `show` | 概览页容器明细表（分区/类型/规则/缓存） | Screen | — |
| `anchor set` | 世界交互：设置屏"设锚点"按钮→回世界→准星指方块确认（或 HUD 快捷键直接取脚下） | 世界+HUD | Litematica 角点交互 |
| `reload` | 概览页"重载"按钮 | Screen | — |
| `start/stop/continue/restart/abort` | HUD 常驻控制条（当前状态对应启用/禁用按钮）或概览页按钮组 | HUD+Screen | — |
| `container add` | 世界交互：标记模式（已有 MarkMode）+ 配置弹窗（type/rule 选择） | 世界+Screen | IPN 覆盖层 + Create 模式按钮 |
| `container mark` | 标记模式开关 = HUD 快捷键/配置屏按钮；**高亮待标记容器** | 世界(Gizmos)+HUD | Wurst7 ChestEsp |
| `container remove` | 世界交互：准星指容器 + HUD 快捷键；或明细表删除按钮 | 世界+Screen | — |
| `container type/mode` | 明细表行内编辑（下拉/互斥按钮组） | Screen | Create 三值模式按钮组 |
| `container rules` | 规则绑定：明细表行内规则选择器 / 规则编辑屏拖拽绑定 | Screen | — |
| `container memory` | 明细表行"缓存"徽标（点击查看/清除） | Screen | — |
| `rule list/show` | 规则编辑屏：规则列表 → 条目列表 | Screen | MaLiLib 配置屏 |
| `rule create/delete` | 规则屏新建/删除（被引用时禁用+tooltip 说明） | Screen | — |
| `rule add` | **物品选择器编辑器**：物品网格+搜索（`@`/`#` 语法）→ ghost 槽 + 选项（数量选择器等） | Screen | **EMI 网格/搜索 + Create ghost 槽** |
| `rule remove` | 条目列表删除按钮 | Screen | — |
| `select pos1/pos2` | 世界交互：准星设角（HUD 快捷键），**选区盒 Gizmos 实时渲染**（三轴三色+角块高亮） | 世界(Gizmos)+HUD | Litematica renderSelectionBox |
| `select expand` | HUD 快捷键 + 滚轮（grab/grow modifier 模式） | 世界+HUD | Litematica GrabbedElement |
| `select show/clear` | show=Gizmos 常显开关；clear=HUD 快捷键 | 世界(Gizmos) | — |
| `select set-type/set-rule/set-cache` | 选区屏：批量操作面板（模式互斥按钮+规则选择+确认） | Screen | Create 模式按钮组 |
| `transfer start/status/stop` | 跨仓库屏：src/dst 下拉 + start；进度走 HUD 状态行 | Screen+HUD | — |
| `config show/set` | 配置屏（分类列表+搜索+各类型控件） | Screen | libIPN 配置 DSL / cloth-config |

**结论**：零命令等价需要四类 UI 面——①仓库管理/规则/选区/配置的 **Screen 群**（3-5 个屏）、②常驻 **HUD**（状态+进度+引擎控制+快捷键提示）、③**世界内 Gizmos 高亮**（容器分区、选区盒、标记目标）、④**容器 Screen 覆盖层**（在原版容器上标注输入/输出区——参考 IPN 三段注入，可作为二期）。四者共用同一数据层（`api/` + `WarehouseEvents`）与同一 UI 引擎。

---

## 6. UI 引擎建议方向

### 6.1 规模判断：轻量元素树，不重造布局/绘制

- **布局**：直接用 vanilla `layouts/` 包（LinearLayout/GridLayout/FrameLayout）+ "init() 全量重建"约定，不引入 Taffy/Yoga。理由：本 mod 的 Screen 群规模小（个位数屏幕），LDLib2 的脏驱动 Flexbox 是为大型复杂 UI 服务的；`AbstractSimiScreen` 的做法已被 vanilla 生态验证。
- **绘制**：全部走 `GuiGraphicsExtractor`；面板用"BoxElement 思路"纯顶点色渐变（免贴图）+ 必要时 `blitSprite` 九宫格；**不引入 SDF shader**（26.x 自定义 RenderPipeline 在 GUI 层成本高，收益仅圆角）。
- **世界高亮**：`Gizmos` API 一行提交，`HighlightManager` 维护状态、渲染帧统一 emit；`collectPerFrameGizmos()` try-with-resources 即可，**不需要 PDD 预设的 mixin**（PDD §8.7 应更新）。
- **HUD**：`HudElementRegistry.addLast` + `HudElement.extractRenderState`；与 Screen 共用同一套轻量控件绘制代码（LDLib2"HUD=Screen 同构"思想，但只共享绘制/布局，不共享输入——HUD 输入用 Fabric screen event / mixin 另议）。

### 6.2 引擎最小件（自研清单）

1. `UiElement` 基类：children 树 + bounds + zIndex + `extractRenderState` 递归 + `hitTest`（含 scissor 交集）。
2. 事件冒泡：`UiEvent`（type/target/phase/坐标）+ capture/bubble 派发器（对照 LDLib2 `UIEventDispatcher` 217 行的实现思路）；伪类状态（hovered/focused/disabled）由引擎每帧维护。
3. 数据绑定：`Value<T>`（监听器推送）+ `bind(Value, Consumer)`；源 = `WarehouseEvents` 订阅器把事件转成 `Value` 更新。
4. 主题：静态常量表（对照 Create `COLOR_*`），首版不做样式表；留 `StyleBag` 式"来源优先级"扩展位。
5. 声明式设置：`Setting.getComponent()` 模式（Wurst7）——config 键自动生成配置屏控件。
6. 覆盖层注入（二期，容器 Screen 标记）：IPN 三段注入模式 + `addSelectableChild` 共存。

### 6.3 与本仓库架构的对接（遵守 PDD 约束）

- UI 只经 `api/` + `WarehouseEvents`；命令层与 UI 层共用同一执行序列（`WhCommands` 各 Group 的调用体是现成范本）。
- i18n：全部 `Component.translatable`，key 前缀新增 `ui.wh.*`（与 `commands.wh.*` 并行），en_us/zh_cn 成对。
- 跨版本：一切 Screen 访问走 `util/McScreens` 探测模式；26.1→26.2 漂移点（GuiGraphicsExtractor→26.2 又有 `Hud` 改名等）在 universal jar 策略下由编译目标 26.1 决定，运行于 26.2 需关注 Wurst7 报告中列出的 4 个漂移点。
- 性能红线（`doc/plan.md`）：UI 全部在客户端线程渲染/提取，逻辑状态更新经事件订阅（同线程），无需额外同步；高亮数据经 on-demand 不可变状态模式传递。

### 6.4 风险清单

1. **26.1 提取式 API 文档稀缺**：反编译源码是唯一权威，实现时勤对照 `refs/dep-src/minecraft`。
2. **GUI 提取阶段禁副作用**：不能在 extract 回调里改状态、保存绘制对象引用——事件驱动数据缓存必须先于渲染帧就绪。
3. **HUD 无输入是 vanilla 现状**：HUD 上放按钮需要输入接管（mixin MouseHandler 或仅用键盘快捷键），设计 HUD 时先按"纯展示 + 快捷键驱动"保守设计。
4. **tooltip 通道唯一**：多层 UI 的 tooltip 全部走 `setTooltipForNextFrame`，自绘 tooltip（IPN 式）仅在做容器覆盖层时考虑。
5. fabric-api HUD 新 API（`HudElementRegistry`）在 26.1/26.2 两版间的稳定性需在版本矩阵测试中确认。

---

## 附：关键类速查（本地路径索引）

- MC 26.1：`refs/dep-src/minecraft/net/minecraft/client/gui/GuiGraphicsExtractor.java`、`.../screens/Screen.java`、`.../layouts/`、`.../gizmos/Gizmos.java`（+`client/renderer/LevelRenderer.java` collectPerFrameGizmos）、`.../client/Minecraft.java` setScreen。
- Wurst7：`refs/mods/Wurst7/src/main/java/net/wurstclient/util/RenderUtils.java`、`WurstRenderLayers.java`、`hacks/ChestEspHack.java`、`clickgui/`、`settings/Setting.java`。
- Create：`refs/mods/Create/src/main/java/com/simibubi/create/content/logistics/filter/`、`foundation/gui/`；Catnip（maven sources jar）`net/createmod/catnip/gui/element/BoxElement.java`。
- LDLib2：`refs/mods/LDLib2/src/main/java/com/lowdragmc/lowdraglib2/gui/ui/UIElement.java`、`ModularUI.java`、`ui/event/UIEventDispatcher.java`、`ui/style/StyleBag.java`、`hud/ModularHudLayer.java`、`texture/SDFRectTexture.java`。
- MaLiLib：`refs/dep-src/malilib/fi/dy/masa/malilib/event/RenderEventHandler.java`、`render/RenderUtils.java`、`render/infohud/InfoHud.java`、`hotkeys/KeybindMulti.java`；Litematica `refs/dep-src/litematica/fi/dy/masa/litematica/render/OverlayRenderer.java`、`selection/SelectionManager.java`。
- IPN：`refs/dep-src/ipn/org/anti_ad/mc/ipnext/mixin/MixinContainerScreen.java`、`gui/inject/ContainerScreenEventHandler.java`、`inventory/AreaTypes.java`、`inventory/ContainerClicker.java`。
- EMI：`refs/dep-src/emi/dev/emi/emi/screen/EmiScreenManager.java`、`search/EmiSearch.java`、`registry/EmiStackList.java`。
- AppleSkin：`refs/dep-src/appleskin/squeek/appleskin/client/HUDOverlayHandler.java`；Neat：`refs/dep-src/neat/vazkii/neat/HealthBarRenderer.java`。
