## 0.1.41

Compatibility hotfix: the mod no longer conflicts with other installed mods at startup, plus a batch of water mill and jetpack fixes.

### Fixed

- **Better compatibility with other mods.** The village tweaks added in the last update are now fully optional under the hood — if another installed mod changes the same vanilla worldgen code, the game keeps loading normally instead of crashing at startup.
- **Water mill wheel spins again.** The outer wheel now rotates while the mill is generating energy, and spins faster the more water touches the mill.
- **Jetpack glow no longer creates water.** Flying up a waterfall with the jetpack used to leave columns of still water behind.
- **Jetpack glow no longer leaves a stray light** in the world if the server stops mid-flight.

### Changed

- **Water mills need breathing room.** Two mills placed too close now hide their wheels, pause generation and show a "wheel interference" status — leave a 2-block gap side-by-side, back-to-back is fine.
- **Water mill crafting got pricier.** The mill and its wheel are now built with copper, cables and a gear, matching the wind mill tier. Energy output is unchanged.
