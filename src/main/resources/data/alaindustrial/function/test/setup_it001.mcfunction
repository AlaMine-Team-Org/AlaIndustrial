# IT-001 — generator -> copper cable x3 -> macerator.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~2 ~ ~0 alaindustrial:generator
setblock ~3 ~ ~0 alaindustrial:copper_cable
setblock ~4 ~ ~0 alaindustrial:copper_cable
setblock ~5 ~ ~0 alaindustrial:copper_cable
setblock ~6 ~ ~0 alaindustrial:macerator
give @s minecraft:coal 64
give @s minecraft:iron_ore 32
tellraw @s {"text":"[IT-001] gen -> cable x3 -> macerator. Fuel the gen, ore the macerator.","color":"green"}
tellraw @s {"text":"Check: one network forms, EU flows, iron_dust x2 appears (gen EU/t=8).","color":"gray"}
