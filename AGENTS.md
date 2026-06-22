# AGENTS.md — MC Warehouse

## Build & run

```sh
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # launches game
./gradlew clean
```

- Minecraft 26.1.2, Java 25, Gradle 9.4, Fabric Loom 1.15, Fabric API 0.152.1
- **Unobfuscated Minecraft**: `mappings = officialMojangMappings()`, use `implementation` (not `modImplementation`), `jar` (not `remapJar`).
- Build passes. No test framework configured.

## Where code lives

All Java code is in `src/client/java/bid/yuanlu/mcwarehouse/` (~56 files). `src/main/java/` is empty (pure client mod via `splitEnvironmentSourceSets()`). Resources (`fabric.mod.json`, mixins config) are in `src/main/resources/`.

Entrypoint: `MCWarehouseClient` implements `ClientModInitializer`. Tick hook: `ClientTickEvents.END_CLIENT_TICK` calls `PathfindingController.getInstance().tick()`.

Mixin config: `src/main/resources/mc-warehouse.mixins.json` — 3 client mixins (`ContainerScreen`, `WorldRenderer` with Gizmos API, `MultiPlayerGameMode`).

## Reference docs

| File | What it covers |
|------|---------------|
| `ARCH.md` (763 lines) | Full architecture: data model, engine design, command tree, storage layout, milestone progress |
| `plan/summary.md` | Current state summary, known blockers, testable command list |
| `plan/refactor_container.md` | Container exploration & transfer redesign (already implemented) |
| `ref/doc/26.1-changes.md` | Minecraft 26.1 specifics (unobfuscated, FAPI renames, toolchain) |

## Known limitations

| Issue | Impact |
|-------|--------|
| `SimpleWalkExecutor` only checks distance (2 blocks), **never sends movement packets** | `/warehouse run` requires manual walking to each container |
| `MultiPlayerGameModeMixin.onUseItemOn` → `onBlockInteraction()` is an empty method | Container auto-opening not wired; waiting for SDK |
| Command rule-creator only parses `--id/--count/--negate` | No support for tag/name/nbt/fill/group/percent selectors via command |
| `ContainerMemoryManager` wrapper is unused | `ContainerController` holds `ContainerMemory` directly |
| `WarehouseEventBus` interface exists but has no trigger bindings | UI layer not yet connected |

## Architecture sketch

- **MVC**: Controller layer (`controller/`) shared by commands (`command/`) and future UI
- **Engine**: `engine/rule/` (matching, quantity calc, plan generation), `engine/container/` (GUI automation, scanner), `engine/pathfinder/` (tick-based state machine), `engine/highlight/` (Gizmos API rendering)
- **Persistence**: JSON via Gson in `<game-dir>/mc-warehouse/` (world config, warehouse data, pathfinder configs)
- **Run flow**: `/warehouse run` → `PathfindingController` (three-phase cycle: OUTPUT → TEMP → INPUT, repeats until idle) → `PathExecutor` → `ContainerInteractor` (tick-based, speed from `worlds.json`)

## Data model key types

- **ContainerType**: `INPUT` (搬出), `OUTPUT` (搬入), `TEMP` (暂存), `IGNORE`
- **RuleMode**: `WHITELIST` (default for OUTPUT), `BLACKLIST` (default for INPUT/TEMP)
- **ItemSelector implementations**: `IdSelector`, `TagSelector`, `NameSelector`, `NbtSelector`, `CompositeSelector`
- **QuantitySelector implementations**: `CountSelector`, `GroupSelector`, `FillSlotsSelector`, `PercentSelector`

## Conventions

- Indentation: tabs in `.gradle`/`.json`, standard Java (4 spaces) for `.java`
- `fabric.mod.json` placeholders expanded via `processResources`
- Environment: `client` only — never add server-side logic
- `AGENTS.md` and `README.md` should agree on project state
