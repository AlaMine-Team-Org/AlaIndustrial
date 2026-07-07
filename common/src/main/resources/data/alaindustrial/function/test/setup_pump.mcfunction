# Pump (POST-v1 fluids) — lava source -> pump -> geothermal. Pump is EU-powered (100 EU/bucket).
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~3 ~ ~0 minecraft:lava
setblock ~4 ~ ~0 alaindustrial:pump
setblock ~5 ~ ~0 alaindustrial:geothermal_generator
setblock ~4 ~ ~-1 alaindustrial:generator
give @s minecraft:coal 64
give @s minecraft:lava_bucket 1
tellraw @s {"text":"[pump] lava source -> pump -> geothermal; generator (north) powers the pump. (Fluids = POST-v1.)","color":"green"}
tellraw @s {"text":"Check: pump consumes EU, moves lava into the geothermal, which then produces 16 EU/t. Refill lava as needed.","color":"gray"}
