# Changelog

## 0.1.210

<p><img alt="Ala Industrial Minecraft mod: teleporter capsule and mirror concentrator outlined as whole structures, plus four fixes" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.210-mc26.2/release-media/v0.1.210-mc26.2/changelog.png" width="720"></p>

Assembled teleporters and Mirror Concentrators now get one accurate outline, plus fixes for servers, machine menus and the reactor.

### Improved

- **Shift-click in machine menus.** An item the machine has no slot for now moves between the inventory and the hotbar, like in the vanilla furnace.
- **Accurate outlines for the teleporter and the Mirror Concentrator.** Selection now follows the model instead of a square box, and aiming at any part of an assembled capsule or concentrator outlines the whole structure.

### Bug Fixes

- **Reactor:** breaching the shell after rejoining the world now silences the racks at once instead of leaving them humming.
- **JEI on dedicated servers:** in some builds of the mod, machine recipes did not reach players on dedicated servers; the mod's categories are no longer empty.
- **Electric tools on servers with changed settings:** mining speed, the drill's torch and the hoe's tilling now use the server's prices, so blocks no longer pop back.
