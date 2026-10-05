# Changelog

## 0.1.195

<p><img alt="Ala Industrial Minecraft mod: the Advanced Electromagnet with a Filter Module pulls diamonds and raw gold and leaves cobblestone and dirt" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.195-mc26.2/release-media/v0.1.195-mc26.2/changelog.png" width="720"></p>

Magnet filter module, tooltips for thirteen more blocks and a round of fixes.

### New

- Electromagnet screen (right-click the magnet): module slots (1 on the Electromagnet, 3 on the Advanced) and the new Magnet Filter Module — pull only chosen items or everything except them, matched by item, mod or category, with drag-and-drop from JEI and REI. New Blank Module, the base for every module; the Column Bore Module is now crafted from it. The magnet lets go of a drop it cannot bring in instead of wasting charge on it.
- **The Alloy Smelter and twelve more blocks now have item tooltips.** The Alloy Smelter, Reinforced
  Energy Storage, Canning Machine, Assembler, Distillation Column, Charging Station, Energy
  Condenser, Wind Turbine, Water Mill, Lightning Rod Generator, Mirror Concentrator, Sprinkler and
  Small Mob Repeller show their tier and
  the figures that apply to them — consumption, duration, buffer, output, tank size or range.
  Their tooltips used to be empty.

### Improved

- **The Mechanic skills Precise Draw and Resilient Cycle now work on the Assembler, the Incubator,
  the Distillation Column and the Thermal Centrifuge.** The skills promise "your machinery", but
  these four machines ignored them. With Precise Draw every tenth working tick of an operation is
  free, so an operation costs 10% less (the Assembler 432 EU instead of 480, the Thermal Centrifuge
  720 instead of 800). The column's warm-up and the centrifuge's spin-up cost what they did: they
  are preparation, not the operation. With Resilient Cycle an operation past its halfway point
  finishes on what is left in the machine's buffer when the supply can no longer pay a tick. Without
  the skills these machines use exactly as much energy as before.

### Bug Fixes

- JEI again shows the recipes of the Fluxweave armour, the electric tools (including diamond-tipped ones), the electric bow and saber, the Energy Pack and the Jetpack in every build of the mod — in some builds they were missing.
- A broken dyed insulated cable drops in its colour again, and the advanced fluid pipe no longer always survives an explosion — it drops like the other pipes.
- A pair of diamond chests is now one 216-slot chest to item pipes and other mods in every build of the mod — in some builds each half answered for its own 108 slots only.
- The Network Analyzer no longer shows item and fluid pipes as both a source and a consumer of
  energy (this happened in some builds of the mod), and a cable network with a pipe pressed against it goes back to sleep when it
  has nothing to do. The analyzer no longer draws the Workstation as a storage block or walks
  through it into a neighbouring network: the network still powers the station alongside the
  machines, as before.
- **The statistics panel now shows real numbers on the Pump, the Garden Drone Station, the Battery
  Box and the Reinforced Energy Storage.** A statistics chip (or the Free Telemetry skill) opened the
  panel on these blocks, but it read zero: the pump and the drone station counted no energy spent,
  no EU/t and no working time, and the two storage blocks counted nothing received or sent. All of
  it is counted now. The blocks themselves use exactly as much energy as before.
- **Machine tooltips no longer repeat themselves or come up empty.** Under Shift the Recycler no
  longer prints its consumption and duration lines twice — it shows its tier instead, like every
  other machine. With EU numbers hidden in the settings, the Sawmill, Polymerizer, Galvanic Bath,
  Fermenter, Vulcanizer, Thermal Centrifuge and Electric Heater now show their tier under Shift
  instead of an empty tooltip. The Teleporter shows its buffer without Shift, not only the
  "hold Shift" hint.
- **The Mob Repeller's energy bar now shows its charge on hover** — "X / max EU", like every other
  machine.
- **An open statistics panel now takes the clicks that land on it.** The Sawmill's mode buttons, the
  Assembler's tabs and buttons and the Mob Repeller's dome button no longer react to a click on a
  panel dragged over them, and are no longer drawn on top of it.
- **The solar panels' energy bar now shows its charge on hover** — "X / max EU" on the Solar,
  Daylight and Moonlit Solar Panels and the Mirror Concentrator, like every other machine.
- **Deleting the config file and reloading brings the defaults back again.** After removing
  `config/alaindustrial.json` and running `/ala config reload`, the server said it wrote the defaults
  but actually wrote the previous, edited values back and kept treating them as your edits. It now
  resets to the defaults and writes those. Comments in the config file are also written as plain text:
  `=`, apostrophes, `<`, `>` and `&` no longer turn into unicode escape sequences (an existing file is
  rewritten once on its own; no values change).
  **Behaviour change: deleting a config line brings its default back immediately.** A key that is not in
  the file now always means this mod version's built-in default — after `/ala config reload` too, not
  only after a restart. Previously a reload kept whatever value the earlier load had left in memory. A
  hand-trimmed file is still fine: the keys and sections it leaves out simply run on their defaults.
- **On a dedicated server players now see the server's numbers, not their own config file's.**
  Clients used to show the values from their own `config/alaindustrial.json`, usually the untouched
  defaults. The mastery level and bar on the dashboard and in the skill tree, the teleporter remote's
  free points, the recycler's batch, the incubator's attempts, the overclocker factors, the sprinkler's
  tank, the Mob Repeller's evolution thresholds and the numbers in tooltips and on REI/JEI pages could
  all disagree with the server. The server now sends these values when a player joins and after every
  `/ala config reload` and `/reload`. The player's file is left untouched. The durability of rotors,
  wheels, lightning rod tips and recycler blades still comes from the player's file and changes only
  after a restart.
- A bare reactor pile with its accident countdown running no longer calls the countdown off by itself
  when its chunk reloads or the server restarts: its instability and the scram time already spent are
  now saved with the world.
