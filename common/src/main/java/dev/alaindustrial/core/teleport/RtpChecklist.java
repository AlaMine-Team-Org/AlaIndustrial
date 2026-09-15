package dev.alaindustrial.core.teleport;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The random jump's checklist (MOD-630): the five things a random jump needs, and what is known about each.
 *
 * <p><b>One set of rules for two readers.</b> The server asks which conditions a jump breaks
 * ({@code TeleportEngine#rtpProblems}); the remote's «Random» tab asks the same question about the station snapshot, which
 * the server built without loading a chunk. Both hand their facts to this class, so the list a player reads cannot drift
 * from the list the server checks.
 *
 * <p><b>Every broken condition, not the first.</b> {@code TeleportEngine#checkRtpPolicy} stops at the first problem —
 * right for refusing a press, wrong for a checklist: a station with no chip and no charge has two things to fix, and a
 * player who fixes one should not then discover the other.
 *
 * <p><b>Unknown is not failed.</b> A station the server has no record of, or someone else's private one, has a chip and a
 * charge the client is not told. Those rows read unknown, and an unknown row does not grey the button: the server checks
 * the real station on the press, as the «Map» tab's Teleport does for an unknown station.
 *
 * <p>Minecraft-free, so the L1 lane tests it.
 */
public final class RtpChecklist {

	private RtpChecklist() {
	}

	/** The conditions, in the order the tab lists them. */
	public enum Check {
		/** The player stands in the Overworld: the search reads a surface, which only the Overworld has. */
		OVERWORLD,
		/** The paying station exists, is in the player's dimension, and lets this player use it. */
		ACCESS,
		/** A Random Jump Chip is fitted in the paying station. */
		CHIP,
		/** The paying station holds at least the flat price. */
		CHARGE,
		/** The player's teleporter recharge has run out. */
		RECHARGED
	}

	/** What is known about one condition. */
	public enum Mark {
		PASSED, FAILED, UNKNOWN
	}

	/** The paying station's side of {@link Check#ACCESS}. */
	public enum Access {
		OK,
		/** No record of the station: whether the player may use it is not known. */
		UNKNOWN,
		/** Someone else's private station. */
		PRIVATE,
		/** Bound in another dimension. */
		OTHER_WORLD,
		/** Nothing stands at the bound position any more. */
		MISSING
	}

	/** Charge the client was not told. */
	public static final long ENERGY_UNKNOWN = -1L;

	/**
	 * What is known about the player and one paying station.
	 *
	 * @param chipKnown whether {@code chip} is known at all — false for a station with no record and for someone else's
	 *     private one, whose chip the server withholds
	 * @param energy the station's charge, or {@link #ENERGY_UNKNOWN}
	 * @param cost the flat price of a random jump
	 * @param cooldownSeconds the player's recharge still to run, 0 when a jump may start now
	 */
	public record Facts(boolean inOverworld, Access access, boolean chipKnown, boolean chip, long energy, long cost,
			int cooldownSeconds) {
	}

	/** One condition's mark. */
	public static Mark mark(Check check, Facts facts) {
		return switch (check) {
			case OVERWORLD -> facts.inOverworld() ? Mark.PASSED : Mark.FAILED;
			case ACCESS -> switch (facts.access()) {
				case OK -> Mark.PASSED;
				case UNKNOWN -> Mark.UNKNOWN;
				case PRIVATE, OTHER_WORLD, MISSING -> Mark.FAILED;
			};
			case CHIP -> !facts.chipKnown() ? Mark.UNKNOWN : facts.chip() ? Mark.PASSED : Mark.FAILED;
			case CHARGE -> facts.energy() < 0 ? Mark.UNKNOWN
					: facts.energy() >= facts.cost() ? Mark.PASSED : Mark.FAILED;
			case RECHARGED -> facts.cooldownSeconds() <= 0 ? Mark.PASSED : Mark.FAILED;
		};
	}

	/** Every condition the facts break, in the tab's order. */
	public static EnumSet<Check> failing(Facts facts) {
		EnumSet<Check> failed = EnumSet.noneOf(Check.class);
		for (Check check : Check.values()) {
			if (mark(check, facts) == Mark.FAILED) {
				failed.add(check);
			}
		}
		return failed;
	}

	/** The first broken condition — the one the hint box explains — or {@code null} when none is broken. */
	public static Check firstFailing(Facts facts) {
		for (Check check : Check.values()) {
			if (mark(check, facts) == Mark.FAILED) {
				return check;
			}
		}
		return null;
	}

	/** Whether every condition is known and passed. */
	public static boolean allPassed(Facts facts) {
		for (Check check : Check.values()) {
			if (mark(check, facts) != Mark.PASSED) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The station switcher's order: stations with a chip first, the rest after, each group in the order they were bound.
	 * A station whose chip is not known counts as one without.
	 *
	 * @param chip for each bound station, in binding order, whether it is known to carry a chip
	 */
	public static List<Integer> order(boolean[] chip) {
		List<Integer> order = new ArrayList<>(chip.length);
		for (int i = 0; i < chip.length; i++) {
			if (chip[i]) {
				order.add(i);
			}
		}
		for (int i = 0; i < chip.length; i++) {
			if (!chip[i]) {
				order.add(i);
			}
		}
		return order;
	}

	/**
	 * The station {@code step} places after {@code current} in {@code order}, wrapping at either end. A current station
	 * that is not in the order — nothing selected yet — steps to the first.
	 */
	public static int step(List<Integer> order, int current, int step) {
		if (order.isEmpty()) {
			return -1;
		}
		int at = order.indexOf(current);
		if (at < 0) {
			return order.get(0);
		}
		int size = order.size();
		return order.get(Math.floorMod(at + step, size));
	}
}
