# MC Warehouse

一个 Fabric 纯客户端模组，用于自动整理 Minecraft 容器中的物品。

> 项目处于早期开发阶段，代码尚未完善。

## 功能概述

- **区域选择**：通过命令选择 3D 区域，标记区域内的容器
- **仓库管理**：将容器分组为仓库，每个仓库独立配置基准点和物品规则
- **物品规则**：自定义各组容器应放入/取出什么物品、各多少（支持按 ID/NBT/名称/标签匹配）
- **容器类型**：输入端（搬出）、输出端（搬入）、暂存端（中转）、忽略
- **自动搬运**：按规则自动整理容器之间的物品流转
- **自动寻路**：规划并执行到目标容器的路径（步行/飞行/传送门/命令传送）
- **容器高亮**：通过 WorldRenderer 渲染容器轮廓与状态

## 快速开始

```sh
# 构建
./gradlew build

# 启动游戏
./gradlew runClient
```

## 命令

`/warehouse` 和 `/wh` 两个入口均可使用，支持多层子命令：

| 子命令 | 功能 |
|--------|------|
| `select` | 区域选择（pos1/pos2/expand/show） |
| `container` | 容器标记管理（add/remove/type/mode/info/memory） |
| `rule` | 物品规则 CRUD（list/create/delete/add/remove/edit/show） |
| `warehouse` | 仓库管理（create/delete/list/activate/deactivate/show/hide） |
| `run` | 开始搬运 |
| `config` | 配置查看与修改 |

## 数据存储

所有数据存储在游戏运行目录下的 `mc-warehouse/` 文件夹：

```
mc-warehouse/
├── config/
│   ├── worlds.json          # 世界配置
│   └── mod.json             # 模组配置
├── warehouses/<name>/
│   └── data.json            # 仓库定义（容器 + 规则）
└── pathfinder/
    └── <type>/<name>.json   # 寻路器配置
```

## 技术栈

- **模组加载器**：Fabric（Fabric Loom 1.15）
- **Minecraft 版本**：26.1.2
- **Java 版本**：25
- **依赖**：Fabric API 0.152.1+
- **纯客户端**：不修改服务端逻辑，模拟正常玩家行为

## 架构

详见 [ARCH.md](ARCH.md)。

- MVC 分层：Controller 层被命令和未来 UI 共享
- 路径执行器：基于 tick 的状态机模式
- 物品规则：ItemSelector + QuantitySelector 组合设计
- 坐标系统：相对坐标 + 仓库级基准点

## 许可

MIT License © 2026 yuanlu
