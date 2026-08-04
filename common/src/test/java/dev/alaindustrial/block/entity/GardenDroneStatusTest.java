package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** L1 contract for the Garden Drone Station's status ordinal (MOD-324). */
class GardenDroneStatusTest {

	@Test
	void translationKeysAreStable() {
		assertEquals("gui.alaindustrial.garden_drone_station.status.idle",
				GardenDroneStatus.IDLE.translationKey());
		assertEquals("gui.alaindustrial.garden_drone_station.status.working",
				GardenDroneStatus.WORKING.translationKey());
		assertEquals("gui.alaindustrial.garden_drone_station.status.no_resources",
				GardenDroneStatus.NO_RESOURCES.translationKey());
		assertEquals("gui.alaindustrial.garden_drone_station.status.no_energy",
				GardenDroneStatus.NO_ENERGY.translationKey());
		assertEquals("gui.alaindustrial.garden_drone_station.status.no_drone",
				GardenDroneStatus.NO_DRONE.translationKey());
	}

	@Test
	void everyStatusHasItsOwnKey() {
		Set<String> keys = new HashSet<>();
		for (GardenDroneStatus status : GardenDroneStatus.values()) {
			assertTrue(keys.add(status.translationKey()),
					status + " reuses another status' lang key");
		}
	}

	@Test
	void idleAndNoResourcesStayDistinct() {
		// The class javadoc calls this out as the distinction that matters most: a fully tended farm
		// and a station stopped on an empty seed slot look identical from across the base, and only
		// one of them wants the player's attention. Collapsing them would be a real regression.
		assertNotEquals(GardenDroneStatus.IDLE, GardenDroneStatus.NO_RESOURCES);
		assertNotEquals(GardenDroneStatus.IDLE.translationKey(),
				GardenDroneStatus.NO_RESOURCES.translationKey());
	}

	@Test
	void ordinalWireFormatRoundTrips() {
		// Ordinals are persisted as well as synced, so their order is a compatibility contract:
		// appending is safe, reordering silently rewrites saved worlds.
		assertEquals(0, GardenDroneStatus.IDLE.ordinal());
		for (GardenDroneStatus status : GardenDroneStatus.values()) {
			assertEquals(status, GardenDroneStatus.byOrdinal(status.ordinal()));
		}
	}

	@Test
	void outOfRangeOrdinalsFallBackToIdle() {
		int last = GardenDroneStatus.values().length - 1;
		assertNotEquals(GardenDroneStatus.IDLE, GardenDroneStatus.byOrdinal(last));
		assertEquals(GardenDroneStatus.IDLE, GardenDroneStatus.byOrdinal(last + 1));
		assertEquals(GardenDroneStatus.IDLE, GardenDroneStatus.byOrdinal(-1));
		assertEquals(GardenDroneStatus.IDLE, GardenDroneStatus.byOrdinal(Integer.MIN_VALUE));
		assertEquals(GardenDroneStatus.IDLE, GardenDroneStatus.byOrdinal(Integer.MAX_VALUE));
	}
}
