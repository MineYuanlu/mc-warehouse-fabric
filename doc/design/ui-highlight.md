# 世界高亮系统（Gizmos）

> 原 UI-PDD §7。章节号保持不变——代码注释中的「UI-PDD §7」引用按本文件定位。
> 高亮类型与颜色表定义在 transport-engine.md §5.8（数据侧）；本章为渲染侧设计。

## 7. 世界高亮系统（Gizmos）

- `HighlightManager`（core/highlight/）维护 `Map<CacheKey, HighlightType>`，由引擎 tick 更新；**渲染侧** = `HighlightRenderer`（L3）订阅 `HIGHLIGHT_CHANGED` + 每帧经 `WorldHighlighter` emit：
  - 容器高亮：`box(canonicalAABB, stroke=类型色, fill=类型色 0x40 alpha)`；多格容器每个 pos 一个盒。
  - 选区盒（ui-screens.md §5.2）与标记模式目标预览同走此管线。
- **性能纪律**：高亮数据是"tick 级缓存的不可变快照"（MaLiLib on-demand RenderState 模式）——`HighlightManager` 在 tick 中构建 `List<HighlightBox>`，渲染帧只读；`HIGHLIGHT_CHANGED` 仅作为数据到达信号。
- 相对坐标→绝对：沿用 anchor + WorldDimPos 语义（data-model.md §3.1）。
- 开关与配色：每类高亮独立配置项（对照 Litematica 的 per-feature config 粒度）。
