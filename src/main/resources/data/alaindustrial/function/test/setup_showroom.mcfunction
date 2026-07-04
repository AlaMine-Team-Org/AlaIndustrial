# Showroom — one of every block for render / model / GUI / lit-state checks (not mechanics).
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~2 ~ ~0 alaindustrial:generator
setblock ~3 ~ ~0 alaindustrial:geothermal_generator
setblock ~4 ~ ~0 alaindustrial:solar_panel
setblock ~5 ~ ~0 alaindustrial:daylight_solar_panel
setblock ~6 ~ ~0 alaindustrial:moonlit_solar_panel
setblock ~7 ~ ~0 alaindustrial:macerator
setblock ~8 ~ ~0 alaindustrial:electric_furnace
setblock ~9 ~ ~0 alaindustrial:compressor
setblock ~10 ~ ~0 alaindustrial:extractor
setblock ~11 ~ ~0 alaindustrial:battery_box
setblock ~12 ~ ~0 alaindustrial:pump
# Cable cross (connection model / noOcclusion) at z+2.
setblock ~6 ~ ~2 alaindustrial:copper_cable
setblock ~5 ~ ~2 alaindustrial:insulated_copper_cable
setblock ~7 ~ ~2 alaindustrial:tin_cable
setblock ~6 ~ ~1 alaindustrial:insulated_tin_cable
setblock ~6 ~1 ~2 alaindustrial:copper_cable
tellraw @s {"text":"[showroom] one of every block (x+2..x+12) + a cable cross at z+2.","color":"green"}
tellraw @s {"text":"Check: textures/models load, non-cube blocks have no X-ray (noOcclusion), cables connect (arms), every machine GUI opens & renders.","color":"gray"}
