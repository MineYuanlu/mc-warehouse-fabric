# Yuanlu Warehouse — AGENTS.md

Minecraft Fabric 模组，**纯客户端**自动化仓库管理（服务端可选装增强）。设计文档见 `doc/PDD.md`。

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
./gradlew runProductionClientGameTest                      # E2E 真实启动 MC
./gradlew runProductionClientGameTestUniversal -PuniversalJar="$(find build/libs -name 'yuanlu-warehouse-*.jar' ! -name '*-sources.jar' | head -1)"
```

- **Universal jar 策略**：单 jar 编译于最老支持 MC 版本，运行时对所有版本生效。`fabric.mod.json` 的 `"minecraft": ">=26.1"` 硬编码（不模板化）。改支持范围时同步更新 `versions.json` 的 `minMinecraft` 和 `tools/resolve_versions.py` 的 `MIN_MINECRAFT`。
- **E2E 死锁绕开**：断言后 `server.halt(false)` 在 server 线程内执行，再 `close()`。
- **网络同步器 bug**：production run task 加 `-Dfabric.client.gametest.disableNetworkSynchronizer=true`。
- **sourcesJar 排除 fabric.mod.json**：未展开 `${version}` 模板被误加载会解析失败。
- 版本矩阵：`versions.json` + `tools/resolve_versions.py`（update/check/matrix 三模式），`refresh-versions.yml` 每周刷新。

## CI 触发

| 触发 | 检查 |
|------|------|
| `dev/*` `fix/*` 推送 | pr-check（JUnit + JAR） |
| develop / master 推送 | matrix-test（版本矩阵+E2E）+ build（打包） |
| tag `v*` 推送 | build 打包 |
| pull_request | pr-check |
| workflow_dispatch | matrix-test / build-test-jar / release |

## Architecture

```
src/
├── main/java/.../warehouse/     # ModInitializer + 核心逻辑
│   ├── api/                     # 公开接口（ContainerDetector, ItemSelector 等）
│   ├── core/                    # 核心实现（引擎、缓存、配置）
│   ├── impl/                    # 内置实现（Selector、Navigator、Detector）
│   ├── command/                 # /wh 命令
│   ├── mixin/                   # 注入点
│   └── util/                    # 工具类
├── client/java/.../warehouse/client/  # ClientModInitializer
├── gametest/java/               # E2E GameTest
├── test/java/                   # JUnit 单测
├── main/resources/              # fabric.mod.json（硬编码 >=26.1）
└── client/resources/            # client mixins config
```

## Cross-cutting rules

- 所有 UI 文本走 i18n（`Component.translatable` / `I18n.get`），同时提供 `en_us.json` 和 `zh_cn.json`
- 命令系统与未来 UI 操作同一套 API（`api/` 下的接口）；不通过 Controller 层
- 插件通过 Fabric entrypoint `warehouse-plugin` 加载
- 代码不从 `src/client/java` 引用 `src/main/java` 以外的包（`splitEnvironmentSourceSets` 隔离）

## 设计参考

`doc/PDD.md` 包含完整的数据模型、状态机（mermaid）、传输引擎逻辑、插件系统 API 和一阶段实现范围。实现前先阅读对应章节。