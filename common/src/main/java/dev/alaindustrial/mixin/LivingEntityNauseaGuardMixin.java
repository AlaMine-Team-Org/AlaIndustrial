package dev.alaindustrial.mixin;

import dev.alaindustrial.effect.FreshBreathEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Nausea out while Fresh Breath lasts (MOD-918).
 *
 * <p>{@code LivingEntity#canBeAffected} is the one gate {@code addEffect} asks before an effect lands — the
 * radiation symptom, a pufferfish and other mods all pass through it — so refusing here guards against every
 * ordinary source at once. Fabric has no event for it; NeoForge's {@code MobEffectEvent.Applicable} would cover
 * only one loader, and a single injection keeps both on the same rule.
 *
 * <p>Config note: registered in {@code alaindustrial.compat-optional.mixins.json}. If another mod makes this
 * injection impossible, the game still boots and gum still clears Nausea on the spot; only the guard that
 * follows is lost.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityNauseaGuardMixin {

	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$freshBreathRefusesNausea(MobEffectInstance incoming,
			CallbackInfoReturnable<Boolean> cir) {
		if (FreshBreathEffect.refuses((LivingEntity) (Object) this, incoming)) {
			cir.setReturnValue(false);
		}
	}
}
