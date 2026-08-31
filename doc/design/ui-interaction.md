# UI 快捷键、交互规范与 i18n

> 原 UI-PDD §8–§13。章节号保持不变——代码注释中的「UI-PDD §8–§10」引用按本文件定位。

## 8. 快捷键系统

vanilla `KeyMapping` + client tick 轮询（Fabric `ClientTickEvents`），声明式表驱动（MaLiLib `KeybindMulti` 语义的极简版）：

```java
record UiKeybind(String id, String category, KeyMapping key, boolean requiresWorld) {}
```

| id | 默认键 | 作用 |
|---|---|---|
| `wh.ui.open` | `K` | 打开仓库管理主屏 |
| `wh.select.pos1` / `pos2` | 未绑定 | 设角 1/2（脚下；与 `shift` 组合 = 准星方块） |
| `wh.select.expand` | 未绑定 | 按压 = 角 2 沿视线主轴扩展 1 格，`shift` = 反向收缩 1 格（等价 `select expand 1 <dir>`；精确多格扩展在选区面板；grab+滚轮语义需滚轮拦截 mixin，经 design-decisions.md D14 裁剪） |
| `wh.select.show` | 未绑定 | 切换选区盒常显 |
| `wh.select.clear` | 未绑定 | 清除选区 |
| `wh.mark.toggle` | 未绑定 | 标记模式开关 |
| `wh.engine.toggle` | 未绑定 | start/stop 切换 |

- 冲突处理：注册时用 `KeyMapping` 同键检测告警（聊天栏 warning 一次）；全部默认未绑定（除 `open`）避免与玩家现有键位冲突。
- i18n：`key.categories.wh.*` / `key.wh.*`（vanilla keybind i18n 惯例）。
- **不做**：MaLiLib 的 context/activateOn/exclusive 全套配置（无场景），保留 record 字段位即可。

## 9. 交互设计规范

从调研提炼的硬性交互规则（L3 组件实现与评审 checklist）：

1. **互斥模式选择**一律用"成对按钮 + 当前项 accent 色指示"（Create FilterScreen 模式），不用下拉框（少一次点击）。
2. **危险操作**（删除仓库/规则、abort）：按钮 danger 色 + 二次确认浮层；浮层聚焦确认按钮需先点击。
3. **表单校验即时反馈**：错误显示在表单内（MessageRenderer 式行内消息条），不用聊天栏；聊天栏仅用于 HUD 时代的遗留反馈。
4. **坐标输入**：支持 `~` 相对语法（复用 `CommandSupport.posOf` 语义）与"准星拾取"按钮（等价缺省准星语义）。
5. **每屏 Esc 可退**；页签间 Backspace 返回上一页签（Create NavigatableSimiScreen 语义，可后加）。
6. **tooltip**：统一走 `UiDraw.setTooltip`（→ `setTooltipForNextFrame`）；disabled 按钮必须 tooltip 说明原因。
7. **列表滚动**：只渲染可见行（行虚拟化）+ 滚轮亚像素累积；列表条目 hover 有背景变化。
8. **声音**：点击/成功/失败走 vanilla UI 音效（`UiPlaySound` 端口），失败音仅用于被拒操作。

## 10. i18n 与文案

- key 前缀：`ui.wh.<screen>.<key>`（与 `commands.wh.*` 并行，复用既有 `wh.state.*` / `wh.grade.*`——design-decisions.md D10）。
- 全部 `Component.translatable`；L1 侧 `UiDraw.textComponent` 透传，`Value<String>` 绑定的是 key 解析后的 Component（语言切换跟随 vanilla）。
- 快捷键名、IOType/CacheType 徽标、TransportState 动作描述全部进 lang 文件；en_us/zh_cn 成对提交。

## 12. UI 包结构规划

```
src/client/java/bid/yuanlu/mc/warehouse/
├── ui/                            # ★ UI 总根
│   ├── core/                      # ★ L1 引擎核心（零 net.minecraft.client import）
│   │   ├── element/               # UiElement, UiRoot, ElementQueries, hitTest
│   │   ├── event/                 # UiEvent, UiEventDispatcher, FocusManager
│   │   ├── bind/                  # Value, Bindings
│   │   ├── layout/                # Layout, Row, Column, Grid, Anchor
│   │   ├── theme/                 # Theme, Themes（内置 CreateDark）, ThemeColors
│   │   ├── draw/                  # UiDraw, Identifier, TextAnchor, CursorKind（端口）
│   │   ├── world/                 # WorldHighlighter（端口）, HighlightBox
│   │   └── anim/                  # TickLerp, TickFlash
│   ├── mc/                        # ★ L2 适配层（唯一 client GUI 依赖点；每 MC 版本一个子包）
│   │   ├── mc261/                 # 26.1 实现（当前唯一）
│   │   │   ├── Mc261UiPlatform.java      # 装配 + 被 UiPlatform.detect() 选中
│   │   │   ├── Mc261Draw.java            # UiDraw → GuiGraphicsExtractor
│   │   │   ├── Mc261ScreenHost.java      # Screen 薄壳（GuiEventListener 适配）
│   │   │   ├── Mc261HudHost.java         # HudElementRegistry 包装
│   │   │   └── Mc261WorldHighlighter.java# Gizmos 直通
│   │   ├── McPlatformDetector.java       # 版本探测（MethodHandle feature probe）
│   │   └── RawGraphics.java              # 逃生舱（@CompatDebt，目标恒 0 处使用）
│   └── app/                       # ★ L3 业务组件（只 import ui/core|mc 门面 与 api/）
│       ├── presenter/             # WarehousePresenter, TransportPresenter, SelectionPresenter
│       ├── screen/                # WarehouseManagerScreen, SelectionPanelScreen,
│       │                          #   RuleListScreen, HudSettingsScreen
│       ├── hud/                   # HudPanel(区块元素), HudRoot
│       ├── highlight/             # HighlightRenderer（渲染侧；HighlightManager 留 core/highlight/）
│       └── widget/                # 复合控件：Modal, ScreenScaffold, CycleSelector, DropdownElement,
│                                  #   ItemGridElement, RuleEntryEditor, ContainerGhostElement
├── core/selection/                # ★ SelectionState 自 command/ 上移（命令层同步改引用）
└── (其余不变，见 design/README.md 包结构)
```

资源：`src/client/resources/assets/yuanlu-warehouse/lang/` 增补 `ui.wh.*` 键；无新贴图（主题全程序化；模拟容器参照用原版 GUI 贴图 blit）。

## 13. UI 测试策略映射

| 层 | 覆盖内容 | 位置 |
|---|---|---|
| JVM 单测 | L1 全部：元素树/命中/事件冒泡合成/焦点、布局器度量与排列、`Value` 推送语义、主题 token 引用完整性（每个 token 至少一处使用）、`SelectionState` 回归 | `src/test`（`ui/` 包；L1 纯 Java 可无头测试） |
| 架构守护单测 | L1 无 `net.minecraft.client` import；L3 无 `net.minecraft.client.gui` import；`RawGraphics` 使用点计数 = 0 | `src/test`（类路径扫描即可，无需 ArchUnit 依赖） |
| E2E GameTest | `Mc261ScreenHost` 开屏/关屏冒烟；HUD extract 不抛异常（多分辨率）；`Mc261WorldHighlighter` 收集窗口提交盒子；主屏打开→列表渲染→激活仓库→引擎 start 的 UI 链路（在既有 production gametest 骨架上扩展） | `src/gametest` |
