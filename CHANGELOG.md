## 0.1.159

<p><img alt="Mirror Concentrator assembled into a two-by-two-by-two machine" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.159/release-media/v0.1.159/changelog.webp" width="720"></p>

4 updates in this release.

### New

- **A third rung for the day branch: the Mirror Concentrator.** Put a Resonance Chip into a
  Daylight Solar Panel's slot and let it work a few clear days — it grows into a concentrator
  that makes twice what the panel did, and half again as much through the two midday hours. The
  charge it had carries over; the chip is spent.
  You can tell from across the base whether it is running: when there is nothing to collect — at
  night, in rain, in a storm or under snow — its mirrors swing up and fold over the collector.
  Snow stops it completely, unlike the two panels below it, which keep a trickle: a mirror under
  snow reflects nothing.
- **The Mirror Concentrator can be built out into a real machine.** Craft seven Solar Panel Sections and place them around a grown concentrator, and it assembles into a two-by-two-by-two installation at the size it was drawn for. Look at the panel and hold sneak: a ghostly schematic shows the seven cells still missing, and walking around the machine moves it to your side — you pick which way it grows by where you build. A cell with something already in it turns amber. Break any of the eight and it comes apart into exactly the pieces you put in, still working as the one-block machine. Assembling is about size, not power: the output is the same.

### Bug Fixes

- Evolution progress no longer survives the chip being taken out. The counter used to simply freeze, so you could farm most of an evolution, pull the chip back and keep the progress for free. Progress now belongs to the chip: remove it, or swap the day branch for the night one, and what you accumulated is gone. This covers the wind mill as well — it uses the same chips.
  The empty chip slot also stopped being silent: it now shows what goes there, cycling between the day and the night chip, because both answers are right.
- **Solar panels no longer claim night has fallen in the middle of the day.** A thunderstorm at
  noon used to show the night mode, so the panel blamed a sunset that had not happened. It now
  names the weather instead. Both the basic and the daylight panel were wrong in the same way.
