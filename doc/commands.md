# Yuanlu Warehouse 命令指南

> 适用版本：MC 26.1+（Fabric），纯客户端模组。
> 设计文档见 `doc/PDD.md` §10；本文面向使用者，所有命令均为**客户端命令**，单人/多人服务器均可直接使用，无需服务端安装。

---

## 目录

- [快速上手](#快速上手)
- [命令总表](#命令总表)
- [基础组](#基础组)
- [引擎控制组](#引擎控制组)
- [容器组](#容器组)
- [标记模式详解](#标记模式详解)
- [规则组与选择器语法](#规则组与选择器语法)
- [选区组](#选区组)
- [跨仓库搬运组](#跨仓库搬运组)
- [世界映射组](#世界映射组)
- [配置组](#配置组)
- [核心概念](#核心概念)
- [常见问题（FAQ）](#常见问题faq)

---

## 快速上手

最简场景：把一个「产出箱」的东西搬进一个「存储箱」。

```
/wh create main                 # 1. 创建仓库（第一个仓库自动激活）
/wh anchor set                  # 2. 站在仓库区域附近，把脚下设为基准点
/wh container mark INPUT        # 3. 进入标记模式 → 右键产出箱 → 再次执行退出
/wh container mark OUTPUT       # 4. 同样标记存储箱
/wh start                       # 5. 开始搬运
```

中途随时：

```
/wh stop                        # 暂停（可 continue 无损恢复）
/wh status                      # 看引擎状态与上次报告
```

> ⚠️ **重要**：标记（注册）时会扫描一次容器内容作为缓存种子。
> 之后如果你**手动**往箱子里放了东西，引擎默认不会重扫（`cacheTtlSeconds = 0` 表示缓存永不过期），
> 可能直接判定「INPUT 为空」而结束。
> 处理方式任选其一：
> - 指向该箱子执行 `/wh container memory clear` 清掉缓存，让引擎重新探索；
> - 或先设置 `/wh config cacheTtlSeconds 10`（缓存 10 秒过期，超时自动重扫）再 start；
> - 或标记前先把物品放好。

---

## 命令总表

`/wh` 与 `/warehouse` 完全等价（后者是别名）。

| 分组 | 命令 | 说明 |
|------|------|------|
| 基础 | `/wh help` | 帮助 |
| | `/wh list [name]` | 列出全部仓库 / 某仓库详情 |
| | `/wh create <name>` | 创建仓库 |
| | `/wh remove <name>` | 删除仓库 |
| | `/wh use <name>` | 激活仓库 |
| | `/wh status` | 当前仓库概览 + 引擎状态 |
| | `/wh show` | 当前仓库容器明细 |
| | `/wh anchor set [x y z]` | 设置基准点（缺省=脚下） |
| | `/wh reload` | 从磁盘重载全部配置 |
| 引擎 | `/wh start [--pathfinder <id>]` | 开始搬运 |
| | `/wh stop` | 暂停 |
| | `/wh continue` | 断点继续（需 SUSPENDED） |
| | `/wh restart` | 重置状态重新开始 |
| | `/wh abort` | 终止并退出 |
| 容器 | `/wh container list` | 容器列表 |
| | `/wh container add [x y z] [--type T] [--rule R]` | 注册容器 |
| | `/wh container mark <type> [--rule R] [--template T]` | 标记模式开关 |
| | `/wh container remove [x y z]` | 注销容器 |
| | `/wh container type [x y z] <type>` | 改 IOType |
| | `/wh container mode [x y z] <mode>` | 改规则模式 |
| | `/wh container rules <pos> <add\|remove> <ruleId>` | 绑/解规则（坐标式） |
| | `/wh container rules <add\|remove> <ruleId>` | 绑/解规则（准星式） |
| | `/wh container memory [x y z] [clear]` | 查询/清除容器缓存 |
| 规则 | `/wh rule list` | 规则列表 |
| | `/wh rule create <id>` | 创建空规则 |
| | `/wh rule delete <id>` | 删除规则（被引用时拒绝） |
| | `/wh rule show <id>` | 查看规则条目 |
| | `/wh rule add <id> <selector> [选项]` | 追加物品条目 |
| | `/wh rule remove <id> <index>` | 删除第 index 条（1 起） |
| 选区 | `/wh select pos1 [x y z \| --look]` | 设角 1 |
| | `/wh select pos2 [x y z \| --look]` | 设角 2 |
| | `/wh select expand <count> <dir>` | 沿方向扩展角 2 |
| | `/wh select show` / `clear` | 查看 / 清除选区 |
| | `/wh select set-type <type>` | 选区内批量改 IOType |
| | `/wh select set-rule <id>` | 选区内批量绑规则 |
| | `/wh select set-cache <type>` | 选区内批量改缓存类型 |
| | `/wh select plan` | （一阶段 stub，暂不可用） |
| 跨仓库 | `/wh transfer <src> <dst> start` | 开始跨仓库搬运 |
| | `/wh transfer status` / `stop` | 状态 / 停止 |
| 世界 | `/wh world list` | 世界映射列表（`*`=当前）+ 服务端报告的维度 |
| | `/wh world info` | 当前服务器 / 世界 / 世界名 / 维度总览 |
| | `/wh world bind <名称> [worldId]` | 手动绑定/换绑世界名 |
| | `/wh world rename <from> <to>` | 重命名世界 |
| 配置 | `/wh config show` | 查看全部配置项 |
| | `/wh config set <key> <value>` | 修改并持久化配置项 |

坐标参数约定：所有 `[x y z]` 支持 `~` 相对坐标；缺省时通常取**准星指向的方块**。

---

## 基础组

### `/wh create <name>`

创建仓库并立即持久化到磁盘。若当前没有激活仓库，自动激活新仓库。
`name` 即磁盘上的 `warehouses/<name>.json` 文件名。

### `/wh anchor set [x y z]`

为当前维度设置**基准点**。容器坐标以「相对基准点的偏移」存储（§3.6），
这样整套仓库布局可以被复制到其它维度/世界复用。

- `/wh anchor set` —— 以你当前脚下位置为基准点；
- `/wh anchor set 0 64 0` 或 `/wh anchor set ~ ~-3 ~` —— 显式坐标。

每个维度独立记录。标记/注册容器前必须先设基准点，否则报「请先设置基准点」。

### `/wh list` / `/wh list <name>`

- 无参：列出全部仓库（标记当前激活项、容器数、规则数）；
- 带名：打印该仓库每个容器的坐标、标签、IOType、规则模式与规则引用。

### `/wh status` / `/wh show`

- `status`：当前仓库的容器统计（INPUT/OUTPUT/TEMP 数量）、规则数、引擎状态与上次运行评级；
- `show`：容器明细（等价 `/wh container list`）。

### `/wh reload`

从磁盘重新加载全部配置（mod config + 仓库 + 全局规则），并刷新内存中的运行时配置。
适用于手改 JSON 后热加载。

---

## 引擎控制组

| 命令 | 前置状态 | 效果 |
|------|----------|------|
| `start` | 空闲 | 从 ENTRY 进入完整流程 GET_TEMP → GET_INPUT → PUT_OUTPUT → PUT_TEMP |
| `start --pathfinder <id>` | 空闲 | 同上，但**仅本轮**使用指定寻路器（如装了条形寻路插件） |
| `stop` | 运行中 | 暂停；之后 `continue` 可无损恢复（不跳过任何容器） |
| `continue` | SUSPENDED | 恢复被异常暂停的搬运；出错的容器按「跳过」处理继续本轮 |
| `restart` | 非运行中 | 重置轮次标志与状态机，从 ENTRY 重新开始 |
| `abort` | 存在状态 | 终止并回到空闲 |

**暂停原因与报告**：运行结束（或挂起）时会输出报告消息 `wh.report.*`：

| 报告 | 含义 |
|------|------|
| `input_empty` | INPUT 全部探索过且无可取出（正常结束） |
| `storage_full` | OUTPUT/TEMP 全满且背包放不下（正常结束） |
| `no_progress` | 连续 2 轮无任何动作（异常终止） |
| 探索失败等 | 挂起（SUSPENDED）后可用 `continue` |

评级（grade）：`PERFECT`（正常完成）、`GOOD`（完成但有容器被跳过）、`ACCEPTABLE`、`BLOCKED`（挂起）、`ABNORMAL`（无进展终止）。

---

## 容器组

### 注册容器：两种方式

**方式 A — 标记模式**（推荐，逐个右键，带缓存种子）：

```
/wh container mark INPUT
# 现在准星对着目标容器并右键打开它 → 自动注册并扫描（27 格之类）
# 已注册的容器再次右键 = 移除
/wh container mark          # 再次执行退出标记模式（或换个 type 继续）
```

可带选项：`--rule <规则id>`（绑规则）、`--template <模板>`（同 rule 的别名入口）。

**方式 B — 单条命令注册**：

```
/wh container add                   # 准星指向容器，注册为 INPUT
/wh container add 2 64 -1 --type OUTPUT --rule 杂项
/wh container add ~ ~1 ~ --type TEMP
```

注意：命令式注册**不打开容器**，没有扫描，容器处于「未探索」状态——引擎首轮会主动去探索它。

### 修改

```
/wh container type 2 64 -1 TEMP        # 改 IOType（同时回落默认规则模式）
/wh container mode 2 64 -1 WHITELIST   # 改规则模式
/wh container remove 2 64 -1           # 注销（缺省坐标=准星）
```

IOType 变更会触发规则引用重校验（如把 OUTPUT 改成 BLACKLIST + 无限条目等非法组合会被拒绝）。

### 规则绑定

```
/wh container rules add 杂项            # 准星指向容器，绑定规则 "杂项"
/wh container rules 2 64 -1 remove 杂项  # 坐标式解绑
```

### 缓存查询与清除

```
/wh container memory                # 准星容器：是否已探索、缓存类型、物品总数
/wh container memory 2 64 -1 clear  # 清除该容器的缓存（下次强制重新探索）
```

什么时候需要 `clear`：你手动改动过容器内容、且没有设置 TTL 时。见[快速上手](#快速上手)的警告。

---

## 标记模式详解

`/wh container mark <type>` 进入标记模式后：

1. **注册**：准星指向容器 → 右键打开界面。开屏瞬间自动完成身份校验 + 全容器扫描 + 注册 + 写入缓存种子。聊天栏显示 `已注册 <type> 容器 @ x y z（扫描到 N 格）`。
2. **移除**：对**已注册**的容器再右键打开一次 = 注销。
3. **退出**：再次执行 `/wh container mark ...`（任意 type 均可切换类型继续标）。

约束与提示：

- 打开界面时若准星没指着容器，本次采集取消（提示「打开界面时未先指向容器」）；
- 多格方块（大箱子）两半都会被合并为一个容器；
- 非多格类型不会因视线穿过多方块而误注册半边。

---

## 规则组与选择器语法

### 概念

容器通过引用**规则**（`ContainerRule`）决定「哪些物品、取/放多少」。每条规则是若干**物品条目**（`ItemRule`）的有序列表；条目 = 选择器 + 取反标志 + 数量选择器（可省略）。

容器的**规则模式**与 IOType 联动（可被 `container mode` 覆盖）：

| IOType | 默认模式 | 语义 |
|--------|----------|------|
| INPUT / TEMP | BLACKLIST | 规则描述「不要动」的物品 |
| OUTPUT | WHITELIST | 规则描述「只允许放入」的物品 |

### 条目语法

```
/wh rule add <规则id> <selector> [--negate] [--quantity <quantity>]
```

- `selector`：`type:value` 简写，或一段完整 JSON；
- `--negate`：条目取反（参与 BLACKLIST/WHITELIST 组合逻辑）；
- `--quantity <quantity>`：数量选择器，同样是 `type:value` 简写或 JSON。

**物品选择器**：

| 简写 | JSON 形态 | 匹配 |
|------|-----------|------|
| `id:minecraft:diamond` | `{"type":"id","value":"minecraft:diamond"}` | 物品 ID |
| `tag:minecraft:planks` | `{"type":"tag","value":"minecraft:planks"}` | 物品标签 |
| `name:钻石` | `{"type":"name","value":"钻石","fuzzy":true}` | 显示名（默认模糊匹配） |
| `nbt:{...}` | `{"type":"nbt","value":"..."}` | 物品组件串（26.1 数据组件），匹配完整组件表 |
| 组合 | `{"type":"composite","op":"AND","selectors":[...]}
```
|
op
```
= `AND` / `OR` / `NOT`（NOT 恰好 1 个子选择器） |

**数量选择器**：

| 简写 | 含义 |
|------|------|
| `count:64` | 固定数量 |
| `group:9` | 按组取（如整组StackSize） |
| `fill_slots:3` | 填满 N 个槽位 |
| `percent:50` | 目标容量的百分比 |

（省略 `--quantity` = 不限量。）

### 示例

```
# 输出箱只收钻石和下界合金锭（WHITELIST 默认，两条 OR 语义由多容器+模式实现，此处直接建组合）
/wh rule create valuables
/wh rule add valuables id:minecraft:diamond
/wh rule add valuables id:minecraft:netherite_ingot
/wh container rules add valuables        # 准星指向输出箱

# 输入箱：垃圾（圆石、泥土）不搬（INPUT 默认 BLACKLIST）
/wh rule create junk
/wh rule add junk id:minecraft:cobblestone
/wh rule add junk tag:minecraft:dirt     # 示意
/wh container rules add junk

# 带数量：放满 3 组即止
/wh rule add valuables id:minecraft:diamond --quantity count:192

# 取反：除了工具都要（WHITELIST 下的反向表达）
/wh rule add valuables tag:minecraft:tools --negate

# JSON 形式（含空格/冒号的值务必加引号）
/wh rule add junk "{type:'composite',op:'OR',selectors:[{type:'id',value:'minecraft:cobblestone'},{type:'name',value:'垃圾'}]}"
```

规则被任何容器引用时不能删除（`rule delete` 会拒绝）；`rule add` 的条目会即时做 D2 严格校验，非法组合当场拒绝并回滚。

---

## 选区组

用于对一片区域内的**已注册容器**做批量操作（不是框选注册）。

```
/wh select pos1 0 64 0        # 角 1（缺省=脚下；--look=准星）
/wh select pos2 10 70 10      # 角 2
/wh select expand 3 up        # 角 2 向上扩 3 格
/wh select set-type OUTPUT    # 框内所有容器改为 OUTPUT
/wh select set-cache MEMORY   # 框内统一缓存类型
/wh select set-rule valuables # 框内统一绑规则
/wh select show / clear
```

方向取值：`north south east west up down`。
IOType 批量变更同样触发规则重校验，失败则整批拒绝。

`/wh select plan` 为一阶段 stub（AgentPlanner 属 §14 裁剪范围），执行会提示暂不可用。

---

## 跨仓库搬运组

把仓库 A 视为 INPUT 源，整体搬进仓库 B 的 OUTPUT：

```
/wh transfer main backup start
/wh transfer status
/wh transfer stop
```

- 搬运期间叠加一个**overlay 仓库**（id 形如 `src→dst`）并自动激活，避免污染原仓库；
- overlay 随本轮 `RUN_FINISHED` 自动回收；`/wh transfer stop` 也可手动结束；
- 有 overlay 存在时，`/wh remove` 与再次 `transfer start` 会被拒绝；
- 完成条件与单仓库一致（input_empty / storage_full / no_progress）。

---

## 世界映射组

配置里的世界标识分三层（设计详见 `doc/PDD.md` §4）：

| 层 | 内容 | 说明 |
|----|------|------|
| **serverId** | 单人 `singleplayer:<存档目录名>`；服务器 `mp:<host>:<port>` | 配置隔离根：锚点（anchors）就挂在这层下面 |
| **worldId** | 存档的物理身份 | 装了本模组的服务端（含单人的集成服）会自动在存档根生成 `yuanluworldid.txt`（随机 id）并推送；存档**改名/复制后 id 不变**。未装模组的服务器缺省 `""` |
| **worldName** | 你起的名字 | 仓库锚点定位、`pos.world` 实际引用的名字 |

进入一个世界时会自动把 worldName 映射到 worldId（默认名取存档名，如「新的世界」），一般无需手动干预。

### `/wh world list`

列出当前服务器的全部映射条目，`*` 标记当前激活的世界名；装了模组的服务端还会附上它报告的维度列表（仅展示，便于确认服务端上有哪些世界/维度）。

### `/wh world info`

当前会话总览：serverId、worldId、激活 worldName、当前维度。排查「配置怎么没生效」时先看这里。

### `/wh world bind <名称> [worldId]`

手动把 `<名称>` 绑定到 `[worldId]`（缺省 = 当前世界的 worldId）；名称已存在则为换绑。

典型场景：**把服务器的世界下载到本地**。服务端世界与本地存档的 worldId 相同（同一条 `yuanluworldid.txt` 随存档走），在新 serverId 下执行：

```
/wh world bind 原世界名
```

即恢复「世界名 → worldId」映射；锚点挂在 serverId 下，还需把 `warehouses/*.json` 中旧的 `mp:<host>:<port>` 键更名为新 serverId（单人为 `singleplayer:<新目录名>`）。

### `/wh world rename <from> <to>`

重命名世界。anchors 与 `pos.world` 引用的名字随之更新，无需改 JSON。

---

## 配置组

```
/wh config show
/wh config set <key> <value>
```

| key | 默认 | 说明 |
|-----|------|------|
| `debug` | false | 调试日志 |
| `defaultInteractionSpeed` | 2 | 交互间隔（tick），越大越慢 |
| `interactionJitterPercent` | 0 | 间隔抖动百分比（0=关；拟人化用） |
| `cacheTtlSeconds` | 0 | MEMORY/DISK 缓存 TTL 秒数；**0=永不过期**（仅会话内失效） |
| `slotAllocator` | first_fit | 槽位分配器（可由插件注册更多） |
| `reachLimit` | 4.5 | 视为可交互的最大距离 |
| `exploreFailMax` | 2 | 同一容器累计探索失败次数上限，达到即暂停 |
| `navRetryMax` | 3 | 寻路失败重试次数 |
| `timeouts.openTicks` | 20 | 开屏等待超时（tick） |
| `timeouts.confirmTicks` | 10 | 操作对账等待超时（tick） |
| `timeouts.settleTicks` | 2 | 落定重扫等待（tick） |

`config set` 即改即存盘（写回 `config/warehouse/config.json`）。

---

## 核心概念

### 坐标系：锚点 + 相对坐标

容器存储的是相对基准点（anchor）的偏移。世界标识分三层（§世界映射组）：`serverId`（单人 `singleplayer:<存档目录名>` / 服务器 `mp:<host>:<port>`）→ `worldName`（你起的名字）→ `dim`（维度，如 `minecraft:overworld`）。切换世界/维度后引擎按当前会话解析对应锚点；仓库布局可整体复用到同尺寸的其它地点（重新 `anchor set` 即可）。

### 缓存（§3.8/§5.4）

- **缓存只决定「要不要去」，不作为「操作依据」**——真正执行取放前会打开容器、对账、落定重扫；
- `NONE`：永不缓存，每次都实地探索；
- `MEMORY`：会话内缓存（默认），受 TTL 约束；
- `DISK`：跨会话持久缓存（`cache/<worldId>/` 下）；
- **手动开箱自动刷新**：你打开仓库内已注册容器并关闭后，缓存按真实扫描自动更新（无需标记模式）；
- 探索失败累计到 `exploreFailMax` 次后暂停（`continue` 可跳过该容器继续）；
- **放入方向预筛**：背包里没有「该容器规则可放」的物品（如 OUTPUT 空白名单）时，引擎不去开该箱子。

### 一轮搬运的流程

```
GET_TEMP（清空中转） → GET_INPUT（从 INPUT/TEMP 取货） → PUT_OUTPUT（放入 OUTPUT/TEMP） → PUT_TEMP（回填中转） → 判定出口
```

出口条件：INPUT 全空（`input_empty`）或 OUTPUT/TEMP 全满（`storage_full`）或连续 2 轮无进展。
存在未探索容器时，出口条件一律视为不满足——先探索再判定。

---

## 常见问题（FAQ）

**Q：`/wh start` 秒结束，报告 `input_empty`，但箱子里明明有东西？**
标记/注册时的缓存种子是空的，且 `cacheTtlSeconds = 0`（永不过期），引擎按缓存判定 INPUT 无物、连去都不去。解决：先指向箱子执行 `/wh container memory clear` 清掉缓存，或 `/wh config cacheTtlSeconds 10` 后重新 start。**另外**：从 v0.3 起，你**手动打开**仓库里的容器再关闭时，缓存会自动刷新——改完箱子后手动开合一次即可让引擎看到新内容。

**Q：手动开箱为什么能刷新缓存？判定会不会错绑？**
三层原版信号收敛：点击瞬间坐标（排除潜行放置）→ 开屏包 FIFO 配对 → 开合信号验证（箱子/木桶/潜影盒），关屏时再经 Detector 身份校验——错绑绝不写缓存。无动画容器（熔炉等）退化为点击配对，极端场景（受保护方块 + 5 秒内开另一同类箱）可能漏刷一次，下次实地访问自愈。

**Q：`/wh start` 只开了一次 OUTPUT 就结束，且 OUTPUT 里有规则却啥也没放？**
OUTPUT 默认 WHITELIST，**没有规则引用 = 任何物品都放不进去**（§3.7 设计）。引擎从 v0.3 起会在放入方向预筛：身上没有该 OUTPUT 规则可放的物品时直接不去开箱。给 OUTPUT 配规则：`/wh rule create x && /wh rule add x id:minecraft:diamond && /wh container rules add x`。

**Q：`未知或不完整的命令`？**
Brigadier 树较深，Tab 补全逐级往下打；带空格/冒号的参数（选择器、JSON）要用引号包住。

**Q：`请先用 /wh anchor set 设置基准点`？**
当前维度没设过锚点。站在仓库附近 `/wh anchor set` 即可。

**Q：标记模式提示「打开界面时未先指向容器」？**
右键打开容器那一刻，准星必须落在该容器的方块上（大箱子任意一半均可）。

**Q：命令是客户端的还是服务端的？**
纯客户端。在服务器上无需管理员权限，服务端不装也能用（服务端可选装以获得增强）。

**Q：存档改名/复制后，容器和锚点全「不见」了？**
锚点挂在 serverId 下，存档目录改名（或换台机器目录名不同）会得到新 serverId，旧数据仍在配置里，只是对不上号：

- **存档改名**：worldId（`yuanluworldid.txt`）不变，世界名映射自动衔接；把 `warehouses/*.json` 里旧的 `singleplayer:<旧目录名>` 键改成新目录名即可恢复锚点；
- **服务器世界下载到本地 / 复制存档**：先 `/wh world bind 原世界名` 恢复映射，再按上法迁移锚点键；
- 改之前可先 `/wh world info` 确认当前 serverId。
