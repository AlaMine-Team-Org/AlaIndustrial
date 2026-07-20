## 0.1.38

<p><img alt="Ala Industrial player dashboard showing mastery rank, energy totals and output per generator" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.38/release-media/v0.1.38/changelog.webp" width="720"></p>

Track your industrial career on a brand-new profile screen, and stop chasing dropped ore around the mine.

### New

- **Player dashboard.** Press **K** — or the button in the corner of your inventory — to see your character, your rank and your career: energy produced, energy usefully spent, how long your setup has been running, and a breakdown of output per generator.
- **Mastery ranks.** A progression of its own, separate from vanilla levels: 40 levels across 8 titles, Apprentice through Legend. Points come from real machine work — every finished macerator, furnace, compressor, extractor or pump operation counts. Generators pay too, but far slower, so idling your way to Legend is not a thing.
- **Electromagnet.** A chargeable gadget that sits in your inventory and pulls nearby dropped items straight to you, up to 3 blocks in every direction. Shift-right-click switches it off, so you can carry it past farms and sorters without draining them.

### Improved

- **Cables are real pipes now.** Energy used to jump from generator to machine in a single tick. Every cable now holds a small buffer and passes power along segment by segment, so a working line has inertia and a cut leaves the remainder sitting in the wires. The buffer is deliberately tiny — 12 EU per cable — so you cannot build a battery out of cable.

### Fixed

- **The activity timer froze on a busy base.** Once your grid filled up, the dashboard's activity clock stopped counting. Time and energy are now tracked separately, so the clock keeps running even when there is nowhere left to put the power.
- **Two guide book entries** — tempered iron tools and the scythe — showed no icon and kept an untranslated title.
