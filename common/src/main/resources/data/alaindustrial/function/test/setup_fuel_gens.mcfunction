# Fuel generators — solid generator (8 EU/t) vs geothermal (16 EU/t, lava). + fuel-validation NEG.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~3 ~ ~0 alaindustrial:generator
setblock ~5 ~ ~0 alaindustrial:geothermal_generator
give @s minecraft:coal 64
give @s minecraft:coal_block 16
give @s minecraft:lava_bucket 1
give @s minecraft:cobblestone 1
tellraw @s {"text":"[fuel-gens] generator(x+3, 8 EU/t) | geothermal(x+5, 16 EU/t, lava=16000 EU/bucket).","color":"green"}
tellraw @s {"text":"NEG (generator): cobblestone -> never burns (MOD-007); lava_bucket -> rejected from slot (MOD-008).","color":"gray"}
tellraw @s {"text":"Check: generator LIT while burning; buffer caps at 4000 (gen) / 4000 (geo). Lava is post-v1.","color":"gray"}
