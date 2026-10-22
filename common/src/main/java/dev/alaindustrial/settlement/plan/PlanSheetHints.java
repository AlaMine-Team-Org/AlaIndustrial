package dev.alaindustrial.settlement.plan;

/**
 * Where each town hall plan sheet lies, as the core tells the player (MOD-859, R92): the hint line per sheet the
 * core's «Construction» tab shows next to a missing sheet (MOD-857), and the journal line the core writes when it
 * wakes. Translation keys only; no Minecraft types, so the tab and a plain test read the same strings.
 */
public final class PlanSheetHints {

	/** Journal line when the core wakes: the town hall can be restored, look for its plan. */
	public static final String JOURNAL_CORE_AWAKE = "settlement.alaindustrial.journal.core_awake_plan";

	/** Prefix of the per-sheet hint keys; the full key is this plus the sheet number. */
	public static final String HINT_PREFIX = "settlement.alaindustrial.plan.hint.";

	private PlanSheetHints() {
	}

	/**
	 * The hint key of sheet {@code sheet}: 1 — in a chest of the town hall, 2 — in the ruins of a house, 3 — in the
	 * lab under the town hall.
	 *
	 * @throws IllegalArgumentException for a number outside 1..{@value PlanSheetRules#SHEETS}
	 */
	public static String hintKey(int sheet) {
		if (sheet < 1 || sheet > PlanSheetRules.SHEETS) {
			throw new IllegalArgumentException("no plan sheet " + sheet);
		}
		return HINT_PREFIX + sheet;
	}
}
