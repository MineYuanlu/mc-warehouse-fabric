# Yuanlu Warehouse — AGENTS.md

Minecraft Fabric 模组，**纯客户端**自动化仓库管理（服务端可选装增强）。设计文档 `doc/design/`（按模块拆分，见 `doc/design/README.md`）；命令手册 `doc/commands.md`。

## Build & Run

```bash
./gradlew build        # 编译 + JUnit + JAR
./gradlew runClient    # 启动 MC
```

**Java 25 必需**。MC 26.1+ 无混淆，不需 mapping。LSP 对 Java 文件可能报误错，仅以 `./gradlew build` 为准。

版本参数：`gradle.properties` 的 key 即 CI `-P` 覆盖的 key（`minecraft_version`/`fabricApiVersion`/`loader_version`）。本地覆盖用 `gradle.local.properties`（gitignored，同 key 格式）。

## Testing

两层测试（详见 `doc/testing.md`）：

```bash
./gradlew build                                            # JVM 单测（src/test）
./gradlew runProductionClientGameTest                      # E2E 真实启动 MC（本地无显示加 -PclientGameTestXVFB=true）
./gradlew runProductionClientGameTestUniversal -PuniversalJar="$(find build/libs -name 'yuanlu-warehouse-*.jar' ! -name '*-sources.jar' | head -1)"
```

- **Universal jar 策略**：单 jar 编译于最老支持 MC 版本，运行时对所有版本生效。`fabric.mod.json` 的 `"minecraft": ">=26.1"` 硬编码（不模板化）。改支持范围时同步更新 `versions.json` 的 `minMinecraft` 和 `tools/resolve_versions.py` 的 `MIN_MINECRAFT`。
- **E2E 死锁绕开**：断言后 `runOnServer(server -> server.halt(false))` 在 server 线程内执行，再 `close()`。
- **gametest 断言用 AssertionError**（production runtime 无 JUnit）；CI 靠 grep 日志中的 `yuanlu-warehouse E2E assertions passed` 判定成功。
- **网络同步器 bug**：production run task 加 `-Dfabric.client.gametest.disableNetworkSynchronizer=true`。
- **sourcesJar 排除 fabric.mod.json**：未展开 `${version}` 模板被误加载会解析失败；CI 会对生产 jar 断言模板已展开。
- **split sources 陷阱**：`test`/`gametest` 源集默认只依赖 main，业务代码在 client，须显式挂 `sourceSets.client.output`。
- **跨版本 gametest API**：26.1 与 26.2 的 fabric-client-gametest API 有差异（如 `waitForChunksRender` 所在类变了），测试用反射兼容；MC 26.2 兼容靠静态常量与 screen 访问的跨版本化。
- 版本矩阵：`versions.json` + `tools/resolve_versions.py`（update/check/matrix 三模式），`refresh-versions.yml` 每周一刷新。

## CI 触发

| 触发                                          | 检查                                                                      |
| --------------------------------------------- | ------------------------------------------------------------------------- |
| 任何非 master/develop 分支推送 + pull_request | pr-check（JUnit + JAR）                                                   |
| develop / master 推送                         | matrix-test（版本矩阵+E2E）+ build（打包）                                |
| tag `v*` 推送                                 | build 打包                                                                |
| workflow_dispatch                             | matrix-test / build-test-jar / release（bump 版本+打 tag+GitHub Release） |

单人流程：`dev/xxx` → develop（全面门禁）→ master → 手动 release。

## Architecture

```
src/
├── main/java/.../warehouse/     # ModInitializer + net/（服务端世界标识推送，PDD §4.2；两侧同源 universal jar）
├── client/java/.../warehouse/   # ★ 全部业务代码（纯客户端 mod）
│   ├── api/                     # 公开接口（container/item/world/navigation/interaction/plugin/warehouse/transport）
│   ├── core/                    # 核心实现（引擎、缓存、配置、事件、registry、mark）
│   ├── impl/                    # 内置实现（Selector、Allocator、Navigator、Detector、Quantity）
│   ├── command/                 # /wh 命令（/warehouse 别名）
│   ├── mixin/                   # 3 个注入点：AbstractContainerScreen / ClientPacketListener / MultiPlayerGameMode
│   └── util/                    # 工具类
├── gametest/java/               # E2E GameTest（独立 source set + 独立测试 mod）
├── test/java/                   # JUnit 单测
├── main/resources/              # fabric.mod.json（硬编码 >=26.1）
└── client/resources/            # client mixins config + lang/
```

## MC 26.1+ API 陷阱（E2E 实测教训）

- 点击必须经 `MultiPlayerGameMode.handleContainerInput`（26.1 更名，旧名 handleInventoryMouseClick）；`menu.clicked` 只是本地预测**不发包**。
- 客户端 `AbstractContainerMenu.getStateId()` 不随服务端包更新，不可作对账信号。对账 = 受监视槽位/光标内容变化 + 2 tick 稳定窗口。
- 26.1 更名：`GenericContainerScreen` → `ContainerScreen`；`displayClientMessage` 已删除 → `sendOverlayMessage`（动作栏）/ `sendSystemMessage`（聊天）。
- Brigadier 字面量参数不能含冒号（如 `count:64`）——需单参数自行分词。
- 世界标识：单机 worldId 用 `getWorldPath(LevelResource.ROOT)`（26.1 `getServerDirectory()` 返回游戏根目录，是坑）；多人用 `mp:<host>:<port>`；另有存档级随机 id 文件。
- **反模式红线**（PDD §15.12）：全程 tick 驱动单线程，禁 worker 线程与 `Thread.sleep` 节流；槽位归属一律用 `Slot.container` 判定，禁索引算术；盲发点击必须逐击对账；操作依据永远是对账后的槽位级快照。

## Cross-cutting rules

- 所有 UI 文本走 i18n（`Component.translatable` / `I18n.get`），同时提供 `en_us.json` 和 `zh_cn.json`
- 命令系统与未来 UI 操作同一套 API（`api/` 下的接口）；不通过 Controller 层
- 插件通过 Fabric entrypoint `warehouse-plugin` 加载（`YuanluWarehouseClient` 注册，PDD §9.1）
- 代码不从 `src/client/java` 引用 `src/main/java` 以外的包（`splitEnvironmentSourceSets` 隔离）

## 依赖源码参考（refs）

探索/实现时直接读本地反编译源码（gitignored，仅本地），不依赖在线：

```bash
python3 tools/gen_refs.py mc                # 全量反编译 MC → refs/dep-src/minecraft/
python3 tools/gen_refs.py dep group:artifact:version   # 单依赖：优先 -sources.jar，缺则 fernflower
python3 tools/gen_refs.py discover          # 列出 gradle/loom cache 的 jar 坐标+路径
```

- MC 26.1 无混淆，反编译名即真名。MC jar 在 loom cache（common + clientOnly 两 jar，无类重叠）；`mc` 把两者一次传入 fernflower 做引用解析。
- 别把 Python `zipfile` 合并的 jar 传给 fernflower（会报 `zip END header not found`）。
- refs 目录有参考 mod `refs/libs/Wurst7`（前身实现，PDD §15.12 对照记录），`refs/dep-src` 有反编译源码。

## 设计参考

- `doc/design/`：持久性设计文档（原 PDD + UI-PDD 按模块拆分，**章节号不变**——代码注释中的「PDD §x.x」引用按 `doc/design/README.md` 的对照表定位）。数据模型（data-model.md）、传输引擎（transport-engine.md）、交互协议（interaction-protocol.md）、插件 API（plugin-api.md）、设计决策与 MC 26.1 API 教训（design-decisions.md）等。实现前先读对应模块。
- `doc/README.md`：文档索引与维护原则（设计文档不记进度；阶段性文档删后由 `doc/archive/INDEX.md` 索引 git 历史）。
