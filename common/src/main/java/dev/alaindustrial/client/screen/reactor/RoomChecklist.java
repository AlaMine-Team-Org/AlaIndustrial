package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.block.entity.ReactorRoomStatus;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The «Room» tab's checklist (MOD-619): the scanner's checks in the order it runs them, and what one verdict
 * says about each.
 *
 * <p><b>Minecraft-free on purpose</b>, like {@link ReactorConsole}: which check a verdict fails, and which
 * checks it has therefore already passed, is a judgement about {@code RoomScan}'s order — exactly the kind of
 * mapping that drifts silently when the scanner is reordered, and so belongs under an L1 test.
 *
 * <p><b>Two rows for one status.</b> The scanner reports {@code CONTROLLER_NOT_IN_WALL} from two different
 * places. Before its rays it finds the block behind the controller's face solid: nothing is measured, and
 * this is the first check. During and after the perimeter walk it finds the controller in the floor, the
 * ceiling or an edge: the rays have measured the box by then, so the walls and the size have passed. The
 * box being measured is what tells the two apart, and the checklist shows them as two rows rather than
 * marking the first row failed above two rows it knows have passed.
 */
public final class RoomChecklist {

	private static final String KEY = "gui.alaindustrial.reactor_controller.";

	private RoomChecklist() {
	}

	/** The checks, in the order {@code RoomScan} runs them. */
	public enum Check {
		/** The block behind the controller's face is open: it looks into a room. */
		FACES_INTERIOR,
		/** A shell block stands within reach in all six directions. */
		WALLS_FOUND,
		/** Every interior edge within this server's limits. */
		SIZE,
		/** Every cell of the six faces is a shell block. */
		SEALED,
		/** The controller is a cell of a vertical wall — not the floor, the ceiling or an edge. */
		IN_WALL,
		/** No second controller in the shell. */
		ONE_CONTROLLER,
		/** A door, if there is one, stands two blocks tall on the floor. */
		DOORWAY,
		/** No more of the shell is glass than this server allows. */
		GLASS;

		public String translationKey() {
			return KEY + "check." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** What the last scan says about one check. */
	public enum Mark {
		PASSED, FAILED, UNCHECKED
	}

	/**
	 * The check a verdict fails, or {@code null} for a sealed room.
	 *
	 * @param boxMeasured whether the scan measured the interior box — what separates the two places the
	 *                    scanner reports a misplaced controller from
	 */
	public static @Nullable Check failing(ReactorRoomStatus status, boolean boxMeasured) {
		return switch (status) {
			case FORMED -> null;
			case CONTROLLER_NOT_IN_WALL -> boxMeasured ? Check.IN_WALL : Check.FACES_INTERIOR;
			case ROOM_UNBOUNDED -> Check.WALLS_FOUND;
			case TOO_SMALL, TOO_LARGE -> Check.SIZE;
			case BREACH -> Check.SEALED;
			case SECOND_CONTROLLER -> Check.ONE_CONTROLLER;
			case NO_DOORWAY -> Check.DOORWAY;
			case TOO_MUCH_GLASS -> Check.GLASS;
		};
	}

	/**
	 * One check's mark: passed if the scanner ran it and moved on, failed if it stopped there, unchecked if it
	 * never got that far.
	 *
	 * <p>One exception to "everything before the failure passed": a controller in the floor or the ceiling is
	 * found in the middle of the perimeter walk, so a scan that stopped there has not finished looking for
	 * holes. The seal is left unchecked rather than claimed.
	 */
	public static Mark mark(Check check, ReactorRoomStatus status, boolean boxMeasured) {
		Check failed = failing(status, boxMeasured);
		if (failed == null) {
			return Mark.PASSED;
		}
		if (check == failed) {
			return Mark.FAILED;
		}
		if (failed == Check.IN_WALL && check == Check.SEALED) {
			return Mark.UNCHECKED;
		}
		return check.ordinal() < failed.ordinal() ? Mark.PASSED : Mark.UNCHECKED;
	}
}
