package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.structure.RoomScan.Result;
import dev.alaindustrial.core.structure.RoomScan.ShellKind;
import dev.alaindustrial.core.structure.RoomScan.Status;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link RoomScan} (MOD-468 stage 1) — every branch of the reactor-room geometry.
 *
 * <p>The room is the first volumetric multiblock in the mod, and its failure modes are exactly the
 * ones a player hits while building: a missed block in a 14³ shell, a door in the floor, a room one
 * block too big. Each of those has to produce a <em>specific</em> status and a <em>usable</em>
 * position — a scanner that answered "not formed" for all of them would pass a naive test and be
 * useless in game, so every case here asserts the coordinates too.
 */
class RoomScanTest {

	/**
	 * A hollow box built in a {@link HashMap}: casing everywhere on the perimeter, air inside, plus
	 * whatever the individual test overrides. Interior spans {@code [1..sx] × [1..sy] × [1..sz]}, so
	 * the shell occupies {@code [0..sx+1]} and the controller can sit at {@code x = 0}.
	 */
	private static final class Room {
		private final Map<Long, ShellKind> cells = new HashMap<>();
		private final int sx;
		private final int sy;
		private final int sz;

		Room(int sx, int sy, int sz) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			for (int x = 0; x <= sx + 1; x++) {
				for (int y = 0; y <= sy + 1; y++) {
					for (int z = 0; z <= sz + 1; z++) {
						boolean perimeter = x == 0 || x == sx + 1
								|| y == 0 || y == sy + 1
								|| z == 0 || z == sz + 1;
						if (perimeter) {
							put(x, y, z, ShellKind.CASING);
						}
					}
				}
			}
		}

		/** Controller in the −X wall, plus a two-cell doorway in the −Z wall at floor level. */
		Room withControllerAndDoor() {
			put(0, 1, 1, ShellKind.CONTROLLER);
			put(1, 1, 0, ShellKind.DOOR);
			put(1, 2, 0, ShellKind.DOOR);
			return this;
		}

		Room put(int x, int y, int z, ShellKind kind) {
			cells.put(key(x, y, z), kind);
			return this;
		}

		RoomScan.ShellProbe probe() {
			return (x, y, z) -> cells.getOrDefault(key(x, y, z), ShellKind.OTHER);
		}

		/** Scans from the controller at {@code (0,1,1)} looking towards +X (into the room). */
		Result scan() {
			return RoomScan.scan(probe(), 0, 1, 1, 1, 0, 0);
		}

		Result scan(int minInner, int maxInner) {
			return RoomScan.scan(probe(), 0, 1, 1, 1, 0, 0, minInner, maxInner);
		}

		private static long key(int x, int y, int z) {
			// Packing is only an identity here; correctness of the scan must not depend on its ordering
			// (the lesson of blockpos-aslong-not-monotone).
			return ((long) (x + 512) << 42) | ((long) (y + 512) << 21) | (z + 512);
		}
	}

	@Test
	void sealedMinimumRoomIsFormed() {
		Result r = new Room(3, 3, 3).withControllerAndDoor().scan();
		assertTrue(r.formed(), () -> "expected FORMED, got " + r.status());
		assertEquals(3, r.sizeX());
		assertEquals(3, r.sizeY());
		assertEquals(3, r.sizeZ());
		assertEquals(1, r.minX());
		assertEquals(3, r.maxZ());
	}

	@Test
	void largestAllowedRoomIsFormed() {
		Result r = new Room(12, 12, 12).withControllerAndDoor().scan();
		assertTrue(r.formed(), () -> "expected FORMED, got " + r.status());
		assertEquals(12, r.sizeX());
	}

	/** Glass, ports and doors are shell too — a room walled in them is just as sealed as casing. */
	@Test
	void glassAndPortCountAsShell() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		room.put(2, 2, 0, ShellKind.GLASS);
		room.put(3, 2, 0, ShellKind.GLASS);
		room.put(2, 1, 4, ShellKind.PORT);
		assertTrue(room.scan().formed());
	}

	/**
	 * Windows are allowed, a glass box is not. Without the cap the cheap option would also be the
	 * strictly best one — glass is shell, so a room made entirely of it would seal just as well while
	 * costing less and looking better.
	 */
	@Test
	void aShellMostlyOfGlassIsRejected() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		// Two whole faces: 45 distinct cells of a 98-cell shell (~46%). One face alone is 25 cells —
		// about 25%, deliberately UNDER the 30% cap, which is exactly the boundary this test would
		// have sat on the wrong side of.
		glazeFaceX(room, 4);
		glazeFaceZ(room, 4);
		assertEquals(Status.TOO_MUCH_GLASS, room.scan().status());
	}

	private static void glazeFaceX(Room room, int x) {
		for (int y = 0; y <= 4; y++) {
			for (int z = 0; z <= 4; z++) {
				room.put(x, y, z, ShellKind.GLASS);
			}
		}
	}

	private static void glazeFaceZ(Room room, int z) {
		for (int y = 0; y <= 4; y++) {
			for (int x = 0; x <= 4; x++) {
				room.put(x, y, z, ShellKind.GLASS);
			}
		}
	}

	/** A few windows stay legal — the cap must not turn into "no glass at all". */
	@Test
	void aFewWindowsAreFine() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		room.put(4, 1, 1, ShellKind.GLASS);
		room.put(4, 1, 2, ShellKind.GLASS);
		room.put(4, 2, 1, ShellKind.GLASS);
		room.put(4, 2, 2, ShellKind.GLASS);
		assertTrue(room.scan().formed(), () -> "four windows should be fine, got " + room.scan().status());
	}

	/** The cap is a parameter: a permissive server may allow the glass box its players want. */
	@Test
	void theGlassCapIsConfigurable() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		glazeFaceX(room, 4);
		glazeFaceZ(room, 4);
		assertEquals(Status.TOO_MUCH_GLASS, room.scan().status());
		assertTrue(RoomScan.scan(room.probe(), 0, 1, 1, 1, 0, 0, 3, 12, 100).formed(),
				"a 100% cap must accept any amount of glass");
	}

	/** Just under the cap passes, just over it fails — the boundary itself, on a known shell size. */
	@Test
	void theGlassCapBoundaryIsExact() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		glazeFaceX(room, 4); // 25 glass cells of 98 shell cells
		// 25/98 is 25.5%: legal at 30, illegal at 25 (25*100 = 2500 > 25*98 = 2450).
		assertTrue(RoomScan.scan(room.probe(), 0, 1, 1, 1, 0, 0, 3, 12, 30).formed());
		assertEquals(Status.TOO_MUCH_GLASS,
				RoomScan.scan(room.probe(), 0, 1, 1, 1, 0, 0, 3, 12, 25).status());
	}

	/** The whole point of the perimeter walk: one missing block anywhere, reported by position. */
	@Test
	void singleHoleIsReportedWithCoordinates() {
		Room room = new Room(4, 4, 4).withControllerAndDoor();
		room.put(3, 5, 2, ShellKind.OTHER); // a ceiling block the player forgot
		Result r = room.scan();
		assertEquals(Status.BREACH, r.status());
		assertEquals(3, r.x());
		assertEquals(5, r.y());
		assertEquals(2, r.z());
		assertFalse(r.formed());
	}

	/** A hole in a corner is still a hole — corners are load-bearing for the seal, not decoration. */
	@Test
	void cornerHoleIsABreach() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		room.put(0, 0, 0, ShellKind.OTHER);
		assertEquals(Status.BREACH, room.scan().status());
	}

	/**
	 * A sealed room with no door at all is FORMED (player request, 2026-08-20). Walling yourself in and
	 * mining back out is a legitimate, cheaper way to run a reactor; the shell only has to be sealed,
	 * and how the player gets inside is their business.
	 */
	@Test
	void roomWithoutADoorIsStillSealed() {
		Room room = new Room(3, 3, 3);
		room.put(0, 1, 1, ShellKind.CONTROLLER);
		assertEquals(Status.FORMED, room.scan().status());
	}

	/** One door cell is a window, not a doorway: the player still cannot walk in. */
	@Test
	void singleDoorCellIsNotADoorway() {
		Room room = new Room(3, 3, 3);
		room.put(0, 1, 1, ShellKind.CONTROLLER);
		room.put(1, 1, 0, ShellKind.DOOR);
		assertEquals(Status.NO_DOORWAY, room.scan().status());
	}

	/** Two door cells stacked above floor level are a window as well — the doorway must reach the floor. */
	@Test
	void doorwayMustStartAtFloorLevel() {
		Room room = new Room(4, 4, 4);
		room.put(0, 1, 1, ShellKind.CONTROLLER);
		room.put(1, 2, 0, ShellKind.DOOR);
		room.put(1, 3, 0, ShellKind.DOOR);
		assertEquals(Status.NO_DOORWAY, room.scan().status());
	}

	/** A door laid into the floor cannot be walked through; it must never satisfy the doorway test. */
	@Test
	void doorInTheFloorIsNotADoorway() {
		Room room = new Room(3, 3, 3);
		room.put(0, 1, 1, ShellKind.CONTROLLER);
		room.put(2, 0, 2, ShellKind.DOOR);
		assertEquals(Status.NO_DOORWAY, room.scan().status());
	}

	@Test
	void interiorSmallerThanTheMinimumIsRejected() {
		Result r = new Room(2, 3, 3).withControllerAndDoor().scan();
		assertEquals(Status.TOO_SMALL, r.status());
		assertEquals(2, r.sizeX(), "the measured box is reported so the player sees which axis is short");
	}

	@Test
	void interiorLargerThanTheMaximumIsRejected() {
		Result r = new Room(13, 3, 3).withControllerAndDoor().scan();
		assertEquals(Status.TOO_LARGE, r.status());
	}

	/** The limits are parameters, not constants — the config keys drive them. */
	@Test
	void limitsAreConfigurable() {
		Room room = new Room(2, 2, 2);
		room.put(0, 1, 1, ShellKind.CONTROLLER);
		room.put(1, 1, 0, ShellKind.DOOR);
		room.put(1, 2, 0, ShellKind.DOOR);
		assertEquals(Status.TOO_SMALL, room.scan().status());
		assertTrue(room.scan(2, 12).formed(), "a lowered minimum must accept the same room");
	}

	/** Nothing within reach in one direction: an open side, or a room past the size cap. */
	@Test
	void unboundedDirectionIsReported() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		for (int y = 0; y <= 4; y++) {
			for (int z = 0; z <= 4; z++) {
				room.put(4, y, z, ShellKind.OTHER); // tear off the far +X wall entirely
			}
		}
		Result r = room.scan();
		assertEquals(Status.ROOM_UNBOUNDED, r.status());
	}

	/** A controller buried behind casing is not in a wall — it faces no interior at all. */
	@Test
	void controllerFacingSolidBlockIsRejected() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		room.put(1, 1, 1, ShellKind.CASING); // fill the cell the controller looks at
		Result r = room.scan();
		assertEquals(Status.CONTROLLER_NOT_IN_WALL, r.status());
		assertEquals(1, r.x());
	}

	/** A controller sitting in a vertical edge belongs to no wall, even though its seed reads open. */
	@Test
	void controllerInAnEdgeIsRejected() {
		Room room = new Room(3, 3, 3);
		room.put(1, 1, 0, ShellKind.DOOR);
		room.put(1, 2, 0, ShellKind.DOOR);
		room.put(0, 0, 1, ShellKind.CONTROLLER); // −X wall meets the floor: an edge cell
		Result r = RoomScan.scan(room.probe(), 0, 0, 1, 1, 0, 0);
		assertEquals(Status.CONTROLLER_NOT_IN_WALL, r.status());
	}

	/** One room, one brain: a second controller in the same shell is an error, with its position. */
	@Test
	void secondControllerIsReported() {
		Room room = new Room(3, 3, 3).withControllerAndDoor();
		room.put(4, 3, 3, ShellKind.CONTROLLER);
		Result r = room.scan();
		assertEquals(Status.SECOND_CONTROLLER, r.status());
		assertEquals(4, r.x());
		assertEquals(3, r.y());
		assertEquals(3, r.z());
	}

	/** Interior contents are the player's business — machines inside must not break the seal. */
	@Test
	void blocksInsideTheRoomDoNotBreakTheScan() {
		Room room = new Room(4, 4, 4).withControllerAndDoor();
		room.put(2, 1, 2, ShellKind.OTHER);
		room.put(3, 1, 3, ShellKind.CASING); // even a stray casing block inside is legal
		assertTrue(room.scan().formed());
	}

	/** The scan must not care where the room sits in the world — negative coordinates included. */
	@Test
	void negativeCoordinatesBehaveTheSame() {
		Map<Long, ShellKind> cells = new HashMap<>();
		int ox = -40;
		int oy = -12;
		int oz = -300;
		for (int x = 0; x <= 4; x++) {
			for (int y = 0; y <= 4; y++) {
				for (int z = 0; z <= 4; z++) {
					boolean perimeter = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
					if (perimeter) {
						cells.put(Room.key(ox + x, oy + y, oz + z), ShellKind.CASING);
					}
				}
			}
		}
		cells.put(Room.key(ox, oy + 1, oz + 1), ShellKind.CONTROLLER);
		cells.put(Room.key(ox + 1, oy + 1, oz), ShellKind.DOOR);
		cells.put(Room.key(ox + 1, oy + 2, oz), ShellKind.DOOR);

		RoomScan.ShellProbe probe = (x, y, z) -> cells.getOrDefault(Room.key(x, y, z), ShellKind.OTHER);
		Result r = RoomScan.scan(probe, ox, oy + 1, oz + 1, 1, 0, 0);
		assertTrue(r.formed(), () -> "expected FORMED, got " + r.status());
		assertEquals(3, r.sizeY());
	}

	/**
	 * The ceiling is geometrically a face like any other, but the controller is a panel the player
	 * reads and wires, so the design restricts it to vertical walls. That restriction is a rule, not a
	 * side effect — it gets its own test, or the next refactor quietly allows ceiling controllers again.
	 */
	@Test
	void controllerInTheCeilingIsRejected() {
		Room room = new Room(3, 3, 3);
		room.put(2, 4, 2, ShellKind.CONTROLLER);
		room.put(1, 1, 0, ShellKind.DOOR);
		room.put(1, 2, 0, ShellKind.DOOR);
		Result r = RoomScan.scan(room.probe(), 2, 4, 2, 0, -1, 0);
		assertEquals(Status.CONTROLLER_NOT_IN_WALL, r.status());
	}

	/** A floor controller is rejected for the same reason, and must not be missed by an off-by-one. */
	@Test
	void controllerInTheFloorIsRejected() {
		Room room = new Room(3, 3, 3);
		room.put(2, 0, 2, ShellKind.CONTROLLER);
		room.put(1, 1, 0, ShellKind.DOOR);
		room.put(1, 2, 0, ShellKind.DOOR);
		Result r = RoomScan.scan(room.probe(), 2, 0, 2, 0, 1, 0);
		assertEquals(Status.CONTROLLER_NOT_IN_WALL, r.status());
	}

	/**
	 * One block over the limit must read as TOO_LARGE, not as ROOM_UNBOUNDED. The distinction is the
	 * difference between "shrink it by one" and "you have no wall there", and it only survives because
	 * the rays deliberately reach one step past the cap.
	 */
	@Test
	void oneBlockOverTheLimitReportsTooLargeRatherThanUnbounded() {
		Result r = new Room(13, 3, 3).withControllerAndDoor().scan();
		assertEquals(Status.TOO_LARGE, r.status());
		assertEquals(13, r.sizeX());
	}

	/** Far past the limit there is nothing to measure, so the honest answer is "no far wall". */
	@Test
	void farBeyondTheLimitReportsUnbounded() {
		Result r = new Room(20, 3, 3).withControllerAndDoor().scan();
		assertEquals(Status.ROOM_UNBOUNDED, r.status());
	}
}
