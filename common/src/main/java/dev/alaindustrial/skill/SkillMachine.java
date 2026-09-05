package dev.alaindustrial.skill;

import dev.alaindustrial.Config;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What the Mechanic and Agronomist branches do to a machine (MOD-483).
 *
 * <p>Every method takes the machine's level and owner rather than a player, because that is what a
 * block has. {@link OwnerPresence#skillsOf} turns the pair into a build and returns an empty one
 * whenever the owner is offline or in creative — which is the owner's rule for these two branches:
 * "player there, buffs work; player gone, buffs don't".
 *
 * <p>The numbers live here rather than at each machine so the branches can be re-balanced by reading
 * one file, and so no machine invents its own rounding.
 */
public final class SkillMachine {

	// Every number this class applies is a Config knob — see the MOD-483 block in Config.

	private SkillMachine() {
	}

	private static boolean has(@Nullable Level level, @Nullable UUID owner,
			SkillBranch branch, SkillSlot slot) {
		return OwnerPresence.skillsOf(level, owner).has(branch, slot);
	}

	// ── Mechanic ──────────────────────────────────────────────────────────────────────────────────

	/**
	 * Tuned Drive + Fine Tuning — operation length after the owner's skills.
	 *
	 * <p>Deliberately small next to an overclocker chip, which buys 25 % speed for 60 % more energy per
	 * operation. These are free, so they are a fifth of that: a skill must not make the chip pointless.
	 */
	public static int duration(int ticks, @Nullable Level level, @Nullable UUID owner) {
		if (ticks <= 0) {
			return ticks;
		}
		SkillBuild build = OwnerPresence.skillsOf(level, owner);
		int percent = 0;
		if (build.has(SkillBranch.MECH, SkillSlot.IN)) {
			percent += Config.skillTunedDrivePercent;
		}
		if (build.has(SkillBranch.MECH, SkillSlot.MID)) {
			percent += Config.skillFineTuningPercent;
		}
		return percent == 0 ? ticks : Math.max(1, ticks * (100 - percent) / 100);
	}

	/**
	 * Precise Draw — whether this tick of an operation is free.
	 *
	 * <p>Counted in ticks rather than taken off the per-tick draw because a basic machine draws 2 EU/t:
	 * ten percent of two rounds to nothing or to half. Skipping one tick in ten is exactly 10 % off the
	 * operation and works at any draw.
	 */
	public static boolean freeDrainTick(int progress, @Nullable Level level, @Nullable UUID owner) {
		return has(level, owner, SkillBranch.MECH, SkillSlot.B1)
				&& progress % Math.max(1, Config.skillPreciseDrawEveryTicks)
						== Math.max(1, Config.skillPreciseDrawEveryTicks) - 1;
	}

	/** Steady Hands — how long a fuel item burns after the owner's skills. */
	public static int burnDuration(int ticks, @Nullable Level level, @Nullable UUID owner) {
		if (ticks <= 0 || !has(level, owner, SkillBranch.MECH, SkillSlot.A1)) {
			return ticks;
		}
		return ticks * (100 + Config.skillSteadyHandsPercent) / 100;
	}

	/** Overclock Headroom — one more overclocker chip than the machine's tier would allow. */
	public static int overclockerCap(int cap, @Nullable Level level, @Nullable UUID owner) {
		if (cap <= 0 || !has(level, owner, SkillBranch.MECH, SkillSlot.A2)) {
			return cap;
		}
		return cap + 1;
	}

	/** Free Telemetry — whether the statistics panel works without a stats chip. */
	public static boolean statsWithoutChip(@Nullable Level level, @Nullable UUID owner) {
		return has(level, owner, SkillBranch.MECH, SkillSlot.B2);
	}

	/**
	 * Resilient Cycle — whether an operation this far along may finish on the machine's own charge.
	 *
	 * <p>Past the halfway mark only, and the energy is still spent: the machine eats its own buffer
	 * instead of demanding a supply. Finishing for free would let a player wire a switch to cut power
	 * just past halfway and take 49 % off every operation, for ever.
	 */
	public static boolean canCoast(int progress, int duration, @Nullable Level level,
			@Nullable UUID owner) {
		if (duration <= 0 || progress * 100 < duration * Config.skillResilientFromPercent) {
			return false;
		}
		return has(level, owner, SkillBranch.MECH, SkillSlot.CAP);
	}

	// ── Agronomist ──────────────────────────────────────────────────────────────────────────────────

	/** Frugal Sprayer — solution one watering pass costs, and Wide Watering adds its price. */
	public static int sprinklerSolution(int amount, @Nullable Level level, @Nullable UUID owner) {
		SkillBuild build = OwnerPresence.skillsOf(level, owner);
		long cost = amount;
		if (build.has(SkillBranch.AGRO, SkillSlot.IN)) {
			cost = Math.ceilDiv(cost * (100 - Config.skillFrugalFluidPercent), 100L);
		}
		if (build.has(SkillBranch.AGRO, SkillSlot.A1)) {
			// The radius grows by one, and area grows with the SQUARE of it: 81 tiles become 121. Paying
			// half again keeps the cost per tile roughly level instead of handing out 49 % of free work.
			cost = Math.round(cost * Config.skillWideWateringCost);
		}
		return (int) Math.max(1L, cost);
	}

	/** Wide Watering — the sprinkler's radius after skills. */
	public static int sprinklerRange(int range, @Nullable Level level, @Nullable UUID owner) {
		return has(level, owner, SkillBranch.AGRO, SkillSlot.A1) ? range + Config.skillWideWateringRadius : range;
	}

	/** Swift Drone — ticks the garden drone spends crossing one block. */
	public static int droneFlightTicks(int ticks, @Nullable Level level, @Nullable UUID owner) {
		if (ticks <= 1 || !has(level, owner, SkillBranch.AGRO, SkillSlot.B1)) {
			return ticks;
		}
		return Math.max(1, ticks - Config.skillSwiftDroneTicks);
	}

	/** Extended Round — the drone's radius, sized so the serviced area is exactly twice as large. */
	public static int droneRange(int range, @Nullable Level level, @Nullable UUID owner) {
		return has(level, owner, SkillBranch.AGRO, SkillSlot.CAP)
				? Math.max(range, Config.skillExtendedRoundRadius) : range;
	}

	/** Selection — mutation chance after skills. The mod's own cap still applies above this. */
	public static double mutationChance(double chance, @Nullable Level level, @Nullable UUID owner) {
		return has(level, owner, SkillBranch.AGRO, SkillSlot.A2) ? chance + Config.skillSelectionBonus : chance;
	}

	/** Frugal Vat — water one fermenter batch costs. */
	public static int fermenterWater(int amount, @Nullable Level level, @Nullable UUID owner) {
		if (amount <= 0 || !has(level, owner, SkillBranch.AGRO, SkillSlot.B2)) {
			return amount;
		}
		return (int) Math.max(1L, Math.ceilDiv((long) amount * (100 - Config.skillFrugalFluidPercent), 100L));
	}

	/**
	 * Crystal Care — greenhouse growth after skills.
	 *
	 * <p>Reached through the sprinkler's owner, not the greenhouse's: the greenhouse controller extends
	 * the energy base rather than the machine base and therefore has no owner of its own. That is also
	 * the game rule — an agronomist speeds up watering, so a greenhouse without a sprinkler gains
	 * nothing.
	 */
	public static int greenhouseGrowth(int ticks, @Nullable Level level, @Nullable UUID sprinklerOwner) {
		if (ticks <= 0 || !has(level, sprinklerOwner, SkillBranch.AGRO, SkillSlot.MID)) {
			return ticks;
		}
		return Math.max(1, ticks * (100 - Config.skillCrystalCarePercent) / 100);
	}
}
