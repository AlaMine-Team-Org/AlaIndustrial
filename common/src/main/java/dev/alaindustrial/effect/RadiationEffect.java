package dev.alaindustrial.effect;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.radiation.RadiationCore;
import dev.alaindustrial.core.radiation.RadiationDose;
import dev.alaindustrial.registry.ModDamageTypes;
import dev.alaindustrial.skill.SkillHazard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Radiation sickness (MOD-470) — the effect that IS the player's accumulated dose.
 *
 * <p><b>Why the dose lives in the duration.</b> Nothing here stores a number: the remaining duration
 * of this effect is the dose, {@link RadiationCore#level} reads the severity off it, and vanilla's own
 * per-tick countdown is the decay. That buys persistence across relog, client sync and the HUD icon on
 * both loaders without a single line of player-data code — see the task log for the alternative that
 * was considered and rejected.
 *
 * <p><b>Symptoms escalate with the dose, not with the amplifier.</b> The effect is always applied at
 * amplifier 0; asking the instance for its duration each tick is what makes a dose that is still
 * climbing feel different from one that is bleeding off, in both directions, with no re-application.
 */
public class RadiationEffect extends MobEffect {

	/** Sickly green — the colour of the HUD icon and of the particles vanilla draws for the effect. */
	public static final int COLOR = 0x4CE04C;

	public RadiationEffect() {
		super(MobEffectCategory.HARMFUL, COLOR);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		// Gating happens below, against the game clock, so the cadence of a symptom does not drift
		// with the dose the way `tickCount % n` would.
		return true;
	}

	@Override
	public boolean applyEffectTick(ServerLevel serverLevel, LivingEntity mob, int amplification) {
		// Off means off, including for a dose already carried. Returning false REMOVES the effect, so
		// flipping the switch clears the world instead of leaving everyone who was already irradiated to
		// keep taking damage from a mechanic the server just turned off.
		if (!Config.radiationEnabled) {
			return false;
		}
		// Only players carry a dose (MOD-470 decision): mobs would turn every reactor into a silent
		// farm-killer, and the per-entity cost buys nothing.
		if (!(mob instanceof Player player) || player.isCreative() || player.isSpectator()) {
			return true;
		}
		int level = RadiationCore.level(RadiationDose.of(player), Config.radiationDoseCapacity);
		if (level <= 0) {
			return true;
		}

		long clock = serverLevel.getGameTime();
		if (clock % Config.radiationSymptomIntervalTicks == 0) {
			applySymptoms(player, level);
		}
		// MOD-483 Tolerance Threshold / Background Shift. Both touch the DAMAGE only: the dose still climbs
		// and the symptoms still show, so a player always sees the background rising even when it has
		// stopped hurting them. Level 1 is the mod's own low band, which is what the threshold skill
		// declares safe.
		int damageInterval = SkillHazard.damageInterval(damageIntervalFor(level), player);
		boolean harmless = SkillHazard.radiationHarmless(player, level, LOW_BAND_LEVEL);
		if (!harmless && damageInterval > 0 && clock % damageInterval == 0) {
			player.hurtServer(serverLevel, ModDamageTypes.radiation(serverLevel), damageFor(level));
		}
		return true;
	}

	/**
	 * The vanilla effects that make a dose legible before it is lethal. Re-applied on a cadence with a
	 * duration comfortably longer than that cadence, so they never flicker between applications.
	 */
	private void applySymptoms(Player player, int level) {
		int duration = Config.radiationSymptomIntervalTicks * 3;
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, duration, 0, true, false, false));
		if (level >= 2) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, true, false, false));
		}
		if (level >= 3) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, 0, true, false, false));
		}
	}

	/**
	 * The lowest severity that hurts at all — see {@link #damageIntervalFor}, where levels below this
	 * have no interval. Tolerance Threshold (MOD-483) removes the damage at exactly this step and
	 * nothing above it: the first painful rung stops being painful, while a serious dose still kills.
	 * Symptoms and the dose itself are untouched at every level, so the player always sees the
	 * background rising.
	 */
	private static final int LOW_BAND_LEVEL = 2;

	/** Ticks between hits at a given severity; 0 means this level does not hurt yet. */
	private static int damageIntervalFor(int level) {
		return switch (level) {
			case 2 -> Config.radiationDamageIntervalLevel2;
			case 3 -> Config.radiationDamageIntervalLevel3;
			case 4 -> Config.radiationDamageIntervalLevel4;
			default -> 0;
		};
	}

	private static float damageFor(int level) {
		return level >= 4 ? Config.radiationDamageLethal : Config.radiationDamageSick;
	}
}
