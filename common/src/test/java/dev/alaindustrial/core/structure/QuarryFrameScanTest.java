package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.structure.QuarryFrameScan.Kind;
import dev.alaindustrial.core.structure.QuarryFrameScan.Limits;
import dev.alaindustrial.core.structure.QuarryFrameScan.Result;
import dev.alaindustrial.core.structure.QuarryFrameScan.Status;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link QuarryFrameScan} (MOD-950) — every rule of the portal frame on the designer's 3 × 3 grid, each
 * with the position the player is sent to. A scanner that answered "not formed" to all of them would pass a naive test
 * and be useless in game, so every failing case asserts the coordinates too.
 */
class QuarryFrameScanTest {

	private static final Limits LIMITS = new Limits(5, new int[] {9, 17, 33});

	private static final int T = QuarryFrameScan.TOWER;

	/** The bottom foundation layer at y 1, the bottom head layer six above it. */
	private static final int HEAD_Y = 1 + QuarryFrameScan.HEAD_HEIGHT;

	/** The upper path layer, where the bridge lies. */
	private static final int TOP = HEAD_Y + QuarryFrameScan.PATH_LAYERS - 1;

	/**
	 * A complete frame in a {@link HashMap}: the controller's tower with its north-west corner at {@code (0, 0)} and
	 * the controller in that very corner of its head, the other towers past {@code spanX} and {@code spanZ} cells,
	 * paths along X (or Z) as wide as the heads and two layers tall, the bridge between them in the upper path layer at
	 * the middle of the paths and the cart in the middle of the bridge.
	 */
	private static final class Frame {
		private final Map<Long, Kind> cells = new HashMap<>();
		final int ox;
		final int oz;
		final int bridgeT;
		final boolean alongX;

		Frame(int spanX, int spanZ, boolean alongX) {
			this.ox = T + spanX;
			this.oz = T + spanZ;
			this.alongX = alongX;
			tower(0, 0);
			tower(ox, 0);
			tower(0, oz);
			tower(ox, oz);
			put(0, HEAD_Y, 0, Kind.CONTROLLER);
			int low = T;
			int high = (alongX ? ox : oz) - 1;
			Kind rail = alongX ? Kind.RAIL_X : Kind.RAIL_Z;
			int far = alongX ? oz : ox;
			for (int y = HEAD_Y; y <= TOP; y++) {
				for (int t = low; t <= high; t++) {
					for (int row : new int[] {0, 1, 2, far, far + 1, far + 2}) {
						put(alongX ? t : row, y, alongX ? row : t, rail);
					}
				}
			}
			this.bridgeT = (low + high) / 2;
			Kind bridge = alongX ? Kind.BRIDGE_Z : Kind.BRIDGE_X;
			for (int u = T; u < far; u++) {
				put(alongX ? bridgeT : u, TOP, alongX ? u : bridgeT, bridge);
			}
			for (int u = 0; u <= far + 2; u++) {
				if (u > T && u < far - 1) {
					continue;
				}
				put(alongX ? bridgeT : u, TOP + 1, alongX ? u : bridgeT, bridge);
			}
			int middle = (far + 2) / 2;
			put(alongX ? bridgeT : middle, TOP + 1, alongX ? middle : bridgeT, Kind.CART);
		}

		Frame(int span) {
			this(span, span, true);
		}

		void tower(int x0, int z0) {
			for (int dx = 0; dx < T; dx++) {
				for (int dz = 0; dz < T; dz++) {
					put(x0 + dx, 1, z0 + dz, Kind.FOUNDATION_1);
					put(x0 + dx, 2, z0 + dz, Kind.FOUNDATION_1);
					for (int layer = 0; layer < QuarryFrameScan.HEAD_LAYERS; layer++) {
						put(x0 + dx, HEAD_Y + layer, z0 + dz, Kind.HEAD);
					}
				}
			}
			for (int y = 3; y < HEAD_Y; y++) {
				for (int dx = 0; dx < T; dx++) {
					for (int dz = 0; dz < T; dz++) {
						put(x0 + dx, y, z0 + dz, Kind.SCAFFOLD);
					}
				}
			}
		}

		void foundations(int x0, int z0, Kind kind) {
			for (int dx = 0; dx < T; dx++) {
				for (int dz = 0; dz < T; dz++) {
					put(x0 + dx, 1, z0 + dz, kind);
					put(x0 + dx, 2, z0 + dz, kind);
				}
			}
		}

		Frame put(int x, int y, int z, Kind kind) {
			cells.put(key(x, y, z), kind);
			return this;
		}

		Frame clear(int x, int y, int z) {
			cells.remove(key(x, y, z));
			return this;
		}

		Result scanFrom(int x, int z) {
			return scanAt(x, HEAD_Y, z);
		}

		Result scanAt(int x, int y, int z) {
			return QuarryFrameScan.scan((px, py, pz) -> cells.getOrDefault(key(px, py, pz), Kind.OTHER),
					x, y, z, LIMITS);
		}

		Result scan() {
			return scanFrom(0, 0);
		}

		private static long key(int x, int y, int z) {
			return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF);
		}
	}

	private static void assertAt(Result result, Status status, int x, int y, int z) {
		assertEquals(status, result.status(), "status");
		assertEquals(x, result.x(), "x of " + status);
		assertEquals(y, result.y(), "y of " + status);
		assertEquals(z, result.z(), "z of " + status);
	}

	@Test
	void aCompleteFrameIsFormedWithItsSpansBoundsAndTier() {
		Result result = new Frame(9).scan();
		assertTrue(result.formed(), () -> "got " + result.status() + " at " + result.x() + "," + result.y() + ","
				+ result.z());
		assertEquals(9, result.spanX());
		assertEquals(9, result.spanZ());
		assertEquals(1, result.tier());
		assertEquals(9, result.limit());
		assertTrue(result.pathsAlongX());
		assertEquals(0, result.minX());
		assertEquals(0, result.minZ());
		assertEquals(14, result.maxX());
		assertEquals(14, result.maxZ());
		Frame frame = new Frame(9);
		assertEquals(frame.bridgeT, result.bridgeAt());
		assertEquals((frame.oz + 2) / 2, result.cartAt());
	}

	@Test
	void aFrameNeedNotBeSquare() {
		Result result = new Frame(9, 5, true).scan();
		assertTrue(result.formed(), () -> "got " + result.status());
		assertEquals(9, result.spanX());
		assertEquals(5, result.spanZ());
	}

	@Test
	void pathsMayRunAlongZWithTheBridgeAlongX() {
		Result result = new Frame(5, 7, false).scan();
		assertTrue(result.formed(), () -> "got " + result.status());
		assertFalse(result.pathsAlongX());
	}

	@Test
	void theControllerMayHeadAnyCornerTowerOnAnyEdgeCell() {
		Frame frame = new Frame(5);
		frame.put(0, HEAD_Y, 0, Kind.HEAD).put(frame.ox + 2, HEAD_Y, frame.oz + 1, Kind.CONTROLLER);
		Result result = frame.scanFrom(frame.ox + 2, frame.oz + 1);
		assertTrue(result.formed(), () -> "got " + result.status());
	}

	@Test
	void aControllerInTheMiddleOfItsHeadIsRefused() {
		Frame frame = new Frame(5);
		frame.put(0, HEAD_Y, 0, Kind.HEAD).put(1, HEAD_Y, 1, Kind.CONTROLLER);
		assertAt(frame.scanFrom(1, 1), Status.CONTROLLER_NOT_ON_EDGE, 1, HEAD_Y, 1);
	}

	@Test
	void aHeadOfTheWrongSizeIsNotATower() {
		assertEquals(Status.CONTROLLER_NOT_ON_TOWER, new Frame(5).clear(2, HEAD_Y, 0).scan().status());
	}

	@Test
	void aMissingHeadCellUnderTheControllersRowIsReportedAtThatCell() {
		assertAt(new Frame(5).clear(2, HEAD_Y, 2).scan(), Status.CONTROLLER_NOT_ON_TOWER, 2, HEAD_Y, 2);
	}

	@Test
	void theTopHeadLayerIsReadFirst() {
		assertAt(new Frame(5).clear(1, HEAD_Y + 2, 1).scan(), Status.CONTROLLER_NOT_ON_TOWER, 1, HEAD_Y + 2, 1);
	}

	@Test
	void aControllerAboveTheBottomHeadLayerIsNotOnItsTower() {
		Frame frame = new Frame(5);
		frame.put(0, HEAD_Y, 0, Kind.HEAD).put(0, HEAD_Y + 1, 0, Kind.CONTROLLER);
		assertAt(frame.scanAt(0, HEAD_Y + 1, 0), Status.CONTROLLER_NOT_ON_TOWER, 0, HEAD_Y + 1, 0);
	}

	@Test
	void aMissingScaffoldUnderTheControllersHeadIsReportedAtThatCell() {
		assertAt(new Frame(5).clear(2, 4, 1).scan(), Status.CONTROLLER_NOT_ON_TOWER, 2, 4, 1);
	}

	@Test
	void aMissingFoundationUnderTheControllersHeadIsReportedAtTheGround() {
		assertAt(new Frame(5).clear(2, 1, 2).scan(), Status.CONTROLLER_NOT_ON_TOWER, 2, 1, 2);
	}

	@Test
	void theCornerOppositeTheControllerIsNamedWhenItsTowerIsMissing() {
		Frame frame = new Frame(5);
		frame.clear(frame.ox, HEAD_Y, frame.oz);
		assertAt(frame.scan(), Status.TOWERS_NOT_FOUND, frame.ox, HEAD_Y, frame.oz);
	}

	@Test
	void aLoneTowerPointsAtTheControllerItself() {
		Frame frame = new Frame(5);
		for (int[] corner : new int[][] {{frame.ox, 0}, {0, frame.oz}}) {
			for (int dx = 0; dx < T; dx++) {
				for (int dz = 0; dz < T; dz++) {
					frame.clear(corner[0] + dx, HEAD_Y, corner[1] + dz);
				}
			}
		}
		assertAt(frame.scan(), Status.TOWERS_NOT_FOUND, 0, HEAD_Y, 0);
	}

	@Test
	void anIncompleteOtherTowerIsReportedAtItsFirstWrongCell() {
		Frame frame = new Frame(5);
		assertAt(frame.clear(frame.ox + 2, 5, frame.oz + 1).scan(), Status.TOWER_INCOMPLETE,
				frame.ox + 2, 5, frame.oz + 1);
		Frame other = new Frame(5);
		assertAt(other.put(0, 2, other.oz + 2, Kind.SCAFFOLD).scan(), Status.TOWER_INCOMPLETE, 0, 2, other.oz + 2);
	}

	@Test
	void theWeakestFoundationSetsTheTier() {
		Frame frame = new Frame(5);
		frame.foundations(0, 0, Kind.FOUNDATION_3);
		frame.foundations(frame.ox, 0, Kind.FOUNDATION_3);
		frame.foundations(0, frame.oz, Kind.FOUNDATION_3);
		frame.foundations(frame.ox, frame.oz, Kind.FOUNDATION_3);
		frame.put(frame.ox + 1, 2, frame.oz + 2, Kind.FOUNDATION_2);
		Result result = frame.scan();
		assertTrue(result.formed(), () -> "got " + result.status());
		assertEquals(2, result.tier());
		assertEquals(17, result.limit());
	}

	@Test
	void aSpanBelowTheMinimumIsTooSmall() {
		Result result = new Frame(9, 4, true).scan();
		assertAt(result, Status.TOO_SMALL, 0, HEAD_Y, 0);
		assertEquals(4, result.spanZ());
	}

	@Test
	void aSpanAboveTheWeakestTierIsTooLargeAndAStrongerTierAcceptsIt() {
		Frame frame = new Frame(10, 5, true);
		Result tooLarge = frame.scan();
		assertAt(tooLarge, Status.TOO_LARGE, 0, HEAD_Y, 0);
		assertEquals(1, tooLarge.tier());
		assertEquals(9, tooLarge.limit());
		assertEquals(10, tooLarge.spanX());
		frame.foundations(0, 0, Kind.FOUNDATION_2);
		frame.foundations(frame.ox, 0, Kind.FOUNDATION_2);
		frame.foundations(0, frame.oz, Kind.FOUNDATION_2);
		frame.foundations(frame.ox, frame.oz, Kind.FOUNDATION_2);
		assertTrue(frame.scan().formed());
	}

	@Test
	void aMissingRailIsReportedWhereThePathBreaks() {
		Frame frame = new Frame(5);
		assertAt(frame.clear(6, HEAD_Y, frame.oz + 2).scan(), Status.PATH_BROKEN, 6, HEAD_Y, frame.oz + 2);
	}

	@Test
	void aRailLyingAcrossItsPathBreaksThePathAtThatBeam() {
		assertAt(new Frame(5).put(5, HEAD_Y, 2, Kind.RAIL_Z).scan(), Status.PATH_BROKEN, 5, HEAD_Y, 2);
	}

	@Test
	void aGapInTheUpperPathLayerBreaksThePath() {
		assertAt(new Frame(5).clear(6, TOP, 1).scan(), Status.PATH_BROKEN, 6, TOP, 1);
	}

	@Test
	void aPathNarrowerThanTheHeadsIsBroken() {
		Frame frame = new Frame(5);
		for (int x = T; x < frame.ox; x++) {
			frame.clear(x, HEAD_Y, 0);
		}
		assertAt(frame.scan(), Status.PATH_BROKEN, T, HEAD_Y, 0);
	}

	@Test
	void noBridgeOnThePathsIsReported() {
		Frame frame = new Frame(5);
		for (int z = T; z < frame.oz; z++) {
			frame.clear(frame.bridgeT, TOP, z);
		}
		assertEquals(Status.NO_BRIDGE, frame.scan().status());
	}

	@Test
	void aBridgeThatStopsShortIsReportedAtTheGap() {
		Frame frame = new Frame(5);
		assertAt(frame.clear(frame.bridgeT, TOP, 6).scan(), Status.BRIDGE_BROKEN, frame.bridgeT, TOP, 6);
	}

	@Test
	void aBridgeBeamLyingAlongThePathsBreaksTheBridge() {
		Frame frame = new Frame(5);
		frame.put(frame.bridgeT, TOP, 4, Kind.BRIDGE_X);
		assertAt(frame.scan(), Status.BRIDGE_BROKEN, frame.bridgeT, TOP, 4);
	}

	@Test
	void aSecondBridgeIsReported() {
		Frame frame = new Frame(5);
		frame.put(T, TOP, T, Kind.BRIDGE_Z);
		assertAt(frame.scan(), Status.SECOND_BRIDGE, frame.bridgeT, TOP, T);
	}

	@Test
	void aSaddleMissingABeamBreaksTheBridge() {
		Frame frame = new Frame(5);
		assertAt(frame.clear(frame.bridgeT, TOP + 1, 1).scan(), Status.BRIDGE_BROKEN, frame.bridgeT, TOP + 1, 1);
		Frame inner = new Frame(5);
		assertAt(inner.clear(inner.bridgeT, TOP + 1, inner.oz - 1).scan(), Status.BRIDGE_BROKEN, inner.bridgeT, TOP + 1,
				inner.oz - 1);
	}

	@Test
	void aCartInASaddleIsABrokenSaddleNotACart() {
		Frame frame = new Frame(5);
		frame.clear(frame.bridgeT, TOP + 1, (frame.oz + 2) / 2).put(frame.bridgeT, TOP + 1, T, Kind.CART);
		assertAt(frame.scan(), Status.BRIDGE_BROKEN, frame.bridgeT, TOP + 1, T);
	}

	@Test
	void aBridgeWithNoCartIsReportedAboveItsMiddle() {
		Frame frame = new Frame(5);
		int middle = (frame.oz + 2) / 2;
		assertAt(frame.clear(frame.bridgeT, TOP + 1, middle).scan(), Status.NO_CART, frame.bridgeT, TOP + 1, middle);
	}

	@Test
	void aSecondCartIsReported() {
		Frame frame = new Frame(5);
		int middle = (frame.oz + 2) / 2;
		frame.put(frame.bridgeT, TOP + 1, T + 1, Kind.CART);
		assertAt(frame.scan(), Status.SECOND_CART, frame.bridgeT, TOP + 1, middle);
	}

	@Test
	void aSecondControllerIsTheLastRule() {
		Frame frame = new Frame(5);
		assertAt(frame.put(frame.ox + 1, HEAD_Y, 2, Kind.CONTROLLER).scan(), Status.SECOND_CONTROLLER,
				frame.ox + 1, HEAD_Y, 2);
	}

	@Test
	void theLimitsTakeExactlyThreeTiers() {
		assertThrows(IllegalArgumentException.class, () -> new Limits(5, new int[] {9, 17}));
		assertEquals(33, LIMITS.maxSpan(3));
		assertEquals(9, LIMITS.maxSpan(0));
	}
}
