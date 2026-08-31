# 世界与服务器标识

> 原 PDD §4。章节号保持不变——代码注释中的「PDD §4.x」引用按本文件定位。

仓库与配置必须回答「这是哪个服务器/存档、哪个世界」的问题，否则不同服务器的同名坐标会互相污染配置与缓存。

## 4.1 三层概念

| 概念       | 来源                                                                                     | 职责                                       |
| ---------- | ---------------------------------------------------------------------------------------- | ------------------------------------------ |
| **serverId** | 内置固定实现（非 SPI）：单人 `singleplayer:<存档目录名>`；多人 `mp:<host>:<port>`       | 配置隔离根、缓存命名空间、会话生命周期     |
| **worldId**  | 服务端推送的**存档级随机 id 文件**（§4.2）；id 文件不可用时缺省 `""`                    | 世界的物理身份；会话切换判定               |
| **worldName**| 玩家可控命名，world-map.json 中 `worldName → worldId` 映射的键                          | **anchors 与 pos.world 实际引用的名字**    |

```java
public interface ServerIdentifier {
    /** 当前会话的服务器标识；会话未就绪（主菜单/已断开）返回 null */
    @Nullable String currentServerId();
}

public interface WorldIdentifier {
    String id();
    /** 当前服务器内的 worldId；不适用返回 null；{@code ""} 是明确的「无 id/未推送」答案 */
    @Nullable String currentWorldId();
}
```

- 内置 `ServerIdentifier`：`SingleplayerServerIdentifier`（存档目录名，经 `getWorldPath(LevelResource.ROOT)` 取归一化后的目录名）、`MultiplayerServerIdentifier`（`ServerData.ip`，无端口补 25565）
- 内置 `WorldIdentifier`：`ServerPushedWorldIdentifier`（读网络推送 holder，单人与多人均生效——单人由集成服推送）；SPI 全部不适用时缺省 `""`
- worldId 变更的兼容映射：**换游戏复用**时 worldId 变化（如"只搬 region 到新存档"），`/wh world bind` 换绑一行即可让全部仓库继续工作

## 4.2 服务端推送（world_id payload）与随机 id 文件

- **存档级随机 id 文件 `yuanluworldid.txt`**（Xaero `xaeromap.txt` 同款粒度，格式单行 `id:<int>`）：位于存档根（`getWorldPath(LevelResource.ROOT)`），服务端首次读取时生成并写回，此后**存档目录改名/复制 id 不变**。粒度 = 存档级（所有维度共享同一 worldId，维度由 dimId 区分，与三层模型一致）。**只防目录改名，不防"只搬 region 到新存档"**——新存档生成新 id，由玩家手动换绑（`/wh world bind`）恢复
- 单人与专用服统一：`ServerWorldIdSync` 挂 main entrypoint，单人的集成服与装本 mod 的专用服都走同一逻辑；原版服务端无推送，worldId 缺省 `""`
- S2C `yuanlu-warehouse:world_id` v2，载荷 `{protocol:int, worldId:string, levelName:string, levels:[string]}`：`levelName` = 服务端存档名（worldName 默认名建议，避免数字 id 作默认名），`levels` = 服务端全部维度（`/wh world list` 展示用）。**v2 codec 与 v1 mod 版本错配解码失败断连——两端须同版本 mod**（universal jar 策略下天然满足）；原版客户端不声明 channel 永不收到，零兼容性风险
- 服务端：`ServerPlayConnectionEvents.JOIN` 推送 + 服务端 tick 检测变化重发 + **每 5s 周期性重发**（自愈客户端 JOIN 清空与推送到达的顺序竞态）；发送前必须 `ServerPlayNetworking.canSend(player, type)`
- id 文件按 server 实例缓存（tick 每帧调用不可每帧 IO），`SERVER_STOPPED` 清理；只读文件系统等 IO 失败时回退 `""`（log warn）

## 4.3 worldName 映射（world-map.json）

```json
{
  "schemaVersion": 1,
  "servers": {
    "singleplayer:caoyuan-26_1": { "新的世界": "-1979958895" },
    "mp:mc.example.com:25565":   { "lobby": "88213", "survival": "88213" }
  }
}
```

- 会话激活时经 `WorldNameMapper.resolveActive(serverId, worldId)` 解析激活 worldName；无映射则**自动创建默认条目并持久化**：默认名优先取服务端推送的 `levelName`（payload v2，单人场景推送未到达前本地读 level.dat 的 LevelName），否则回退 worldId 本身；默认名冲突追加 `#2` 后缀
- 同一 worldId 允许多别名，激活名取插入序第一个；`/wh world list` 查看、`/wh world rename <from> <to>` 改名、`/wh world bind <名称> [worldId]` 手动绑定/换绑（worldId 缺省 = 当前会话 worldId，是"只搬 region/复制存档"场景的恢复入口）
- **`""` 绑定自动迁移（幂等）**：旧条目 worldId 为 `""` 时；客户端拿到新文件 id 后，`resolveActive` 自动把该 serverId 下所有绑 `""` 的条目重绑为新 id——旧 worldName 与锚点无缝衔接，无版本标记（无 `""` 条目即 no-op）
- worldName 为任意 JSON 字符串（anchors 三层嵌套避免分隔符转义问题）

## 4.4 会话切换

引擎每 tick 解析 `(serverId, worldId, worldName)` 组成 `WorldSession` 快照；**serverId、worldId 或 worldName 任一变化视为会话切换**：MEMORY 缓存清空、DISK 缓存卸载、运行中的搬运终止并报告。

## 4.5 维度与 WorldDim

dim 即 MC 维度 id（如 `minecraft:overworld`）。`(serverId, worldName, dimId)` 三元组下称 **WorldDim**，是 anchor、容器坐标、寻路目标的完整限定；仅 dim 不构成唯一性。

**解析流程（会话相对）**：会话 = (serverId, worldId) → world-map.json 得激活 worldName → 仓库 anchors `serverId → worldName → dimId` 下按当前会话解析。仓库因此天然跨游戏复用：当前服务器无对应 anchor 即视为不可达，无需绑定单一服务器。
