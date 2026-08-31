# UI 引擎 — L1 核心与 L2 适配层

> 原 UI-PDD §3–§4。章节号保持不变——代码注释中的「UI-PDD §3.x / §4.x」引用按本文件定位。
> 架构分层总览与依赖方向规则见 [README.md](README.md)。

## 3. L1 引擎核心（ui/core/）

### 3.1 元素模型

```java
public abstract class UiElement {
    // 树
    @Nullable UiElement parent;
    List<UiElement> children;
    // 几何（GUI 缩放坐标，由布局器写入）
    int x, y, width, height;          // 相对父级
    // 状态
    boolean visible, enabled;
    int zIndex;                        // 同层绘制与命中顺序
    String id;  Set<String> classes;   // 主题选择与查询用
    // 引擎维护的伪状态（只读，主题/绘制可见）
    boolean hovered, focused, pressed;
}
```

- **生命周期**：`onAttached/onDetached`（进/出树）+ `onThemeChanged`；无独立 init——**挂载即构建**（Screen 侧沿用 vanilla "resize 时全量重建" 约定，见 design-decisions.md D4）。
- **命中测试** `hitTest(px, py)`：先 `visible && contains(px,py)`（含父链 scissor 交集预判），再按 `zIndex` 降序递归子级。**每帧提取前算一次 hover 结果并缓存**（LDLib2 模式），输入事件只查缓存。
- **提取式渲染**：`extract(UiDraw g)` 递归——`g.pushClip(本元素)` → 自绘 → 按 zIndex 升序递归子级 → `g.popClip()`。L1 元素**禁止在 extract 中改状态**（26.x 提取模型约束，主渲染数据必须先于帧就绪）。

### 3.2 事件系统

DOM 式三阶段（LDLib2 `UIEventDispatcher` 思想，自研约 200 行）：

```java
public record UiEvent(Type type, UiElement target, double x, double y,
                      int button, int keyCode, int modifiers, double scrollY, Phase phase) {
    enum Type { MOUSE_DOWN, MOUSE_UP, CLICK, DOUBLE_CLICK, MOUSE_MOVE, ENTER, LEAVE, WHEEL,
                DRAG_START, DRAG, DRAG_END, KEY_DOWN, KEY_UP, CHAR, FOCUS, BLUR }
    enum Phase { CAPTURE, BUBBLE }
}
```

- **派发**：CAPTURE 根→父 → 目标 → BUBBLE 父→根；`consume()` 断链。
- **合成事件在引擎层做一次**：`MOUSE_DOWN+UP` 合成 `CLICK`；300ms 同元素同键合成 `DOUBLE_CLICK`；跨帧位置比较合成 `ENTER/LEAVE`；拖拽状态机合成 `DRAG_*`。元素端只需注册 `onClick(Runnable)` 级别的便捷 API。
- **与 MC 输入对接**：L2 的 `ScreenHost` 把 vanilla `MouseButtonEvent/KeyEvent/CharacterEvent` 翻译成 `UiEvent` 投入根元素；`ScreenHost` 本身是唯一的 `GuiEventListener`（LDLib2 的单一适配器壳模式）。
- **焦点**：引擎维护 `focusedElement` 链，`requestFocus` 发 `BLUR/FOCUS`；Tab 导航按 `tabOrder`（默认注册序）。

### 3.3 数据绑定

```java
public final class Value<T> {
    T get();
    void set(T v);                     // 触发 listeners
    void listen(Consumer<T> l);        // 注册即回调一次（callImmediately 语义）
}
Value<T> derived(Function<T2,T> f, Value<T2>... srcs);   // 派生值
```

- 绑定方向：业务状态 → `Value` → 元素（单向推送）。`Value.set` 在客户端主线程被调用（事件源是 `WarehouseEvents`，同线程），监听器只做"更新元素可见状态"，不做布局计算——布局脏标记见 §3.4。
- **业务侧装配**：`ui/app/` 每个 UI 面有对应的 `*Presenter`：订阅 `WarehouseEvents` → 把事件转成 `Value` 更新。UI 元素只认 `Value`，不认事件（保证同一 Presenter 可同时喂 HUD 与 Screen，满足"跨 UI 持续显示"）。

### 3.4 布局器

不引入 Flexbox/Taffy（design-decisions.md D5），但吸收其核心语义做轻量自研。L1 提供布局器 + "变更时重排"：

```java
interface Layout { void arrange(container); int measureWidth(container); int measureHeight(container); }
Row(.gap)  Column(.gap)  Grid(cols, cell, gap)  Anchor(偏移)  [ScrollElement 内置视口版 Column]
```

- **grow 权重（轻量 flex-grow）**：`UiElement.grow(float)` 只作用于直接子级的主轴——容器主轴尺寸为定值时，先累计固定尺寸子级与 gap，剩余空间按权重瓜分（截断分配不超发）；显式 `size()` 在有权重时作为**最小尺寸**。容器主轴尺寸不定（AUTO）时权重子级只贡献声明最小值（退化为普通流式）。`UiRoot` 根级对 `grow>0` 的直接子级 = 全屏满铺（`ScreenScaffold` 语义）；无权重页面维持 SCREEN_MARGIN 左上锚定（malilib 式）。
- **ScrollElement（overflow-y: auto）**：内置视口布局器（子级像 Column 堆叠、AUTO 宽拉伸到视口宽），滚轮 2 行步进 + 滚动钳制 + 2px 滚动条指示；`clipContent` 强制开启且**裁剪矩形参与命中测试**（滚出视口的内容不可见亦不可点）。
- 布局在**挂载时**与**元素显式 `invalidateLayout()` 后**的下一帧提取前重排（脏标记，不做每帧求解）。窗口 resize = 全量重建（见 D4），天然正确。
- HUD 元素用 `HudLayout` 定角（读 `HudConfig` 组偏移）；Screen 内容用 `ScreenScaffold`（全屏脚手架，ui-screens.md §5.1）。
- 度量单位：GUI 缩放像素（与 vanilla 一致），引擎不做 DPI 换算。

### 3.5 主题 token

```java
public record Theme(String id,
    int bgPanel, int bgPanelGradient,       // BoxElement 式上下渐变（程序化，无贴图）
    int border, int borderGradient,
    int textPrimary, int textMuted, int textAccent,
    int accent, int accentHover,            // 主色/悬浮
    int success, int warning, int danger,   // 语义色（对齐 transport-engine.md §5.8 高亮色系）
    int overlayScrim,                       // 屏外遮罩
    int bgScreen,                           // 全屏脚手架底色（半透明黑，ui-screens.md §5.1）
    int radius, int padding, int gap, int lineWidth)
```

- 颜色绘制全部走"BoxElement 思路"的纯顶点色渐变（`UiDraw.fillRectGradient` + `outline`），**零贴图资源**；`blitSprite` 留给原版图标类元素。
- 换主题 = 换 `Theme` 实例；元素绘制只读 token，不写死颜色（样式不做级联系统，token 直连——design-decisions.md D6）。
- 悬浮渐变动画：元素级 `Value<Float> hoverProgress`，tick 步进（0.125/tick 折返，AppleSkin 模式），绘制时插值两 token 色。

### 3.6 动画

仅三种原语，全部 tick 驱动（L1 纯数学，可 JUnit）：

1. `TickLerp`（0→1 缓动，用于面板淡入/滑入）；
2. `TickFlash`（折返 alpha，用于进度条/告警）；
3. 数值滚动（`lerp(current, target, 0.25)`，用于进度百分比）。

不做 stylesheet transition / keyframe 体系。

### 3.7 窗口尺寸与分辨率适配

问题域三件事：不同窗口尺寸（窗口/全屏切换）、不同 GUI Scale（1~4/Auto，决定 `guiScaledWidth/Height`）、不同宽高比。参考各 mod 做法：

| 来源 | 做法 |
|---|---|
| vanilla | Screen 持有 scaled `width/height`，resize → `rebuildWidgets()` 全量重建，布局在 init 时按当前尺寸现算 |
| Create | 内容固定尺寸 + `setWindowSize` 每次居中重算（"内容不变、位置随窗口"） |
| LDLib2 | HUD 每帧校验窗口尺寸，变化即整树 re-init（`ModularHudLayer.validModularUI`） |
| EMI | `recalculate()` 带 lastWidth/lastHeight/lastExclusion 缓存跳过重算 + 排除区迭代避让 |
| MaLiLib | 每屏 init 时读 `getScaledWindowWidth/Height` 现算绝对坐标 |

**本引擎策略：全部坐标在"构建时"针对当前根尺寸一次性求解，运行期零布局重算**：

1. 引擎只感知 GUI 缩放坐标，不感知物理分辨率/DPI（缩放由 vanilla 完成）。GUI Scale 不同 = 可视区域尺寸不同，同一套布局逻辑天然适应。
2. **Screen**：根元素在 `ScreenHost.init()` 构建，构建时 `UiDraw.screenWidth/Height` 即当前值；resize → vanilla `rebuildWidgets` → 全量重建（D4）。宽高比变化不影响——布局器是流式的。**业务面板禁止写死绝对屏幕坐标**（评审 checklist 项）；列表/详情等自适应区域一律用 `grow(1)` 撑满 + `ScrollElement` 滚动（§3.4），不手工 `min(上限, 屏高-边距)` 派生。
3. **HUD**：`HudRoot` 用 Anchor 定角 + 配置偏移，区块纵向排布；`HudHost` 每帧比对缓存尺寸，变化则下一帧重建（LDLib2 模式，重建成本 = 十几个文本元素，可忽略）。
4. **极端小可视区**（高 GUI Scale）：面板超出时靠列表元素自带的滚动消化，**不做 DPI 式 UI 缩放**（Create/EMI/MaLiLib 通例，mod 生态没有动态缩放 UI 的先例与需求）。
5. 百分比仅以"派生 token"形式存在（如 `Theme.panelWidth = min(360, screen*0.6)`，构建时求值），不引入通用百分比布局语法——LDLib2 的 PERCENT 是其 Taffy 体系的伴生品，我们没有布局引擎。

约束传递：布局器接口含 `Constraints(maxWidth/maxHeight)`，由根向子级传递（与 §3.4 布局器配合）。

## 4. L2 适配层（ui/mc/<mc版本>/）

### 4.1 UiDraw 端口（接口定义在 L1 `ui/draw/`，实现在此包）

```java
public interface UiDraw {                       // 2D 绘制门面（~20 方法，26.1 → GuiGraphicsExtractor）
    void fill(int x0, int y0, int x1, int y1, int argb);
    void fillGradient(int x0, int y0, int x1, int y1, int topArgb, int bottomArgb);
    void outline(int x, int y, int w, int h, int argb, float lineWidth);
    void blitSprite(Identifier sprite, int x, int y, int w, int h, int tintArgb);
    void text(String s, int x, int y, int argb, boolean shadow, TextAnchor anchor);
    void textComponent(Component c, int x, int y, int argb, boolean shadow);
    int  textWidth(String s);
    void pushClip(int x0, int y0, int x1, int y1);  void popClip();
    void pushPose();  void popPose();  void translate(float x, float y);  void scale(float s);
    void itemIcon(ItemStack stack, int x, int y);   // 含方块物品（GUI 上下文）
    void itemDecorations(ItemStack stack, int x, int y);
    void setTooltip(List<Component> lines);         // → setTooltipForNextFrame 通道
    void requestCursor(CursorKind kind);
    int  screenWidth();  int screenHeight();  float partialTick();  long tickCounter();
}
```

- 姿态栈用 JOML `Matrix3x2fStack` 语义（`pushPose/translate/scale`）——JOML 是库不是 MC 类，L1 可依赖其类型；L2 映射到 `GuiGraphicsExtractor.pose()`。
- `Identifier`：L1 侧定义同名值类型（record），L2 转换——避免 L1 import MC 资源类。
- 文本富样式首版不支持（纯色 + Component 透传）；需要分色 token 高亮时（搜索框语法着色）用多段 `text` 拼。

### 4.2 挂载点

| 挂载点 | 26.1 实现 | 说明 |
|---|---|---|
| `ScreenHost` | `Screen` 子类薄壳（~80 行）：`init()` 建根元素+Presenter；输入事件翻译成 `UiEvent`；`extractRenderState` 转发根元素 `extract`；`tick()` 驱动动画与 `Value` 轮询 | 唯一 `GuiEventListener`；打开 = `mc.setScreen(new UiScreenHost(...))`，无注册 |
| `HudHost` | Fabric `HudElementRegistry.addLast(id, element)` + `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` → 转 `UiDraw` 调用 | HUD 无输入（design-decisions.md D8）；动画/数据由 client tick 驱动 |
| `OverlayHost` | **只留接口**（`MountTarget.OVERLAY`），实现待 mixin GameRenderer 或 Fabric screen event | 供后续"跨 UI 持续显示侧栏"与容器覆盖层（design-decisions.md D16） |

### 4.3 WorldHighlighter 端口（世界空间）

```java
public interface WorldHighlighter {
    void beginFrame();                                  // try-with-resources 打开 Gizmos 收集窗口
    void box(AABB box, int strokeArgb, int fillArgb, float lineWidth, boolean throughWalls);
    void line(Vec3 a, Vec3 b, int argb, float width);
    void label(String text, Vec3 pos, int argb);
    void endFrame();
}
```

26.1 实现 = `Gizmos.cuboid/line/billboardText` 直通；`throughWalls` → `setAlwaysOnTop()`。**不需要高亮专用 mixin**（transport-engine.md §5.8 的实现方式据此描述；现有 LevelRendererMixin 仅是 renderLevel HEAD 的事件钩子，见 container-detection.md §8.7）。

### 4.4 Mc261UiPlatform 与版本探测

- 每个支持的 MC 版本一个独立子包：`ui/mc/mc261/`（当前）、未来 `ui/mc/mc262/`——同包内实现互不引用，版本探测一次选定一个。
- `ui/mc/mc261/Mc261UiPlatform.java`：单例装配——构建 `UiDraw` 实现、`ScreenHost` 工厂、`HudHost` 注册、`WorldHighlighter` 实现。
- 版本探测沿用 `util/McScreens` 的 MethodHandle 探测模式：`UiPlatform.detect()` 按 feature probe（如 `GuiGraphicsExtractor` 类存在性、GUI 入口类名）选择实现；**26.2 适配 = 新增 `ui/mc/mc262/` 子包 + 探测分支，L1/L3 零改动**。
- **逃生舱**：`ui/mc/RawGraphics.java` 提供 `static void raw(Consumer<GuiGraphicsExtractor>)`（仅 L2 可见、`@CompatDebt` 注解；每个使用点在版本升级时必须逐个审查，目标数量恒为 0）。注意：逃生舱 API 放在 `ui/mc/` 根（版本无关签名、版本相关实现），L3 若要用它就等于破坏分层——因此 L3 对它的任何使用都视为里程碑缺陷。
