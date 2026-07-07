# Clear a 12x5x8 arena in front of player and lay a smooth_stone floor.
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
tellraw @s {"text":"[clear] arena cleared + floor laid.","color":"green"}
