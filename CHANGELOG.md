# Changelog

## 0.1.181

<p><img alt="Ala Industrial 0.1.181 for Minecraft 26.2 - the monitoring wall panel as a slim lit terminal" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.181-mc26.2/release-media/v0.1.181-mc26.2/changelog.png" width="720"></p>

6 updates in this release. The monitoring wall is open for everyone — its panel is now a proper
slim terminal — with a new alloy item and a recipe-unlock fix.

### New

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

- **The monitoring panel is a slim wall terminal.** A designer-made bezel with a recessed screen
  replaces the placeholder cube: dark with no filter item, lit while watching one, with rear
  contacts in one warm colour instead of mixed shades. The item icon and count sit right on the
  screen plane, like a real dashboard.
- **Capacity cards are the price of monitoring.** The free allowance of four watched types is
  gone: an empty rack tracks nothing, and every filled panel shows the yellow cross. A working
  wall and a cardless one no longer look identical.

### Fixed

- **Palladium plate recipe unlock.** The advancement's item predicate used a name the game
  never read, so the recipe never registered as learned.
