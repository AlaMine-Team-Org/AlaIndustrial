package dev.alaindustrial.settlement.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.plan.PlanSheetRules.Reason;
import dev.alaindustrial.settlement.plan.PlanSheetRules.Result;
import dev.alaindustrial.settlement.plan.PlanSheetRules.Sheet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 for the town hall plan sheets (MOD-859; epic MOD-824, R83, R92): three sheets of one style make the plan of
 * that style's intact hall, in any order and from any towns of the style; every other set is refused with the first
 * reason that applies; and the place of each sheet has its hint.
 *
 * @implements MOD-859-RULES
 */
class PlanSheetRulesTest {

	private static final String PLAINS = "alaindustrial:plains";
	private static final String DESERT = "alaindustrial:desert";

	private static List<Sheet> three(String style) {
		return List.of(new Sheet(style, 1), new Sheet(style, 2), new Sheet(style, 3));
	}

	@Test
	void threeSheetsOfOneStyleMakeThePlanOfItsIntactHall() {
		Result result = PlanSheetRules.assemble(three(PLAINS), PLAINS);
		assertTrue(result.ok());
		assertNull(result.reasonKey());
		assertEquals(PLAINS, result.plan().style());
		assertEquals("alaindustrial:ala_town/plains/intact/town_hall", result.plan().template());
	}

	@Test
	void orderDoesNotMatterAndTheStyleNotTheTownOwnsThePlan() {
		// Sheets of the same style are the same whichever town they came from: the rules see only style and number.
		List<Sheet> shuffled = List.of(new Sheet(DESERT, 3), new Sheet(DESERT, 1), new Sheet(DESERT, 2));
		Result result = PlanSheetRules.assemble(shuffled, DESERT);
		assertTrue(result.ok());
		assertEquals("alaindustrial:ala_town/desert/intact/town_hall", result.plan().template());
	}

	@Test
	void aStyleFromADatapackPointsIntoItsOwnNamespace() {
		Result result = PlanSheetRules.assemble(three("ala_town_test:ruin"), "ala_town_test:ruin");
		assertEquals("ala_town_test:ala_town/ruin/intact/town_hall", result.plan().template());
		assertEquals("ala_town_test:chests/ala_town/ruin/plan_sheet_2", PlanSheetRules.lootTable("ala_town_test:ruin",
				2));
	}

	@Test
	void anUnknownSettlementStyleSkipsOnlyTheStyleCheck() {
		assertTrue(PlanSheetRules.assemble(three(PLAINS), null).ok());
	}

	@Test
	void theWrongStyleIsRefusedWithItsReason() {
		Result result = PlanSheetRules.assemble(three(DESERT), PLAINS);
		assertFalse(result.ok());
		assertEquals(Reason.WRONG_STYLE, result.reason());
		assertEquals("settlement.alaindustrial.plan.reason.wrong_style", result.reasonKey());
	}

	@Test
	void mixedStylesAreRefused() {
		List<Sheet> mixed = List.of(new Sheet(PLAINS, 1), new Sheet(DESERT, 2), new Sheet(PLAINS, 3));
		assertEquals(Reason.MIXED_STYLES, PlanSheetRules.assemble(mixed, PLAINS).reason());
	}

	@Test
	void aMissingSheetIsNamed() {
		Result result = PlanSheetRules.assemble(List.of(new Sheet(PLAINS, 3), new Sheet(PLAINS, 1)), PLAINS);
		assertEquals(Reason.MISSING_SHEET, result.reason());
		assertEquals(List.of(2), result.missing());
		Result one = PlanSheetRules.assemble(List.of(new Sheet(PLAINS, 2)), PLAINS);
		assertEquals(List.of(1, 3), one.missing());
	}

	@Test
	void aRepeatedSheetIsRefusedEvenWithAllThreeThere() {
		List<Sheet> four = List.of(new Sheet(PLAINS, 1), new Sheet(PLAINS, 2), new Sheet(PLAINS, 2),
				new Sheet(PLAINS, 3));
		assertEquals(Reason.DUPLICATE_SHEET, PlanSheetRules.assemble(four, PLAINS).reason());
	}

	@Test
	void blankAndOutOfRangeSheetsAreUnmarked() {
		assertEquals(Reason.UNMARKED_SHEET, PlanSheetRules.assemble(
				List.of(new Sheet(null, 1), new Sheet(PLAINS, 2), new Sheet(PLAINS, 3)), PLAINS).reason());
		assertEquals(Reason.UNMARKED_SHEET, PlanSheetRules.assemble(
				List.of(new Sheet(PLAINS, 0), new Sheet(PLAINS, 2), new Sheet(PLAINS, 3)), PLAINS).reason());
		assertEquals(Reason.UNMARKED_SHEET, PlanSheetRules.assemble(
				List.of(new Sheet(PLAINS, 4), new Sheet(PLAINS, 2), new Sheet(PLAINS, 3)), PLAINS).reason());
	}

	@Test
	void noSheetsIsItsOwnReason() {
		assertEquals(Reason.NO_SHEETS, PlanSheetRules.assemble(List.of(), PLAINS).reason());
	}

	@Test
	void reasonsComeInTheirFixedOrder() {
		// A blank beats mixed styles; mixed styles beat a missing sheet; a missing sheet beats the wrong style.
		assertEquals(Reason.UNMARKED_SHEET, PlanSheetRules.assemble(
				List.of(new Sheet(null, 1), new Sheet(DESERT, 2)), PLAINS).reason());
		assertEquals(Reason.MIXED_STYLES, PlanSheetRules.assemble(
				List.of(new Sheet(PLAINS, 1), new Sheet(DESERT, 2)), PLAINS).reason());
		assertEquals(Reason.MISSING_SHEET, PlanSheetRules.assemble(
				List.of(new Sheet(DESERT, 1), new Sheet(DESERT, 2)), PLAINS).reason());
	}

	@Test
	void everyReasonHasItsOwnKey() {
		Set<String> keys = new HashSet<>();
		for (Reason reason : Reason.values()) {
			assertTrue(reason.key().startsWith(PlanSheetRules.REASON_PREFIX));
			assertTrue(keys.add(reason.key()), reason + " shares its key");
		}
	}

	@Test
	void lootTablesFollowTheStyle() {
		assertEquals("alaindustrial:chests/ala_town/plains/plan_sheet_1", PlanSheetRules.lootTable(PLAINS, 1));
		assertEquals("alaindustrial:chests/ala_town/desert/plan_sheet_3", PlanSheetRules.lootTable(DESERT, 3));
	}

	@Test
	void theHousePickIsStableInRangeAndSpreads() {
		Set<Integer> picked = new HashSet<>();
		for (int town = 0; town < 64; town++) {
			int index = PlanSheetRules.houseIndex(830L, town * 512, 64, -town * 300, 4);
			assertTrue(index >= 0 && index < 4);
			assertEquals(index, PlanSheetRules.houseIndex(830L, town * 512, 64, -town * 300, 4));
			picked.add(index);
		}
		assertEquals(4, picked.size(), "64 towns should reach every one of four houses");
		assertEquals(0, PlanSheetRules.houseIndex(1L, 2, 3, 4, 1));
		assertThrows(IllegalArgumentException.class, () -> PlanSheetRules.houseIndex(1L, 2, 3, 4, 0));
	}

	@Test
	void everySheetHasAHint() {
		assertEquals("settlement.alaindustrial.plan.hint.1", PlanSheetHints.hintKey(1));
		assertEquals("settlement.alaindustrial.plan.hint.3", PlanSheetHints.hintKey(3));
		assertThrows(IllegalArgumentException.class, () -> PlanSheetHints.hintKey(0));
		assertThrows(IllegalArgumentException.class, () -> PlanSheetHints.hintKey(4));
	}

	/** The creative tab lists the sheets of exactly the styles the mod ships (owner's round 2). */
	@Test
	void builtInStylesAreTheShippedStyleFiles() throws IOException {
		Path dir = Path.of("src/main/resources/data/alaindustrial/alaindustrial/settlement_style");
		Set<String> shipped = new HashSet<>();
		try (var files = Files.list(dir)) {
			files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json"))
					.forEach(name -> shipped.add("alaindustrial:" + name.substring(0, name.length() - 5)));
		}
		assertEquals(shipped, new HashSet<>(PlanSheetRules.BUILT_IN_STYLES));
	}
}
