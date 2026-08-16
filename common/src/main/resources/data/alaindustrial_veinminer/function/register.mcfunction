# Ala Industrial -> Ore Vein Miner compat: TICK hook (#minecraft:tick).
#
# Registers the Ala Industrial ore blocks into Ore Vein Miner's pickaxe block list by calling its
# own `svm:add_block` API (so we never copy or patch its files).
#
# Two gates, in this order:
#   - `done ala_vm.state == 1`               -> already reconciled since this world was LOADED, stop.
#     This is a per-load gate, not a per-world one: `setup` resets it to 0 on every #minecraft:load.
#     A one-shot per-world flag is exactly what MOD-341 fixed — a world created before an ore
#     existed kept `done=1` forever, so the ore was never picked up and updating the mod could not
#     repair an existing save.
#   - `svm:data blocks.pickaxe` is missing   -> Ore Vein Miner is absent (or has not built its list
#     yet), stop before touching any `svm:` function. This gate runs BEFORE `done` is set, so a
#     late-loading Ore Vein Miner is simply retried on the next tick.
# The presence gate MUST be a data-existence check, NOT a scoreboard check: `execute if score` on a
# non-existent objective THROWS "Unknown objective" (ObjectiveArgument.getObjective, verified in the
# 26.2 sources) -> that would error every tick when Ore Vein Miner is absent. `execute if data`
# returns false for a missing storage/path without throwing, so this stays truly silent. Ore Vein
# Miner builds `svm:data blocks.pickaxe` from its own load (svm:setup -> reset_config), so on a world
# that has it the list already exists by the time this tick runs and add_block can append.
# `ala_vm.state` below always exists (our setup creates it on #minecraft:load, before any tick).
#
# Idempotency is per-ORE, never per-world. `svm:add_block` is a bare `data modify ... append value`
# with NO de-duplication of its own, and `svm:check_loop` walks the WHOLE list every tick (both
# verified against the shipped Ore Vein Miner datapack) — so a duplicate entry is not cosmetic, it
# is a permanent per-tick cost. Each ore is therefore appended only if it is not already present.
# `blocks.pickaxe[{namespace:...,id:...}]` is an NbtPathArgument MatchElementNode, which filters list
# elements with NbtUtils.compareNbt(pattern, element, true) — a PARTIAL compound match (verified
# against the 26.2 bytecode). This also makes future ores self-registering: adding a line below is
# enough, with no gate to touch and no save migration.

execute if score done ala_vm.state matches 1 run return fail
execute unless data storage svm:data blocks.pickaxe run return fail

# List size before/after, so the confirmation is shown only when something was actually added
# (`data get` on a CollectionTag returns its size — verified against the 26.2 bytecode).
execute store result score before ala_vm.state run data get storage svm:data blocks.pickaxe

execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"tin_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"tin_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"deepslate_tin_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"deepslate_tin_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"silver_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"silver_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"deepslate_silver_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"deepslate_silver_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"nickel_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"nickel_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"deepslate_nickel_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"deepslate_nickel_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"sulfur_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"sulfur_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"deepslate_sulfur_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"deepslate_sulfur_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"uranium_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"uranium_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"deepslate_uranium_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"deepslate_uranium_ore",category:"pickaxe"}
execute unless data storage svm:data blocks.pickaxe[{namespace:"alaindustrial",id:"palladium_ore"}] run function svm:add_block {namespace:"alaindustrial",id:"palladium_ore",category:"pickaxe"}

execute store result score after ala_vm.state run data get storage svm:data blocks.pickaxe
scoreboard players set done ala_vm.state 1
execute if score after ala_vm.state > before ala_vm.state run tellraw @a ["",{"text":"[Ala Industrial]","color":"gold"},{"text":" Ores registered with Ore Vein Miner.","color":"dark_green"}]
