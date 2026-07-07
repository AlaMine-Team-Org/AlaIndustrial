# TC-GEN-001 — fuel generator in isolation.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~2 ~ ~0 alaindustrial:generator
give @s minecraft:coal 64
give @s minecraft:cobblestone 1
give @s minecraft:diamond_pickaxe 1
tellraw @s {"text":"[TC-GEN-001] generator placed. Coal=valid fuel, cobble=invalid (NEG02).","color":"green"}
tellraw @s {"text":"Check: FUN01 fuel burns, FUN02 buffer caps at 4000, NEG01 no external EU in.","color":"gray"}
