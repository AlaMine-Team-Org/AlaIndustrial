## 0.1.101

<p><img alt="Ala Industrial 0.1.101 update preview" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.101/release-media/v0.1.101/changelog.png" width="720"></p>

Two more machines found their voice, and the Water Mill finally tells you what it is waiting for.

### New

- **The Canning Machine rattles.** A light metallic clatter of tin down the line while it packs.
- **The Galvanic Bath hums.** A faint ionic buzz of electrolysis while it plates.
- Both tones are deliberately unlike the ones already in use — the press thud, the pump suction, the grinder — so you can tell what is running without looking. The silence chip mutes them like everything else, and subtitles are there in every language.

### Fixed

- **The Water Mill says "No wheel".** Place one down and open it, and the screen used to be blank: it looked like it was working, made nothing, and gave no hint. It now names what is missing, the way the Wind Turbine has always announced a missing rotor.
- The mill's status line is never empty any more. "No wheel", "No water", "Blocked", "Wheel interference" — or how much it is currently making.

### Improved

- Bad numbers in the config file no longer vanish without a word. Type a value the mod cannot use and it says so in the log, instead of quietly swapping it out and rewriting your file.
- A pump whose search fails now reports it with coordinates. Before, "nothing to pump here" and "the search broke" looked exactly the same.
