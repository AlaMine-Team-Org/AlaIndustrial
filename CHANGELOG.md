## 0.1.39

<p><img alt="Ala Industrial electromagnet pulling dropped items toward the player" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.39/release-media/v0.1.39/changelog.png" width="720"></p>

A technical update: the item magnet reaches further, plus a big round of stability, balance and bug fixes across the whole mod.

### Improved

- **The item magnet reaches further.** Your electromagnet now pulls dropped items in from **5 blocks** away, up from 3 — enough to actually clear a mining trip. It still charges from EU and toggles off with shift-right-click.

### Fixed

- **A bad config value no longer crashes the world.** A wrong number in the settings file — a negative buffer, a zero rate — now falls back to a safe default instead of crashing the game.
- **Running generators light up again.** A generator that is producing power now glows the way it should.
- **The generator hum no longer clicks.** Removed a repeating tick at the loop point of the running-generator sound.
- **Machine output no longer overfills.** The furnace, macerator, compressor and extractor now respect an item's real stack limit when adding results.

### Changed

- **Energy Storage costs a bit more to craft.** The battery box now takes two batteries in its recipe instead of a cheap plank shell — a fairer price for a starter energy buffer, and it ties into the ore-to-battery chain.

### Behind the scenes

- **Large stability pass.** A broad round of internal cleanup and hardening across the mod — no gameplay change, just fewer edge-case bugs and a more solid base for what comes next.
