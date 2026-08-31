# 已归档文档索引

本目录不存放文件。以下阶段性（过程性）文档已完成使命并删除——**git 历史即归档**，点击 commit 链接可查看删除前的最终版本（仓库：MineYuanlu/mc-warehouse-fabric，分支：dev/ui）。

| 已删除文件 | 性质 | 删除原因 | 最后版本 |
|---|---|---|---|
| `doc/plan.md` | 阶段性 | 一阶段实施计划（L0–L11 分层进度 + B1–B11 修复批次 + D1–D6 决策 + 技术锚点表）。L0–L11 与修复批次全部完成；决策与锚点已吸收进 `design/` 各模块文档，进度表失去时效 | [`b53a687`](https://github.com/MineYuanlu/mc-warehouse-fabric/blob/b53a687/doc/plan.md) `doc: F5 PDD §5.3/§5.4 落地回写 + commands.md 手册更新 + plan.md D6` |
| `doc/MIGRATION.md` | 阶段性 | mc-warehouse-old → mc-warehouse2 一次性代码迁移指南（模块映射 + 三阶段流程）。迁移已完毕 | [`dd3a0bf`](https://github.com/MineYuanlu/mc-warehouse-fabric/blob/dd3a0bf/doc/MIGRATION.md) `doc: PDD v0.2` |
| `doc/PDD.md` | 设计（已拆分） | 单文件 1451 行过长；已按模块拆分为 `doc/design/` 下 10 个文件（章节号不变，代码内「PDD §x.x」引用仍然有效）；其中 §10 命令表（commands.md 覆盖）、§14 一阶段范围表（阶段完成）、§16 版本历史（git 承接）未迁移 | [`0039fe5`](https://github.com/MineYuanlu/mc-warehouse-fabric/blob/0039fe5/doc/PDD.md) `feat(world): 世界标识三层化——serverId/worldId/worldName 分离 + 存档级随机 id 文件` |
| `doc/UI-PDD.md` | 设计（已拆分） | 同上；已拆分为 `doc/design/` 下 4 个 UI 文件（ui-engine / ui-screens / ui-highlight / ui-interaction）+ 并入 README/plugin-api/design-decisions；§14 里程碑状态表未迁移 | [`c2c4cb5`](https://github.com/MineYuanlu/mc-warehouse-fabric/blob/c2c4cb5/doc/UI-PDD.md) `docs(ui): UI-PDD v0.2 → v0.3——全量绘制方案落库` |

> 另：`doc/ui-research.md` 未删除，移动为 [doc/research/ui-research.md](../research/ui-research.md)（仍为 UI 设计的依据文档）。
