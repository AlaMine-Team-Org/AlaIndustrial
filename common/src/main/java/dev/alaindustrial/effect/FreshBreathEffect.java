package dev.alaindustrial.effect;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fresh Breath (MOD-918) — what a piece of chewing gum leaves behind: no Nausea while it lasts.
 *
 * <p>The effect itself does nothing on its tick. The guard is a refusal at the door, {@link #refuses}, which
 * {@code LivingEntityNauseaGuardMixin} asks from {@code LivingEntity#canBeAffected}: every ordinary way Nausea
 * arrives — the radiation symptom, a pufferfish, another mod's {@code addEffect} — goes through that check, so
 * the guard needs no list of sources. Removing Nausea from inside this effect's own tick instead would mutate
 * the map vanilla is iterating at that moment.
 */
public class FreshBreathEffect extends MobEffect {

	/** Mint green — the HUD icon tint and the particles vanilla draws for the effect. */
	public static final int COLOR = 0x7CCB9B;

	public FreshBreathEffect() {
		super(MobEffectCategory.BENEFICIAL, COLOR);
	}

	/**
	 * Chewing one piece: Nausea goes now, and the guard starts over for {@link Config#chewingGumGuardTicks}.
	 * A second piece restarts the timer rather than adding to it — a longer instance simply replaces the
	 * shorter one, as with any vanilla effect.
	 */
	public static void chew(LivingEntity entity) {
		entity.removeEffect(MobEffects.NAUSEA);
		entity.addEffect(new MobEffectInstance(ModEffects.FRESH_BREATH.get(), Config.chewingGumGuardTicks));
	}

	/** True when {@code incoming} is Nausea and {@code entity} is still under Fresh Breath. */
	public static boolean refuses(LivingEntity entity, MobEffectInstance incoming) {
		return incoming.is(MobEffects.NAUSEA) && entity.hasEffect(ModEffects.FRESH_BREATH.get());
	}
}
