# BUILD — building, running and verifying the mod

The `alaindustrial` mod code lives in this repository (root + `src/`) and is built with
Fabric Loom. This document is the single source of truth for the toolchain and commands.

> Toolchain source: the values below were verified against live sources (fabricmc.net,
> meta.fabricmc.net, maven.fabricmc.net, Modrinth) on 2026-06-28.

## Toolchain (26.2)

| Component | Version | Where verified |
|-----------|---------|----------------|
| Minecraft | `26.2` ("Chaos Cubed", released 2026-06-16) | fabricmc.net; meta.fabricmc.net `/v2/versions/game` |
| **JDK** | **Java 25** (`openjdk@25`, 25.0.3) | required since 26.1; `build.gradle` sets `release = 25` |
| Fabric Loom | `1.17.12` | latest patch of the 1.17 line on maven.fabricmc.net |
| Gradle | `9.5.1` | wrapper `distributionUrl = gradle-9.5.1-bin.zip` |
| Fabric Loader | `0.19.3` | meta.fabricmc.net `/v2/versions/loader/26.2` |
| Fabric API | `0.153.0+26.2` | Modrinth `game_versions=["26.2"]` |
| Team Reborn Energy | `5.0.0` | maven.fabricmc.net `teamreborn/energy`; mod id `team_reborn_energy` |

Versions are pinned in [`gradle.properties`](../gradle.properties).

### Mappings — Mojang official (mojmap), NOT Yarn

Yarn does **not** exist for the `26.x` version scheme (meta.fabricmc.net publishes Yarn only up
to `1.21.11`; there are no `26.x` artifacts on maven). Loom 1.17 uses Mojang's official
mappings by default — that is why there is no `mappings` line in `build.gradle`, and all
Minecraft symbols in the code are written using Mojang names
(`net.minecraft.world.level.block.Block`, `net.minecraft.resources.Identifier`,
`BlockBehaviour.Properties`, `BuiltInRegistries`, …). Any MC symbol is verified against the
unpacked namespaced jar (`~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar`) via `javap`.

Mod dependencies are added through plain `implementation` (not `modImplementation`/`include`,
which do not exist in Loom 1.17), as in the canonical `fabric-example-mod`.

## Prerequisites

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25   # path to JDK 25 (macOS/Homebrew)
export PATH="$JAVA_HOME/bin:$PATH"
java -version                                    # must be 25.x
```

## Build

```bash
./gradlew build          # compile + resources + build-time gametest
```

The artifact is `build/libs/alaindustrial-<version>.jar`.

## Run

```bash
./gradlew runClient      # MC 26.2 client with the mod (visual check in-world)
./gradlew runServer      # dedicated server with the mod
```

A fresh `run/` directory requires accepting the EULA:

```bash
mkdir -p run && printf 'eula=true\n' > run/eula.txt
```

`runClient` is a **separate throwaway dev environment** (game dir `run/`, worlds in
`run/saves/`). It is handy for quick visual checks and screenshots, but its world and inventory
must not be confused with a survival test (these are different save pools).

## Survival testing via the official launcher (Windows)

To **play in survival and keep progress across rebuilds**, run the mod through the official
Minecraft Launcher rather than `runClient`. This is a separate, stable environment that Gradle
does not touch.

| What | Where |
|------|-------|
| Launcher | official Minecraft Launcher, **"Fabric Loader"** profile (`fabric-loader-0.19.3-26.2`) |
| Profile game dir | `C:\Users\User` |
| Mods folder | `C:\Users\User\mods\` — exactly 3 files: `alaindustrial-*.jar` + `fabric-api-*.jar` + `energy-*.jar` |
| Worlds (persistent) | `C:\Users\User\saves\` |

Why progress is not lost: Gradle only writes to `build/` and `run/`. Replacing
`alaindustrial-*.jar` in `mods\` does not touch the world in `saves\`.

**Update cycle for the mod in your survival world (PowerShell):**

```powershell
# 1. Build (from the project root; JAVA_HOME = JDK 25)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build

# 2. Replace ONLY our jar in mods (leave fabric-api and energy alone)
Get-ChildItem "C:\Users\User\mods" | Where-Object { $_.Name -like "alaindustrial*" } | Remove-Item -Force
$jar = Get-ChildItem "build\libs" | Where-Object { $_.Name -like "alaindustrial-*.jar" -and $_.Name -notlike "*sources*" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
Copy-Item $jar.FullName "C:\Users\User\mods\" -Force

# 3. Open the launcher -> "Fabric Loader" profile -> Play -> your world.
```

> ⚠️ **The only thing that wipes progress even with the correct workflow is renaming or
> removing registry IDs of blocks/items** (e.g. `batbox → battery_box`). On world load,
> Minecraft discards unknown IDs: placed blocks and items in inventories/chests disappear. If
> such a rename is unavoidable, warn that the specific survival world will not survive it (or
> add a registry alias / data fixer).

## Behaviour verification (without manual clicking)

### Unit tests (L1) and server game tests (L2)

```bash
./gradlew test         # JUnit: pure logic (tiers, balance invariants)
./gradlew runGameTest  # Fabric GameTest: block behaviour in a live ServerLevel, exit != 0 on failure
```

`runGameTest` is the primary behavioural check. Test cases run against a force-loaded region so
block entities can be driven directly.

### Client game test (GUI screenshots)

`fabric-client-gametest-api-v1` creates a `gametest` source set and a `runClientGameTest` task:
it renders the screens of every machine in a real client and captures screenshots — a check of
GUI layout and that resources (blockstate/model/texture) load without errors.

```bash
./gradlew runClientGameTest
# screenshots: build/run/clientGameTest/screenshots/
```

## Gotchas

- `build/` in this repo is used by both Gradle and the texture-generation tools
  (artifacts under `build/resourcepacks`, `build/texture-preview-16x.png`). `./gradlew clean`
  wipes the texture artifacts — both sets are gitignored and regenerated.
- Block entities tick only in a *loaded* chunk — game tests drive the BlockEntity directly
  (`be.serverTick(...)`) in the test's force-loaded region, without waiting for natural ticks.
- 26.2 rewrote GUI rendering: screens override `extractBackground(GuiGraphicsExtractor,…)`
  (there is no `renderBg`). Final machine GUIs must blit the texture atlas via
  `blit(RenderPipelines.GUI_TEXTURED,…)`: the static `176×166` background from `u=0,v=0`, then
  runtime overlay sprites from the service zone of the atlas. Keep procedural `fill()` only for
  temporary/debug screens.
- The 5-argument `text()` draws a shadow (which looks like a duplicate on a light panel) — use
  the 6-argument form with `shadow=false`.
