# AlaIndustrial Mod Icon

Single source of truth for the official mod icon — a block mid-evolution (T1 → T2),
pixel art, isometric, dark background.

## Files

- `icon_original.jpeg` — the original file as-is, unmodified (archival copy).
- `icon_master_2048.png` — lossless PNG master at native 2048×2048 resolution.
  Source of truth for all other exports; when the icon design is updated, this
  file is changed first and everything else is regenerated from it.

## Game Assets (generated from the master)

Sizes for `fabric.mod.json` → `icon` (size-map, the loader picks the nearest
size ≥ the requested one — see the [Fabric wiki spec](https://wiki.fabricmc.net/documentation:fabric_mod_json_spec)):

`src/main/resources/assets/alaindustrial/icon-{16,32,64,128,256,512}.png`

## Publishing (Modrinth / CurseForge)

`icon-512.png` (~234 KiB) fits within Modrinth's limit (≤256 KiB per project
icon) and is suitable for direct upload when publishing the project.

## Regeneration

The icon set rebuild script (not stored in this repo, used ad hoc from a
scratch directory) — if the set needs to be recreated from a new master: take
`icon_master_2048.png`, downscale with Lanczos to the required powers of two,
place the results at the paths above, and update `fabric.mod.json`.
