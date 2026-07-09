## 0.1.9

The Wind Mill is no longer a "place it and forget it" block. It now needs a rotor to run, can evolve into one of two stronger variants, and shows its rotor spinning in the world. This update also fixes several placement and display issues with the Copper Cable.

### Gameplay

- Reworked the **Wind Mill**: it now requires a **Wooden Rotor** in its slot to generate any power — without one, it stays idle. Install an **alignment chip** (the same kind used by Solar Panels) and leave it under open sky long enough, and it evolves in place into one of two stronger variants:
  - **High Altitude Wind Mill** — its base output climbs twice as fast with height (capped at 16 EU/t). The reward for building a tall tower.
  - **Storm Wind Mill** — same base as the standard mill, but much stronger weather multipliers (rain ×2.0, thunder ×3.0), also capped at 16 EU/t. A gambler's "storm chaser".
  - Stored energy carries over when it evolves, and the output cable on the back keeps working.
- Removed a questionable Extractor recipe that turned cactus into dried kelp — it behaved like magic transmutation rather than extraction. The Extractor now has a clean, sensible recipe set.

### Quality of Life

- The Wind Mill's **rotor now spins visually** in front of the block. Its speed tracks the current output (from a slow idle to fast in a thunderstorm), and the rotor hides when the slot is empty.
- The **Wooden Rotor** item now has a tooltip describing what it does and the mill's peak output for the current height.
- Updated the mod description and homepage link shown in the in-game mod list.

### Changes

- Reworked the **Wind Mill** and **Wooden Rotor** recipes. The old wool-based wind mill recipe was too cheap and read oddly for a machine; the new one uses oak planks, copper and tin ingots, and a copper cable, bringing its cost closer to a Solar Panel. The rotor recipe now uses oak planks, iron ingots, and a copper cable.

### Bug Fixes

- **Wind Mills now emit power only from their back face.** Previously they output from five faces (all but the front), which was hard to read. All three mills (standard, High Altitude, Storm) now have a single output port on the back, opposite the rotor — consistent before and after evolution.
- Fixed the **Copper Cable drawing a connection sleeve toward the Iron Chest**, which falsely hinted that power could flow into it. The chest is plain storage with no energy connection.
- Fixed **not being able to place a Copper Cable directly against another cable** — the click was swallowed by the existing cable. Cables now place normally when you right-click an adjacent one.
- Fixed **`/ala version` showing raw `${version}` / `${githash}` / `${buildtime}` placeholders** instead of the real build info. It now prints the actual version, git hash, and build time.
