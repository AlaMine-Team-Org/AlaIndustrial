# Changelog

## 0.1.199

<p><img alt="Ala Industrial Minecraft mod: vanilla map on item frames showing a base of machines, cables and chests beside a forest and a lake" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.199-mc26.2/release-media/v0.1.199-mc26.2/changelog.png" width="720"></p>

Your builds now show up on maps, the gardener drone weeds its field, the rectification section opens the column, and a batch of fixes for the capsule door, the incubator, abandoned labs and config reloads.

### New

- Rectification section: right-clicking it opens the column's screen and a wrench on it cleans the column; hovering over any storey outlines the whole column.
- The gardener drone now clears grass, ferns, dead bushes, firefly bushes and leaf litter in its area; each clearing costs one point of hoe durability and the usual action price, and the drops go to the output slots.

### Bug Fixes

- **The mod's builds now show up on maps.** Machines, cables, pipes, chests, ores and decorative
  blocks used to be skipped by a map, which drew the ground beneath them instead. Every block now has
  a map colour matching its material: machines read as metal, the reactor as dark grey, copper cable
  as orange, wooden blocks as wood, ores as their host rock. Glass stays see-through on maps, as in
  vanilla.
- Mutation chances and durations follow the server's settings. On a dedicated server with changed Incubator mode chances or durations, the recipe viewer (JEI and REI) and the mutation chip tooltip quoted the numbers from the player's own config. They now follow the server's settings and update when the server reloads its config, without rejoining.
- **Processing machines follow a config reload at once.** After `/ala config reload` with a new duration, an Electric Furnace, Macerator, Compressor, Extractor or Sawmill already standing in the world kept the old length until its chunk reloaded, while a freshly placed one used the new one. Now the next operation of every machine runs the new length, and one already running finishes against it without losing the progress it has made.
- **Abandoned labs no longer look into chunks that are not generated yet.** While checking that it sits
  in solid rock, a lab read the terrain one block further than world generation allows, where a chunk
  could still be bare, and creating a world filled the log with hundreds of "unsafe terrain read" lines.
  Labs are now placed so that every check stays inside the allowed area.
- The incubator and the kok-saghyz root no longer hide the faces of neighbouring blocks: the see-through gap next to a wall and at the edge of farmland is gone, and both still block light as before.
- The closed door of the teleporter capsule no longer hides water and the capsule's own walls behind its glass.
