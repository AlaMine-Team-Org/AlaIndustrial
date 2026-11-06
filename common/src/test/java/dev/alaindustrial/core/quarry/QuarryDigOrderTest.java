package dev.alaindustrial.core.quarry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The quarry's working order (MOD-951, MOD-952): 2 x 2 tiles in a snake, layer after layer, the odd side's last tile
 * moved back, the depth limits.
 */
class QuarryDigOrderTest {

	private static final int TOP = 64;
	private static final int BOTTOM = -64;
	private static final float EPS = 1.0E-5F;

	/** The spec's example: 3 cells along the bridge (X), 2 along the paths (Z), from (0, 0). */
	private static QuarryDigOrder specExample(int layers) {
		return new QuarryDigOrder(0, 0, TOP, false, 3, 2, 1, 1, layers, BOTTOM);
	}

	private static List<String> cells(QuarryDigOrder.Tile tile) {
		List<String> out = new ArrayList<>();
		for (QuarryDigOrder.Cell cell : tile.cells()) {
			out.add("(" + cell.x() + "," + cell.z() + ")");
		}
		return out;
	}

	@Test
	void aStepIsATwoByTwoTileAndTheDrillStandsOnItsSharedCorner() {
		QuarryDigOrder order = specExample(1);
		assertEquals(2, order.perLayer(), "3 x 2 cells: two tiles");
		QuarryDigOrder.Tile first = order.tile(0, 0);
		assertEquals(List.of("(0,0)", "(1,0)", "(0,1)", "(1,1)"), cells(first));
		assertEquals(1.0F, first.centreX(), EPS);
		assertEquals(1.0F, first.centreZ(), EPS);
		assertEquals(TOP, first.y());
	}

	@Test
	void theLastTileOfAnOddSideIsMovedBackOverThePreviousOne() {
		QuarryDigOrder.Tile last = specExample(1).tile(0, 1);
		assertEquals(List.of("(1,0)", "(2,0)", "(1,1)", "(2,1)"), cells(last));
		assertEquals(2.0F, last.centreX(), EPS, "one cell from the pit's edge, not half");
	}

	@Test
	void theNextLayerRunsTheTilesBackwards() {
		QuarryDigOrder order = specExample(2);
		assertEquals(cells(order.tile(0, 1)), cells(order.tile(1, 0)), "the next layer starts where the last ended");
		assertEquals(TOP - 1, order.tile(1, 0).y());
		assertEquals(cells(order.tile(0, 0)), cells(order.tile(1, 1)));
	}

	@Test
	void stepsAdvanceThroughTheLayerAndIntoTheNext() {
		QuarryDigOrder order = specExample(2);
		assertArrayEquals(new int[] {0, 1}, order.next(0, 0));
		assertArrayEquals(new int[] {1, 0}, order.next(0, 1));
		assertArrayEquals(new int[] {2, 0}, order.next(1, 1));
		assertTrue(order.done(2, 0));
		assertFalse(order.done(1, 1));
		assertThrows(IllegalArgumentException.class, () -> order.tile(2, 0));
	}

	@Test
	void everyCellOfALayerIsTakenAndTheDrillKeepsOneCellFromTheEdge() {
		// 9 x 9, signs negative along the bridge: five tiles a row, the last moved back.
		QuarryDigOrder order = new QuarryDigOrder(10, -5, TOP, true, 9, 9, -1, 1, 1, BOTTOM);
		assertEquals(25, order.perLayer());
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < order.perLayer(); i++) {
			QuarryDigOrder.Tile tile = order.tile(0, i);
			assertEquals(4, tile.cells().size());
			for (QuarryDigOrder.Cell cell : tile.cells()) {
				seen.add(cell.x() + "," + cell.z());
				assertTrue(Math.abs(cell.x() + 0.5F - tile.centreX()) <= 0.5F + EPS, "x under the drill: " + cell);
				assertTrue(Math.abs(cell.z() + 0.5F - tile.centreZ()) <= 0.5F + EPS, "z under the drill: " + cell);
			}
			// The pit's cells: x 10..18 (paths along X, t), z -5..-13 (u, negative).
			assertTrue(tile.centreX() >= 11.0F - EPS && tile.centreX() <= 18.0F + EPS, "x " + tile.centreX());
			assertTrue(tile.centreZ() <= -5.0F + EPS && tile.centreZ() >= -12.0F - EPS, "z " + tile.centreZ());
		}
		assertEquals(81, seen.size(), "every cell taken");
	}

	@Test
	void aPitOneCellWideStillHasTiles() {
		QuarryDigOrder order = new QuarryDigOrder(0, 0, TOP, false, 1, 3, 1, 1, 1, BOTTOM);
		assertEquals(2, order.perLayer());
		assertEquals(List.of("(0,0)", "(0,1)"), cells(order.tile(0, 0)));
		assertEquals(0.5F, order.tile(0, 0).centreX(), EPS);
		assertEquals(List.of("(0,1)", "(0,2)"), cells(order.tile(0, 1)));
	}

	@Test
	void thePitIsTheFramesInsideAndStartsAtTheControllersCorner() {
		// A 9 x 9 frame from (0, 0) to (14, 14), towers of 3, controller in the north-east tower's head.
		QuarryDigOrder order = QuarryDigOrder.forFrame(0, 0, 14, 14, 3, true, 13, 1, TOP, 24, BOTTOM);
		assertEquals(9, order.spanU());
		assertEquals(9, order.spanT());
		QuarryDigOrder.Tile first = order.tile(0, 0);
		assertEquals(List.of("(11,3)", "(11,4)", "(10,3)", "(10,4)"), cells(first),
				"the start corner on the controller's (north-east) side");
		assertEquals(11.0F, first.centreX(), EPS);
		assertEquals(4.0F, first.centreZ(), EPS);
		QuarryDigOrder.Tile second = order.tile(0, 1);
		assertEquals(6.0F, second.centreZ(), EPS, "paths along X: the first row runs along the bridge, Z");
		assertEquals(11.0F, second.centreX(), EPS);
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
		assertEquals(500, order.layerPermille(1));
		assertEquals(1000, order.layerPermille(2));
	}

	@Test
	void anEmptyPitOrABadSignIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> new QuarryDigOrder(0, 0, TOP, true, 0, 3, 1, 1, 1, BOTTOM));
		assertThrows(IllegalArgumentException.class, () -> new QuarryDigOrder(0, 0, TOP, true, 3, 3, 2, 1, 1, BOTTOM));
	}
}
