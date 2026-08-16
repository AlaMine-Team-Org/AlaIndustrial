# Ala Industrial -> Ore Vein Miner compat: LOAD hook (#minecraft:load).
#
# Creates the per-ore "mined" stat objectives that Ore Vein Miner (namespace `svm`) reads to
# detect a break. Ore Vein Miner names them `svm.b.<namespace>.<id>` (see its check_block macro),
# so the names below are FIXED by that convention and must match exactly.
#
# Runs on every world load / `/reload`, and re-adds every objective unconditionally. There is
# deliberately NO one-shot `created` flag any more: it made the objectives a snapshot of the ore
# list as it was on the world's FIRST load, so an ore added later (sulfur, MOD-245) never got its
# objective in an existing save and could not be detected — the stat half of MOD-341.
#
# Re-adding an existing objective is a silent no-op, not an error: `scoreboard objectives add`
# throws ERROR_OBJECTIVE_ALREADY_EXISTS (commands.scoreboard.objectives.add.duplicate), a
# CommandSyntaxException that reaches CommandSourceStack.sendFailure — which returns without
# output when the source is silent, and datapack functions run with suppressed output (all verified
# against the 26.2 bytecode). The `ala_vm.state` line below has always run unguarded on every load
# for exactly this reason. Failing to re-add also cannot lose data: the objective it collides with
# is the one we wanted, already carrying the player's counts.
#
# `done` is reset here, on every load, and is what lets `register` reconcile the block list once
# per load instead of once per world — see register.mcfunction.

scoreboard objectives add ala_vm.state dummy
scoreboard players set done ala_vm.state 0

scoreboard objectives add svm.b.alaindustrial.tin_ore minecraft.mined:alaindustrial.tin_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_tin_ore minecraft.mined:alaindustrial.deepslate_tin_ore
scoreboard objectives add svm.b.alaindustrial.silver_ore minecraft.mined:alaindustrial.silver_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_silver_ore minecraft.mined:alaindustrial.deepslate_silver_ore
scoreboard objectives add svm.b.alaindustrial.nickel_ore minecraft.mined:alaindustrial.nickel_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_nickel_ore minecraft.mined:alaindustrial.deepslate_nickel_ore
scoreboard objectives add svm.b.alaindustrial.sulfur_ore minecraft.mined:alaindustrial.sulfur_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_sulfur_ore minecraft.mined:alaindustrial.deepslate_sulfur_ore
scoreboard objectives add svm.b.alaindustrial.uranium_ore minecraft.mined:alaindustrial.uranium_ore
scoreboard objectives add svm.b.alaindustrial.deepslate_uranium_ore minecraft.mined:alaindustrial.deepslate_uranium_ore
scoreboard objectives add svm.b.alaindustrial.palladium_ore minecraft.mined:alaindustrial.palladium_ore
