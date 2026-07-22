# Ala Industrial -> Ore Vein Miner compat: TICK hook (#minecraft:tick).
#
# Registers the 8 Ala Industrial ore blocks into Ore Vein Miner's pickaxe block list, exactly once,
# by calling its own `svm:add_block` API (so we never copy or patch its files).
#
# Self-disabling safety gate:
#   - `done ala_vm.state == 1`               -> already registered this world, stop.
#   - `svm:data blocks.pickaxe` is missing   -> Ore Vein Miner is absent (or has not built its list
#     yet), stop before touching any `svm:` function.
# The presence gate MUST be a data-existence check, NOT a scoreboard check: `execute if score` on a
# non-existent objective THROWS "Unknown objective" (ObjectiveArgument.getObjective, verified in the
# 26.2 sources) -> that would error every tick when Ore Vein Miner is absent. `execute if data`
# returns false for a missing storage/path without throwing, so this stays truly silent. Ore Vein
# Miner builds `svm:data blocks.pickaxe` from its own load (svm:setup -> reset_config), so on a world
# that has it the list already exists by the time this tick runs and add_block can append.
# `ala_vm.state` below always exists (our setup creates it on #minecraft:load, before any tick).

execute if score done ala_vm.state matches 1 run return fail
execute unless data storage svm:data blocks.pickaxe run return fail

function svm:add_block {namespace:"alaindustrial",id:"tin_ore",category:"pickaxe"}
function svm:add_block {namespace:"alaindustrial",id:"deepslate_tin_ore",category:"pickaxe"}
function svm:add_block {namespace:"alaindustrial",id:"silver_ore",category:"pickaxe"}
function svm:add_block {namespace:"alaindustrial",id:"deepslate_silver_ore",category:"pickaxe"}
function svm:add_block {namespace:"alaindustrial",id:"nickel_ore",category:"pickaxe"}
function svm:add_block {namespace:"alaindustrial",id:"deepslate_nickel_ore",category:"pickaxe"}
function svm:add_block {namespace:"alaindustrial",id:"uranium_ore",category:"pickaxe"}
function svm:add_block {namespace:"alaindustrial",id:"deepslate_uranium_ore",category:"pickaxe"}

scoreboard players set done ala_vm.state 1
tellraw @a ["",{"text":"[Ala Industrial]","color":"gold"},{"text":" Ores registered with Ore Vein Miner.","color":"dark_green"}]
