## 0.1.31

<p><img alt="Ala Industrial 0.1.31 update preview: a player winding up a teleport jump" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.31/release-media/v0.1.31/changelog.webp" width="720"></p>

The teleporter is here: park a station at your base, carry the remote, and come home from the mine in five seconds. Machines also gained an upgrade panel with a chip that silences their hum, and the scythe learned to harvest crops without wrecking the field.

### New

- **Teleporter** — an endgame pair. The **station** is a high-voltage block you place at your base; it quietly banks up to 500,000 EU. The **remote** brings you back to it from anywhere in the same dimension. The target station pays for the jump, not the remote, so a trip is exactly as reliable as your base is charged — a full station is good for roughly 25–50 trips home before it needs a top-up.
  - **Two clicks to get home.** Shift-right-click a station to add it to the remote; right-click the remote to open the list with the first station already selected, then hit Teleport. The remote works from either hand and does not need clear sky to point at — clicking stone, dirt or any block without its own action opens the list too, because a mine has no sky and that is exactly where you need it. Chests, crafting tables and machines still open as normal. One remote remembers up to **16** stations, each renamable; unnamed ones are called Teleporter 1, 2, 3… and reuse numbers as you delete them.
  - **The jump is a scene, not a wait.** Five seconds of wind-up: a purple vortex tightens around you — visible to bystanders and to yourself in third person — a hum rises, and in the last second the screen fades almost to black before snapping clear somewhere else. Stepping away, taking damage or dying cancels it, and **a cancelled jump costs nothing**: energy is only spent once you actually arrive.
  - **A stolen remote is useless.** It binds to whoever first uses it, so a thief cannot follow it home. Stations are **private** by default; you can open one to everyone with the switch on its screen, but only as the owner and only after clicking the **padlock** beside it, so a stray click cannot flip your base open. The same padlock guards the remote's Delete button — removing a point erases the only record of where home was.
  - **Refusals are visible.** Station flat, still on cooldown, station gone — the reason appears as a red band inside the remote's own screen instead of behind it, and clears itself.
  - **Crafting.** The station is a real endgame recipe: 3 uranium ingots, an eye of ender, a block of diamond, 2 copper coils and 2 electronic circuits. The remote is cheaper — an ender pearl, an electronic circuit and 3 tempered iron — but a station is useless without one, and a remote leads nowhere without a station.

- **Upgrade panel on every machine, and a chip that shuts them up** — machines now have a slide-out panel with **4 upgrade slots**, tucked away until you open it. The first chip for it is the **mute chip**: a machine holding one stops humming, which starts to matter once a row of macerators lives next to your base. Craft an empty chip from glass, copper coils and an electronic circuit, then wrap it in 8 wool. The slots only accept chips, so there is no shoving ore in there by accident. The macerator, electric furnace, compressor, extractor, generators, pump, solar panels, wind and water mills and the battery box all have it.

- **Metal gears** — stone, iron, gold and silver join the wooden gear. Each is a cross of its own material around a wooden gear at the centre. Nothing consumes them yet: they are groundwork for the machines of the next versions.

### Quality of Life

- **Scythe: shift to harvest, right-click to clear** — hold **Shift + right-click** and the scythe becomes a sickle: one sweep collects **only fully grown** crops — wheat, carrots, potatoes, beetroot and the rest, ripe sweet berries, pitcher crops, plus cactus and sugar cane (only the part above the base, so the root keeps growing). Unripe seedlings, decorative plants, melon and pumpkin stems and farmland are left alone. And plain right-click **no longer breaks crops at all**, so clearing grass around a field is finally safe.

- **The electric furnace shows everything it can smelt** — its category in the recipe viewer used to list only this mod's 17 recipes, which made it look like the machine could not smelt anything else. It now lists all of them, ordinary smelting included — 106 recipes — each priced the way the machine actually charges you, so the label cannot disagree with the meter.

### Changes

- **Scythe: 1 durability per block cut** — durability used to follow the normal tool rules, so zero-hardness plants (grass, ferns, flowers) cost nothing to cut while leaves cost 1 apiece. Now every block the scythe actually cuts costs exactly 1, so mowing grass is no longer free forever. Creative still costs nothing.

### Bug Fixes

- **Electric gear no longer drains in creative** — a worn energy pack honestly ran itself flat in creative, the battery pouch burned its idle charge, and the drill paid for every torch it placed. None of that matched the way tools behave in creative. Now nothing in the electric family spends charge for a creative or spectator player, while a worn pack still charges everything else.

- **The energy pack now charges the item on your cursor and in the 2×2 crafting grid** — picking a pouch up with the mouse or dropping it into the crafting grid quietly stopped it charging, which read as "charging sometimes just stops". An open chest or machine is still left alone: that belongs to the world, not to you.

- **Your gear talks to other mods' power** — the pouch, pack and drill now understand other mods' energy systems at a 1:1 rate. Their charging stations fill our items just as our own battery box does, capped at each item's own rate so nothing charges faster than intended, and they cannot siphon energy back out. A worn pack likewise tops up other mods' powered tools and batteries alongside our own.

- **The drill's torches make a sound** — placing a torch with the drill was silent for the player placing it, which made a working feature feel broken. The torch now goes down with the same sound as placing one by hand, taken from the block that actually landed, so a regular torch and an enriched uranium torch each sound like themselves.

- **Silver and Gold Chests appear in the creative tab** — both were craftable, but missing from the tab, which made a shipped feature look like it was never there.
