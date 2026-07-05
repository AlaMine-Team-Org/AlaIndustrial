## 0.1.1

A small quality patch. It adds four new dusts with ore-doubling processing and reworks the
advancement tree into a proper start-to-finish progression.

### Gameplay

- **Four new dusts** — coal, diamond, emerald and lapis. Crush ore in the Ore Crusher to double it:
  - Coal / diamond / emerald ore → **2 dust** (deepslate variants included)
  - Lapis ore → **6 dust**
  - Any clean gem, or coal / charcoal → **1 dust**
- **The Compressor turns dust back into the item** — `coal_dust → coal`, `diamond_dust → diamond`,
  `emerald_dust → emerald`, `lapis_dust → lapis_lazuli`. This closes the loop: one ore block becomes
  twice the items, with no free duplication (item → dust → item breaks even).
- **Reworked advancement tree** (4 → 14 advancements). It now starts from mining your first ore and
  walks you step by step through smelting, cables, generators, machines and the solar branch.

### Quality of Life

- The mod's in-game description is now available in **13 languages**.
- Added a button that links to the mod's **issue tracker**.
- **Cleaner pixel-art icon** that stays crisp at every size.

### Changes

- **Copper cable losses now scale with distance and flow**: longer lines and bigger currents lose
  more, while short top-ups stay free so buffers still fill to their exact capacity.
- Updated the Electric Furnace front texture (off state).

### Bug Fixes

- Fixed the copper cable tooltip claiming "no energy loss" — it now shows the real per-block loss.
- Fixed solar panel and cable icons that could appear white or off-centre in some setups.
