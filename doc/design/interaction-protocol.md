# 运行时交互协议

> 原 PDD §6。章节号保持不变——代码注释中的「PDD §6.x」引用按本文件定位。

所有与容器的物理交互都经过本章协议。三大纪律——盲发点击不验证、GUI 被关当成功、快照记录客户端预测态——均由此章约束杜绝。

## 6.1 容器打开流程

到达 ≠ 可交互。打开容器是显式子流程，每步有超时：

```
openContainer(pos):
  0. CLOSE       关闭当前残留的任意 Screen（干净起点；残留界面会干扰会话绑定）
  1. PRECHECK    方块存在且某 Detector.matchesBlock 成立；
                 玩家距离 ≤ reachLimit（默认 4.5 格，服务端会校验触及距离）
                 失败 → 交还寻路 / 异常 CONTAINER_GONE
  2. FACE        转向方块中心
  3. OPEN        ContainerInteraction.requestOpen(handle)（默认实现：useItemOn 模拟右键）
  4. WAIT_SCREEN openTimeoutTicks 内收到 ScreenHandler 初始同步包即视为打开确认
                 （包内自带 syncId 与容器槽位数——以同步包为确认信号，而非轮询 Screen 实例）；
                 syncId 随即成为本次容器会话的身份键，
                 后续一切回调按 syncId 门控，不匹配者一律丢弃。
                 身份校验：Detector.matches 组合判定（方块实体类型 + 标题 + 槽位数，container-detection.md §8.1）。
                 超时 → 异常 CONTAINER_NOT_OPENED；不符 → 异常 UI_MISMATCH
  5. SCAN        同步包含初始槽位内容，据此执行扫描快照
```

## 6.2 执行原语与精确数量算法

**点击原语**（由 ContainerInteraction 提供，接口见 container-detection.md §8.3；每次调用经下方对账确认）：

| 原语                       | 行为                                                                 | 典型用途                 |
| -------------------------- | -------------------------------------------------------------------- | ------------------------ |
| quickMoveToPlayer(slot)    | 整堆 QUICK_MOVE：槽位 → 背包                                         | 取出方向快速路径         |
| quickMoveToContainer(slot) | 整堆 QUICK_MOVE：背包 → 槽位                                         | 存入方向快速路径         |
| pickupAll(slot)            | 左键拾起整堆到光标                                                   | 取出方向全取             |
| pickupHalf(slot)           | 右键拾起半组（向上取整）到光标                                       | 折半批量                 |
| placeOne(slot)             | 右键从光标放 1 个入槽                                                | 单件补齐 / 放回多余      |
| putBackHeld(slot)          | 左键把光标全部放回该槽                                               | 归还剩余                 |
| dragDistribute(slots[])    | QUICK_CRAFT 拖拽协议：开始拖拽→逐槽加入→结束拖拽，把光标堆均分到多槽 | 多目标槽一次发包序列分发 |

（quickMoveTo\* 是原语之上的语义封装；下文算法中简写为 quickMove。）

不支持原语的交互通道声明 `supportsExactAmount()=false`，引擎自动降级为整堆粒度。

**精确数量算法**（在协议层统一实现一份，所有交互通道零成本复用；双向算法均经前身实现真实运行验证，点击序列以其实现为准）：

存入方向——把背包槽位的 `count` 个物品存入配额 `quota` 个（仅当 `count > quota` 时启用，否则走 quickMove 快速路径）：

```
不变式：R(剩余配额) < S(剩余堆量)，由前置条件保证，不会过量存入
① 折半批量段：while (S > t && R >= S/2)      // t = 本槽初始 ≤4 ? 2 : 4（打磨过的最优阈值）
     pickupHalf(slot) → quickMoveToContainer(slot)；S -= S/2, R -= S/2     // O(log n)
② 单件补齐段：pickupAll(slot) → placeOne(slot) × R → quickMoveToContainer(slot)
     （源背包槽位自身充当暂存区：放回 R 个后整批移入）
③ 归还段：putBackHeld(slot)                  // 光标剩余放回原槽
复杂度 O(log n) + O(R)。
```

取出方向——从容器槽位 `nowCount` 个中取出 `needCount` 个到光标：

```
needCount ≤ ⌈nowCount/2⌉ ? pickupHalf 后 placeOne 放回 (⌈nowCount/2⌉ − needCount) 个
                         : pickupAll  后 placeOne 放回 (nowCount − needCount) 个
光标最终恰好持有 needCount 个；随后 dragDistribute 到目标背包槽位。
```

**两遍式落位**（SlotAllocator 默认实现的参考顺序）：先并入同类不满堆、再依次填空槽；每步先做容量预检，余量 ≤ 0 直接跳过该条目。

```
executeMove(move):
  1. 确保容器会话有效（Screen 已开且身份一致，syncId 门控生效），否则失败为 UI_CLOSED_EXTERNAL
     （执行中途丢屏不做 §6.1 重开续跑——半途计划作废，由引擎 SUSPENDED 处理）
  2. 按 transport-engine.md §5.3 计划粒度调用上述原语/算法执行
  3. 对账（D3）：confirmTimeoutTicks 内观察受监视槽位/光标内容变化并保持 2 tick 稳定窗口：
     a. 槽位内容符合预期增量 → 成功；
     b. 槽位被回滚至点击前状态 → CLICK_CORRECTED（服务端纠正/拒绝该操作）；
     c. 超时无变化 → 操作超时异常。
     b/c 均失效该容器缓存并 SUSPENDED。
     注：不依赖客户端 stateId——26.1 实测其不随服务端包更新，不能作对账信号。
  4. 计划推进只以对账后的状态为准；客户端预测值不作为任何决策输入
```

## 6.3 快照时机

- 只有**对账完成后**的状态才允许写入 ContainerMemory
- 一组 moves 执行完毕后再等 settleTicks 重扫一次作为最终快照（覆盖中途残留的预测态）

## 6.4 反作弊暴露面声明

默认 speed=2 tick/次 ≈ 10 click/s、固定顺序遍历容器，在装了反作弊插件的服务器上特征明显。缓解手段：调大 `interactionSpeed`、开启 `interactionJitterPercent`（每次操作附加随机延迟百分比，默认 0）。本模组立场为模拟正常玩家行为，不对绕过反作弊提供支持。

## 6.5 时间参数汇总（均可配置，configuration.md §11.4）

| 参数                | 默认 | 含义                         |
| ------------------- | ---- | ---------------------------- |
| reachLimit          | 4.5  | 开容器的最大触及距离（格）   |
| openTimeoutTicks    | 20   | 等待 Screen 打开的超时       |
| confirmTimeoutTicks | 10   | 单次点击等待服务端对账的超时 |
| settleTicks         | 2    | 一组操作后的最终稳定等待     |
| exploreFailMax      | 2    | 同一容器连续探索失败上限     |
| navRetryMax         | 3    | 同一寻路目标的引擎侧重试上限 |
