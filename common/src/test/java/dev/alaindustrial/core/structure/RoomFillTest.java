package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.structure.RoomScan.ShellKind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link RoomFill} (MOD-505) — the flood fill that lets a greenhouse be any shape.
 *
 * <p>The shapes here are the point. A rectangular scanner passes the first test and fails every
 * other one, which is exactly the bug this class exists to fix: playtest four built a stepped
 * pyramid, the room was airtight, and the old scanner called it a breach. So the pyramid is a test,
 * not an anecdote.
 */
class RoomFillTest {

	/** A world made of whatever the test puts in it; everything unspecified is open air. */
	private static final class World implements RoomScan.ShellProbe {
		private final Map<Long, ShellKind> cells = new HashMap<>();

		private static long key(int x, int y, int z) {
			return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
		}

		World put(int x, int y, int z, ShellKind kind) {
			cells.put(key(x, y, z), kind);
			return this;
		}

		/** Walls a solid box, hollowing its interior — the shell only, so the inside stays air. */
		World box(int x0, int y0, int z0, int x1, int y1, int z1, ShellKind kind) {
			for (int x = x0; x <= x1; x++) {
				for (int y = y0; y <= y1; y++) {
					for (int z = z0; z <= z1; z++) {
						boolean surface = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
						if (surface) {
							put(x, y, z, kind);
						}
					}
				}
			}
			return this;
		}

		/** Fills a solid slab of shell — used to cap the steps of a pyramid. */
		World slab(int x0, int y, int z0, int x1, int z1, ShellKind kind) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					put(x, y, z, kind);
				}
			}
			return this;
		}

		@Override
		public ShellKind kindAt(int x, int y, int z) {
			return cells.getOrDefault(key(x, y, z), ShellKind.OTHER);
		}
	}

	/** A controller in the middle of the south wall of a box, looking out. Interior lies north. */
	private static RoomFill.Result fill(World world, int cx, int cy, int cz) {
		return RoomFill.fill(world, cx, cy, cz, 0, 0, -1, 8, 4096, 24);
	}

	@Test
	void sealedBoxIsSealed() {
		World world = new World().box(0, 0, 0, 6, 6, 6, ShellKind.CASING);
		world.put(3, 3, 6, ShellKind.CONTROLLER);
		RoomFill.Result result = fill(world, 3, 3, 6);
		assertTrue(result.sealed(), "a plain box must still seal: " + result.status());
		assertEquals(125, result.volume(), "interior of a 7-cube shell is 5³");
	}

	@Test
	void steppedPyramidIsSealed() {
		// Three shrinking storeys stacked, each open to the one below — the shape from playtest four.
		// A rectangular scanner rejects this outright; a fill does not care.
		World world = new World();
		world.box(0, 0, 0, 8, 3, 8, ShellKind.GLASS);
		world.box(2, 3, 2, 6, 6, 6, ShellKind.GLASS);
		// The lower roof exists only where the upper storey does NOT stand on it, or the two would be
		// separate rooms rather than one.
		world.slab(0, 3, 0, 8, 8, ShellKind.GLASS);
		for (int x = 3; x <= 5; x++) {
			for (int z = 3; z <= 5; z++) {
				world.put(x, 3, z, ShellKind.OTHER); // the opening between the storeys
			}
		}
		world.put(4, 1, 8, ShellKind.CONTROLLER);

		RoomFill.Result result = fill(world, 4, 1, 8);
		assertTrue(result.sealed(), "a stepped pyramid must seal: " + result.status());
		// Lower storey 7×2×7 = 98, the gap 3×1×3 = 9, upper storey 3×2×3 = 18.
		assertEquals(125, result.volume());
	}

	@Test
	void oneMissingBlockLeaks() {
		World world = new World().box(0, 0, 0, 6, 6, 6, ShellKind.CASING);
		world.put(3, 3, 6, ShellKind.CONTROLLER);
		world.put(0, 3, 3, ShellKind.OTHER); // a single block prised out of the far wall
		RoomFill.Result result = fill(world, 3, 3, 6);
		assertFalse(result.sealed());
		assertEquals(RoomFill.Status.UNSEALED, result.status());
	}

	@Test
	void aRoomTooSmallIsRejected() {
		World world = new World().box(0, 0, 0, 2, 2, 2, ShellKind.CASING);
		world.put(1, 1, 2, ShellKind.CONTROLLER);
		RoomFill.Result result = fill(world, 1, 1, 2);
		assertEquals(RoomFill.Status.TOO_SMALL, result.status(), "one interior cell is not a greenhouse");
	}

	@Test
	void aBuriedControllerIsRejected() {
		World world = new World().box(0, 0, 0, 6, 6, 6, ShellKind.CASING);
		world.put(3, 3, 6, ShellKind.CONTROLLER);
		world.put(3, 3, 5, ShellKind.CASING); // walled in right behind its own face
		RoomFill.Result result = fill(world, 3, 3, 6);
		assertEquals(RoomFill.Status.CONTROLLER_NOT_IN_WALL, result.status());
		assertEquals(3, result.x());
		assertEquals(5, result.z());
	}

	@Test
	void aSecondControllerIsRejected() {
		World world = new World().box(0, 0, 0, 6, 6, 6, ShellKind.CASING);
		world.put(3, 3, 6, ShellKind.CONTROLLER);
		world.put(3, 3, 0, ShellKind.CONTROLLER); // a rival brain in the opposite wall
		RoomFill.Result result = fill(world, 3, 3, 6);
		assertEquals(RoomFill.Status.SECOND_CONTROLLER, result.status());
		assertEquals(0, result.z(), "the report must point at the rival, not at us");
	}

	@Test
	void framingIsToldApartFromWalls() {
		// The corners and edges of a box touch the interior only diagonally. They must still be part
		// of the shell — a sweep that missed them would leave a finished room unpainted along every
		// seam — and they must be marked as framing, which is what keeps the outline drawn.
		World world = new World().box(0, 0, 0, 6, 6, 6, ShellKind.CASING);
		world.put(3, 3, 6, ShellKind.CONTROLLER);
		RoomFill.Result result = fill(world, 3, 3, 6);
		assertTrue(result.sealed());

		Set<String> shell = new HashSet<>();
		boolean cornerIsFraming = false;
		boolean wallIsNotFraming = false;
		int[] cells = result.shell();
		for (int i = 0; i < cells.length; i += 3) {
			int x = cells[i];
			int y = cells[i + 1];
			int z = cells[i + 2];
			shell.add(x + "," + y + "," + z);
			if (x == 0 && y == 0 && z == 0) {
				cornerIsFraming = result.shellIsEdge()[i / 3];
			}
			if (x == 3 && y == 3 && z == 0) {
				wallIsNotFraming = !result.shellIsEdge()[i / 3];
			}
		}
		assertTrue(shell.contains("0,0,0"), "a corner belongs to the shell even though it faces nothing");
		assertTrue(cornerIsFraming, "a corner faces no interior cell, so it is framing");
		assertTrue(wallIsNotFraming, "a wall cell faces the interior, so it is not framing");
	}

	@Test
	void theFillStaysOnItsLeash() {
		// Open on one side and no shell anywhere: without the span limit this would walk until the
		// cell budget ran out, reading blocks dozens of chunks away on the server thread.
		World world = new World();
		world.put(0, 0, 1, ShellKind.CONTROLLER);
		RoomFill.Result result = RoomFill.fill(world, 0, 0, 1, 0, 0, -1, 8, 4096, 4);
		assertEquals(RoomFill.Status.UNSEALED, result.status());
		assertTrue(Math.abs(result.x()) <= 5 && Math.abs(result.z()) <= 5,
				"the reported cell must be at the leash, not far beyond it");
	}
}
