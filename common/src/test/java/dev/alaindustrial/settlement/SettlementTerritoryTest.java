package dev.alaindustrial.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * L1 for the territory's shape (MOD-831, MOD-848): a square column of side {@code 2r + 1} centred on the core for every
 * town; for a town a structure built, also its buildings' footprint with the margin — one rectangle around both
 * (epic decision R59), which only grows with the tier.
 *
 * @implements MOD-848-RULE
 */
class SettlementTerritoryTest {

	@Test
	void theEdgesAreInsideAndOneStepPastThemIsNot() {
		assertTrue(SettlementTerritory.contains(100, -50, 32, 100, -50), "the core's own column");
		assertTrue(SettlementTerritory.contains(100, -50, 32, 132, -50), "east edge");
		assertTrue(SettlementTerritory.contains(100, -50, 32, 68, -82), "south-west corner");
		assertTrue(SettlementTerritory.contains(100, -50, 32, 132, -18), "north-east corner");
		assertFalse(SettlementTerritory.contains(100, -50, 32, 133, -50), "one past the east edge");
		assertFalse(SettlementTerritory.contains(100, -50, 32, 100, -83), "one past the south edge");
		assertFalse(SettlementTerritory.contains(100, -50, 32, 133, -17), "one past a corner");
	}

	@Test
	void aZeroRadiusHoldsOnlyTheCoreColumnAndANegativeOneHoldsNothing() {
		assertTrue(SettlementTerritory.contains(0, 0, 0, 0, 0));
		assertFalse(SettlementTerritory.contains(0, 0, 0, 1, 0));
		assertFalse(SettlementTerritory.contains(0, 0, -1, 0, 0));
	}

	/** Coordinates at the far ends of the int range must not overflow into a false "inside". */
	@Test
	void farApartCoordinatesDoNotOverflow() {
		assertFalse(SettlementTerritory.contains(Integer.MAX_VALUE, 0, 32, Integer.MIN_VALUE, 0));
		assertFalse(SettlementTerritory.contains(0, Integer.MIN_VALUE, 32, 0, Integer.MAX_VALUE));
		SettlementTerritory.Area edge = SettlementTerritory.Area.square(Integer.MAX_VALUE, Integer.MIN_VALUE, 32);
		assertFalse(edge.contains(Integer.MIN_VALUE, Integer.MAX_VALUE), "a square at the border wrapped round");
		assertTrue(edge.contains(Integer.MAX_VALUE, Integer.MIN_VALUE));
		assertEquals(Integer.MAX_VALUE, edge.grow(100).maxX(), "growing saturates instead of wrapping");
	}

	/** Inside means within the radius along BOTH axes — the property the shift workers' leash will rely on. */
	@Property
	void insideIsWithinTheRadiusAlongBothAxes(@ForAll @IntRange(min = -30_000_000, max = 30_000_000) int coreX,
			@ForAll @IntRange(min = -30_000_000, max = 30_000_000) int coreZ,
			@ForAll @IntRange(min = 0, max = 512) int radius,
			@ForAll @IntRange(min = -600, max = 600) int dx, @ForAll @IntRange(min = -600, max = 600) int dz) {
		boolean expected = Math.abs(dx) <= radius && Math.abs(dz) <= radius;
		assertTrue(SettlementTerritory.contains(coreX, coreZ, radius, coreX + dx, coreZ + dz) == expected,
				"core " + coreX + "," + coreZ + " r=" + radius + " offset " + dx + "," + dz);
		SettlementTerritory.Area square = SettlementTerritory.area(coreX, coreZ, radius, Optional.empty(), 99);
		assertEquals(expected, square.contains(coreX + dx, coreZ + dz), "a town without a footprint is the square");
	}

	/**
	 * A town along a pipe — its buildings run 60 blocks east of the core, past the LV square — holds every one of
	 * them and the margin round them, and still the whole square around its core.
	 */
	@Test
	void aStructuresTownHoldsItsFootprintWithTheMarginAndTheSquare() {
		SettlementTerritory.Area footprint = new SettlementTerritory.Area(97, -20, 160, 15);
		SettlementTerritory.Area area = SettlementTerritory.area(100, 0, 32, Optional.of(footprint), 8);
		assertEquals(new SettlementTerritory.Area(68, -32, 168, 32), area);
		assertTrue(area.contains(footprint.grow(8)), "the footprint and the margin round it");
		assertTrue(area.contains(SettlementTerritory.Area.square(100, 0, 32)), "the square round the core");
		assertTrue(area.contains(168, 0) && !area.contains(169, 0), "the east edge is the margin past the pump house");
		assertTrue(area.contains(68, 0) && !area.contains(67, 0), "the west edge is the square's");
		assertEquals(32, area.reach(100, 0), "the core's reach is set by the nearest edge, the square's");
	}

	/**
	 * The acceptance property of R59: whatever the footprint, the radius and the growth, the territory holds the
	 * footprint grown by the growth on every side and the square round the core — and nothing more than the rectangle
	 * around those two.
	 */
	@Property
	void theTerritoryIsTheEnvelopeOfTheGrownFootprintAndTheSquare(
			@ForAll @IntRange(min = -1000, max = 1000) int coreX, @ForAll @IntRange(min = -1000, max = 1000) int coreZ,
			@ForAll @IntRange(min = -1200, max = 1200) int minX, @ForAll @IntRange(min = 0, max = 200) int width,
			@ForAll @IntRange(min = -1200, max = 1200) int minZ, @ForAll @IntRange(min = 0, max = 200) int depth,
			@ForAll @IntRange(min = 0, max = 128) int radius, @ForAll @IntRange(min = 0, max = 128) int growth,
			@ForAll @IntRange(min = -1500, max = 1500) int x, @ForAll @IntRange(min = -1500, max = 1500) int z) {
		SettlementTerritory.Area footprint = new SettlementTerritory.Area(minX, minZ, minX + width, minZ + depth);
		SettlementTerritory.Area area = SettlementTerritory.area(coreX, coreZ, radius, Optional.of(footprint), growth);
		SettlementTerritory.Area grown = footprint.grow(growth);
		SettlementTerritory.Area square = SettlementTerritory.Area.square(coreX, coreZ, radius);
		assertTrue(area.contains(grown) && area.contains(square));
		boolean inEnvelope = x >= Math.min(grown.minX(), square.minX()) && x <= Math.max(grown.maxX(), square.maxX())
				&& z >= Math.min(grown.minZ(), square.minZ()) && z <= Math.max(grown.maxZ(), square.maxZ());
		assertEquals(inEnvelope, area.contains(x, z), "column " + x + ", " + z);
		assertTrue(area.reach(coreX, coreZ) >= radius, "the core reaches at least its radius");
	}

	/**
	 * Moving up a tier never takes a column away: a larger radius and the growth that comes with it only widen the
	 * territory, on every side the footprint sets as well as on the square's.
	 */
	@Property
	void aHigherTierNeverShrinksTheTerritory(@ForAll @IntRange(min = -300, max = 300) int minX,
			@ForAll @IntRange(min = 0, max = 150) int width, @ForAll @IntRange(min = -300, max = 300) int minZ,
			@ForAll @IntRange(min = 0, max = 150) int depth, @ForAll @IntRange(min = 1, max = 64) int lowRadius,
			@ForAll @IntRange(min = 0, max = 64) int climb, @ForAll @IntRange(min = 0, max = 16) int margin) {
		SettlementTerritory.Area footprint = new SettlementTerritory.Area(minX, minZ, minX + width, minZ + depth);
		SettlementTerritory.Area low = SettlementTerritory.area(0, 0, lowRadius, Optional.of(footprint), margin);
		SettlementTerritory.Area high = SettlementTerritory.area(0, 0, lowRadius + climb, Optional.of(footprint),
				margin + climb);
		assertTrue(high.contains(low), "tier up shrank " + low + " to " + high);
		if (climb > 0) {
			assertTrue(high.minX() < low.minX() && high.maxX() > low.maxX() && high.minZ() < low.minZ()
					&& high.maxZ() > low.maxZ(), "tier up must widen every side: " + low + " -> " + high);
		}
	}

	/** The tier's growth is what its radius gained over the first tier's: zero at LV, the climb above it. */
	@Test
	void theGrowthOfATierIsItsRadiusPastTheFirstTiers() {
		assertEquals(0, SettlementTier.FIRST.growth());
		for (SettlementTier tier : SettlementTier.values()) {
			assertEquals(tier.radius() - SettlementTier.FIRST.radius(), tier.growth(), tier.name());
			assertTrue(tier.growth() >= 0, tier.name());
		}
		SettlementTerritory.Area footprint = new SettlementTerritory.Area(-40, -10, 90, 20);
		SettlementTerritory.Area previous = null;
		for (SettlementTier tier : SettlementTier.values()) {
			SettlementTerritory.Area area = SettlementTerritory.of(0, 0, tier, Optional.of(footprint));
			assertTrue(area.contains(footprint.grow(SettlementConfig.settlementTerritoryMargin)), tier.name());
			assertTrue(previous == null || area.contains(previous), tier + " shrank the town");
			previous = area;
		}
	}

	@Test
	void anAreaHasItsSizeAndRefusesCrossedBounds() {
		SettlementTerritory.Area area = new SettlementTerritory.Area(-3, 4, 6, 4);
		assertEquals(10, area.sizeX());
		assertEquals(1, area.sizeZ());
		assertEquals(0, area.reach(-3, 4), "a column on the edge reaches nothing further");
		assertTrue(area.reach(7, 4) < 0, "a column outside has a negative reach");
		assertThrows(IllegalArgumentException.class, () -> new SettlementTerritory.Area(1, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> area.grow(-1));
		assertThrows(IllegalArgumentException.class, () -> SettlementTerritory.Area.square(0, 0, -1));
	}
}
