package dev.alaindustrial.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RecipeViewerLayoutTest {

	@Test
	void slotPositionsAreOrderedAndSharedForOneOrTwoSlots() {
		assertEquals(java.util.List.of(38), RecipeViewerLayout.inputXs(1));
		assertEquals(java.util.List.of(27, 49), RecipeViewerLayout.inputXs(2));
		assertEquals(java.util.List.of(95), RecipeViewerLayout.outputXs(1));
		assertEquals(java.util.List.of(84, 106), RecipeViewerLayout.outputXs(2));
	}

	@Test
	void slotCountsOutsideTheSupportedContractFailLoudly() {
		assertThrows(IllegalArgumentException.class, () -> RecipeViewerLayout.inputXs(0));
		assertThrows(IllegalArgumentException.class, () -> RecipeViewerLayout.inputXs(3));
		assertThrows(IllegalArgumentException.class, () -> RecipeViewerLayout.outputXs(0));
		assertThrows(IllegalArgumentException.class, () -> RecipeViewerLayout.outputXs(3));
	}

	@Test
	void labelsHaveOneLoaderNeutralFormat() {
		assertEquals("400 EU · 10 s", RecipeViewerLayout.costLabel(400, 200));
		assertEquals("401 EU · 10.1 s", RecipeViewerLayout.costLabel(401, 201));
		assertEquals("400 EU · 10 s · 1000 mB", RecipeViewerLayout.fluidCostLabel(400, 200, 1000));
		assertEquals("400 EU · 10 s · 45 %",
				RecipeViewerLayout.withChance(RecipeViewerLayout.costLabel(400, 200), 0.45));
		assertEquals("400 EU · 10 s",
				RecipeViewerLayout.withChance(RecipeViewerLayout.costLabel(400, 200), -1.0));
	}
}
