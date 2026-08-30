package dev.alaindustrial.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * The datapack names of the reactor milestones (MOD-473).
 *
 * <p>These strings are written into {@code data/alaindustrial/advancement/reactor_*.json}, so a rename
 * on either side silently unearns the advancement — the trigger stops matching and nothing throws.
 * That is the whole reason this mapping is asserted rather than trusted.
 */
class ReactorMilestoneTest {

	@Test
	void everyMilestoneRoundTripsThroughItsDatapackName() {
		for (ReactorMilestone milestone : ReactorMilestone.values()) {
			assertEquals(milestone, ReactorMilestone.byId(milestone.id()), milestone.name());
		}
	}

	@Test
	void theNamesAreTheOnesTheDatapackShips() {
		assertEquals("room_sealed", ReactorMilestone.ROOM_SEALED.id());
		assertEquals("power", ReactorMilestone.POWER.id());
		assertEquals("steam", ReactorMilestone.STEAM.id());
		assertEquals("meltdown", ReactorMilestone.MELTDOWN.id());
		assertEquals("blast", ReactorMilestone.BLAST.id());
	}

	/**
	 * A typo in a datapack leaves that advancement unearnable instead of handing it out on the first
	 * reactor that runs — the trigger asks {@code byId} and compares, so an unknown name must never
	 * resolve to a real milestone.
	 */
	@Test
	void anUnknownNameResolvesToNothing() {
		assertNull(ReactorMilestone.byId("roomsealed"));
		assertNull(ReactorMilestone.byId("ROOM_SEALED"));
		assertNull(ReactorMilestone.byId(""));
		assertNotNull(ReactorMilestone.byId("room_sealed"));
	}
}
