package dev.alaindustrial.core.radiation;

import dev.alaindustrial.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The one place that reads and writes somebody's dose (MOD-470).
 *
 * <p>The dose IS the remaining duration of the radiation effect — vanilla persists it, syncs it and
 * counts it down for us, on both loaders, with no player data of our own.
 *
 * <p><b>The icon shows always, the particles only on mobs (MOD-568).</b> In the HUD vanilla draws just the
 * icon, blinking in the last ten seconds — the "you are irradiated" signal that needs no instrument. The
 * level and the time left are read in the inventory screen, which lists an effect as "name  level  mm:ss".
 *
 * <p><b>The amplifier carries the severity, and that costs a remove.</b> With a fixed amplifier the only
 * thing that inventory line could tell was a countdown that inexplicably went UP whenever the player took
 * more dose. Showing the level there turns the same line into something true and useful:
 * <i>Radiation III — 2:41 until you are clean</i>. It cannot
 * be done by re-applying alone: {@code MobEffectInstance.update} pushes a LOWER amplifier into a hidden
 * effect that resurfaces later, which would corrupt the dose. So a change of level removes the effect
 * first — a handful of times over an exposure, not once a sweep.
 */
public final class RadiationDose {

	private RadiationDose() {
	}

	/** Dose the entity currently carries, in ticks. */
	public static int of(LivingEntity entity) {
		MobEffectInstance current = entity.getEffect(ModEffects.RADIATION.get());
		return current == null ? 0 : current.getDuration();
	}

	/**
	 * Write a dose back, showing its severity as the effect's level.
	 *
	 * @param particles whether the effect's particles show — off for the player (the screen already
	 *                  wobbles), on for mobs, where they are the only sign anything is happening; the icon
	 *                  shows either way
	 */
	public static void apply(LivingEntity entity, int dose, int capacity, boolean particles) {
		int amplifier = Math.max(0, RadiationCore.level(dose, capacity) - 1);
		MobEffectInstance current = entity.getEffect(ModEffects.RADIATION.get());
		if (current != null && current.getAmplifier() != amplifier) {
			entity.removeEffect(ModEffects.RADIATION.get());
		}
		entity.addEffect(new MobEffectInstance(ModEffects.RADIATION.get(), dose, amplifier,
				false, particles, true));
	}

	/** Take the dose away entirely — used when a mob transforms into what radiation makes of it. */
	public static void clear(LivingEntity entity) {
		entity.removeEffect(ModEffects.RADIATION.get());
	}
}
