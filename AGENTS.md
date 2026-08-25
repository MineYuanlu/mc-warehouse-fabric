# Yuanlu Warehouse — AGENTS.md

Minecraft Fabric 模组（MC >= 26.1，Java 25）。基础框架与 SeedMapForXaero 同构：
跨版本兼容矩阵、两层测试（JUnit → E2E 客户端 GameTest）、GitHub Release 发布。

## Build & Run

```bash
./gradlew build        # 编译 + JUnit + JAR
./gradlew runClient    # launch Minecraft
```

**Java 25 required.** MC 26.1+ ships unobfuscated — no mapping needed.

LSP maybe shows false errors when edit java files — only `./gradlew build` is authoritative.

版本参数：`gradle.properties` 的 key 即 CI `-P` 覆盖的 key（`minecraft_version`/`fabricApiVersion`/`loader_version`）。本地覆盖可用 `gradle.local.properties`（gitignored，同 key 格式）。

## Testing

两层测试（详见 `doc/testing.md`）：JVM 单测 → E2E 客户端 GameTest。

```bash
./gradlew build                                            # JVM 单测
./gradlew runProductionClientGameTest                      # E2E（真实启动 MC）
./gradlew runProductionClientGameTestUniversal -PuniversalJar="$(find build/libs -name 'yuanlu-warehouse-*.jar' ! -name '*-sources.jar' | head -1)"
# E2E（复用预构建 universal jar；排除 -sources.jar，其 fabric.mod.json 是未展开的 ${version} 模板）
```

**Single universal jar**: `fabric.mod.json` hardcodes `"minecraft": ">=26.1"` (not templated). The published jar is compiled against the oldest supported MC line so referenced symbols are a subset of all supported versions. CI (`matrix-test.yml`): `test` = N-MC compile+JUnit (source-compat early warning, not published), `build-universal` = build the one jar, `universal-e2e` = run that same jar on all supported MC versions. Future breaking MC versions are caught by universal-e2e as `versions.json` grows.

版本矩阵：`versions.json` + `tools/resolve_versions.py`（update/check/matrix 三模式），每周由 `refresh-versions.yml` 自动刷新。CI 矩阵 + E2E 定义在 `.github/workflows/matrix-test.yml`。

### CI 触发矩阵（单人开发流程）

开发流程：feature 分支只跑快速检查；**develop 是合入 master 前的完整门禁**（版本矩阵 + E2E + 打包全绿才合）；master 再次全量确认；tag 触发的 build 供发版引用。

| 触发 | `pr-check.yml` | `matrix-test.yml` | `build.yml` |
| ---- | -------------- | ----------------- | ----------- |
| `dev/xxx`、`fix/xxx` 推送 | ✅ 快速检查（JUnit + JAR） | — | — |
| develop 推送 | — | ✅ 版本矩阵 + E2E | ✅ 打包 |
| master 推送 | — | ✅ 版本矩阵 + E2E | ✅ 打包 |
| tag `v*` 推送 | — | — | ✅ 打包 |
| pull_request | ✅ 快速检查 | — | — |
| 手动 | — | ✅ | build-test-jar / release |

## Release

`workflow_dispatch` in `.github/workflows/release.yml` with patch/minor/major choice. Auto-bumps `gradle.properties`, commits, tags (vX.Y.Z), builds, creates GitHub Release（含全部 jars）。

`build-test-jar.yml`（workflow_dispatch）：手动产一个生产 JAR 供人工测试，无 bump/tag/发布。`ref` input 指定分支/tag/SHA（默认 `master`），`runTests` 开关打包时的 JUnit。

打包统一在 `reusable-package.yml`（编译 + JUnit + 生产 jar 模板展开校验）。

## Architecture

### Source layout

```
src/client/java/bid/yuanlu/mc/warehouse/
└── client/             # client entrypoint (mixin 目录待功能引入时建)
src/main/java/bid/yuanlu/mc/warehouse/
├── YuanluWarehouse.java    # ModInitializer
└── (mixin/ 待功能引入)
src/client/resources/    # yuanlu-warehouse.client.mixins.json
src/main/resources/      # fabric.mod.json + mixins json + icon.png
src/gametest/            # E2E client gametest（独立测试 mod，见 doc/testing.md）
src/test/                # JUnit 单测（见 doc/testing.md）
tools/                   # resolve_versions.py（版本矩阵解析）
doc/                     # testing.md 等开发文档
.github/workflows/       # pr-check / matrix-test / build / release / refresh-versions / reusable-package / build-test-jar
```

### Cross-cutting rules

- All UI strings go through i18n (`Component.translatable` / `I18n.get`) — add both `en_us.json` and `zh_cn.json`
- `fabric.mod.json` 的 `"minecraft"` 依赖是硬编码下限（不随版本模板化）——改支持范围时同时更新它和 `versions.json` 的 `minMinecraft` 与 `tools/resolve_versions.py` 的 `MIN_MINECRAFT`

## Dependencies

| Dependency | Source                                    |
| ---------- | ----------------------------------------- |
| Fabric API | `net.fabricmc:fabric-api`（Modrinth/Maven）|
