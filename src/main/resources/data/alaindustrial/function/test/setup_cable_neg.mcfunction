# Cable break + two sources. R-CON-07 (air gap breaks net), IT-001-NEG03 (two gens).
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~2 ~ ~0 alaindustrial:generator
setblock ~3 ~ ~0 alaindustrial:copper_cable
setblock ~4 ~ ~0 alaindustrial:copper_cable
setblock ~5 ~ ~0 minecraft:air
setblock ~6 ~ ~0 alaindustrial:copper_cable
setblock ~7 ~ ~0 alaindustrial:macerator
setblock ~7 ~ ~1 alaindustrial:generator
give @s minecraft:coal 64
give @s minecraft:iron_ore 16
tellraw @s {"text":"[cable_neg] gap at ~5: left gen cannot reach macerator (R-CON-07).","color":"green"}
tellraw @s {"text":"Fill the gap to merge nets; add 2nd gen -> EU sums but macerator caps at its limit.","color":"gray"}
