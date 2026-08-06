## 0.1.74

<p><img alt="Water mill interface showing its live output" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.74/release-media/v0.1.74/changelog.png" width="720"></p>

The water mill now tells you how much it makes, and its wheel finally respects the blocks around it.

### New

- **See what the mill makes.** The screen shows `Output: N EU/t` while the wheel turns. Block part of the current and watch the number drop.

### Fixed

- **Water has to reach the wheel, not the block.** Pouring water around the visible wheel used to do nothing. The four cells around the wheel drive it now: above, below, and one on each side. Still up to 4 EU/t.
- **The wheel no longer spins through terrain.** Burying it in the ground or packing blocks against it kept it turning through solid rock. Now it hides and the screen reads "Blocked".

### Heads up

- Mills fed only from the sides or the back of the block, and wheels buried in terrain, need a quick rebuild — dig the wheel out and let the water run past it.
