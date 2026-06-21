# AGENTS.md — MC Warehouse

## Build & run

```sh
./gradlew build
./gradlew runClient
./gradlew clean
```

- Minecraft 26.1.2, Java 25, Gradle 9.4, Fabric Loom 1.15, Fabric API 0.152.1
- **Unobfuscated Minecraft** (`mappings = officialMojangMappings()`): use `implementation` (not `modImplementation`), `jar` (not `remapJar`).
- No tests exist yet; no test framework configured.

## Project structure

```
src/
  client/java/bid/yuanlu/mcwarehouse/   # client-only code (entrypoint + mixins)
  main/java/bid/yuanlu/mcwarehouse/     # shared/common code (empty)
  main/resources/fabric.mod.json
```

- Mod ID: `mc-warehouse`, environment: `client` (pure client-side, no server logic).
- Entrypoint: `bid.yuanlu.mcwarehouse.MCWarehouseClient` (implements `ClientModInitializer`).

## Architecture (see ARCH.md)

| Layer | Path | Purpose |
|-------|------|---------|
| Controller | `controller/` | Business logic, shared by command & future UI |
| Command | `command/` | `/warehouse` + `/wh` subcommands, thin view layer |
| Engine | `engine/rule/`, `engine/container/`, `engine/pathfinder/`, `engine/highlight/` | Core logic: rule matching, container automation, path execution, rendering |
| Model | `model/` | Data models: Warehouse, ContainerInfo, ItemRule/ItemRules, selectors, quantifiers |
| Storage | `storage/` | JSON persistence (`warehouses/<name>/data.json`, `config/worlds.json`) |
| Mixin | `mixin/` | ContainerScreen, WorldRenderer, MultiPlayerGameMode hooks |

- Data root: `<game-dir>/mc-warehouse/`
- Coordinates: relative + per-warehouse anchor point.
- Container types: INPUT (搬出), OUTPUT (搬入), RELAY (暂存), IGNORE.
- ItemSelector: single-method interface `boolean matches(ItemStack)`.
- PathExecutor: tick-based state machine (`MOVING/ARRIVED/FAILED/DONE`).

## Key conventions

- Follow existing indentation (tabs in `.gradle`/`.json`, standard Java).
- Expand `fabric.mod.json` placeholders at build time via `processResources`.
- Do NOT add server-side logic; the mod is `environment: client` only.
