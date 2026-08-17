package dev.alaindustrial.core.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.teleport.RtpSiteFinder.Offset;
import dev.alaindustrial.core.teleport.RtpSiteFinder.Site;
import dev.alaindustrial.core.teleport.RtpSiteFinder.Terrain;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1 for the random-jump search (MOD-116).
 *
 * <p>This is the layer that carries the feature. A real 5000-block jump cannot be exercised from an
 * L2 gametest — the lane's rigs are 8×8×8, its world origin is random per run and its tick budget is
 * shorter than the warmup — so everything decidable without a world is decided here: the ring
 * geometry, the give-up path, and the ordering that keeps the expensive probe rare.
 */
class RtpSiteFinderTest {

	private static final int SEA_LEVEL = 63;

	/** A world the test writes itself: heights come from a rule, and every probe is counted. */
	private static final class FakeTerrain implements Terrain {
		private final java.util.function.IntBinaryOperator noise;
		private final java.util.function.IntBinaryOperator safe;
		private final java.util.function.IntBinaryOperator border;
		final List<String> probes = new ArrayList<>();

		FakeTerrain(java.util.function.IntBinaryOperator noise, java.util.function.IntBinaryOperator safe,
				java.util.function.IntBinaryOperator border) {
			this.noise = noise;
			this.safe = safe;
			this.border = border;
		}

		/** Everything everywhere is land at y=80 and safe — the "it just works" world. */
		static FakeTerrain allGood() {
			return new FakeTerrain((x, z) -> 80, (x, z) -> 80, (x, z) -> 1);
		}

		@Override
		public int noiseGroundY(int x, int z) {
			probes.add("noise");
			return noise.applyAsInt(x, z);
		}

		@Override
		public int safeFeetY(int x, int z) {
			probes.add("safe");
			return safe.applyAsInt(x, z);
		}

		@Override
		public boolean isInsideBorder(int x, int z) {
			probes.add("border");
			return border.applyAsInt(x, z) != 0;
		}

		long count(String kind) {
			return probes.stream().filter(kind::equals).count();
		}
	}

	/** A fixed roll sequence; running past its end is itself a failure worth seeing. */
	private static DoubleSupplier rolls(double... values) {
		int[] cursor = { 0 };
		return () -> {
			if (cursor[0] >= values.length) {
				throw new IllegalStateException("the search asked for more rolls than the test supplied");
			}
			return values[cursor[0]++];
		};
	}

	/** Every attempt takes exactly two draws, so a test's roll list is read two at a time. */
	private static DoubleSupplier attemptsOf(double... anglesAndRadii) {
		return rolls(anglesAndRadii);
	}

	// --- ring geometry -------------------------------------------------------------------------

	@Test
	@DisplayName("offsetInRing: the angle draw walks the compass, and 0 points down +X")
	void angleWalksTheCompass() {
		assertEquals(new Offset(100, 0), RtpSiteFinder.offsetInRing(0.0, 1.0, 0, 100));
		assertEquals(new Offset(0, 100), RtpSiteFinder.offsetInRing(0.25, 1.0, 0, 100));
		assertEquals(new Offset(-100, 0), RtpSiteFinder.offsetInRing(0.5, 1.0, 0, 100));
		assertEquals(new Offset(0, -100), RtpSiteFinder.offsetInRing(0.75, 1.0, 0, 100));
	}

	@Test
	@DisplayName("offsetInRing: the sampling is uniform by AREA, not by radius")
	void samplingIsAreaUniform() {
		// The whole reason this test exists. Half the draw must land at sqrt(0.5) of the radius
		// (≈70.7), NOT at half of it: a radius-linear r = min + u × (max − min) would answer 50 and
		// pile jumps up near the origin, which is exactly the bug this formula avoids.
		assertEquals(new Offset(71, 0), RtpSiteFinder.offsetInRing(0.0, 0.5, 0, 100));
		// A quarter of the AREA is half the radius — the mirror statement of the same rule.
		assertEquals(new Offset(50, 0), RtpSiteFinder.offsetInRing(0.0, 0.25, 0, 100));
	}

	@Test
	@DisplayName("offsetInRing: the ring's floor and ceiling are both reachable and never crossed")
	void ringBoundsHold() {
		assertEquals(new Offset(500, 0), RtpSiteFinder.offsetInRing(0.0, 0.0, 500, 5000),
				"a zero draw must land exactly on the inner edge");
		assertEquals(new Offset(5000, 0), RtpSiteFinder.offsetInRing(0.0, 1.0, 500, 5000),
				"a full draw must land exactly on the outer edge");
		for (int i = 0; i <= 100; i++) {
			Offset offset = RtpSiteFinder.offsetInRing(i / 100.0, i / 100.0, 500, 5000);
			double distance = Math.sqrt((double) offset.dx() * offset.dx() + (double) offset.dz() * offset.dz());
			assertTrue(distance >= 499.0 && distance <= 5001.0,
					"draw " + i + " landed " + distance + " out, outside [500, 5000]");
		}
	}

	@Test
	@DisplayName("offsetInRing: a minimum radius keeps a jump from going nowhere")
	void minimumRadiusIsHonoured() {
		// Without the floor a legitimate draw can land the player where they already stood, having
		// spent a full jump's EU. Every draw must clear the minimum.
		for (int i = 0; i <= 50; i++) {
			Offset offset = RtpSiteFinder.offsetInRing(0.13 * i, i / 50.0, 1000, 2000);
			double distance = Math.sqrt((double) offset.dx() * offset.dx() + (double) offset.dz() * offset.dz());
			assertTrue(distance >= 999.0, "draw " + i + " landed only " + distance + " blocks out");
		}
	}

	@Test
	@DisplayName("offsetInRing: rubbish draws are folded into range instead of throwing the geometry off")
	void degenerateDrawsAreClamped() {
		assertEquals(new Offset(100, 0), RtpSiteFinder.offsetInRing(0.0, 9.0, 0, 100));
		assertEquals(new Offset(0, 0), RtpSiteFinder.offsetInRing(0.0, -9.0, 0, 100));
		assertEquals(new Offset(0, 0), RtpSiteFinder.offsetInRing(Double.NaN, Double.NaN, 0, 100));
		assertEquals(new Offset(100, 0), RtpSiteFinder.offsetInRing(0.0, 1.0, 100, 0),
				"a reversed ring is read as the ring it describes, not as an empty one");
	}

	// --- the search ----------------------------------------------------------------------------

	@Test
	@DisplayName("find: the first usable candidate is taken, and the site is the probed height")
	void firstUsableCandidateWins() {
		FakeTerrain terrain = FakeTerrain.allGood();
		Site site = RtpSiteFinder.find(terrain, 0, 0, 0, 100, SEA_LEVEL, 8, attemptsOf(0.0, 1.0));

		assertNotNull(site);
		assertEquals(new Site(100, 80, 0), site);
		assertEquals(1, terrain.count("safe"), "one candidate should cost exactly one precise probe");
	}

	@Test
	@DisplayName("find: an ocean column is rejected WITHOUT paying for the precise probe")
	void oceanIsRejectedBeforeTheExpensiveProbe() {
		// The performance invariant of the whole feature. The precise probe forces a chunk load, and
		// on fresh terrain that is a full worldgen run on the server thread; if the cheap noise probe
		// stopped gating it, a handful of retries would freeze the server. Counting probes is the only
		// way to state that ordering as a test — an assertion on the returned site cannot see it.
		FakeTerrain terrain = new FakeTerrain((x, z) -> 40, (x, z) -> 80, (x, z) -> 1);
		Site site = RtpSiteFinder.find(terrain, 0, 0, 0, 100, SEA_LEVEL, 3,
				attemptsOf(0.0, 1.0, 0.25, 1.0, 0.5, 1.0));

		assertNull(site, "every column was under water, so there is nowhere to land");
		assertEquals(3, terrain.count("noise"), "all three candidates should be sampled cheaply");
		assertEquals(0, terrain.count("safe"), "and none of them should have cost a chunk");
	}

	@Test
	@DisplayName("find: sea level is the boundary — exactly at it is water, one above it is land")
	void seaLevelIsTheBoundary() {
		FakeTerrain atSeaLevel = new FakeTerrain((x, z) -> SEA_LEVEL, (x, z) -> SEA_LEVEL, (x, z) -> 1);
		assertNull(RtpSiteFinder.find(atSeaLevel, 0, 0, 0, 100, SEA_LEVEL, 1, attemptsOf(0.0, 1.0)),
				"ground level with the waterline is still water");

		FakeTerrain oneAbove = new FakeTerrain((x, z) -> SEA_LEVEL + 1, (x, z) -> SEA_LEVEL + 1, (x, z) -> 1);
		assertNotNull(RtpSiteFinder.find(oneAbove, 0, 0, 0, 100, SEA_LEVEL, 1, attemptsOf(0.0, 1.0)),
				"one block above the waterline is dry land");
	}

	@Test
	@DisplayName("find: a candidate outside the world border costs no probe at all")
	void borderIsCheckedFirst() {
		FakeTerrain terrain = new FakeTerrain((x, z) -> 80, (x, z) -> 80, (x, z) -> 0);
		assertNull(RtpSiteFinder.find(terrain, 0, 0, 0, 100, SEA_LEVEL, 2, attemptsOf(0.0, 1.0, 0.5, 1.0)));
		assertEquals(0, terrain.count("noise"), "outside the border there is nothing worth sampling");
		assertEquals(0, terrain.count("safe"));
	}

	@Test
	@DisplayName("find: an unusable precise probe is retried, and a later attempt can still succeed")
	void retryReachesALaterCandidate() {
		// Safe only in the far east: the first two draws point elsewhere, the third lands there.
		FakeTerrain terrain = new FakeTerrain((x, z) -> 80,
				(x, z) -> x > 50 ? 72 : RtpSiteFinder.NO_SITE, (x, z) -> 1);
		Site site = RtpSiteFinder.find(terrain, 0, 0, 0, 100, SEA_LEVEL, 3,
				attemptsOf(0.5, 1.0, 0.25, 1.0, 0.0, 1.0));

		assertEquals(new Site(100, 72, 0), site);
		assertEquals(3, terrain.count("safe"), "all three candidates should have been probed");
	}

	@Test
	@DisplayName("find: the retry budget is spent exactly, then the search gives up")
	void givesUpAfterTheBudget() {
		FakeTerrain terrain = new FakeTerrain((x, z) -> 80, (x, z) -> RtpSiteFinder.NO_SITE, (x, z) -> 1);
		// Six attempts, two rolls each: supplying exactly twelve draws makes an over- or under-spent
		// budget throw rather than pass quietly.
		Site site = RtpSiteFinder.find(terrain, 0, 0, 0, 100, SEA_LEVEL, 6,
				attemptsOf(0.0, 1.0, 0.1, 1.0, 0.2, 1.0, 0.3, 1.0, 0.4, 1.0, 0.5, 1.0));

		assertNull(site);
		assertEquals(6, terrain.count("safe"), "the budget is attempts, not attempts ± 1");
	}

	@Test
	@DisplayName("find: a zero or negative budget probes nothing instead of looping")
	void degenerateBudgetIsSafe() {
		FakeTerrain zero = FakeTerrain.allGood();
		assertNull(RtpSiteFinder.find(zero, 0, 0, 0, 100, SEA_LEVEL, 0, attemptsOf()));
		assertEquals(0, zero.probes.size());

		FakeTerrain negative = FakeTerrain.allGood();
		assertNull(RtpSiteFinder.find(negative, 0, 0, 0, 100, SEA_LEVEL, -5, attemptsOf()));
		assertEquals(0, negative.probes.size());
	}

	@Test
	@DisplayName("find: an unsamplable column is treated as unusable, not as bedrock-level ground")
	void unsamplableColumnIsRejected() {
		// Level#getHeight answers minY for a chunk that is not loaded, so NO_SITE has to mean "no
		// answer" rather than "ground at the bottom of the world" — otherwise a jump would aim at the
		// void every time the cheap probe came up empty.
		FakeTerrain terrain = new FakeTerrain((x, z) -> RtpSiteFinder.NO_SITE, (x, z) -> 80, (x, z) -> 1);
		assertNull(RtpSiteFinder.find(terrain, 0, 0, 0, 100, SEA_LEVEL, 2, attemptsOf(0.0, 1.0, 0.5, 1.0)));
		assertEquals(0, terrain.count("safe"));
	}

	@Test
	@DisplayName("find: the landing is inside the configured ring, whatever the draws are")
	void landingRespectsTheConfiguredRing() {
		FakeTerrain terrain = FakeTerrain.allGood();
		for (int i = 0; i <= 40; i++) {
			Site site = RtpSiteFinder.find(terrain, 1000, -2000, 500, 5000, SEA_LEVEL, 1,
					attemptsOf(i / 40.0, i / 40.0));
			assertNotNull(site);
			double distance = Math.sqrt(Math.pow(site.x() - 1000.0, 2) + Math.pow(site.z() + 2000.0, 2));
			assertTrue(distance >= 499.0 && distance <= 5001.0,
					"draw " + i + " landed " + distance + " blocks from the origin");
		}
	}
}
