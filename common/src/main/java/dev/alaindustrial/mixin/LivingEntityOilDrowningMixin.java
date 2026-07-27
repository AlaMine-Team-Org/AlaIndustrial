package dev.alaindustrial.mixin;

import dev.alaindustrial.fluid.OilPhysics;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes oil drown you (MOD-250).
 *
 * <p>Vanilla's whole breathing block in {@code LivingEntity#baseTick} hangs off a single call —
 * {@code isEyeInFluid(FluidTags.WATER)} — and a modded fluid is not in that tag, so under oil the air
 * bar never moves. Worse, the {@code else} arm of that same branch <em>refills</em> air by four points
 * a tick, so a hand-rolled "minus one per tick" elsewhere would be swallowed whole; the branch itself
 * has to be told that oil counts.
 *
 * <p>Redirecting that one call is therefore the smallest possible change with the largest reuse:
 * draining bubbles, the drowning damage, the client-side pop event and dismounting a boat all come
 * from vanilla unmodified. Two consequences are accepted rather than worked around: Respiration and
 * Water Breathing protect against oil too (a sealed mask is a sealed mask), and the death message is
 * the drowning one — a bespoke damage type would mean a new death message in all twenty language
 * files, which is its own task.
 *
 * <p>Config note: registered in {@code alaindustrial.compat-optional.mixins.json}. If another mod's
 * transformation makes this injection impossible, oil stops drowning and the game still boots — the
 * deliberate trade, with the same caveat as the MOD-248 overlay: a failure is silent.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityOilDrowningMixin {

	@Redirect(method = "baseTick",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean alaindustrial$oilCountsAsSubmerged(LivingEntity self, TagKey<Fluid> tag) {
		if (self.isEyeInFluid(tag)) {
			return true;
		}
		// Guarded on WATER so that if vanilla ever asks the same question about another tag in this
		// method, oil does not silently answer for it.
		return tag == FluidTags.WATER && OilPhysics.isEyeInOil(self);
	}
}
