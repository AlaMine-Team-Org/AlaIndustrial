package dev.alaindustrial.block.entity.quarry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.block.entity.quarry.QuarryFramePlacements.Part;
import dev.alaindustrial.block.entity.quarry.QuarryFramePlacements.Placement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The cells a formed quarry frame covers and where its drawn parts stand (MOD-950). */
class QuarryFrameLayoutTest {

	private static final int HEAD_Y = 70;

	/** A 13 × 13 frame from (0, 0), paths along X, the bridge at x = 6, the cart at z = 4. */
	private static QuarryFrameLayout frame(boolean alongX) {
		return new QuarryFrameLayout(0, 0, 12, 12, HEAD_Y, alongX, 6, 4);
	}

	@Test
	void everyCellIsVisitedOnce() {
		Set<Long> seen = new HashSet<>();
		int[] visits = {0};
		frame(true).forEachCell((x, y, z) -> {
			visits[0]++;
			seen.add(((long) x << 40) | ((long) y << 20) | z);
		});
		// Towers 4 × (3 × 3 × 9), paths 2 layers × 2 × 7 × 3, bridge 7 between the paths, two saddles of 4, cart 1.
		assertEquals(4 * 81 + 84 + 7 + 8 + 1, visits[0]);
		assertEquals(visits[0], seen.size(), "a cell was visited twice");
	}

	@Test
	void theBridgeSpansBothPathsAndTheCartSitsOnIt() {
		Set<String> cells = new HashSet<>();
		frame(true).forEachCell((x, y, z) -> cells.add(x + "," + y + "," + z));
		for (int z = 3; z <= 9; z++) {
			assertTrue(cells.contains("6," + (HEAD_Y + 1) + "," + z), "bridge cell z=" + z);
		}
		assertTrue(cells.contains("6," + (HEAD_Y + 1) + ",0"), "upper path layer under the bridge's end");
		assertTrue(cells.contains("6," + (HEAD_Y + 2) + ",4"), "cart cell");
		for (int z : new int[] {0, 1, 2, 3, 9, 10, 11, 12}) {
			assertTrue(cells.contains("6," + (HEAD_Y + 2) + "," + z), "saddle cell z=" + z);
		}
		assertTrue(!cells.contains("6," + (HEAD_Y + 2) + ",6"), "no saddle in the middle of the bridge");
		assertTrue(cells.contains("0," + (HEAD_Y - 6) + ",0"), "foundation corner");
		assertTrue(cells.contains("2," + (HEAD_Y + 2) + ",2"), "top head layer");
		assertTrue(!cells.contains("0," + (HEAD_Y + 3) + ",0"), "nothing above the heads");
	}

	@Test
	void theLayoutSurvivesItsArray() {
		QuarryFrameLayout layout = frame(false);
		assertEquals(layout, QuarryFrameLayout.fromArray(layout.toArray()));
		assertArrayEquals(new int[] {0, 0, 12, 12, HEAD_Y, 0, 6, 4}, layout.toArray());
		assertNull(QuarryFrameLayout.fromArray(null));
		assertNull(QuarryFrameLayout.fromArray(new int[7]));
	}

	@Test
	void partsAreCountedAsTheModelIsCut() {
		List<Placement> parts = QuarryFramePlacements.of(frame(true));
		assertEquals(4, count(parts, Part.TOWER));
		// Path cells 3..9, two under each head beam left out: 5..7 on each of the two paths.
		assertEquals(6, count(parts, Part.PATH));
		assertEquals(2, count(parts, Part.SADDLE));
		assertEquals(6, count(parts, Part.BRIDGE));
		assertEquals(1, count(parts, Part.CART));
	}

	@Test
	void towersFaceThePathAcrossTheFrame() {
		for (Placement tower : QuarryFramePlacements.of(frame(true))) {
			if (tower.part() == Part.TOWER) {
				assertEquals(tower.x() < 6 ? 90.0F : 270.0F, tower.yaw(), "tower at x=" + tower.x());
			}
		}
		for (Placement tower : QuarryFramePlacements.of(frame(false))) {
			if (tower.part() == Part.TOWER) {
				assertEquals(tower.z() < 6 ? 0.0F : 180.0F, tower.yaw(), "tower at z=" + tower.z());
			}
		}
	}

	@Test
	void bridgeSegmentsStopWhereTheSaddlesBegin() {
		float near = 1.5F + 2.0F;
		float far = 11.5F - 2.0F;
		for (Placement part : QuarryFramePlacements.of(frame(true))) {
			if (part.part() == Part.BRIDGE) {
				assertEquals(6.5F, part.x());
				assertTrue(part.z() - 0.5F >= near && part.z() + 0.5F <= far, "segment at z=" + part.z());
				assertEquals(HEAD_Y + 2, part.lightY(), "lit from the air above the paths");
			}
		}
	}

	@Test
	void pathsAlongZAreTheSameFrameTurned() {
		List<Placement> alongX = QuarryFramePlacements.of(frame(true));
		List<Placement> alongZ = QuarryFramePlacements.of(frame(false));
		assertEquals(alongX.size(), alongZ.size());
		for (Placement cart : alongZ) {
			if (cart.part() == Part.CART) {
				assertEquals(4.5F, cart.x());
				assertEquals(6.5F, cart.z());
				assertEquals(HEAD_Y + 3, cart.lightY(), "lit from the air above the cart");
			}
		}
	}

	private static long count(List<Placement> parts, Part part) {
		return parts.stream().filter(p -> p.part() == part).count();
	}
}
