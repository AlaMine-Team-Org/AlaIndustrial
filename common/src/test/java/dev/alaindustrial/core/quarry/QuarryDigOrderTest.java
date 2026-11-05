package dev.alaindustrial.core.quarry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The quarry's working order (MOD-951): the spec's example, the snake, the layers and the depth limits. */
class QuarryDigOrderTest {

	private static final int TOP = 64;
	private static final int BOTTOM = -64;

	/** The spec's example: 3 cells along the bridge (X), 2 along the paths (Z), from (0, 0). */
	private static QuarryDigOrder specExample(int layers) {
		return new QuarryDigOrder(0, 0, TOP, false, 3, 2, 1, 1, layers, BOTTOM);
	}

	private static List<String> layer(QuarryDigOrder order, int layer) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < order.perLayer(); i++) {
			QuarryDigOrder.Cell cell = order.cell(layer, i);
			out.add("(" + cell.x() + "," + cell.z() + ")");
		}
		return out;
	}

	@Test
	void theSpecsExampleRunsAsASnakeAndTheNextLayerBackwards() {
		QuarryDigOrder order = specExample(2);
		assertEquals(List.of("(0,0)", "(1,0)", "(2,0)", "(2,1)", "(1,1)", "(0,1)"), layer(order, 0));
		assertEquals(List.of("(0,1)", "(1,1)", "(2,1)", "(2,0)", "(1,0)", "(0,0)"), layer(order, 1));
		assertEquals(TOP, order.cell(0, 0).y());
		assertEquals(TOP - 1, order.cell(1, 0).y());
	}

	@Test
	void aLayerStartsWhereTheLastEnded() {
		QuarryDigOrder order = specExample(3);
		for (int layer = 0; layer + 1 < 3; layer++) {
			QuarryDigOrder.Cell last = order.cell(layer, order.perLayer() - 1);
			QuarryDigOrder.Cell first = order.cell(layer + 1, 0);
			assertEquals(last.x(), first.x(), "layer " + layer);
			assertEquals(last.z(), first.z(), "layer " + layer);
		}
	}

	@Test
	void stepsAdvanceThroughTheLayerAndIntoTheNext() {
		QuarryDigOrder order = specExample(2);
		assertArrayEquals(new int[] {0, 1}, order.next(0, 0));
		assertArrayEquals(new int[] {1, 0}, order.next(0, 5));
		assertArrayEquals(new int[] {2, 0}, order.next(1, 5));
		assertTrue(order.done(2, 0));
		assertFalse(order.done(1, 5));
		assertThrows(IllegalArgumentException.class, () -> order.cell(2, 0));
	}

	@Test
	void everyCellOfALayerIsVisitedOnce() {
		QuarryDigOrder order = new QuarryDigOrder(10, -5, TOP, true, 9, 9, -1, 1, 1, BOTTOM);
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (int i = 0; i < order.perLayer(); i++) {
			QuarryDigOrder.Cell cell = order.cell(0, i);
			assertTrue(seen.add(cell.x() + "," + cell.z()), "visited twice: " + cell);
		}
		assertEquals(81, seen.size());
	}

	@Test
	void thePitIsTheFramesInsideAndStartsAtTheControllersCorner() {
		// A 9 x 9 frame from (0, 0) to (14, 14), towers of 3, controller in the north-east tower's head.
		QuarryDigOrder order = QuarryDigOrder.forFrame(0, 0, 14, 14, 3, true, 13, 1, TOP, 24, BOTTOM);
		assertEquals(9, order.spanU());
		assertEquals(9, order.spanT());
		QuarryDigOrder.Cell first = order.cell(0, 0);
		assertEquals(11, first.x(), "the start corner is on the controller's (east) side");
		assertEquals(3, first.z(), "and on its (north) side");
		QuarryDigOrder.Cell second = order.cell(0, 1);
		assertEquals(11, second.x(), "paths along X: the first row runs along the bridge, Z");
		assertEquals(4, second.z());
		QuarryDigOrder.Cell rowTwo = order.cell(0, 9);
		assertEquals(10, rowTwo.x(), "the second row is one step along the paths, into the pit");
		assertEquals(11, rowTwo.z(), "and runs back");
	}

	@Test
	void theTierLimitsTheDepthAndTheWorldBottomLimitsTheRest() {
		assertEquals(24, QuarryDigOrder.forFrame(0, 0, 14, 14, 3, true, 0, 0, TOP, 24, BOTTOM).layers());
		assertEquals(48, QuarryDigOrder.forFrame(0, 0, 14, 14, 3, true, 0, 0, TOP, 48, BOTTOM).layers());
		assertEquals(TOP - BOTTOM + 1, QuarryDigOrder.forFrame(0, 0, 14, 14, 3, true, 0, 0, TOP, 0, BOTTOM).layers(),
				"no tier limit: down to the world's bottom");
		assertEquals(10, QuarryDigOrder.forFrame(0, 0, 14, 14, 3, true, 0, 0, -55, 48, BOTTOM).layers(),
				"a pit near the bottom stops at it");
	}

	@Test
	void layerProgressIsPerMille() {
		QuarryDigOrder order = specExample(1);
		assertEquals(0, order.layerPermille(0));
		assertEquals(500, order.layerPermille(3));
		assertEquals(1000, order.layerPermille(6));
	}

	@Test
	void anEmptyPitOrABadSignIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> new QuarryDigOrder(0, 0, TOP, true, 0, 3, 1, 1, 1, BOTTOM));
		assertThrows(IllegalArgumentException.class, () -> new QuarryDigOrder(0, 0, TOP, true, 3, 3, 2, 1, 1, BOTTOM));
	}
}
