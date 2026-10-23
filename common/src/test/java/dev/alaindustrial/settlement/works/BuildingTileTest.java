package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** L1: the card data of the core's «Construction» tab (round 3 of 0.1.219, R3-3). */
class BuildingTileTest {

	@Test
	void theIconIsTheStyleAndTheBuildingOfATownTemplate() {
		assertEquals("plains/capsule_house_2", BuildingTile.iconOf("ala_town/plains/intact/capsule_house_2"));
		assertEquals("desert/warehouse", BuildingTile.iconOf("ala_town/desert/warehouse"));
		assertEquals("", BuildingTile.iconOf("lab/plains/warehouse"), "a template outside the towns' folder");
		assertEquals("", BuildingTile.iconOf("ala_town/warehouse"), "a path too short to hold a style");
	}

	@Test
	void aChipReadsBackByItsOrdinalAndAnUnknownOneIsARuin() {
		for (BuildingTile.Chip chip : BuildingTile.Chip.values()) {
			assertEquals(chip, BuildingTile.Chip.byOrdinal(chip.ordinal()));
		}
		assertEquals(BuildingTile.Chip.RUIN, BuildingTile.Chip.byOrdinal(-1));
		assertEquals(BuildingTile.Chip.RUIN, BuildingTile.Chip.byOrdinal(BuildingTile.Chip.values().length));
		assertEquals("settlement.alaindustrial.works.chip.no_plan", BuildingTile.Chip.NO_PLAN.key());
		assertEquals("settlement.alaindustrial.works.section.house", BuildingTile.Group.HOUSE.key());
	}

	@Test
	void theJobsAreACopy() {
		List<Integer> jobs = new ArrayList<>(List.of(3, 4));
		BuildingTile tile = new BuildingTile(1, "", BuildingTile.Group.OTHER, BuildingTile.Chip.DONE,
				Optional.empty(), jobs);
		jobs.add(5);
		assertEquals(List.of(3, 4), tile.jobs());
		assertThrows(NullPointerException.class, () -> new BuildingTile(1, null, BuildingTile.Group.OTHER,
				BuildingTile.Chip.DONE, Optional.empty(), List.of()));
	}
}
