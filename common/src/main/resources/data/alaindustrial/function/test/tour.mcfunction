# Guided manual-test order. Run each setup_*, follow its chat hints + MANUAL-CHECKLIST.md.
tellraw @s {"text":"=== AlaIndustrial manual test tour ===","color":"gold"}
tellraw @s {"text":"0) prep + give_all once. Each setup_* clears the arena (stand in the open, face +x/east).","color":"gray"}
tellraw @s {"text":"1) setup_showroom  - every block renders, GUIs open, cables connect.","color":"aqua"}
tellraw @s {"text":"2) setup_fuel_gens - generator 8 EU/t, geothermal 16; cobble/lava rejected (MOD-007/008).","color":"aqua"}
tellraw @s {"text":"3) setup_solar_matrix + env_noon/midnight/rain - rates 1/4/2, rain=0 (MOD-003).","color":"aqua"}
tellraw @s {"text":"4) setup_solar_cover + env_noon - glass=full, leaves=half, stone=0 (MOD-004).","color":"aqua"}
tellraw @s {"text":"5) setup_machines - macerator/furnace/compressor/extractor process + hopper IO.","color":"aqua"}
tellraw @s {"text":"6) setup_battery_box - charge to 100% (MOD-009), input only on facing (MOD-006), keeps EU on break.","color":"aqua"}
tellraw @s {"text":"7) setup_network - machine before storage (MOD-009), equal split, no cable loss.","color":"aqua"}
tellraw @s {"text":"8) setup_drops - hand=no drop, pickaxe=drop (MOD-005).","color":"aqua"}
tellraw @s {"text":"9) setup_evolution - chip -> daylight/moonlit. setup_pump - fluids (post-v1).","color":"aqua"}
