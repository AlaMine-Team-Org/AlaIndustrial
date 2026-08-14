## 0.1.95

<p><img alt="Ala Industrial 0.1.95 electric heater screen" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.95/release-media/v0.1.95/changelog.png" width="720"></p>

The Electric Heater is now a furnace you have to light, and a machine sitting under another machine finally gets power.

### Improved

- **The Electric Heater warms up.** Cold, it gives no heat at all — the Vulcanizer above waits about ten seconds, then runs at ×3 straight away.
- **It has a screen.** Warm-up thermometer, energy reserve, and what it actually costs per tick.
- **You can read it without opening anything.** The coils glow brighter as they heat, and a fully warmed heater lets heat escape the seam with the machine above.
- **It costs more to run.** 6 EU/t and a 2400 EU buffer — one cold start plus one batch even with the cable cut. With nothing to heat it still costs nothing.

### Fixed

- **A machine under another machine no longer starves.** It used to take a little power and then freeze forever, while moving it anywhere else charged it instantly.
