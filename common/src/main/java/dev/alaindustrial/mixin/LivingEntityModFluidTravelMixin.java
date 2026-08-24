package dev.alaindustrial.mixin;

import dev.alaindustrial.fluid.ModFluidPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the mod's fluids on vanilla's AIR movement path (MOD-495).
 *
 * <p>See {@link ModFluidPhysics} for the full mechanism. In short: on NeoForge 26.2.0.49-beta and
 * later, {@code shouldTravelInFluid} answers true for any registered {@code FluidType}, which sends
 * the entity into {@code travelInFluid}. There, a fluid that is neither water-like nor carrying a
 * custom {@code FluidType#move} matches no branch at all, so NEITHER input NOR gravity is applied
 * and the fluid behaves like a solid block. This mixin restores the behaviour the mod's physics is
 * built on — and which Fabric has natively, since it has no such path.
 *
 * <p><b>Why here rather than {@code FluidType#move}.</b> Overriding {@code move} is NeoForge's
 * intended extension point, but it requires performing the air movement ourselves, and vanilla's
 * {@code LivingEntity#travelInAir} is {@code private} — reachable only through an access
 * transformer, which is a heavy, loader-specific tool for restoring stock behaviour. Answering the
 * routing question instead is smaller, is one implementation for both loaders, and leaves every
 * movement rule to vanilla.
 *
 * <p><b>Why the water/lava guard.</b> The vanilla condition is
 * {@code (isInWater() || isInLava() || isInFluidType(state)) && ...}. An entity can be inside our
 * fluid and inside water at the same time — a flooded shaft, a deposit meeting a spring. Returning
 * false unconditionally would strip water physics in exactly those places, so the override applies
 * only when vanilla's own two fluids are not involved and the answer would therefore have come from
 * the modded-fluid term alone.
 *
 * <p><b>Config note:</b> registered in {@code alaindustrial.mixins.json} ({@code required: true}),
 * unlike the cosmetic MOD-248/MOD-250 injections. A silent failure here does not degrade a feature —
 * it makes every deposit in the world an invisible wall, which is worse than not booting.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityModFluidTravelMixin {

	@Inject(method = "shouldTravelInFluid", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$modFluidsKeepAirPhysics(FluidState fluidState,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.isInWater() || self.isInLava()) {
			return;
		}
		if (ModFluidPhysics.staysOnAirPath(fluidState.getType())) {
			cir.setReturnValue(false);
		}
	}
}
