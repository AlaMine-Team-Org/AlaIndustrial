## 0.1.13

<p><img alt="Ala Industrial 0.1.13 update preview — wind turbines and cables" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.13/release-media/v0.1.13/changelog.png" width="720"></p>

Wind turbines no longer spin through each other, the Network Analyzer sees past your storage, and cables connect only to the faces that actually carry power.

### Gameplay

- **Wind turbines interfere with each other.** Two turbines placed so close that their rotors overlap now both stop working honestly instead of drawing flickering, clipping blades. A new "rotor interference" status appears in the GUI when this happens.
- **The Network Analyzer now sees through storage.** It no longer stops highlighting at the Battery Box. Use it on a cable to light up the whole connected system, or switch to the "stop at storage" mode to inspect a single segment. Toggle modes with **Shift + right-click in the air** — storage blocks show up in a third color so you can tell them apart from producers and consumers.

### Quality of Life

- **Cables connect only to working power faces.** A copper cable next to a wind turbine, a Battery Box, or another machine now draws its connector sleeve only to the sides that actually carry energy — the wind turbine's back, the Battery Box's two in/out faces — instead of drawing a misleading stub on every side. The iron chest is left alone entirely.

### Bug Fixes

- Fixed two neighboring wind turbines rendering flickering, overlapping blades when their rotors crossed.
- Fixed the Network Analyzer stopping at the Battery Box and failing to highlight the rest of the network.
