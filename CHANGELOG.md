# Changelog

All notable changes to the **AlaIndustrial** mod are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

Before the first public release, the registry IDs of blocks and items may still
change (such a rename breaks existing worlds — see [docs/BUILD.md](docs/BUILD.md)).
The first public release is planned as `0.1.0`; after it, ID stability is
maintained per SemVer.

The version here always matches `mod_version` in
[`gradle.properties`](gradle.properties).

## [Unreleased]

### Changed

- **Copper cable losses** now follow a proportional distance-based model:
  `floor(flow × copperCableLossPerBlock × distance)` EU/tick per consumer, where distance
  is the number of cables to the nearest source (BFS over the network). The longer the line
  and the larger the flow, the greater the loss; a short hop and a top-up packet floor to 0,
  so the buffer always fills to its exact capacity. The `cableLossPerBlock` field
  (deprecated/no-op) was renamed to `copperCableLossPerBlock` = 0.0125 (PROPOSED, tuned via
  playtesting).
- The roadmap was reworked around the first public `0.1.0` release, a public-repository
  showcase flow, and new phases: LV quality patch, compatibility/utility, oil/fluid transport,
  oil-to-rubber processing, and then MV and higher tiers.
- The future rubber flow is now fixed: liquid oil → Polymerizer → raw rubber → rubber.

## [0.1.0] — 2026-07-02

Internal dev snapshot. **Fabric**, **Minecraft 26.2**, Java 25. Implements an early
LV core: generators, copper cable, Energy Storage, basic machines, and diagnostic tools.
The further delivery order is in [docs/ROADMAP.md](docs/ROADMAP.md).
Requires **Fabric API** and **Team Reborn Energy**.

### Added

**Energy system (EU, LV tier)**
- Energy network core: a cached union-find graph (`EnergyNetwork` + `NetworkManager`) —
  merge on placement, BFS rebuild on break, proportional distribution,
  sleeping for idle networks, and a `networksPerTick` budget.
- Cable transmission currently acts as a throughput limit without losses:
  `cableLossPerBlock` is deprecated/no-op.
- No-overvoltage model: `maxVoltage` is only a per-tick throughput limit; blocks are not
  destroyed by exceeding it.

**Generators**
- Solar Panel: base T1 plus a daytime and a "lunar" night-time T2; the base panel evolves
  into a branch via an alignment chip and accumulated run time (carried over in NBT). Accounts
  for snow/rain and partial sky occlusion.
- Fuel Generator (`generator`) with a fuel filter.
- Geothermal Generator: a 10-bucket lava tank, filled by buckets or by fluid via a pump
  → 16 EU/t, with a GUI showing the lava level.
- Pump (`pump`): a lava-only implementation exists in the codebase but is hidden from the
  public MVP.

**Energy transfer**
- Copper cable as the public MVP cable. Tin and insulated cables remain hidden stubs
  for future phases.

**Energy storage**
- Energy Storage (`battery_box`) — an EU accumulator with a charge GUI and directional
  input/output.

**Machines (`MachineBlockEntity` core)**
- Ore Crusher, Electric Furnace, Compressor, Extractor — on the shared core (buffer,
  inventory, persistence, GUI sync); recipes are data-driven.

**Components and items**
- Electronic Circuit (item + crafting recipe).
- Network Analyzer (`network_analyzer`) — right-clicking a cable shows network statistics and
  highlights the network in the world.

**Progression**
- Advancement tree: "First Watt", "Closed Circuit", "Industrialization".
- All of the mod's crafting and smelting recipes are unlocked in the vanilla recipe book.
- Basic item browsing and standard crafting recipes via the recipe viewer were verified
  by hand in-game; dedicated machine recipe categories remain a task for the next phase.

**Commands**
- `/ala version` — mod version, git hash, build time.
- `/ala status` — a summary of registered blocks/items/recipes and `Config` keys.
- `/ala net` — energy network telemetry per dimension.

<!-- Reference links to the diff/tag (`[0.1.0]: <repo>/releases/tag/v0.1.0`) will be added
     once the public repository URL is fixed. Per Keep a Changelog they are optional. -->
