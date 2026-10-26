package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 for what a town needs (MOD-886, the town's pulse): each fact gives its need, the needs come most pressing first,
 * and the materials of several blocked jobs read as one list.
 */
class TownNeedsTest {

	private static final String GLASS = "block.minecraft.glass";
	private static final String BRICKS = "block.minecraft.bricks";
	private static final String HOUSE = "settlement.alaindustrial.building.capsule_house.type_2";
	private static final String HALL = "settlement.alaindustrial.building.town_hall";

	private static List<TownNeeds.Kind> kinds(List<TownNeeds.Need> needs) {
		return needs.stream().map(TownNeeds.Need::kind).toList();
	}

	private static List<ConstructionRow.Text> texts(TownNeeds.Facts facts) {
		return TownNeeds.of(facts).stream().map(TownNeeds.Need::text).toList();
	}

	@Test
	void aTownWithNoFactsNeedsNothing() {
		assertEquals(List.of(), TownNeeds.of(TownNeeds.Facts.NONE));
	}

	@Test
	void everyFactGivesItsNeedMostPressingFirst() {
		TownNeeds.Facts facts = new TownNeeds.Facts(true, List.of(List.of(ConstructionRow.Text.counted(6, GLASS))), 2,
				false, List.of(HOUSE), List.of(HALL), 3);
		List<TownNeeds.Need> needs = TownNeeds.of(facts);
		assertEquals(List.of(TownNeeds.Kind.ENERGY, TownNeeds.Kind.MATERIALS, TownNeeds.Kind.HOME,
				TownNeeds.Kind.RESTORE, TownNeeds.Kind.RESTORE, TownNeeds.Kind.WORKPLACE), kinds(needs));
		assertEquals(ConstructionRow.Text.of(TownNeeds.ENERGY), needs.get(0).text());
		assertEquals(new ConstructionRow.Text(TownNeeds.MATERIALS, List.of("6*" + GLASS), true), needs.get(1).text());
		assertEquals(ConstructionRow.Text.of(TownNeeds.HOME, "2"), needs.get(2).text());
		assertEquals(new ConstructionRow.Text(TownNeeds.RESTORE, List.of(HOUSE), true), needs.get(3).text());
		assertEquals(new ConstructionRow.Text(TownNeeds.REPAIR, List.of(HALL), true), needs.get(4).text());
		assertEquals(ConstructionRow.Text.of(TownNeeds.WORKPLACE, "3"), needs.get(5).text());
	}

	@Test
	void theMissingItemIsTheTopNeedOfATownWithPower() {
		TownNeeds.Facts facts = new TownNeeds.Facts(false, List.of(List.of(ConstructionRow.Text.counted(12, BRICKS))),
				0, false, List.of(HOUSE), List.of(), 0);
		List<TownNeeds.Need> needs = TownNeeds.of(facts);
		assertEquals(TownNeeds.Kind.MATERIALS, needs.getFirst().kind());
		assertTrue(needs.getFirst().text().args().contains("12*" + BRICKS), needs.toString());
	}

	@Test
	void aHomelessWorkerWinsOverTheNextWorkersHome() {
		assertEquals(List.of(ConstructionRow.Text.of(TownNeeds.HOME, "1")),
				texts(new TownNeeds.Facts(false, List.of(), 1, true, List.of(), List.of(), 0)));
		assertEquals(List.of(ConstructionRow.Text.of(TownNeeds.HOME_FOR_NEXT)),
				texts(new TownNeeds.Facts(false, List.of(), 0, true, List.of(), List.of(), 0)));
	}

	@Test
	void theMaterialsOfSeveralJobsAreSummedFirstSeenFirst() {
		List<String> merged = TownNeeds.mergeMaterials(List.of(
				List.of(ConstructionRow.Text.counted(6, GLASS), ConstructionRow.Text.counted(2, BRICKS)),
				List.of(ConstructionRow.Text.counted(10, BRICKS), "block.minecraft.oak_door"),
				List.of("block.minecraft.oak_door")));
		assertEquals(List.of("6*" + GLASS, "12*" + BRICKS, "block.minecraft.oak_door"), merged);
	}

	@Test
	void aPlainItemSeenBeforeItsCountIsCountedAfterAll() {
		assertEquals(List.of("3*" + GLASS), TownNeeds.mergeMaterials(List.of(List.of(GLASS),
				List.of(ConstructionRow.Text.counted(3, GLASS)))));
	}

	@Test
	void theMaterialListNamesAtMostItsLimit() {
		List<String> many = new ArrayList<>();
		for (int i = 0; i < TownNeeds.MAX_ITEMS + 3; i++) {
			many.add(ConstructionRow.Text.counted(1, "item.minecraft.thing_" + i));
		}
		List<String> merged = TownNeeds.mergeMaterials(List.of(many));
		assertEquals(TownNeeds.MAX_ITEMS, merged.size());
		assertEquals("1*item.minecraft.thing_0", merged.getFirst());
	}

	@Test
	void aBuildingNamedTwiceIsListedOnce() {
		assertEquals(List.of(new ConstructionRow.Text(TownNeeds.RESTORE, List.of(HOUSE, HALL), true)),
				texts(new TownNeeds.Facts(false, List.of(), 0, false, List.of(HOUSE, HOUSE, HALL), List.of(), 0)));
	}

	@Test
	void rankKeepsTheOrderWithinAKind() {
		TownNeeds.Need repair = new TownNeeds.Need(TownNeeds.Kind.RESTORE, ConstructionRow.Text.of(TownNeeds.REPAIR));
		TownNeeds.Need restore = new TownNeeds.Need(TownNeeds.Kind.RESTORE,
				ConstructionRow.Text.of(TownNeeds.RESTORE));
		TownNeeds.Need energy = new TownNeeds.Need(TownNeeds.Kind.ENERGY, ConstructionRow.Text.of(TownNeeds.ENERGY));
		assertEquals(List.of(energy, repair, restore), TownNeeds.rank(List.of(repair, restore, energy)));
	}
}
