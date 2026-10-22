package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.plan.PlanSheetHints;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The «Plan sheets» section of the settlement core's «Construction» tab (MOD-859, epic decision R92): one row per
 * sheet of the town hall plan, the place to look for it as its line, and whether the town has found it. A sheet is
 * found once it lay in the town's working dispatcher ({@link TownWorks#foundSheets()}); the core reads no other
 * container (ADR-010).
 *
 * <p>Read-only for everybody (R95): the sheets are put together in the dispatcher, not here. A town no structure built
 * (no style) has no sheets to find and shows no section.
 */
public final class PlanSheetRows {

	/** The section heading. */
	public static final String SECTION = "settlement.alaindustrial.works.section.plan_sheets";
	/** The status word of a found sheet. */
	public static final String FOUND = "settlement.alaindustrial.works.sheet.found";
	/** The status word of a sheet still to find. */
	public static final String MISSING = "settlement.alaindustrial.works.sheet.missing";

	/** The keys this section words besides the hints of {@link PlanSheetHints}. */
	static final List<String> KEYS = List.of(SECTION, FOUND, MISSING);

	private PlanSheetRows() {
	}

	static void add(ConstructionRows.Context context, List<ConstructionRow> rows) {
		if (context.town().style().isEmpty()) {
			return;
		}
		rows.addAll(rows(context.works().foundSheets()));
	}

	/** The section's rows for the found sheets {@code mask} (bit 0 — sheet 1): sheet 1, 2, 3 in order. */
	public static List<ConstructionRow> rows(int mask) {
		List<ConstructionRow> rows = new ArrayList<>();
		for (int sheet = 1; sheet <= PlanSheetRules.SHEETS; sheet++) {
			boolean found = PlanSheetRules.found(mask, sheet);
			rows.add(new ConstructionRow(SECTION, ConstructionRow.Text.of(PlanSheetHints.hintKey(sheet)),
					Optional.of(ConstructionRow.Text.of(found ? FOUND : MISSING)), -1,
					found ? ConstructionRow.Tone.GOOD : ConstructionRow.Tone.BAD, Optional.empty(), sheet, 0));
		}
		return rows;
	}
}
