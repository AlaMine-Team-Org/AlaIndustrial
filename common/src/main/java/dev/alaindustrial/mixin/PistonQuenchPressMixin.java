package dev.alaindustrial.mixin;

import dev.alaindustrial.core.ceramic.QuenchPress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The quench press (MOD-590): a piston fired into a water source splits the carbon briquettes floating
 * there into ceramic plates.
 *
 * <p><b>Why the hook is here and not anywhere else.</b> {@code triggerEvent} is the moment the piston
 * is TOLD to extend, before anything has moved. One tick later the extension has already destroyed the
 * water source and shoved the item entities out of the way, so any later hook — and any passive check
 * on the item itself — would look at a block that is no longer water and find nothing floating in it.
 * {@code moveBlocks} is private and would need an accessor for no gain; {@code triggerEvent} is
 * protected and carries the level, the position and the trigger id already.
 *
 * <p>Injected at HEAD without cancelling: the piston then does exactly what vanilla does. Whether the
 * extension eats the water source is vanilla's business and deliberately not ours — a player who wants
 * a press that reloads itself builds it the way an infinite water source is built.
 *
 * <p>Registered in {@code alaindustrial.mixins.json} (required), not in the optional config. A failed
 * injection here would leave the press silently paying nothing while the recipe book still promises
 * it, and a gate that lies is worse than one that crashes.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonQuenchPressMixin {

	@Inject(method = "triggerEvent", at = @At("HEAD"))
	private void alaindustrial$quenchBeforeExtending(BlockState state, Level level, BlockPos pos,
			int id, int param, CallbackInfoReturnable<Boolean> callback) {
		if (id != PistonBaseBlock.TRIGGER_EXTEND || !(level instanceof ServerLevel server)) {
			return;
		}
		BlockPos target = pos.relative(state.getValue(DirectionalBlock.FACING));
		QuenchPress.quench(server, target, QuenchPress.pressYield());
	}
}
