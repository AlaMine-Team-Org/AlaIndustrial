# Changelog

## 0.1.188

<p><img alt="Ala Industrial Minecraft mod: the new item pipe runs between two chests, a teal nozzle on one end and an orange flange on the other" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.188-mc26.2/release-media/v0.1.188-mc26.2/changelog.png" width="720"></p>

6 updates in this release. The item pipe gets a new look, and pipes and cables now give way to
flowing water.

### New

- **The Charging Station tells you when you're done.** When a charge it started has finished, the
  station plays one soft two-note chime, so you can walk off without watching the indicator.
  Stepping on already fully charged plays nothing; the chime comes again after you step off or
  start a new charge.

### Improved

- **Item Pipe - new model.** A dark ribbed body with collars; an extracting side now ends in an
  orange flange and an inserting side in a tapering nozzle with a teal tip. Straight runs join into
  one continuous line with a collar in the middle of each block. The pipe's size, connections and
  behaviour are unchanged.
- **Flowing fluids now wash away pipes and cables - the same on both Minecraft versions.** Every
  cable, item pipe, fluid pipe, steam pipe and the monitoring wire is washed away by flowing water
  and drops as an item, while lava destroys it - just like a vanilla torch. Before, they were never
  washed away on Minecraft 26.3, and only sometimes on 26.2, depending on the pipe's shape. Keep
  your lines out of the way of spreading water, for example next to a pump's pool.
- **Cables now meet the assembled teleporter low.** The cable drops down to the capsule's base and
  reaches in to touch it, the way it meets a solar panel, instead of passing above it.

### Fixed

- **Fluid and steam pipes no longer look broken up.** A sheet of glass showed across the inside of
  the pipe where each block's centre met a connection, so a filled line read as a chain of separate
  pieces. Straight runs are now seamless, filled or empty - for the regular and reinforced fluid and
  steam pipes.
- **The Creative Energy Source no longer shows a statistics tab.** It cannot take a statistics
  chip, so the tab did nothing.
