# 世界高亮系统（Gizmos）

> 原 UI-PDD §7。章节号保持不变——代码注释中的「UI-PDD §7」引用按本文件定位。
> 高亮类型与颜色表定义在 transport-engine.md §5.8（数据侧）；本章为渲染侧设计。

## 7. 世界高亮系统（Gizmos）

- **数据侧**（`HighlightManager`，core/highlight/）：每客户端 tick（及 `WAREHOUSE_CHANGED`）从激活仓库配置构建不可变快照 `List<Entry(AABB, HighlightType)>`；`HIGHLIGHT_CHANGED` 事件保留为未来运行时状态色（HAS_SPACE/FULL，design-decisions.md §15.11）的接入信号，当前数据流不经它。
- **渲染侧** = `HighlightRenderer`（L3）经 `UiHooks.onPerFrameWorld`（renderLevel HEAD 钩子，container-detection.md §8.7）每帧驱动，读 tick 快照经 `WorldHighlighter` emit：
  - 容器高亮：`box(canonicalAABB, stroke=类型色, fill=类型色低 alpha)`；多格容器每个 pos 一个盒。
  - 选区盒（ui-screens.md §5.2）：三轴三色线框 + 角块高亮 + `quad` 半透明面（双面渲染）+ 开关切换。
  - 标记模式目标预览同走此管线（待实现，见 todos）。
- **性能纪律**：高亮数据是"tick 级缓存的不可变快照"（MaLiLib on-demand RenderState 模式）——tick 构建一次、渲染帧只读，不逐帧重算。
- 相对坐标→绝对：沿用 anchor + WorldDimPos 语义（data-model.md §3.1）。
- 开关与配色：每类高亮独立配置项（对照 Litematica 的 per-feature config 粒度）——待实现（见 todos），现仅选区盒有全局开关。
