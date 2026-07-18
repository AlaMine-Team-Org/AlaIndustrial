# Ala Industrial -> Ore Vein Miner compat: LOAD hook (#minecraft:load).
#
# Creates the per-ore "mined" stat objectives that Ore Vein Miner (namespace `svm`) reads to
# detect a break. Ore Vein Miner names them `svm.b.<namespace>.<id>` (see its check_block macro),
# so the names below are FIXED by that convention and must match exactly.
#
# Runs on every world load / `/reload`. The state objective + a one-shot `created` flag mean the
# stat objectives are created exactly once per world (they persist in the save afterwards), which
# keeps them baselined at 0 before any mining and avoids re-adding them every load.

scoreboard objectives add ala_vm.state dummy
execute if score created ala_vm.state matches 1 run return 0

scoreboard objectives add svm.b.alaindustrial.tin_ore minecraft.mined:alaindustrial.tin_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_tin_ore minecraft.mined:alaindustrial.deepslate_tin_ore
scoreboard objectives add svm.b.alaindustrial.silver_ore minecraft.mined:alaindustrial.silver_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_silver_ore minecraft.mined:alaindustrial.deepslate_silver_ore
scoreboard objectives add svm.b.alaindustrial.nickel_ore minecraft.mined:alaindustrial.nickel_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_nickel_ore minecraft.mined:alaindustrial.deepslate_nickel_ore
scoreboard objectives add svm.b.alaindustrial.uranium_ore minecraft.mined:alaindustrial.uranium_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_uranium_ore minecraft.mined:alaindustrial.deepslate_uranium_ore

scoreboard players set created ala_vm.state 1
