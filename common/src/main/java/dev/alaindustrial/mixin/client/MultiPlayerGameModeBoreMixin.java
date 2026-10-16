package dev.alaindustrial.mixin.client;

import dev.alaindustrial.item.tool.ElectricDrillItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the extra blocks of a bore on the miner's own client in the same moment as the centre one
 * (MOD-821), with the break particles and sound the server never sends to the miner.
 *
 * <p>The centre block is client-PREDICTED: {@code continueDestroyBlock} runs {@code destroyBlock} inside
 * {@code startPrediction}, so it vanishes at once. The extras were only removed when the server's block
 * updates arrived, a ping later, and the server's break event skips the miner, so there were no particles
 * for them at all. Here the planned extras ({@link ElectricDrillItem#plannedExtrasShown}) are broken with the same
 * client call right after the centre one, still inside that prediction: {@code BlockStatePredictionHandler}
 * records the server-verified state of every position touched, so if the server refuses an extra (a protection
 * mod, a different charge) the acknowledgement puts it back, and if it agrees nothing changes.
 *
 * <p>The plan is drawn up at the head, while the centre block still stands, and executed at the return only if
 * vanilla really broke it. A re-entrancy flag keeps the extras' own {@code destroyBlock} calls from planning
 * again. Optional mixin config: a cosmetic prediction must never stop the game from loading.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeBoreMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	/** Set while the extras are being broken; client thread only. */
	private static boolean alaindustrial$breakingExtras;
	/** The plan drawn up at the head of the outermost call; client thread only. */
	private static List<BlockPos> alaindustrial$plan = List.of();

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void alaindustrial$planBoreExtras(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (alaindustrial$breakingExtras) {
			return;
		}
		alaindustrial$plan = List.of();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}
		ItemStack drill = player.getMainHandItem();
		if (drill.getItem() instanceof ElectricDrillItem) {
			alaindustrial$plan = ElectricDrillItem.plannedExtrasShown(drill, level, pos,
						level.getBlockState(pos), player);
		}
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void alaindustrial$breakBoreExtras(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (alaindustrial$breakingExtras) {
			return;
		}
		List<BlockPos> plan = alaindustrial$plan;
		alaindustrial$plan = List.of();
		if (plan.isEmpty() || !Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		alaindustrial$breakingExtras = true;
		try {
			for (BlockPos extra : plan) {
				((MultiPlayerGameMode) (Object) this).destroyBlock(extra);
			}
		} finally {
			alaindustrial$breakingExtras = false;
		}
	}
}
