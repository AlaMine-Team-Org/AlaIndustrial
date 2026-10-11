package dev.alaindustrial.mixin;

import dev.alaindustrial.item.fluid.CapsuleFuel;
import dev.alaindustrial.registry.ModContent;
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
 * on both loaders (the config lives in {@code common/alaindustrial.compat-optional.mixins.json}) and drives every furnace fuel
 * decision that routes through {@link FuelValues}: placement/ignition on Fabric via {@code isFuel} +
 * {@code burnDuration}, and on NeoForge via {@code ItemStack#getBurnTime} whose default delegates to
 * {@code burnDuration}. The empty-capsule remainder is handled separately by the item's {@code craftRemainder}
 * property; per-stack size is capped to one in the fuel slot by {@code FurnaceFuelSlotMixin} (no tare loss).
 *
 * <p>Sawdust (MOD-655) is a short fuel that burns as long as vanilla bamboo. 26.2 has no fuel component, so
 * the facade {@code FurnaceFuel.burnsLikeBamboo} leaves its properties alone and this mixin answers for it
 * instead: {@code isFuel} says yes and {@code burnDuration} reports the table's own bamboo value, so a
 * datapack that edits bamboo moves sawdust with it.
 */
@Mixin(FuelValues.class)
public abstract class FuelValuesMixin {

	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$lavaCapsuleIsFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (CapsuleFuel.isLavaCapsule(stack) || isSawdust(stack)) {
			cir.setReturnValue(true);
		}
	}

	// MOD-498 — FuelValues#burnDuration is deprecated by NeoForge only; vanilla does not mark it, and the
	// replacement NeoForge names (ItemStack#getBurnTime) is loader-only API absent from the vanilla
	// classes this shared mixin is compiled against for Fabric. It is also the exact method this mixin
	// injects into, so the re-entrant lookup below must go through the same one to stay datapack-faithful.
	@SuppressWarnings("deprecation")
	@Inject(method = "burnDuration", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$lavaCapsuleBurnDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (CapsuleFuel.isLavaCapsule(stack)) {
			// Mirror the vanilla lava bucket's own value (datapack-faithful) rather than hardcoding 20000.
			// Re-entrant call with a lava bucket is safe: the guard above rejects it, so it falls through to
			// the original body and returns the map value.
			cir.setReturnValue(((FuelValues) (Object) this).burnDuration(new ItemStack(Items.LAVA_BUCKET)));
		} else if (isSawdust(stack)) {
			// Bamboo's own table value, re-entrant like the lava bucket above: bamboo is no sawdust.
			cir.setReturnValue(((FuelValues) (Object) this).burnDuration(new ItemStack(Items.BAMBOO)));
		}
	}

	private static boolean isSawdust(ItemStack stack) {
		return stack.is(ModContent.SAWDUST.get());
	}
}
