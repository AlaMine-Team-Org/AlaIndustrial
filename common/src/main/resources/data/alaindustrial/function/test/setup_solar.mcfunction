# Solar panel -> cable -> battery_box. R-NRG-15 (day-only), R-CON-11 (direct), MOD-003 (rain=0).
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
fill ~3 ~1 ~0 ~3 100 ~0 minecraft:air replace
setblock ~3 ~ ~0 alaindustrial:solar_panel
setblock ~4 ~ ~0 alaindustrial:copper_cable
setblock ~5 ~ ~0 alaindustrial:battery_box[facing=west]
tellraw @s {"text":"[solar] panel -> cable -> battery_box[input=west], open sky above the panel.","color":"green"}
tellraw @s {"text":"env_noon -> EU>0; env_midnight -> 0; env_rain / env_thunder -> 0 (MOD-003, not halved). R-NRG-15.","color":"gray"}
