# Changelog

## 0.1.192

<p><img alt="Ala Industrial Minecraft mod: network analyzer beam with sparks running from a nuclear reactor outlet along copper cable into a storage block" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.192-mc26.3/release-media/v0.1.192-mc26.3/changelog.webp" width="720"></p>

The Network Analyzer's energy trace is back on Minecraft 26.3 and now shows where the energy really goes.

### Fixed

- **The Network Analyzer highlight shows again.** On Minecraft 26.3 it did not appear at all.
- **The trace reads as a beam.** A bright core in a soft glow, shading from blue at the source to warm
  yellow at the far end, with solid sparks that no longer vanish or break apart from any angle.
- **Sparks follow the real flow of energy.** They run from generators, solar panels, the nuclear
  reactor's outlet and discharging storage to the machines and storage that need power. Distant sources
  show too, a full storage block no longer swallows every spark, and sparks never run head-on.
- **A more honest highlight.** It clears when you leave the world or change dimension, draws wires only
  where the network really connects, and right-clicking the air clears it. It is always visible through
  walls; the "Through blocks" setting is gone.
