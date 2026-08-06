## 0.1.72

<p><img alt="Wind turbine interface showing live EU/t output" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.72/release-media/v0.1.72/changelog.png" width="720"></p>

Wind turbines now tell you how much power they make, and newer ores finally vein-mine in old worlds.

### New

- **Wind turbines show live output.** While the blades spin, the interface reads `Output: N EU/t`. When they stop, the row tells you why instead.

### Improved

- **The two turbine branches now play different roles.** The Sky Mill gives steady income in almost any weather, peaking at 12 EU/t. The Tempest Mill is the weakest under clear skies but reaches 21 EU/t in a thunderstorm.
- **Pumping no longer earns mastery.** Moving fluid around is not machine work — progress in the oil branch now comes from processing what you pumped, not from leaving a pump running.

### Fixed

- **Newer ores now vein-mine in worlds that already existed.** Sulfur was registered once per world, so a world created before sulfur existed never picked it up and mined one block at a time. Ores are re-checked every time you load a world now — old saves repair themselves, no commands needed, and any ore added later works the same way.
