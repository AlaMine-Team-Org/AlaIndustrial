# Energy network — machine priority over storage (MOD-009) + fair split between equal consumers (R-NRG-08).
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
# Rig A (priority): generator -> cable -> macerator + battery_box on the same net.
setblock ~3 ~ ~0 alaindustrial:generator
setblock ~4 ~ ~0 alaindustrial:copper_cable
setblock ~4 ~ ~1 alaindustrial:macerator
setblock ~4 ~ ~-1 alaindustrial:battery_box[facing=south]
# Rig B (split): generator -> cable -> two equal macerators.
setblock ~7 ~ ~0 alaindustrial:generator
setblock ~8 ~ ~0 alaindustrial:copper_cable
setblock ~8 ~ ~1 alaindustrial:macerator
setblock ~8 ~ ~-1 alaindustrial:macerator
give @s minecraft:coal 64
give @s minecraft:raw_iron 32
tellraw @s {"text":"[network] Rig A (x3-4): gen -> cable -> macerator + battery_box. Rig B (x7-8): gen -> cable -> 2 macerators.","color":"green"}
tellraw @s {"text":"Rig A: the MACHINE charges first; the battery_box only takes the remainder (MOD-009 priority).","color":"gray"}
tellraw @s {"text":"Rig B: the two equal macerators fill ~evenly (R-NRG-08). No EU lost on the cable (throughput-limit, MOD-009).","color":"gray"}
