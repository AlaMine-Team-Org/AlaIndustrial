# Building Ala Industrial

Java 25 is required. Use `gradlew` on Linux/macOS or `gradlew.bat` on Windows.
The selected branch pins Minecraft and loader dependencies in `gradle.properties`, build plugins
in `settings.gradle`, and Gradle itself in `gradle/wrapper/gradle-wrapper.properties`.

`main` targets Minecraft 26.3; `mc/26.2` targets 26.2. Both support Fabric and NeoForge.
Changing only `minecraft_version` is insufficient to port between their APIs and data formats.

## Modules

- `common/`: shared gameplay, resources, unit tests and game-test scenarios.
- `fabric/`: Fabric registration, compatibility and test entry points.
- `neoforge/`: NeoForge registration, compatibility and test entry points.

## Verification

```bash
./gradlew build :neoforge:runGameTests
```

The root build compiles all modules, runs common and NeoForge unit tests, and Fabric server game tests.
The explicit NeoForge task runs its world game tests. Do not skip either loader when changing gameplay.
The public export is self-contained; development-repository document validators are not part of this export.

```bash
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
./gradlew :fabric:runClientGameTest
```

Use a separate working directory for each branch. Back up saves before upgrading Minecraft and
use separate saves for the two game versions. Do not open a world saved by 26.3 in 26.2.
Production JARs are in `fabric/build/libs/` and `neoforge/build/libs/`:
`alaindustrial-<loader>-<minecraft>-<version>.jar`.

For Minecraft 26.3, the default development recipe viewer is JEI. The pinned REI API is used only
for compilation until a compatible runtime release is available.
