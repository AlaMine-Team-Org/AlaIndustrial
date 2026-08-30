## 0.1.130

<p><img alt="A reactor room going critical and exploding" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.130/release-media/v0.1.130/changelog.webp" width="720"></p>

A reactor left to overheat now explodes, and the ground remembers it.

### New

- **Reactors explode.** A gauge pinned at the top starts a countdown of two to three minutes, rolled fresh every time, and then the core goes up. Water, the lever or a breached wall call it off - hold the core below the red line for five seconds and it is over. Blast power grows with the rod count: a small accident is contained by the shell entirely, a large station is not. A full buffer does not save it.
- **Bare reactors have a limit.** A core with no room around it now builds up instability with the size of its pile, shown on its panel. Up to three racks it settles and runs indefinitely; a fourth runs away into the same countdown. Lava farms built on a bare core still work and still cost nothing - they simply have a boundary now, visible before you cross it.
- **Irradiated soil.** The scar an accident leaves: it irradiates everything alive nearby, fades through four visible steps and washes away twice as fast under water. An ordinary block - dig it, hold it, place it - but it keeps irradiating from your bag, one step below refined uranium. It only settles where the blast actually reached.

### Bug Fixes

- **Fuel rod assemblies give their rods back when broken.** Uranium left in a column used to vanish on any break - pickaxe, explosion or piston.
- **JEI lists the mod's machines again.** Recipe cards were missing for every machine, not just one: the Fermenter had no mapping to the viewer, and that took the whole plugin down with it.
