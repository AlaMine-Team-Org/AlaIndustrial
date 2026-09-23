# Changelog

## 0.1.183

<p><img alt="Ala Industrial 0.1.183 - the Mob Repeller's new sound and its upgrade panel" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.183-mc26.3/release-media/v0.1.183-mc26.3/changelog.png" width="720"></p>

3 updates in this release. The Mob Repeller finds its voice and gets an upgrade panel.

### New

- **The Mob Repeller pings.** A soft sonar-like ping plays while the field is on, on all three
  tiers. It goes quiet when the field does: no power, a redstone signal, or a Mute Chip.
- **An upgrade panel for the Mob Repeller.** A Mute Chip silences it and a Statistics Chip
  tracks the field's upkeep. The overclocker slot is locked - a field cannot sweep "faster".
  Installed chips move along when the block grows a tier.

### Fixed

- **Worlds saved on Minecraft 26.2 no longer lose incubator and kok-sagyz data.** The game
  changed how block states are saved, and older worlds forgot the incubator dome glass (its tint
  and its drop) and the root's sandy soil (its growth bonus). Both now load and are rewritten in
  the new format on the first save.
