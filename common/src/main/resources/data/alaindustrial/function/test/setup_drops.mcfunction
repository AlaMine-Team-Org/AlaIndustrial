# MOD-005 tool gating — break by hand (no drop) vs pickaxe (drops). + R-BRK-07 battery_box keeps EU.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~3 ~ ~0 alaindustrial:generator
setblock ~4 ~ ~0 alaindustrial:macerator
setblock ~5 ~ ~0 alaindustrial:battery_box
setblock ~6 ~ ~0 alaindustrial:solar_panel
setblock ~7 ~ ~0 alaindustrial:daylight_solar_panel
setblock ~8 ~ ~0 alaindustrial:copper_cable
give @s minecraft:wooden_pickaxe 1
give @s minecraft:diamond_pickaxe 1
tellraw @s {"text":"[drops] Break by HAND -> slow + NO drop (block vanishes). Break with any PICKAXE -> drops the block (MOD-005).","color":"green"}
tellraw @s {"text":"R-BRK-07: charge the battery_box first, then break with a pickaxe -> the dropped battery_box keeps its EU. Machines lose their buffer.","color":"gray"}
