# Contributing to Ala Industrial

Bug reports, focused fixes, and feature proposals are welcome. Include Minecraft, loader and mod
versions, reproduction steps and relevant log output in bug reports. Remove credentials from logs.

Target `main` for Minecraft 26.3 or `mc/26.2` for 26.2. State whether a change applies to both versions.
Keep shared gameplay in `common/`; loader-specific integration belongs in `fabric/` or `neoforge/`.
Use original code and assets and translate player-visible text through language keys.

Use Java 25 and run `./gradlew build :neoforge:runGameTests` before submitting a change.
For gameplay changes, also verify both development clients with `:fabric:runClient` and
`:neoforge:runClient`. See [build instructions](docs/BUILD.md).

Keep commits focused and describe the behavior being fixed or added. Pull requests should explain
the problem, the resulting behavior, and the checks performed. Cross-version ports should preserve
source commit attribution through `git cherry-pick -x`.
