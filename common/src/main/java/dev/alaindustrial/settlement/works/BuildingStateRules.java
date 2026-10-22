package dev.alaindustrial.settlement.works;

import java.util.List;

/**
 * How a building's state follows from what the world shows and what the queue holds (MOD-857, epic decisions R79,
 * R86). The town's scanner feeds it one finished pass over the building and acts on the answer; nothing here touches
 * the world.
 *
 * <p><b>Restored (R86)</b> — the share of the building's cells that match its intact template reaches the
 * definition's threshold (90 % for the built-in town halls) and every key point is in place. The first time that
 * happens the town hall's log gets a line ({@link Verdict#justRestored()}).
 *
 * <p><b>An intact building that loses a block goes back to repair.</b> When a building becomes intact the number of
 * cells it still misses is remembered ({@link Verdict#allowedMissing()}) and only ever shrinks; a later pass that
 * misses more — a player broke a wall, a creeper took a corner — reads {@link BuildingState#REPAIR}, and the building
 * is intact again once it is back to that number. No new log line: the town hall logs a restoration once.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks every transition ({@code BuildingStateRulesTest}).
 */
public final class BuildingStateRules {

	/** The share of matching cells a building needs when its definition names none (R86). */
	public static final double DEFAULT_RESTORED_AT = 0.9;

	private BuildingStateRules() {
	}

	/**
	 * One finished pass over a building.
	 *
	 * @param matched     cells that hold what the intact template wants there
	 * @param total       cells the comparison counts: the intact template's blocks and the ruin's debris
	 * @param missingKeys the translation keys of the key points not in place, empty when all are
	 */
	public record Measure(int matched, int total, List<String> missingKeys) {

		public Measure {
			if (matched < 0 || total < 0 || matched > total) {
				throw new IllegalArgumentException("matched " + matched + " of " + total);
			}
			missingKeys = List.copyOf(missingKeys);
		}

		/** Cells still to fix. */
		public int missing() {
			return total - matched;
		}

		/** The matching share in whole percent, rounded down; 0 for an empty comparison. */
		public int percent() {
			return total == 0 ? 0 : (int) ((long) matched * 100 / total);
		}
	}

	/**
	 * What the building was before this pass.
	 *
	 * @param everRestored   it has been restored at least once
	 * @param allowedMissing the cells it may miss and still be intact, or -1 before its first restoration
	 */
	public record Before(boolean everRestored, int allowedMissing) {
		/** A building never restored. */
		public static final Before NEVER = new Before(false, -1);
	}

	/**
	 * The answer.
	 *
	 * @param state          the building's state now
	 * @param everRestored   it has been restored at least once, this pass included
	 * @param allowedMissing see {@link Before#allowedMissing()}
	 * @param justRestored   this pass restored it for the first time: the town hall logs it
	 */
	public record Verdict(BuildingState state, boolean everRestored, int allowedMissing, boolean justRestored) {
	}

	/** Whether {@code measure} meets the restoration rule at {@code restoredAt} (a share, 0..1). */
	public static boolean meetsRestoration(Measure measure, double restoredAt) {
		if (measure.total() == 0 || !measure.missingKeys().isEmpty()) {
			return false;
		}
		double threshold = Math.max(0.0, Math.min(1.0, restoredAt));
		return measure.matched() >= Math.ceil(threshold * measure.total() - 1e-9);
	}

	/**
	 * The state after a finished pass.
	 *
	 * @param before     the building as it was
	 * @param measure    the pass
	 * @param restoredAt the definition's threshold share
	 * @param clearOpen  a clearing job on the building is open (queued, taken or blocked)
	 * @param buildOpen  a building job on it is open
	 */
	public static Verdict afterPass(Before before, Measure measure, double restoredAt, boolean clearOpen,
			boolean buildOpen) {
		boolean meets = meetsRestoration(measure, restoredAt);
		if (meets && (!before.everRestored() || measure.missing() <= before.allowedMissing())) {
			int allowed = before.everRestored() ? Math.min(before.allowedMissing(), measure.missing())
					: measure.missing();
			return new Verdict(BuildingState.INTACT, true, allowed, !before.everRestored());
		}
		return new Verdict(withoutRestoration(before, clearOpen, buildOpen), before.everRestored(),
				before.allowedMissing(), false);
	}

	/**
	 * Whether a pass found a building restored before newly damaged (owner's round 4 of stage 6): it reads
	 * {@link BuildingState#REPAIR} and misses more cells than it did before the pass. The town's drones then mend it by
	 * themselves. A repair that cannot put a cell back (a block no drone makes) leaves the number where it is, so it is
	 * not raised over and over.
	 *
	 * @param before          the building as it was
	 * @param previousMissing the cells it missed before the pass, or -1 when it was never measured
	 * @param verdict         the pass's answer
	 * @param measure         the pass
	 */
	public static boolean newlyDamaged(Before before, int previousMissing, Verdict verdict, Measure measure) {
		return before.everRestored() && verdict.state() == BuildingState.REPAIR && previousMissing >= 0
				&& measure.missing() > previousMissing;
	}

	/**
	 * Whether a pass found a building restored before whole again (owner's round 4): it was not intact before the pass
	 * and is now — and it is no first restoration, which has its own news.
	 */
	public static boolean mended(Before before, BuildingState previous, Verdict verdict) {
		return before.everRestored() && !verdict.justRestored() && verdict.state() == BuildingState.INTACT
				&& previous != BuildingState.INTACT && previous != BuildingState.UPGRADED;
	}

	/**
	 * The state of a building no pass has measured since a change of its jobs — or one with nothing to measure
	 * against, a building without an intact template. Keeps an intact building intact: only a pass can say it lost
	 * a block.
	 */
	public static BuildingState betweenPasses(BuildingState current, Before before, boolean clearOpen,
			boolean buildOpen) {
		if (current == BuildingState.INTACT || current == BuildingState.UPGRADED) {
			return current;
		}
		return withoutRestoration(before, clearOpen, buildOpen);
	}

	private static BuildingState withoutRestoration(Before before, boolean clearOpen, boolean buildOpen) {
		if (before.everRestored()) {
			return BuildingState.REPAIR;
		}
		// The debris goes first (R85): while a clearing job is open the building is being cleared.
		if (clearOpen) {
			return BuildingState.CLEARING;
		}
		return buildOpen ? BuildingState.REPAIR : BuildingState.RUIN;
	}
}
