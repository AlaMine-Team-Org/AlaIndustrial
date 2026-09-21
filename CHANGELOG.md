# Changelog

## 0.1.181

<p><img alt="Ala Industrial 0.1.181 - the move from Minecraft 26.2 to 26.3" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.181-mc26.3/release-media/v0.1.181-mc26.3/changelog.png" width="720"></p>

6 updates in this release. The mod now runs on Minecraft 26.3, and the monitoring wall is open
for everyone.

### New

- **Minecraft 26.3 support.** Rendering, controls, screens and world data all work on the new
  game version. Worlds from 26.2 keep working on 26.3 — old-format lava capsules are healed
  automatically on load — but a world saved on 26.3 cannot be opened on 26.2 again, so upgrade
  on a copy of your save.
- **Monitoring wall.** The panel and the core now have recipes and a place in the creative tab.
  The core is built from four reinforced amethysts, two advanced circuits, two electrum plates
  and an energy crystal.
- **Reinforced amethyst.** A new item: four amethyst shards plus silver dust in the alloy
  smelter, 1200 EU per operation.
- **Monitor core screen.** The core got an interface: ten sockets for capacity cards (drag them
  in and out), four numbers — type allowance against types actually watched, seated cards,
  stored energy and upkeep with its panel count — and a status lamp that says whether the wall
  is running, waiting for a card or out of power. A full rack says so instead of swallowing
  the click.

### Changes

- **Capacity cards are the price of monitoring.** The free allowance of four watched types is
  gone: an empty rack tracks nothing, and every filled panel shows the yellow cross. A working
  wall and a cardless one no longer look identical.

### Fixed

- **Palladium plate recipe unlock.** The advancement's item predicate used a name the game
  never read, so the recipe never registered as learned.
