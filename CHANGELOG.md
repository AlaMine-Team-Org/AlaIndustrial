## 0.1.2

A quality patch focused on convenience and correctness: a placement preview for cables, fully
translated command output, and two fixes for ore processing and recipe visibility.

### Quality of Life

- **Cable placement preview** — while you hold a cable and aim at a block, a translucent ghost shows
  exactly where it will go, already wired up with the right connections to nearby cables and machines.
- The **`/ala` commands** (`version`, `status`, `net`) now print in your game language, across 13
  languages.

### Bug Fixes

- **Raw ore is no longer doubled in the Ore Crusher.** Raw iron, copper, gold, tin, silver, nickel
  and uranium now yield a single dust, the same as an ingot — doubling stays where it belongs, on the
  mined ore block. This closes the loophole of doubling a single vein twice (ore → raw → ×2).
- **Machine recipes now appear in the recipe viewer.** The Ore Crusher, Electric Furnace, Compressor
  and Extractor show their recipes (input → output, energy, time) in both directions.
