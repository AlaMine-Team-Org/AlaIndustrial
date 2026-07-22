## 0.1.42

<p><img alt="Ala Industrial 0.1.42 update preview" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.42/release-media/v0.1.42/changelog.gif" width="720"></p>

The water mill gets a proper rework — it now runs on flowing water — plus better compatibility with other installed mods.

### New
- **"No water" hint on the mill.** A mill that has its wheel but no water nearby now tells you what's missing right in its screen, instead of just sitting idle.
- **Water mill outputs from the back.** Like the wind mill, energy now leaves only through the back face (marked with a red port) — connect your cable or battery there.

### Changed
- **Water mills need flowing water now.** A still pond no longer powers a mill — you need real current: a waterfall hitting the wheel, or a channel where water runs past it. (Rivers and oceans count as still water, so just dropping a mill in a river won't work.)
- **Clearer Player Profile icon.** In the guide book it now uses a player head instead of the book icon, so it no longer looks identical to the guide book entry.

### Fixed
- **Water wheels need room to spin.** A solid block in the wheel's path — in front, above, or to the sides — now hides the wheel and stops generation with an "Obstructed" status; clear it and the mill starts right back up. Water channels along the sides are still fine.
- **Hoppers respect the machine front.** A machine's working (front) face is inert again — hoppers can no longer insert items through it, matching how energy already behaved.
- **Hoppers can't overstuff the mill.** Automation now only adds a wheel to an empty slot, instead of piling extra ones onto the installed wheel.
- **No more crashes from other mods.** If a neighbouring mod errors inside its own energy network, the mod isolates that failure and keeps the server running instead of going down with it.
- **Better startup compatibility.** Optional cosmetic tweaks now turn themselves off if another mod changes the same things, so the game keeps loading normally.
