# MOD-004 sky transparency — daylight panels under CLEAR / GLASS / LEAVES / STONE.
# Daylight panel (4 EU/t) chosen so the x0.5 halving is visible numerically.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
fill ~3 ~1 ~-2 ~3 100 ~4 minecraft:air replace
setblock ~3 ~ ~-2 alaindustrial:daylight_solar_panel
setblock ~3 ~ ~0 alaindustrial:daylight_solar_panel
setblock ~3 ~1 ~0 minecraft:glass
setblock ~3 ~ ~2 alaindustrial:daylight_solar_panel
setblock ~3 ~1 ~2 minecraft:oak_leaves[persistent=true]
setblock ~3 ~ ~4 alaindustrial:daylight_solar_panel
setblock ~3 ~1 ~4 minecraft:stone
tellraw @s {"text":"[solar-cover] daylight panels: z-2 OPEN, z0 GLASS, z+2 LEAVES, z+4 STONE.","color":"green"}
tellraw @s {"text":"env_noon expect: open=4 (DAY), glass=4 (DAY, 100%), leaves=2 (PARTIAL), stone=0. MOD-004.","color":"gray"}
tellraw @s {"text":"GUI mode: open/glass=Day, leaves=Day (partial), stone=Night/idle (no sky).","color":"gray"}
