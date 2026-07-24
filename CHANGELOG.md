## 0.1.48

<p><img alt="Tin, copper and gold cables carrying power from solar panels to battery boxes" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.48/release-media/v0.1.48/changelog.png" width="720"></p>

Cables come in three grades now, and each one is good at something different.

### New

- **Three cables instead of one.** Tin, Copper and Gold — the cheap one, the all-rounder, and the wide one.
- **Tin Cable** is the cheapest to make and leaks the least. It carries a single solar panel's trickle with no loss at all, at any distance — big panel farms are finally cheap to wire up.
- **Gold Cable** moves four times as much power as copper, but leaks more per block. Use it for short, heavy runs right next to the machines.
- **Don't mix grades on one line.** A network runs on the strongest cable in it — both its throughput and its loss — so one gold segment inside a copper run won't speed the line up, it will only make it leak more.

### Fixed

- **Cables connect to low blocks properly.** Next to a solar panel the cable's sleeve floated above the surface and your cursor went straight through it.
- **Gold Chest reads correctly in Hindi and Ukrainian.** Two letters came from the wrong script, and the Ukrainian name now agrees in gender like the iron and silver ones do.
- **Guide book descriptions cleaned up.** The water wheel and windmill blades entries ended with a stray internal marker.
