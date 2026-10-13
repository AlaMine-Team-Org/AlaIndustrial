package dev.alaindustrial.core.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.acoustic.AcousticZones.Cell;
import dev.alaindustrial.core.acoustic.AcousticZones.Zone;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the sound absorber's scale and zones (MOD-804): the owner's table, the face-only
 * grouping, the zone boundary and "the strongest zone wins, never the sum".
 */
class AcousticZonesTest {

	@Test
	void tierTableIsTheOwnersScale() {
		int[][] table = {{1, 1, 5, 35}, {2, 2, 8, 50}, {3, 2, 8, 50}, {4, 3, 12, 65}, {7, 3, 12, 65},
				{8, 4, 16, 75}, {15, 4, 16, 75}, {16, 5, 20, 85}, {32, 5, 20, 85}, {200, 5, 20, 85}};
		for (int[] row : table) {
			AbsorberTier tier = AbsorberTier.forGroupSize(row[0]);
			assertEquals(row[1], tier.level(), "tier of " + row[0] + " blocks");
			assertEquals(row[2], tier.radius(), "radius of " + row[0] + " blocks");
			assertEquals(row[3], tier.dampeningPercent(), "dampening of " + row[0] + " blocks");
		}
		assertEquals(AbsorberTier.SATURATION, AbsorberTier.V.minBlocks());
		assertEquals(0.15f, AbsorberTier.V.volumeFactor(), 1e-6f);
		assertEquals(3, AbsorberTier.II.maxBlocks());
		assertEquals(AbsorberTier.GROUP_CAP, AbsorberTier.V.maxBlocks());
	}

	@Test
	void groupsJoinByFacesOnly() {
		// A pair, plus one absorber touching it only by an edge — that one is its own group.
		List<Zone> zones = AcousticZones.groups(List.of(new Cell(0, 0, 0), new Cell(1, 0, 0), new Cell(2, 1, 0)));
		assertEquals(2, zones.size());
		Zone pair = zones.stream().filter(z -> z.size() == 2).findFirst().orElseThrow();
		assertEquals(AbsorberTier.II, pair.tier());
		assertEquals(0, pair.minX());
		assertEquals(1, pair.maxX());
	}

	@Test
	void tiersForOneTwoFourEightSixteen() {
		for (int[] row : new int[][] {{1, 1}, {2, 2}, {4, 3}, {8, 4}, {16, 5}}) {
			List<Cell> line = new ArrayList<>();
			for (int i = 0; i < row[0]; i++) {
				line.add(new Cell(i, 0, 0));
			}
			List<Zone> zones = AcousticZones.groups(line);
			assertEquals(1, zones.size());
			assertEquals(row[1], zones.get(0).tier().level(), row[0] + " blocks in a row");
		}
	}

	@Test
	void zoneBoundaryIsTheBoxGrownByTheRadius() {
		Zone single = AcousticZones.groups(List.of(new Cell(0, 0, 0))).get(0);
		// The block occupies [0, 1); radius 5 reaches x = -5 .. 6.
		assertTrue(single.contains(6.0, 0.5, 0.5));
		assertTrue(single.contains(-5.0, 0.5, 0.5));
		assertFalse(single.contains(6.01, 0.5, 0.5));
		assertFalse(single.contains(-5.01, 0.5, 0.5));
		assertFalse(single.contains(0.5, 6.5, 0.5));
	}

	@Test
	void strongestZoneWinsAndNothingAddsUp() {
		Zone weak = AcousticZones.groups(List.of(new Cell(0, 0, 0))).get(0);
		Zone strong = AcousticZones.groups(List.of(new Cell(3, 0, 0), new Cell(4, 0, 0))).get(0);
		assertEquals(0.5f, AcousticZones.factorAt(List.of(weak, strong), 2.0, 0.5, 0.5), 1e-6f);
		assertEquals(0.65f, AcousticZones.factorAt(List.of(weak), 2.0, 0.5, 0.5), 1e-6f);
		assertEquals(1.0f, AcousticZones.factorAt(List.of(weak, strong), 100.0, 0.5, 0.5), 1e-6f);
		assertEquals(1.0f, AcousticZones.factorAt(List.of(), 0.5, 0.5, 0.5), 1e-6f);
	}
}
