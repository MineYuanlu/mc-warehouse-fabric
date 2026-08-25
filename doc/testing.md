# Yuanlu Warehouse — 测试文档

两层测试：**JVM 单元测试**（快，无 MC 运行时）→ **E2E 客户端 GameTest**（真实启动 MC，CI 上跑）。全部由 CI 矩阵自动执行。

## 测试分层与覆盖

### 1. JVM 单元测试 — `src/test/java`（JUnit 5）

无 MC 客户端运行时的纯逻辑测试（`fabric-loader-junit` 引导 loader + 探测游戏版本）。

| 测试类                | 覆盖                                                                     |
| --------------------- | ------------------------------------------------------------------------ |
| `FrameworkSmokeTest`  | loader-junit 引导链路：游戏版本探测成功、版本在支持线内（矩阵各版本下跑） |
| `McBootstrap`         | 基类：需要注册表/静态初始化的测试先 `Bootstrap.bootStrap()`              |

### 2. E2E 客户端 GameTest — `src/gametest/java`（fabric-client-gametest）

独立 source set + 独立测试 mod（`yuanlu-warehouse-gametest`），production 真实启动：

创建单机世界 → 等 chunk 渲染 → 截图 → 打印 `yuanlu-warehouse E2E assertions passed` 供 CI grep。当前为冒烟骨架，验证 universal jar 在目标版本可加载、入口点可用；业务断言随功能补充。

主 mod 和测试 mod 的 `fabric.mod.json` 都用宽松下限写死版本（`>=26.1`），**不 expand**——单 jar 在任意支持版本上通用，无需跟随矩阵精确版本。

## 版本矩阵与单 jar 策略

- `versions.json` + `tools/resolve_versions.py`（update/check/matrix 三模式）：piston-meta 拉全部 release MC 版本（>= `MIN_MINECRAFT`=26.1），Modrinth 拉每个版本最新的 fabric-api。
- 版本覆盖用 `-P` 属性传入，**key 与 gradle.properties 同名**（见下节）。
- `refresh-versions.yml` 每周一自动跑 `update` 并仅在有真实变更时提交。
- **发布产物是单 jar（universal jar）**：`fabric.mod.json` 的 `"minecraft": ">=26.1"` 让 loader 接受全部版本；编译目标取全局最老支持线，引用的符号是全部支持版本集合的子集，向前兼容所有版本。未来 MC 若有破坏性变更，由 CI 的 universal E2E（矩阵随 versions.json 自动增长）提前暴露。

## 运行

### 本地

```bash
# JVM 单元测试（含 JUnit）
./gradlew build

# E2E client gametest（需真实显示或用 -PclientGameTestXVFB=true 无头）
./gradlew runProductionClientGameTest

# 用预构建 universal jar 跑 E2E（验证发布产物，不在目标版本上重编译 mod）
# 排除 -sources.jar：其 fabric.mod.json 是未展开的 ${version} 模板，误载会解析失败
./gradlew runProductionClientGameTestUniversal \
  -PuniversalJar="$(find build/libs -name 'yuanlu-warehouse-*.jar' ! -name '*-sources.jar' | head -1)"
```

### CI（`.github/workflows/matrix-test.yml`）

单人开发流程：`dev/xxx`、`fix/xxx` 只跑 `pr-check.yml` 快速检查；**develop / master 是全面门禁**（`matrix-test.yml` 版本矩阵 + E2E 与 `build.yml` 打包都触发）。

- `resolve`：校验 `versions.json` 新鲜度（过期仅告警不阻塞）+ 输出矩阵 JSON 与最老行（universal 编译目标）
- `test`（N 行 = 全部支持的 MC 版本，**源码兼容预警**）：`./gradlew build -Pminecraft_version=… -PfabricApiVersion=…`（含 JUnit）。保证源码在全部 MC 组合下可编译 + JUnit 通过；产物是编译载体，不发布
- `build-universal`（1 行）：按最老 MC 线编一个 universal jar，上传 artifact
- `universal-e2e`（N 行，依赖 `build-universal`）：真实启动 MC 的 E2E，**复用同一个 universal jar**，`runProductionClientGameTestUniversal -PuniversalJar=<artifact>` + 版本 `-P` 覆盖，grep `yuanlu-warehouse E2E assertions passed` 判定成功；每组合的 `gametest.log` + `run/screenshots` 按 `mc` 命名始终上传

> **运行时 vs 编译**：`test` 的 N 组合矩阵保证源码在全部 MC 下可编译 + JUnit 通过；`universal-e2e` 用发布产物（单个 universal jar）在全部 MC 上真实启动，验证 mixin 的运行时应用（目标方法/字段在对应版本真实存在、注入点命中）。两个 job 互补，缺一不可。

### 失败诊断

- universal-e2e 失败先看上传的 `gametest.log`：无客户端日志 = 构建阶段挂；有日志无断言 = 启动/死锁问题。
- 截图 artifact 为空 = 从未到截图阶段（构建失败或世界创建前就挂）。

## 已知限制与踩坑记录

1. **服务端 `runGameTest` 被禁用**（`build.gradle` `enableGameTests = false`）：game test server 与真实 DedicatedServer 行为不一致；我们只要客户端 E2E。
2. **E2E 退出死锁（MC 26）**：`IntegratedServer.halt` 先 `executeBlocking` 等 server 线程，而 fabric client gametest 的 phaser 让 server 卡在 `postRunTasks` → 三线死锁。绕开：断言后 `runOnServer(server -> server.halt(false))`（server 线程内不阻塞）。
3. **fabric-client-gametest 跨版本 API**：5.1.x（26.1）`getClientLevel().waitForChunksRender()`；6.0.0+（26.2）改用 `getConnection().waitForChunksRender()`——测试用反射兼容。
4. **网络同步器 bug**：production run task 加 `-Dfabric.client.gametest.disableNetworkSynchronizer=true`（fabric-docs warning）。
5. **CI 无头**：`useXVFB` 默认取 `CI` 环境变量；本地无显示时传 `-PclientGameTestXVFB=true`。
6. **Java 25 必需**；sources jar 排除 `fabric.mod.json`（未展开模板），生产 jar 在打包后由 `reusable-package.yml` 断言模板已展开。

## 参数命名约定

- `gradle.properties` 的 key **即** CI `-P` 覆盖的 key（camelCase / snake_case 保持原样）：`minecraft_version`、`loader_version`、`fabricApiVersion`。本地可用 `gradle.local.properties`（gitignored）同格式覆盖。
- 其他构建开关：`-PclientGameTestXVFB`、`-PuniversalJar`（`runProductionClientGameTestUniversal` 用，指向预构建 universal jar）。
