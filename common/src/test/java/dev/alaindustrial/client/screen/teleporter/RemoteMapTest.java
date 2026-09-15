package dev.alaindustrial.client.screen.teleporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.client.screen.teleporter.RemoteMap.Marker;
import dev.alaindustrial.client.screen.teleporter.RemoteMap.Placed;
import java.util.List;
import org.junit.jupiter.api.Test;

/** L1 coverage for {@link RemoteMap} (MOD-629): scale, price, compass, clusters, rim arrows, clicks and wheel order. */
class RemoteMapTest {

	private static final double EPS = 1e-9;

	@Test
	void theScaleRunsFromTheInnerRingToTheLastOne() {
		assertEquals(RemoteMap.INNER, RemoteMap.radius(0), EPS, "the player's own spot is the inner ring");
		assertEquals(RemoteMap.OUTER, RemoteMap.radius(RemoteMap.LAST_RING_BLOCKS), EPS, "5 000 blocks is the last ring");
		assertEquals(55.0, RemoteMap.OUTER, EPS, "the mockup's 122 px radar keeps 6 px outside its last ring");
		double previous = RemoteMap.radius(0);
		for (int d = 10; d <= 5000; d += 10) {
			double r = RemoteMap.radius(d);
			assertTrue(r > previous, "the scale must grow with distance at " + d);
			previous = r;
		}
		// Logarithmic, not linear: the 1k ring sits well past the middle of the rim.
		assertTrue(RemoteMap.radius(1000) > (RemoteMap.INNER + RemoteMap.OUTER) / 2);
	}

	@Test
	void thePriceShownIsRoundedUpToAHundred() {
		assertEquals(14_200, RemoteMap.shownPrice(14_135));
		assertEquals(14_200, RemoteMap.shownPrice(14_200), "an exact hundred stays as it is");
		assertEquals(100, RemoteMap.shownPrice(1));
		assertEquals(0, RemoteMap.shownPrice(0), "no price, nothing to show");
		for (long exact = 1; exact < 30_000; exact += 7) {
			long shown = RemoteMap.shownPrice(exact);
			assertTrue(shown >= exact && shown - exact < 100 && shown % 100 == 0, "shown " + shown + " for " + exact);
		}
	}

	/**
	 * The regression the task names: a jump costing 20,025 over a charge of 20,010. Rounded to the nearest hundred the
	 * screen would say 20,000 — less than the charge — and promise a jump the server refuses.
	 */
	@Test
	void aPriceJustAboveTheChargeNeverReadsAsAffordable() {
		long charge = 20_010;
		long exact = 20_025;
		assertEquals(20_100, RemoteMap.shownPrice(exact));
		assertTrue(RemoteMap.shownPrice(exact) > charge, "the shown price must not fall under a charge the jump exceeds");
	}

	@Test
	void compassPointsFollowTheMapNorthUp() {
		assertEquals(0, RemoteMap.compassPoint(0, -10), "−Z is north");
		assertEquals(1, RemoteMap.compassPoint(10, -10));
		assertEquals(2, RemoteMap.compassPoint(10, 0), "+X is east");
		assertEquals(3, RemoteMap.compassPoint(10, 10));
		assertEquals(4, RemoteMap.compassPoint(0, 10), "+Z is south");
		assertEquals(5, RemoteMap.compassPoint(-10, 10));
		assertEquals(6, RemoteMap.compassPoint(-10, 0), "−X is west");
		assertEquals(7, RemoteMap.compassPoint(-10, -10));
		assertEquals(0, RemoteMap.compassPoint(3, -100), "a few degrees off north is still north");
	}

	@Test
	void dotsCloseTogetherMergeIntoOneCluster() {
		// Frame C's village, 700 m north-east: three stations within a few blocks of each other.
		List<Marker> markers = RemoteMap.markers(List.of(
				new Placed(1, 700, -120), new Placed(2, 708, -128), new Placed(3, 694, -135), new Placed(0, -14, 18)));
		assertEquals(2, markers.size(), "the three merge, the station next to the player does not");
		Marker cluster = markers.get(0);
		assertTrue(cluster.isCluster());
		assertEquals(List.of(1, 2, 3), cluster.members());
		assertFalse(cluster.far());
		assertEquals(List.of(0), markers.get(1).members());
	}

	@Test
	void aClusterSitsAtTheAverageOfItsDots() {
		List<Marker> alone = RemoteMap.markers(List.of(new Placed(0, 700, -120)));
		List<Marker> pair = RemoteMap.markers(List.of(new Placed(0, 700, -120), new Placed(1, 708, -128)));
		List<Marker> other = RemoteMap.markers(List.of(new Placed(1, 708, -128)));
		assertEquals((alone.get(0).x() + other.get(0).x()) / 2, pair.get(0).x(), 1e-9);
		assertEquals((alone.get(0).y() + other.get(0).y()) / 2, pair.get(0).y(), 1e-9);
	}

	@Test
	void farStationsBecomeArrowsOnTheRimAndNeverMerge() {
		List<Marker> markers = RemoteMap.markers(List.of(new Placed(0, 6000, -5000), new Placed(1, 6010, -5000)));
		assertEquals(2, markers.size(), "two arrows side by side stay two arrows");
		Marker arrow = markers.get(0);
		assertTrue(arrow.far());
		double fromCentre = Math.hypot(arrow.x() - RemoteMap.CENTRE, arrow.y() - RemoteMap.CENTRE);
		assertEquals(RemoteMap.OUTER + 0.5, fromCentre, 1e-9, "an arrow rides just outside the last ring");
		assertEquals(1.0, Math.hypot(arrow.dirX(), arrow.dirY()), 1e-9);
		assertTrue(arrow.dirX() > 0 && arrow.dirY() < 0, "it points the way the station lies: north-east");
	}

	@Test
	void aStationExactlyAtThePlayerSitsOnTheCentre() {
		Marker marker = RemoteMap.markers(List.of(new Placed(0, 0, 0))).get(0);
		assertEquals(RemoteMap.CENTRE, marker.x(), EPS);
		assertEquals(RemoteMap.CENTRE, marker.y(), EPS);
	}

	@Test
	void aClickHitsTheMarkerUnderItAndMissesEmptySpace() {
		List<Marker> markers = RemoteMap.markers(List.of(new Placed(0, 900, 0), new Placed(1, -900, 0)));
		Marker east = markers.get(0);
		assertEquals(0, RemoteMap.markerAt(markers, east.pixelX() + 0.5, east.pixelY() + 0.5));
		assertEquals(0, RemoteMap.markerAt(markers, east.pixelX() + 4.5, east.pixelY() - 4.5), "the plate's corner counts");
		assertEquals(-1, RemoteMap.markerAt(markers, east.pixelX() + 7, east.pixelY()), "beyond the plate is empty map");
		assertEquals(1, RemoteMap.markerAt(markers, markers.get(1).pixelX(), markers.get(1).pixelY()));
	}

	@Test
	void repeatedClicksOnAClusterWalkItsStations() {
		List<Integer> members = List.of(4, 2, 7);
		assertEquals(4, RemoteMap.nextInCluster(members, 0), "a selection elsewhere lands on the first");
		assertEquals(2, RemoteMap.nextInCluster(members, 4));
		assertEquals(7, RemoteMap.nextInCluster(members, 2));
		assertEquals(4, RemoteMap.nextInCluster(members, 7), "and wraps");
	}

	@Test
	void theWheelWalksStationsNearestFirstAndWraps() {
		List<Integer> order = RemoteMap.byDistance(List.of(
				new Placed(0, 300, 0), new Placed(1, 20, 0), new Placed(2, 0, -300), new Placed(3, 5000, 0)));
		assertEquals(List.of(1, 0, 2, 3), order, "nearest first; the two at 300 blocks keep the remote's order");
		assertEquals(0, RemoteMap.step(order, 1, 1));
		assertEquals(1, RemoteMap.step(order, 3, 1), "forward past the farthest wraps to the nearest");
		assertEquals(3, RemoteMap.step(order, 1, -1), "back past the nearest wraps to the farthest");
		assertEquals(1, RemoteMap.step(order, 9, 1), "a selection off the map starts from the nearest");
		assertEquals(3, RemoteMap.step(order, 9, -1), "or from the farthest going back");
		assertEquals(5, RemoteMap.step(List.of(), 5, 1), "no stations, nothing moves");
	}

	@Test
	void yawTurnsIntoTheScreenDirectionTheMapUses() {
		double[] north = RemoteMap.facing(180f);
		assertEquals(0.0, north[0], 1e-9);
		assertEquals(-1.0, north[1], 1e-9, "yaw 180 faces north, up the map");
		double[] west = RemoteMap.facing(90f);
		assertEquals(-1.0, west[0], 1e-9, "yaw 90 faces west, left");
		assertEquals(0.0, west[1], 1e-9);
		double[] south = RemoteMap.facing(0f);
		assertEquals(1.0, south[1], 1e-9, "yaw 0 faces south, down the map");
	}

	/**
	 * The arrows are pixel art, so they are pinned pixel for pixel to the approved mockup's generator (variant 6): the
	 * player's arrow facing north and north-east (frame A), and a rim arrow for a station 6 000 E / 5 000 N away. A thin
	 * tip loses its last pixel to the pixel-centre rule, which is why no "the tip reaches further" check could stand in.
	 */
	@Test
	void theArrowsMatchTheApprovedMockupPixelForPixel() {
		assertPixels("57,64 58,62 58,63 59,60 59,61 59,62 60,58 60,59 60,60 60,61 60,62 61,58 61,59 61,60 61,61 61,62 "
				+ "62,60 62,61 62,62 63,62 63,63 64,64",
				"56,64 57,62 57,63 57,65 58,60 58,61 58,64 59,58 59,59 59,63 60,57 60,63 61,57 61,63 62,58 62,59 62,63 "
						+ "63,60 63,61 63,64 64,62 64,63 64,65 65,64",
				RemoteMap.playerArrow(0.0, -1.0), "player arrow facing north");
		assertPixels("57,60 58,60 58,61 59,60 59,61 60,59 60,60 60,61 60,62 60,63 61,59 61,60 61,61 61,62 61,63 61,64 "
				+ "62,58 62,59 62,60 62,61 63,58 63,59",
				"56,60 57,59 57,61 58,59 58,62 59,59 59,62 59,63 60,58 60,64 61,58 61,65 62,57 62,62 62,63 62,64 63,57 "
						+ "63,60 63,61 64,58 64,59",
				RemoteMap.playerArrow(Math.sin(Math.toRadians(45)), -Math.cos(Math.toRadians(45))),
				"player arrow facing north-east");
		Marker far = RemoteMap.markers(List.of(new Placed(0, 6000, -5000))).get(0);
		assertPixels("100,24 101,24 101,25 102,24 102,25 102,26 102,27 103,24 103,25 103,26 103,27 103,28 104,24 104,25 "
				+ "104,26 104,27 105,24",
				"99,24 100,23 100,25 101,23 101,26 101,27 102,23 102,28 103,23 103,29 104,23 104,28 105,23 105,25 105,26 "
						+ "105,27 106,24",
				RemoteMap.rimArrow(far), "rim arrow for a far north-east station");
	}

	private static void assertPixels(String body, String edge, RemoteMap.ArrowPixels actual, String what) {
		assertEquals(java.util.Set.of(body.split(" ")), keys(actual.body()), what + ": body");
		assertEquals(java.util.Set.of(edge.split(" ")), keys(actual.edge()), what + ": outline");
	}

	private static java.util.Set<String> keys(List<int[]> pixels) {
		return pixels.stream().map(p -> p[0] + "," + p[1]).collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void theZoneIsDottedOnlyBetweenItsRadiiOnEveryThirdDiagonal() {
		int left = 8;
		int top = 20;
		double inner = RemoteMap.radius(500);
		double outer = RemoteMap.radius(5000);
		int dots = 0;
		for (int y = top; y < top + RemoteMap.SIZE; y++) {
			for (int x = left; x < left + RemoteMap.SIZE; x++) {
				if (RemoteMap.zoneDot(x, y, left, top, inner, outer)) {
					dots++;
					double d = Math.hypot(x + 0.5 - left - RemoteMap.CENTRE, y + 0.5 - top - RemoteMap.CENTRE);
					assertTrue(d >= inner && d <= outer);
					assertEquals(0, Math.floorMod(x + y, 3));
				}
			}
		}
		assertTrue(dots > 100, "the ring is visibly dotted, " + dots + " dots");
		assertFalse(RemoteMap.zoneDot(left + 61, top + 61, left, top, inner, outer), "nothing under the player");
	}

	@Test
	void aRingIsOnePixelWideAndListsEachPixelOnce() {
		List<int[]> ring = RemoteMap.ringPixels(RemoteMap.radius(1000));
		assertEquals(ring.size(), ring.stream().map(p -> p[0] + "," + p[1]).distinct().count());
		for (int[] p : ring) {
			double d = Math.hypot(p[0] + 0.5 - RemoteMap.CENTRE, p[1] + 0.5 - RemoteMap.CENTRE);
			assertEquals(RemoteMap.radius(1000), d, 1.0, "every ring pixel lies on its radius");
		}
	}
}
