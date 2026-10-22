package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.plan.PlanSheetHints;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import dev.alaindustrial.settlement.plan.PlanSheetRules.Sheet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 for the «Plan sheets» section of the core's «Construction» tab (MOD-859, R92): which sheets lying in a
 * dispatcher a town finds, and the three rows the tab shows for them — the place to look as the line, found or
 * missing as the status, nobody's controls.
 *
 * @implements MOD-859-RULES
 */
class PlanSheetRowsTest {

	private static final String PLAINS = "alaindustrial:plains";
	private static final String DESERT = "alaindustrial:desert";

	@Test
	void onlyMarkedSheetsOfTheTownsStyleAreFound() {
		int mask = PlanSheetRules.foundMask(List.of(new Sheet(PLAINS, 1), new Sheet(DESERT, 2), new Sheet(null, 0),
				new Sheet(PLAINS, 3)), PLAINS);
		assertEquals(0b101, mask);
		assertTrue(PlanSheetRules.found(mask, 1));
		assertFalse(PlanSheetRules.found(mask, 2));
		assertTrue(PlanSheetRules.found(mask, 3));
		assertFalse(PlanSheetRules.found(mask, 4));
		assertEquals(0, PlanSheetRules.foundMask(List.of(new Sheet(PLAINS, 4), new Sheet("", 1)), PLAINS));
		assertEquals(0b010, PlanSheetRules.foundMask(List.of(new Sheet(DESERT, 2)), ""),
				"a town of no style counts a marked sheet of any style");
	}

	@Test
	void theSectionShowsEverySheetWithItsPlaceFoundOrMissing() {
		List<ConstructionRow> rows = PlanSheetRows.rows(0b010);
		assertEquals(PlanSheetRules.SHEETS, rows.size());
		for (int i = 0; i < rows.size(); i++) {
			ConstructionRow row = rows.get(i);
			int sheet = i + 1;
			boolean found = sheet == 2;
			assertEquals(PlanSheetRows.SECTION, row.section());
			assertEquals(PlanSheetHints.hintKey(sheet), row.text().key(), "the line is where to look for the sheet");
			assertEquals(found ? PlanSheetRows.FOUND : PlanSheetRows.MISSING, row.status().orElseThrow().key());
			assertEquals(found ? ConstructionRow.Tone.GOOD : ConstructionRow.Tone.BAD, row.tone());
			assertEquals(0, row.actions(), "the sheets are read-only for everybody");
		}
	}

	@Test
	void allFoundIsEveryRowGreen() {
		assertTrue(PlanSheetRows.rows(PlanSheetRules.ALL_FOUND).stream()
				.allMatch(row -> row.tone() == ConstructionRow.Tone.GOOD));
	}
}
