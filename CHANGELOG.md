## 0.1.55

<p><img alt="Ala Industrial 0.1.55 — the Vulcanizer and the Electric Heater running" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.55/release-media/v0.1.55/changelog.png" width="720"></p>

Rubber is no longer something you bake in a furnace — it now takes two ingredients, a machine, a heat source, and a new ore to go find.

### New

- **Sulfur ore** generates underground and grinds into sulfur dust — half of every batch of rubber.
- **The Vulcanizer** combines raw rubber and sulfur dust into rubber. It needs EU and a heat source directly below it.
- **The Electric Heater** is the strongest heat source there is: it triples what the machine above it produces.
- **Insulated cables** for tin, copper and gold — rubber halves the energy they lose over distance.

### Improved

- Heat quality decides your yield: a campfire gives 1 rubber, lava or a magma block 2, an Electric Heater 3. Swapping the source mid-batch restarts it without eating your ingredients.
- Insulating is a batch job now: 3 bare cables + 3 rubber + 3 bare cables gives 6 insulated cables — half the rubber per cable.

### Fixed

- Long cable runs no longer swallow the whole flow — losses ramp up smoothly and every packet delivers at least 1 EU.
