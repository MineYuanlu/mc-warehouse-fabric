# 配置持久化

> 原 PDD §11。章节号保持不变——代码注释中的「PDD §11.x」引用按本文件定位。

## 11.1 文件结构

```
config/yuanlu-warehouse/
├── warehouses/           # 仓库定义
│   ├── main.json         # 仓库 "main" 的配置
│   └── mining.json       # 仓库 "mining" 的配置
├── rules/                # 全局规则（可被多个仓库引用）
│   ├── ores.json         # 矿石规则
│   └── building.json     # 建材规则
├── pathfinders/          # 寻路配置（格式见 navigation.md §7.3）
├── config.json           # 全局配置（ModConfig + ServerConfig）
└── cache/<worldId>/      # DISK 磁盘缓存，按 world 隔离；自动生成
```

另：`world-map.json`（world-identity.md §4.3）与 `config.json` 同目录；服务端存档根的 `yuanluworldid.txt`（world-identity.md §4.2）由服务端自动生成，不属于本目录。

## 11.2 序列化格式与健壮性

使用 Gson 进行 JSON 序列化/反序列化。`ItemSelector` 和 `QuantitySelector` 是接口，TypeAdapter 按 **Registry 中注册的 codec 表**分发（内置实现同样经 codec 注册——插件因此获得同等的持久化能力）：

```json
{
  "type": "id",
  "value": "minecraft:diamond"
}
```

- 每个 JSON 文件顶层携带 `"schemaVersion"`，为未来迁移留路
- 写盘一律 **临时文件 + 原子移动**（ATOMIC_MOVE），防止崩溃截断文件
- 读失败/缺字段：回退默认值并告警日志，不静默吞掉

## 11.3 仓库配置 JSON 示例

```json
{
  "schemaVersion": 2,
  "id": "main",
  "anchors": {
    "singleplayer:caoyuan-26_1": {
      "新的世界": {
        "minecraft:overworld": { "x": 0, "y": 64, "z": 0 }
      }
    }
  },
  "containers": [
    {
      "pos": [{ "dim": "minecraft:overworld", "x": 10, "y": 0, "z": 20 }],
      "ioType": "OUTPUT",
      "ruleMode": "WHITELIST",
      "rules": ["ores"],
      "cacheType": "DISK",
      "priority": { "hard": 10, "soft": 5 },
      "label": "主存储箱-1"
    }
  ],
  "rules": {
    "ores": {
      "id": "ores",
      "itemRules": [
        {
          "selector": { "type": "tag", "value": "minecraft:coal_ores" },
          "negative": false,
          "quantity": { "type": "count", "value": 64 }
        }
      ]
    }
  }
}
```

坐标说明：

- `pos` 条目为**相对坐标**：x/z 相对同 `(world, dim)` 的 anchor，y 为相对 anchor.y 的偏移
- pos 条目的 `world` 是 **worldName**（world-identity.md §4.3 映射的键，玩家可控），缺省世界写 `""`；可省略：省略时取 anchors 展平后唯一的 worldName，多世界歧义则报错
- `anchors` 为 `serverId → worldName → dimId → Pos` 三层嵌套（v2），避免分隔符转义问题；serverId 为会话相对解析（world-identity.md §4.5）

**v1→v2 迁移**（无损、自动）：anchors 每键插入 worldName 层 `""`；旧 `pos.world` 值（serverId 格式）若等于某 anchor 键 → `""`，含 `#` → 取后缀，其余置 `""`。

**规则 id 冲突**：仓库内嵌 `rules` 与全局 `rules/` 目录出现同名 id → 加载报错并列出冲突，拒绝载入该仓库（要求改名），不做静默覆盖。

## 11.4 全局配置

```json
{
  "schemaVersion": 2,
  "debug": false,
  "defaultInteractionSpeed": 2,
  "interactionJitterPercent": 0,
  "cacheTtlSeconds": 0,
  "slotAllocator": "first_fit",
  "reachLimit": 4.5,
  "exploreFailMax": 2,
  "navRetryMax": 3,
  "timeouts": { "openTicks": 20, "confirmTicks": 10, "settleTicks": 2 },
  "servers": {
    "singleplayer:caoyuan-26_1": {
      "dimensions": {
        "minecraft:overworld": { "interactionSpeed": 2, "pathfinder": "noop" }
      }
    },
    "mp:mc.example.com:25565": {
      "dimensions": {
        "minecraft:overworld": { "interactionSpeed": 3, "pathfinder": "noop" }
      }
    }
  }
}
```

全局配置分为 `ModConfig`（模组级）和 `ServerEntry`（服务器级）两部分。按服务器地址分层（键值即 serverId，world-identity.md §4.1），查找顺序：`servers[server].dimensions[dim]` → `servers[server]` 级默认 → 全局默认。worldName→worldId 映射独立存于 world-map.json（world-identity.md §4.3），不在此文件。

**config.json v1→v2 迁移**（自动）：顶层 `worlds` 键改名为 `servers`（语义同步 world-identity.md 的 serverId 三层化），其余键不变。

| 配置项                                   | 类型    | 默认值    | 说明                                                  |
| ---------------------------------------- | ------- | --------- | ----------------------------------------------------- |
| debug                                    | boolean | false     | 是否输出调试日志                                      |
| defaultInteractionSpeed                  | int     | 2         | 默认交互速度（每次操作后的 tick 等待数）              |
| interactionJitterPercent                 | int     | 0         | 每次操作的随机额外延迟百分比（反作弊缓解）            |
| cacheTtlSeconds                          | int     | 0（关）   | MEMORY/DISK 缓存的 TTL 秒数保险，超期强制重扫（transport-engine.md §5.4） |
| reachLimit                               | double  | 4.5       | 开容器的最大触及距离（格）（interaction-protocol.md §6.5） |
| exploreFailMax                           | int     | 2         | 同一容器连续探索失败上限（interaction-protocol.md §6.5） |
| navRetryMax                              | int     | 3         | 同一寻路目标的引擎侧重试上限（interaction-protocol.md §6.5） |
| slotAllocator                            | String  | first_fit | 槽位分配器 id                                         |
| timeouts.openTicks                       | int     | 20        | 等待容器 UI 打开的超时（interaction-protocol.md §6.5） |
| timeouts.confirmTicks                    | int     | 10        | 单次点击对账超时（interaction-protocol.md §6.5）      |
| timeouts.settleTicks                     | int     | 2         | 一组操作后的稳定等待（interaction-protocol.md §6.5）  |
| servers[s].dimensions[d].interactionSpeed | int    | 2         | 特定 (server,dim) 的交互速度                         |
| servers[s].dimensions[d].pathfinder      | String  | noop      | 特定 (server,dim) 的默认寻路器                       |
