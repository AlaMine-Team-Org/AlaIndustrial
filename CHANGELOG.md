# Changelog

## 0.1.203

<p><img alt="Ala Industrial Minecraft mod: a block breaker mining cobblestone from a lava and water generator on its own" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.203-mc26.2/release-media/v0.1.203-mc26.2/changelog.webp" width="720"></p>

A block breaker that mines on its own, safer menus, radiation from boats and minecarts, and a lighter fluid network.

### New

- **Block Breaker — a first-tier machine that breaks the block in front of it.** Place it facing any of the six sides; it draws 8 EU/t only while breaking and uses the tool you give it, up to stone tier, exactly as a player would: a pickaxe is quick on stone and ores, a shovel on dirt, an axe on wood, and a block above the tool's tier breaks with no drop. Enchantments and tool wear work as for a player, drops fall into the world, and a chest in front spills its contents. It breaks in its owner's name, so land claims are respected. Its screen has a tool slot, a redstone mode and a status line; the blocks-broken counter shows with a Statistics Chip.

### Improved

- Fluid pipes are much lighter on the server. A running line no longer redraws every segment twice per tick, and a line with nothing to move (tanks full or empty at both ends) now sleeps, waking on its own as soon as there is work again. Flow, speed and behaviour are unchanged.
- **Uranium in vehicles now radiates.** Chest boats and rafts (vanilla and the mod's), chest and hopper minecarts, and donkeys, mules and llamas carrying a chest irradiate exactly like a chest block with the same contents — same leak cap, same radius. Before, radiation did not see them at all, so any vehicle was a free shielding chest. Only the boat with the Shielding Chest keeps its contents shielded. **Heads-up for existing worlds:** uranium already stored in a vehicle will start irradiating nearby players, and turning nearby villagers and cows, after the update. Unopened minecarts in abandoned mineshafts are not looked into, so their loot is left alone.

### Bug Fixes

- The Statistics Chip no longer shows "now 0 EU/t" on a machine that is working. The average over the last 2 seconds now keeps one decimal below 10 EU/t (a Garden Drone Station reads "0.2 EU/t"), a very small rate reads "<0.1", and "0" means nothing moved at all.
- With the Resilient Cycle skill, the Vulcanizer now pays its Electric Heater for every tick it finishes on stored charge. The second half of such an operation used to be heated for free; now, as with the Thermal Centrifuge, no heat means no progress.
- A Battery Box backing up a machine no longer drains its reserve into a Teleporter on the same line. While a box covered a machine the generators fell short of, a Teleporter (or a Charging Station or an Energy Condenser) on the line took a share of that energy, and one closer to the source than the machine could also cut the machine off and empty the box into itself. Now, while a box backs the machines up, those blocks take only what the generators make beyond the machines' needs, and the box's reserve goes to the machines.
- Cable, tank, pipe, magnet, pouch and briquette tooltips follow the server's settings. On a dedicated server with changed settings, a cable's buffer and loss, a fluid tank's capacity (and its bar), the advanced fluid pipe's and the item pipe's throughput, the electromagnet's range and running cost, the number of cells in a pouch's tooltip picture, the quench press's ceramic yield and the voltage presets of the creative energy source came from the player's own config. They now follow the server's.
- Shift-clicking no longer tops up a machine's output slots: wheat from your inventory used to join the Garden Drone Station's harvest, and dust the Macerator's result. Items now go only into slots that accept them and otherwise stay in your inventory. Shift-clicking results out works as before.
