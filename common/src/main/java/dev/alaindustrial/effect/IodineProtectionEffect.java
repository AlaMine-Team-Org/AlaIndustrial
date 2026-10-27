package dev.alaindustrial.effect;

import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.registry.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Iodine Protection (MOD-916) — what an iodine tablet leaves behind: radiation builds up slower while it lasts.
 *
 * <p>Prevention, not a cure: the effect scales only the dose that a sweep is about to ADD
 * ({@link #doseAdded}), so a dose already carried is left exactly where it was. Milk and the Antirad bar
 * (MOD-914) are the cure — "a tablet before the job, a bar after it".
 *
 * <p>The effect does nothing on its own tick; the radiation sweep asks it, the same way it asks the
 * Respirator skill ({@code SkillHazard#doseAdded}).
 */
public class IodineProtectionEffect extends MobEffect {

	/** Iodine violet — the HUD tint and the particles vanilla draws for the effect. */
	public static final int COLOR = 0x8A52B8;

	public IodineProtectionEffect() {
		super(MobEffectCategory.BENEFICIAL, COLOR);
	}

	/**
	 * Taking one tablet: the protection runs for {@link RadiationConfig#iodineProtectionTicks}. A second tablet
	 * sets the timer back to full instead of strengthening the protection — the effect has one level only, and a
	 * longer instance simply replaces the shorter one.
	 */
	public static void take(LivingEntity entity) {
		entity.addEffect(new MobEffectInstance(ModEffects.IODINE_PROTECTION.get(),
				RadiationConfig.iodineProtectionTicks));
	}

	/**
	 * The dose a radiation sweep adds, after the tablet: scaled by {@link RadiationConfig#iodineDosePercent}
	 * while the effect lasts, unchanged otherwise. Rounded down, so the protection never costs a tick extra.
	 */
	public static int doseAdded(int added, LivingEntity entity) {
		if (added <= 0 || !entity.hasEffect(ModEffects.IODINE_PROTECTION.get())) {
			return added;
		}
		return scaled(added, RadiationConfig.iodineDosePercent);
	}

	/** {@code added} at {@code percent} of itself, rounded down; the arithmetic of {@link #doseAdded}. */
	public static int scaled(int added, int percent) {
		return (int) ((long) added * Math.max(0, percent) / 100);
	}
}
