## 0.1.90

<p><img alt="Ala Industrial 0.1.90 creative tab preview" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.90/release-media/v0.1.90/changelog.png" width="360"></p>

A housekeeping update for the things you touch daily: the creative tab, item pipes and the charging plate.

### Improved

- **The creative tab is sorted properly.** Energy, then storage, cables, machines, fluids, logistics, tools, upgrades, materials, blocks — instead of one long run.
- **Pipes obey your settings.** Configure one side and only what you configured happens; untouched sides are just route.
- **Cables and machines cost the server less.** A base with hundreds of cables and busy pipes runs lighter, and everything behaves exactly as before.
- **The config file is grouped into sections.** Your edits are kept, and a file written by a newer build is no longer applied silently.

### Fixed

- **Two chests joined by a pipe no longer swap items forever.** Nothing configured used to mean both ends took and gave at once.
- **The charging plate no longer makes your inventory stutter.** Same charging speed, far fewer updates per second.
- **Leaving a world no longer keeps it in memory.** Switching worlds in one session used to pile them up.

### Good to know

- If you had "extract from a chest into an unconfigured machine", give the machine's side an insert.
- Lowering a buffer size in the config now trims the charge of already-placed blocks when their chunk loads.
