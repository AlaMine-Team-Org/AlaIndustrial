package dev.alaindustrial.mixin;

import dev.alaindustrial.item.CapsuleFuel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a lava-filled Vacuum Capsule burn in a vanilla furnace exactly like a lava bucket (MOD-077).
 *
 * <p>{@link FuelValues} is keyed by item type, so it cannot distinguish a lava capsule from a water one — a
 * plain item registration would (wrongly) turn every filled capsule into fuel. This stack-aware guard runs
 * on both loaders (the config lives in {@code common/alaindustrial.mixins.json}) and drives every furnace fuel
 * decision that routes through {@link FuelValues}: placement/ignition on Fabric via {@code isFuel} +
 * {@code burnDuration}, and on NeoForge via {@code ItemStack#getBurnTime} whose default delegates to
 * {@code burnDuration}. The empty-capsule remainder is handled separately by the item's {@code craftRemainder}
 * property; per-stack size is capped to one in the fuel slot by {@code FurnaceFuelSlotMixin} (no tare loss).
 */
@Mixin(FuelValues.class)
public abstract class FuelValuesMixin {

	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$lavaCapsuleIsFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (CapsuleFuel.isLavaCapsule(stack)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "burnDuration", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$lavaCapsuleBurnDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (CapsuleFuel.isLavaCapsule(stack)) {
			// Mirror the vanilla lava bucket's own value (datapack-faithful) rather than hardcoding 20000.
			// Re-entrant call with a lava bucket is safe: the guard above rejects it, so it falls through to
			// the original body and returns the map value.
			cir.setReturnValue(((FuelValues) (Object) this).burnDuration(new ItemStack(Items.LAVA_BUCKET)));
		}
	}
}
