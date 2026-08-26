# All 4 machines, each powered by an adjacent generator (north face). Feed inputs, watch process.
# facing=south is REQUIRED, not cosmetic: a block's FACING face is energy-inert (R-NRG-03,
# EnergyBlockEntity.facingAwareRole). With the default north the machine's front would look
# straight at its generator and no EU could enter. MOD-516.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~3 ~ ~-1 alaindustrial:generator
setblock ~3 ~ ~0 alaindustrial:macerator[facing=south]
setblock ~5 ~ ~-1 alaindustrial:generator
setblock ~5 ~ ~0 alaindustrial:electric_furnace[facing=south]
setblock ~7 ~ ~-1 alaindustrial:generator
setblock ~7 ~ ~0 alaindustrial:compressor[facing=south]
setblock ~9 ~ ~-1 alaindustrial:generator
setblock ~9 ~ ~0 alaindustrial:extractor[facing=south]
give @s minecraft:coal 64
give @s minecraft:raw_iron 16
give @s alaindustrial:iron_dust 16
give @s minecraft:cobblestone 16
give @s minecraft:clay_ball 16
give @s minecraft:gravel 16
tellraw @s {"text":"[machines] macerator(x3) furnace(x5) compressor(x7) extractor(x9), each gen-fed (north).","color":"green"}
tellraw @s {"text":"macerator: raw_iron->2 iron_dust (E300). furnace: cobblestone->stone / iron_dust->ingot (E200).","color":"gray"}
tellraw @s {"text":"compressor: clay_ball->brick (E260). extractor: gravel->flint (E240). Machines use 2 EU/t.","color":"gray"}
tellraw @s {"text":"Check: progress arrow advances only while powered; output slot fills; hopper can't pull input / push output (R-GUI-05).","color":"gray"}
