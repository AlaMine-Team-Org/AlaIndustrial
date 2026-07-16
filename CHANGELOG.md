## 0.1.30

<p><img alt="Ala Industrial 0.1.30 — the storage progression: copper, iron, silver and the new gold chest" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.30/release-media/v0.1.30/changelog.png" width="720"></p>

A new top-tier chest finishes the storage progression, the mining drill can place torches without leaving the hotbar, and the macerator now doubles raw ore just like it already did with ore blocks.

### New

- **Gold Chest** — 54 slots, the new top of the storage line. Full 3D chest with animated lid and its own golden texture. Crafts from a silver chest surrounded by 8 gold ingots — same "previous tier + 8 ingots" pattern as the rest. No power needed, any pickaxe breaks it. Storage now runs copper (27) → iron (36) → silver (45) → gold (54).

### Quality of Life

- **Drill places torches** — right-click a block with the mining drill equipped and it places a torch straight from your inventory, so the drill never has to leave your hotbar to light a tunnel. If you carry both, the enriched uranium torch (the one that stays lit underwater) is placed first, then the regular torch. Costs a small amount of energy per torch; if the drill is nearly flat the torch still goes down.

### Changes

- **Macerator doubles raw ore** — crushing a raw metal ore (the form you get from mining without Silk Touch) now yields 2 dust instead of 1, matching the way mined ore blocks already behaved. The ingot path stays at 1 dust, so there's no free loop. Smelting raw ore still gives 1 ingot, so running it through the macerator first is now the clear way to double your metal no matter how you mined it.
- **Copper cable loses more over distance** — long copper lines now lose roughly 2% of their flow per block (up from 1.25%). Short hookups are barely affected, but routing power across your whole base costs more, giving you a reason to put a battery box closer to the load. The cable tooltip already reflects the new value.
