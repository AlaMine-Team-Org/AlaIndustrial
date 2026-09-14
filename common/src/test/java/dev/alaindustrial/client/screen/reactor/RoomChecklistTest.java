package dev.alaindustrial.client.screen.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.client.screen.reactor.RoomChecklist.Check;
import dev.alaindustrial.client.screen.reactor.RoomChecklist.Mark;
import dev.alaindustrial.core.structure.RoomScan;
import dev.alaindustrial.core.structure.RoomScan.ShellKind;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link RoomChecklist} (MOD-619).
 *
 * <p>Two halves. The first pins the marks each verdict draws. The second is the one that can catch a real
 * drift: it builds rooms with TWO faults and asks the real {@link RoomScan} which one it reports — the
 * checklist's order is only right if the scanner names the fault of the earlier row every time.
 */
class RoomChecklistTest {

	@Test
	void aSealedRoomPassesEveryCheck() {
		assertNull(RoomChecklist.failing(ReactorRoomStatus.FORMED, true));
		for (Check check : Check.values()) {
			assertEquals(Mark.PASSED, RoomChecklist.mark(check, ReactorRoomStatus.FORMED, true), check::name);
		}
	}

	@Test
	void everyFaultMarksItsOwnRowAndNothingPastIt() {
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			for (boolean measured : new boolean[] {false, true}) {
				Check failed = RoomChecklist.failing(status, measured);
				if (failed == null) {
					continue;
				}
				for (Check check : Check.values()) {
					Mark mark = RoomChecklist.mark(check, status, measured);
					if (check == failed) {
						assertEquals(Mark.FAILED, mark, () -> status + " should fail " + check);
					} else if (check.ordinal() > failed.ordinal()) {
						assertEquals(Mark.UNCHECKED, mark, () -> status + " never got as far as " + check);
					}
				}
			}
		}
	}

	@Test
	void eachVerdictFailsTheRightCheck() {
		assertEquals(Check.WALLS_FOUND, RoomChecklist.failing(ReactorRoomStatus.ROOM_UNBOUNDED, false));
		assertEquals(Check.SIZE, RoomChecklist.failing(ReactorRoomStatus.TOO_SMALL, true));
		assertEquals(Check.SIZE, RoomChecklist.failing(ReactorRoomStatus.TOO_LARGE, true));
		assertEquals(Check.SEALED, RoomChecklist.failing(ReactorRoomStatus.BREACH, true));
		assertEquals(Check.ONE_CONTROLLER, RoomChecklist.failing(ReactorRoomStatus.SECOND_CONTROLLER, true));
		assertEquals(Check.DOORWAY, RoomChecklist.failing(ReactorRoomStatus.NO_DOORWAY, true));
		assertEquals(Check.GLASS, RoomChecklist.failing(ReactorRoomStatus.TOO_MUCH_GLASS, true));
	}

	/**
	 * The one status reported from two places. Unmeasured, the controller faces a solid block and nothing
	 * else was looked at; measured, the walls and the size have passed, and the seal is not claimed because
	 * a floor or ceiling controller stops the perimeter walk half way.
	 */
	@Test
	void aMisplacedControllerIsTwoDifferentRows() {
		ReactorRoomStatus status = ReactorRoomStatus.CONTROLLER_NOT_IN_WALL;
		assertEquals(Check.FACES_INTERIOR, RoomChecklist.failing(status, false));
		assertEquals(Mark.UNCHECKED, RoomChecklist.mark(Check.WALLS_FOUND, status, false));

		assertEquals(Check.IN_WALL, RoomChecklist.failing(status, true));
		assertEquals(Mark.PASSED, RoomChecklist.mark(Check.FACES_INTERIOR, status, true));
		assertEquals(Mark.PASSED, RoomChecklist.mark(Check.WALLS_FOUND, status, true));
		assertEquals(Mark.PASSED, RoomChecklist.mark(Check.SIZE, status, true));
		assertEquals(Mark.UNCHECKED, RoomChecklist.mark(Check.SEALED, status, true));
		assertEquals(Mark.FAILED, RoomChecklist.mark(Check.IN_WALL, status, true));
	}

	@Test
	void aBreachHasPassedEverythingBeforeTheSeal() {
		assertEquals(Mark.PASSED, RoomChecklist.mark(Check.FACES_INTERIOR, ReactorRoomStatus.BREACH, true));
		assertEquals(Mark.PASSED, RoomChecklist.mark(Check.WALLS_FOUND, ReactorRoomStatus.BREACH, true));
		assertEquals(Mark.PASSED, RoomChecklist.mark(Check.SIZE, ReactorRoomStatus.BREACH, true));
		assertEquals(Mark.FAILED, RoomChecklist.mark(Check.SEALED, ReactorRoomStatus.BREACH, true));
		assertEquals(Mark.UNCHECKED, RoomChecklist.mark(Check.IN_WALL, ReactorRoomStatus.BREACH, true));
	}

	// ── The scanner's real order ────────────────────────────────────────────────────────────────────

	@Test
	void aSolidBlockBehindTheFaceIsReportedBeforeAnythingElse() {
		Room room = new Room(2, 3, 3); // also too small
		room.put(1, 1, 1, ShellKind.CASING);
		assertRow(Check.FACES_INTERIOR, room.scan());
	}

	@Test
	void theSizeIsReportedBeforeAHole() {
		Room room = new Room(2, 3, 3);
		room.put(2, 4, 2, ShellKind.OTHER);
		assertRow(Check.SIZE, room.scan());
	}

	/** Glass goes on first in these rooms: glazing a face afterwards would paint over the other faults. */
	@Test
	void aHoleIsReportedBeforeASecondControllerADoorOrGlass() {
		Room room = new Room(3, 3, 3);
		glazeFace(room, 4);
		glazeFaceZ(room, 4);
		room.put(4, 2, 2, ShellKind.CONTROLLER);
		room.put(2, 1, 0, ShellKind.DOOR);
		room.put(2, 4, 2, ShellKind.OTHER);
		assertRow(Check.SEALED, room.scan());
	}

	/**
	 * A controller in the ceiling looks into an open interior, so the rays measure the box and the walk finds
	 * the fault — the second place the scanner reports a misplaced controller from.
	 */
	@Test
	void aControllerInTheCeilingIsReportedBeforeASecondController() {
		Room room = new Room(3, 3, 3);
		room.put(0, 1, 1, ShellKind.CASING);
		room.put(2, 4, 2, ShellKind.CONTROLLER);
		room.put(4, 2, 2, ShellKind.CONTROLLER);
		assertRow(Check.IN_WALL, RoomScan.scan(room.probe(), 2, 4, 2, 0, -1, 0));
	}

	@Test
	void aSecondControllerIsReportedBeforeADoorOrGlass() {
		Room room = new Room(3, 3, 3);
		glazeFace(room, 4);
		glazeFaceZ(room, 4);
		room.put(4, 2, 2, ShellKind.CONTROLLER);
		room.put(2, 1, 0, ShellKind.DOOR);
		assertRow(Check.ONE_CONTROLLER, room.scan());
	}

	@Test
	void aDoorIsReportedBeforeGlass() {
		Room room = new Room(3, 3, 3);
		room.put(2, 1, 0, ShellKind.DOOR);
		glazeFace(room, 4);
		glazeFaceZ(room, 4);
		assertRow(Check.DOORWAY, room.scan());
	}

	private static void assertRow(Check expected, RoomScan.Result result) {
		boolean measured = result.maxX() >= result.minX();
		assertEquals(expected, RoomChecklist.failing(ReactorRoomStatus.of(result.status()), measured),
				() -> "scanner reported " + result.status() + " (box measured: " + measured + ")");
	}

	private static void glazeFace(Room room, int x) {
		for (int y = 0; y <= 4; y++) {
			for (int z = 0; z <= 4; z++) {
				room.put(x, y, z, ShellKind.GLASS);
			}
		}
	}

	private static void glazeFaceZ(Room room, int z) {
		for (int y = 0; y <= 4; y++) {
			for (int x = 1; x <= 4; x++) {
				room.put(x, y, z, ShellKind.GLASS);
			}
		}
	}

	/** A casing box with interior {@code [1..sx] × [1..sy] × [1..sz]} and the controller at {@code (0,1,1)}. */
	private static final class Room {
		private final Map<Long, ShellKind> cells = new HashMap<>();

		Room(int sx, int sy, int sz) {
			for (int x = 0; x <= sx + 1; x++) {
				for (int y = 0; y <= sy + 1; y++) {
					for (int z = 0; z <= sz + 1; z++) {
						if (x == 0 || x == sx + 1 || y == 0 || y == sy + 1 || z == 0 || z == sz + 1) {
							put(x, y, z, ShellKind.CASING);
						}
					}
				}
			}
			put(0, 1, 1, ShellKind.CONTROLLER);
		}

		void put(int x, int y, int z, ShellKind kind) {
			cells.put(((long) (x + 512) << 42) | ((long) (y + 512) << 21) | (z + 512), kind);
		}

		RoomScan.ShellProbe probe() {
			return (x, y, z) -> cells.getOrDefault(((long) (x + 512) << 42) | ((long) (y + 512) << 21) | (z + 512),
					ShellKind.OTHER);
		}

		RoomScan.Result scan() {
			return RoomScan.scan(probe(), 0, 1, 1, 1, 0, 0);
		}
	}
}
