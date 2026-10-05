# Changelog

## 0.1.197

<p><img alt="Ala Industrial Minecraft mod: before and now panels, two Battery Boxes level with each other while the Teleporter takes only surplus" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.197-mc26.2/release-media/v0.1.197-mc26.2/changelog.png" width="720"></p>

Energy network fixes: a spare cable spur no longer slows your machines, and Battery Boxes now share their charge fairly.

### Bug Fixes

- A spare spur of cable branching off the middle of a line no longer halves the speed of the machine at its end: the spur no longer takes energy that is on its way to the machine, and it charges once the machine is full.
- Two Battery Boxes now level out properly when a Teleporter, a Charging Station or an Energy Condenser shares their line: while the boxes even out, those blocks take only the generators' surplus instead of draining the fuller box almost to empty.
- The Sawmill's mode tooltip no longer appears over an open upgrade panel. While the panel is open the mode buttons do not switch the mode, and now they show neither a tooltip nor a hover highlight either, even when the panel is dragged right over them.
- The recipe viewer now shows the server's costs and times. On a dedicated server with changed machine settings, JEI quoted the Electric Furnace's vanilla smelting cost from the player's own config, and every machine card (in JEI and REI) timed its operation at the player's own rate. Both now follow the server's settings, and REI shows a vanilla smelt's time the same as JEI; in JEI the numbers update when the server reloads its config, without rejoining.
