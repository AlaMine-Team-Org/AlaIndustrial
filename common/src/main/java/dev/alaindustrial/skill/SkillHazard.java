package dev.alaindustrial.skill;

import dev.alaindustrial.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * What the Liquidator branch does (MOD-483) — the mod's existing hazards, softened.
 *
 * <p>Two rules shape every number here, both learned from bugs the mod already had.
 *
 * <p><b>A skill is weaker than the gear that does the same job.</b> One worn piece of protective
 * armour is worth 25 %, a full set caps at 95 %, and nothing here comes close: a skill that matched a
 * crafted set would make the whole line pointless.
 *
 * <p><b>A skill never wears the suit.</b> Protective gear is charged durability for what it actually
 * stopped, so a cut applied inside that calculation would bill the suit for protection the player's
 * skill provided. Every method here is therefore applied at the point of harm, after wear has been
 * charged — never inside the shielding maths.
 */
public final class SkillHazard {

	// Every number this class applies is a Config knob — see the MOD-483 block in Config.

	private SkillHazard() {
	}

	private static boolean has(@Nullable Entity entity, SkillSlot slot) {
		return entity instanceof ServerPlayer player
				&& SkillStore.build(player).has(SkillBranch.HAZARD, slot);
	}

	/**
	 * Respirator — dose actually added this sweep.
	 *
	 * <p>Applied to the total AFTER the suit has been charged for its own work, so the skill and the
	 * armour never compete for credit and the suit is not worn out by protection it did not provide.
	 */
	public static int doseAdded(int dose, @Nullable Entity player) {
		if (dose <= 0 || !has(player, SkillSlot.IN)) {
			return dose;
		}
		return (int) ((long) dose * (100 - Config.skillRespiratorPercent) / 100L);
	}

	/**
	 * Dielectric + Full Insulation — bare-cable damage after skills.
	 *
	 * <p>Applied at the point the damage lands, never in the "does this cable bite" predicate: that
	 * predicate is a pure function asserted directly by gametests on both loaders.
	 */
	public static float shockDamage(float damage, @Nullable Entity player) {
		if (damage <= 0.0f) {
			return damage;
		}
		int cut = 0;
		if (has(player, SkillSlot.A1)) {
			cut = Config.skillDielectricPercent;
		}
		if (has(player, SkillSlot.A2)) {
			cut = Config.skillFullInsulationPercent;
		}
		return cut == 0 ? damage : damage * (100 - cut) / 100.0f;
	}

	/** Careful Wear — dose one point of suit durability is worth, after skills. */
	public static int dosePerDurability(int dose, @Nullable Entity player) {
		if (dose <= 0 || !has(player, SkillSlot.B1)) {
			return dose;
		}
		return dose * (100 + Config.skillCarefulWearPercent) / 100;
	}

	/**
	 * Tolerance Threshold — whether radiation may hurt at all right now.
	 *
	 * <p>Tied to the mod's own low-dose band rather than a number of its own: that band already means
	 * "background exposure a miner lives with", and this skill says exactly that such exposure stops
	 * being lethal. Above it, the dose hurts as it always did.
	 */
	public static boolean radiationHarmless(@Nullable Entity player, int dose, int lowBandCeiling) {
		return has(player, SkillSlot.MID) && dose <= lowBandCeiling;
	}

	/** Background Shift — ticks between radiation hits, after skills. */
	public static int damageInterval(int ticks, @Nullable Entity player) {
		if (ticks <= 0 || !has(player, SkillSlot.CAP)) {
			return ticks;
		}
		return ticks * Math.max(1, Config.skillBackgroundShiftFactor);
	}

	/** Dosimetrist — how far the Geiger counter reaches. */
	public static int geigerRadius(int radius, @Nullable Entity player) {
		return has(player, SkillSlot.B2) ? Math.max(radius, Config.skillDosimetristRadius) : radius;
	}
}
