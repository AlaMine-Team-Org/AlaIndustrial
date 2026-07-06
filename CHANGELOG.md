## 0.1.4

A small polish pass: machines get their own distinct tops, the energy bars line up cleanly, and the basic generator stops accepting fuel it can't use.

### Visuals

- **Distinct tops for machines and generators.** Generators, the electric furnace, extractor and compressor now have their own top faces instead of all sharing a single one, with separate looks for when they're idle and when they're running. Machines are much easier to tell apart at a glance.

### Bug Fixes

- **The basic generator no longer takes lava buckets.** Lava is meant for the geothermal generator, so the basic generator now cleanly rejects lava buckets placed into its slot — no more flicker where the item briefly appeared and popped back out.
- **Energy bars fit their frame.** Fixed the energy bar in the extractor and compressor screens spilling a pixel past the top and bottom of its border.
