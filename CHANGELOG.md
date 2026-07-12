## 0.1.19

<p><img alt="Network analyzer overlay — animated energy flow through cables" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.19/release-media/v0.1.19/changelog.gif" width="720"></p>

This update reworks the **Network Analyzer overlay** — the in-world visualization you see when inspecting your power grid. Cables now render as thick piped tubes, energy travels along them as visible flowing pulses, and every machine on the line shows up as a clear junction.

### Quality of Life

- **Thick, readable cables.** The overlay used to draw cables as thin, easy-to-miss lines. They now render as proper piped tubes with corner joints, so the shape of your grid is obvious at a glance from any angle.
- **Energy flow is visible again.** The moving sparks that show energy traveling through a cable were effectively invisible before. They're now short, bright pulses that run along the tube wall — one per block — so you can watch power move without them clipping or disappearing at a distance.
- **Machines always show as junctions.** A generator, machine or battery boxed between two aligned cables used to look like the cable just passed straight through it. Now every endpoint is drawn as its own junction marker: the tube breaks around it and drops down to the block on both sides, and the flow pulses line up with the rest of the pipe.

### Changes

- The overlay's look and animation are now consistent across both loaders — same thick tubes, junctions and smooth per-frame flow everywhere.
