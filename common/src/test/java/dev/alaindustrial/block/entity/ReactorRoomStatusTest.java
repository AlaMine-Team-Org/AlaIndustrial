package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.structure.RoomScan;
import java.util.EnumSet;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ReactorRoomStatus} (MOD-468) — the wire-and-lang face of the room scanner.
 *
 * <p>Small, but not trivial: this enum is where a scanner verdict becomes an ordinal on a sync
 * channel and a translation key on screen. Both are the kind of mapping that breaks silently — a
 * missed {@code switch} arm stops compiling, but a <em>reordered</em> constant does not, and it
 * would make every client read the wrong status.
 */
class ReactorRoomStatusTest {

	/** Every scanner verdict maps to a status; the switch is total, so a new one cannot be forgotten. */
	@Test
	void everyScanStatusMaps() {
		for (RoomScan.Status status : RoomScan.Status.values()) {
			assertNotNull(ReactorRoomStatus.of(status), () -> "no mapping for " + status);
		}
	}

	/** The two enums are parallel by name — the mapping is an identity, and drift would be a bug. */
	@Test
	void namesLineUpWithTheScannerEnum() {
		for (RoomScan.Status status : RoomScan.Status.values()) {
			assertEquals(status.name(), ReactorRoomStatus.of(status).name());
		}
	}

	/**
	 * The ordinal is the wire format. Pinning the first and last constants catches an insertion in the
	 * middle, which is the change that would silently re-label every status on the client.
	 */
	@Test
	void ordinalsAreTheWireFormat() {
		assertEquals(0, ReactorRoomStatus.FORMED.ordinal());
		assertEquals(ReactorRoomStatus.TOO_MUCH_GLASS,
				ReactorRoomStatus.values()[ReactorRoomStatus.values().length - 1]);
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			assertEquals(status, ReactorRoomStatus.byOrdinal(status.ordinal()));
		}
	}

	/** A short channel can carry junk after a desync; the screen must show something, not crash. */
	@Test
	void outOfRangeOrdinalsFallBackInsteadOfThrowing() {
		assertEquals(ReactorRoomStatus.CONTROLLER_NOT_IN_WALL, ReactorRoomStatus.byOrdinal(-1));
		assertEquals(ReactorRoomStatus.CONTROLLER_NOT_IN_WALL, ReactorRoomStatus.byOrdinal(999));
		assertEquals(ReactorRoomStatus.CONTROLLER_NOT_IN_WALL, ReactorRoomStatus.byOrdinal(
				ReactorRoomStatus.values().length));
	}

	/** Success is the only quiet state; everything else is something the player has to go and fix. */
	@Test
	void onlyFormedIsSilent() {
		assertFalse(ReactorRoomStatus.FORMED.needsAttention());
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			if (status != ReactorRoomStatus.FORMED) {
				assertTrue(status.needsAttention(), () -> status + " should ask for attention");
			}
		}
	}

	/**
	 * A status advertises a position only when walking there helps. Pinning the exact set is the point:
	 * marking, say, {@code NO_DOORWAY} as located would send the player to a spot that means nothing.
	 */
	@Test
	void onlyPositionalStatusesAdvertiseALocation() {
		EnumSet<ReactorRoomStatus> located = EnumSet.of(
				ReactorRoomStatus.BREACH,
				ReactorRoomStatus.SECOND_CONTROLLER,
				ReactorRoomStatus.CONTROLLER_NOT_IN_WALL,
				ReactorRoomStatus.TOO_MUCH_GLASS);
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			assertEquals(located.contains(status), status.hasLocation(),
					() -> status + ": hasLocation() disagrees with the intended set");
		}
	}

	/**
	 * An unbounded room must NOT advertise a location. Its "position" is wherever the ray gave up —
	 * up to 13 blocks away, usually open ground with nothing built on it. A lone controller pointing at
	 * a hillside was a real complaint from the first playtest, so this is a regression guard, not a
	 * restatement of the implementation.
	 */
	@Test
	void unboundedRoomsPointAtNothing() {
		assertFalse(ReactorRoomStatus.ROOM_UNBOUNDED.hasLocation(),
				"a ray that found no wall has no offending block to send the player to");
	}

	/** The measured box is only meaningful where a box was actually measured. */
	@Test
	void onlySizeStatusesAdvertiseASize() {
		EnumSet<ReactorRoomStatus> sized = EnumSet.of(
				ReactorRoomStatus.FORMED, ReactorRoomStatus.TOO_SMALL, ReactorRoomStatus.TOO_LARGE);
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			assertEquals(sized.contains(status), status.hasSize(),
					() -> status + ": hasSize() disagrees with the intended set");
		}
	}

	/** Keys are derived, not hand-written, so they cannot drift from the constant they belong to. */
	@Test
	void translationKeysFollowTheConstantName() {
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			String key = status.translationKey();
			assertTrue(key.startsWith("gui.alaindustrial.reactor_controller.status."), key);
			assertEquals(key.toLowerCase(Locale.ROOT), key, "lang keys are lower case");
			assertTrue(key.endsWith(status.name().toLowerCase(Locale.ROOT)), key);
		}
	}
}
