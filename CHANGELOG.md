## 0.1.51

<p><img alt="The Incubator running: an item spinning inside its glass dome under the emitter" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.51/release-media/v0.1.51/changelog.webp" width="720"></p>

A new machine copies items and turns them into new materials, and a pipe can now put what it made back into it, so it keeps going on its own.

### New

- **The Incubator** — place the base, put any glass on top and the glass becomes a dome. The item floats and spins inside while the emitter showers it with particles; coloured glass tints the dome, and you get that exact block back when you take it apart.
- **A chip decides what it does.** Transformation trades an item for an equal one from another family (lapis for redstone, sapling for sapling), duplication gives you a second one, creation irradiates ordinary items into new materials. Each chip explains itself on hover, with the numbers behind Shift.
- **Uranium is the fuel** — one ingot is taken the moment you drop it in and powers three attempts, then burns out into depleted uranium. A failed roll still costs you the item.
- **Every success rolls a rarity,** Common through Legendary. A graded item mutates more readily next time, but the bonus is spent when it does — no legendary can be farmed.
- **The screen says why it is idle:** no dome, no chip, no recipe for that item, out of uranium, or the result slot is full.
- **A pipe can put a machine's own result back into it.** Take the finished item from one side, drop it into the input on another — two pipes and a wrench, and the duplication runs without you.

### Fixed

- **Pipes no longer pretend to connect to a machine's front.** That face never accepted automation, yet a pipe would join it and silently move nothing; it now refuses the face, so the mistake shows itself at once.
- **Sawmill recipe tabs have names** — "Sawmill: Planks" through "Sawmill: Stairs", instead of four tabs all called "Sawmill".
