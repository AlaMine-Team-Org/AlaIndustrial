---
type: Roadmap
title: Roadmap
description: "AlaIndustrial delivery map: current phase, release gates, process rules, and the phased plan up to the MV tier."
tags: [roadmap, planning, phases, release, alaindustrial]
timestamp: 2026-07-02T00:00:00Z
mod_id: alaindustrial
lifecycle_status: draft
---

# Roadmap — AlaIndustrial

AlaIndustrial is an EU tech mod for Minecraft 26.2 / Fabric. Each block is planned before it is
built: it first gets a concept, then test cases, code, automated tests, and a balance check.

This file is the **main delivery map**. The details of each phase are tracked separately so the
roadmap does not grow into a wall of text.

## Principle

We are not chasing features. Growth is layered: **one mechanic is deepened before a new one is
started.** Every tier and every mechanic is fully fleshed out before we move on. Recipes and
machines are built on vanilla materials (iron/copper/gold) at first. **The exception is ore
blocks:** custom ores (tin/silver/nickel/uranium) are added to world generation **early**
(Phase 0) so that worlds contain ore from the start and do not require wipes; their **consumers**
arrive later, in their own phases.

## Snapshot

What already exists as of 2026-07-02 (Foundation MVP code):

- a working LV core: EU buffers, face ports, network, NBT persistence, synchronization;
- a cached energy network `EnergyNetwork` + `NetworkManager`;
- generators: solar, daylight, moonlit, fuel generator, geothermal generator;
- solar evolution: daytime and lunar branches via evolution chips;
- cable: copper cable;
- storage: Battery Box;
- LV machines: macerator, electric furnace, compressor, extractor;
- data-driven recipes; components: electronic circuit, dusts, alignment chips, Network Analyzer;
- progression advancements, localization, icon, `/ala`;
- `build` and the GameTest suite pass.

## Current phase

The project is currently on **Phase 0 — Foundation (dev + test) + Materials**. The MVP code is
ready; the phase closes when **all current non-fluid blocks are covered** by test cases and
automated tests, and the QA process docs are out of draft. A parallel stream of the phase is
**custom ores in world generation** (tin/silver/nickel/uranium), pulled forward from the former
Phase 7 (worlds ship with ore from the start). We do not prepare a public release (Phase 1) until the
infrastructure is closed out.

## Process rules

These apply across all phases:

- **A phase is an umbrella over several releases. A release is one feature.**
- **A generator with a new mechanic → 1 per release.**
- **Consumers (EU machines) → up to 2 per release.**
- **Every 4th release is a tech sprint:** only bugfixing/refactoring of what exists, 0 new features.
- **Game version upgrades** (currently 26.3) begin about a month after a release.
- **Balance/economy numbers are computed against the numeric source of truth, not by eye.**
- **The idea backlog is NOT the source of phases.** Only work that has been broken down into phases makes it into the roadmap.
- Versioning: the MVP is public as `0.1.0`; each feature phase → MINOR; bugfix/tech sprint → PATCH;
  `1.0.0` is a late stable milestone, not at MV.

## Delivery phases

| Phase | Status | Release target | Summary |
|---|---:|---|---|
| 0 | 🔄 | — | Development/testing machine + coverage of current blocks **+ custom ores (tin/silver/nickel/uranium) in world generation**. |
| 1 | ⬜ | `0.1.0` | Public showcase repository, copy, images, listing platforms. |
| 2 | ⬜ | post-0.1.0 | Light, sound, block states, GUI hints, cable preview. 0 content, 0 mechanics. |
| 3 | ⬜ | MINOR | Water mill, wind turbine, EU loss — across separate releases. |
| 4 | ⬜ | MINOR | Pump + water source + tank: extract and store fluid. |
| 5 | ⬜ | MINOR | Boiler + hydro-thermal generator (water+heat→steam→EU) **+ geyser** — a natural source of the same steam. |
| 6 | ⬜ | MINOR | Oil as a fluid, finite sources, extraction by pump. |
| 7 | ⬜ | MINOR | Polymerizer, oil → latex → rubber. |
| 8 | ⬜ | Beta milestone | MV network/machines + generator evolutions (dam, large wind turbine). Gate: metals + rubber. |

> Materials (ores) are folded into [Phase 0](#current-phase), the geyser into Phase 5; phase
> numbering is contiguous 0–8 with no gaps.

## Release gates

### Public Release (`0.1.0`)

We can publish when:

- Phase 0 is closed: all current non-fluid blocks are covered by test cases and automated tests;
- `./gradlew build` is green;
- the survival smoke test passes;
- a dedicated public GitHub repository is ready as a showcase;
- the public surface contains no internal roadmap/task/research/dev-only material;
- the public surface contains no mentions of third-party projects/mods;
- README / CHANGELOG / version are synchronized for `0.1.0`;
- the public copy does not promise phases 2+ as finished features.

### Beta (MV)

We can move to beta once Phase 8 is closed: the game has a real LV → MV transition, an MV network,
MV storage, MV machines, and generator evolutions (dam, large wind turbine), with no known dupes.

### Long-term stable

`1.0.0` is planned once LV + fluids (water/steam) + ores + oil/rubber + MV are stable, the public
docs and packaging are closed out, and there are no critical/high issues around dupes, data loss,
or crashes.

## Beyond MV (backlog, not scheduled)

Kept in the idea backlog, not yet broken down into phases:

- machine upgrades (speed/efficiency/buffer);
- HV / EV infrastructure;
- nuclear power;
- high-tech / automation / UU matter;
- semifluid generator (liquid fuel); enrichment/RTG/uranium reactor (the uranium **ore** itself is already in worldgen from P0, but the consumer is backlog);
- solar T3/T4 (radiant/umbral/zenith/eclipse) — content on the existing evolution;
- LV consumers without a current sink: recycler (needs matter), canner (needs fuel systems);
- compatibility: REI machine categories, gold wire, energy-compat with external mods.

## Sources of truth

| Topic | Location |
|---|---|
| Build and run | [BUILD.md](BUILD.md) |
| Contribution guide | [CONTRIBUTING.md](../CONTRIBUTING.md) |
| Changelog | [CHANGELOG.md](../CHANGELOG.md) |

## Definition of Done

A phase or release is only considered closed if:

- the concept docs for the affected blocks are up to date;
- test cases are written before the code, the manual run has passed, and the automated test is implemented;
- the numbers are consistent between the balance source of truth, `Config.java`, and the recipe JSON;
- `./gradlew build` passes;
- runtime behavior is verified via gametest or `runClient` when the change is gameplay/visual;
- no assets, textures, strings, or code are copied from other mods;
- the public documentation does not promise unfinished features.
