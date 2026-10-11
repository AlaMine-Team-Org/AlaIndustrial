# Changelog

## 0.1.206

<p><img alt="Ala Industrial Minecraft mod: cable feeding four machines past a battery box, with sawdust, uranium radiation, powered tools, fluid pipes and the alloy smelter" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.206-mc26.3/release-media/v0.1.206-mc26.3/changelog.png" width="720"></p>

Machines now get energy first, the Sawmill makes sawdust, and uranium radiates from more places.

### New

- **Sawdust.** The Sawmill has a fifth mode: a log gives 6 sawdust, a bamboo block 3. Sawdust feeds the Fermenter, counts as combustible in the Recycler and burns like bamboo.

### Improved

- Uranium in item frames, armour stands, mobs' hands, armour or inventories, and a player's off-hand or armour slots now radiates. In old worlds, mobs that already picked up uranium will start irradiating.
- **Energy sharing.** Machines are fed first, the nearest first when power is short. What they cannot take goes straight to the nearest Battery Box, and boxes feed machines when generators fall short.

### Bug Fixes

- In some builds of the mod, fluids now report their viscosity, temperature and name to other mods correctly: every fluid but crude oil used to show default numbers, and steam an untranslated name.
- Fluid pipes now behave the same wherever they are built: when two pipes equally far from a tank share one neighbour, they take turns in the same order anywhere in the world, including builds that cross height 0.
- Powered item charge follows the server's settings. On a dedicated server with changed buffers, the "X / Y EU" line, the charge bar and the energy HUD now use the server's capacity.
- The jetpack's height ceiling follows the server's settings. On a dedicated server with a changed ceiling, the player no longer rises above it for free or spends charge on thrust that never happened.
- **Alloy Smelter.** Clicking anywhere on its progress arrow now opens its recipes in the recipe viewer.
