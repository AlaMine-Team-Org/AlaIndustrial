package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 for the pictures of the town's needs (MOD-890): energy, the first missing item, a home in the town's style, the
 * building a restoration names, a workplace; and the cell an «In the way» reason names, read back for «show it».
 */
class NeedIconTest {

	private static final String GLASS = "block.minecraft.glass";
	private static final String HOUSE = "settlement.alaindustrial.building.capsule_house.type_2";
	private static final String HALL = "settlement.alaindustrial.building.town_hall";
	private static final Map<String, String> BUILDINGS = Map.of(HOUSE, "desert/capsule_house_2",
			HALL, "desert/town_hall");

	private static List<String> icons(TownNeeds.Facts facts) {
		String style = NeedIcon.style(BUILDINGS.values());
		return TownNeeds.of(facts).stream().map(need -> NeedIcon.of(need, BUILDINGS, style)).toList();
	}

	@Test
	void everyNeedCarriesThePictureOfWhatItIsAbout() {
		TownNeeds.Facts facts = new TownNeeds.Facts(true, List.of(List.of(ConstructionRow.Text.counted(6, GLASS),
				"item.minecraft.stick")), 2, false, List.of(HOUSE), List.of(HALL), 3);
		assertEquals(List.of(NeedIcon.ENERGY, NeedIcon.ITEM + GLASS, NeedIcon.BUILDING + "desert/capsule_house_1",
				NeedIcon.BUILDING + "desert/capsule_house_2", NeedIcon.BUILDING + "desert/town_hall",
				NeedIcon.ITEM + NeedIcon.WORKPLACE_ITEM), icons(facts));
	}

	@Test
	void aBuildingOfNoPictureAndATownOfNoBuildingFallBack() {
		TownNeeds.Facts facts = new TownNeeds.Facts(false, List.of(), 0, true,
				List.of("settlement.alaindustrial.building.unnamed"), List.of(), 0);
		List<TownNeeds.Need> needs = TownNeeds.of(facts);
		assertEquals(NeedIcon.BUILDING + "plains/" + NeedIcon.HOUSE,
				NeedIcon.of(needs.get(0), Map.of(), NeedIcon.style(List.of())));
		assertEquals("", NeedIcon.of(needs.get(1), Map.of(), NeedIcon.DEFAULT_STYLE),
				"a building with no picture draws none rather than a wrong one");
	}

	@Test
	void anObstructionNamesItsCellAndNothingElseDoes() {
		BlockReason reason = BlockReason.obstructed("block.alaindustrial.town_dispatcher", 871, 64, -4120);
		assertArrayEquals(new int[] {871, 64, -4120},
				BlockReason.obstructedCell(reason.key(), reason.args()).orElseThrow());
		BlockReason noRoute = BlockReason.noRoute(1, 2, 3);
		assertTrue(BlockReason.obstructedCell(noRoute.key(), noRoute.args()).isEmpty());
		assertTrue(BlockReason.cell("12 x 5").isEmpty());
		assertTrue(BlockReason.cell("12 5").isEmpty());
	}
}
