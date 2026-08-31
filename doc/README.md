# Yuanlu Warehouse — 文档索引

Minecraft Fabric 纯客户端自动化仓库管理模组。构建/测试/CI 流程见根目录 [AGENTS.md](../AGENTS.md)。

## 文档结构

| 路径 | 类型 | 内容 |
|---|---|---|
| [design/](design/README.md) | **持久性·设计** | 系统设计文档（原 PDD + UI-PDD 按模块拆分）：架构分层、数据模型、传输引擎、交互协议、容器检测、插件 API、UI 三层等。修改功能/接口前先读对应模块 |
| [commands.md](commands.md) | **持久性·参考** | `/wh` 命令用户手册（快速上手 + 全命令表 + FAQ） |
| [testing.md](testing.md) | **持久性·参考** | 测试分层策略（JVM 单测 / E2E GameTest）、版本矩阵、CI、已知坑 |
| [research/ui-research.md](research/ui-research.md) | **持久性·依据** | UI 引擎前置调研报告（11 个参考源分析、零命令等价能力映射），UI 设计决策的依据 |
| [archive/INDEX.md](archive/INDEX.md) | **索引** | 已删除阶段性文档的归档索引（指向 git 历史） |

## 文档维护原则

- **设计文档（design/）只描述"系统是什么、怎么工作、为什么这样设计"**——不记录实现进度、里程碑状态、版本变更史；这些属于 git 历史与提交信息。
- 阶段性文档（实施计划、迁移指南、进度跟踪）在阶段完成后删除，由 git 历史 + `archive/INDEX.md` 索引承接，不长期留在仓库。
- `design/` 各文件保留原 PDD 章节号（§3–§15 + UI 的 §3–§13），代码注释中的「PDD §x.x」「UI-PDD §x.x」引用据此定位，不需改代码。
