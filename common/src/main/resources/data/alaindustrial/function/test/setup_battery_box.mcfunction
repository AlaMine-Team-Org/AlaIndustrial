# BatteryBox — charge to 100% (MOD-009), directional IO (MOD-006), keeps EU on break (R-BRK-07).
# facing=west => INPUT face points at the generator (x+3); OUTPUT is the opposite (east) face.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
setblock ~3 ~ ~0 alaindustrial:generator
setblock ~4 ~ ~0 alaindustrial:battery_box[facing=west]
give @s minecraft:coal_block 16
give @s minecraft:diamond_pickaxe 1
tellraw @s {"text":"[battery_box] generator -> battery_box[input=west]. Fuel the generator, open the battery_box GUI.","color":"green"}
tellraw @s {"text":"Check MOD-009: charge climbs to 20000/20000 (100%) — never stuck at 99% / 19998.","color":"gray"}
tellraw @s {"text":"Check MOD-006: only the WEST (facing) face accepts; put the generator on any other side -> no charge.","color":"gray"}
tellraw @s {"text":"Check R-BRK-07: charge it, break with a pickaxe -> dropped battery_box keeps its EU (re-place to confirm).","color":"gray"}
