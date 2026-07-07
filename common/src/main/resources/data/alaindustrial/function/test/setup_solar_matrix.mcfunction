# Solar matrix — all 3 panel types side by side, open sky. Toggle env_* and read each GUI.
# Covers MOD-003 (rain=0) + day/night gating + EU rates 1 / 4 / 2.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
fill ~3 ~1 ~0 ~7 100 ~0 minecraft:air replace
setblock ~3 ~ ~0 alaindustrial:solar_panel
setblock ~5 ~ ~0 alaindustrial:daylight_solar_panel
setblock ~7 ~ ~0 alaindustrial:moonlit_solar_panel
tellraw @s {"text":"[solar-matrix] base(x+3) daylight(x+5) moonlit(x+7), open sky.","color":"green"}
tellraw @s {"text":"env_noon -> base=1, daylight=4, moonlit=0 EU/t.","color":"gray"}
tellraw @s {"text":"env_midnight -> base=0, daylight=0, moonlit=2 EU/t.","color":"gray"}
tellraw @s {"text":"env_rain / env_thunder -> ALL panels 0 EU (MOD-003); GUI mode shows (rain).","color":"gray"}
tellraw @s {"text":"Open each panel GUI to read mode + EU/t. Re-run env_* if the clock drifts.","color":"gray"}
