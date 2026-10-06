package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 contract for the drone's job priority (MOD-324) and for the ordinals it saves (MOD-779).
 *
 * <p>{@code PRIORITY_ORDER} is declared by its own class javadoc as a contract rather than an
 * implementation detail, and nothing verified it. The order is behaviour a player can see: harvest
 * before plant keeps a finished farm from stalling on a full-but-unripe field, and till last means
 * the drone only widens the plot once the existing one is fully tended. Clearing weeds (MOD-779) sits
 * right before tilling, because the weed is what stands on the ground the till needs.
 */
class GardenDroneActionTest {

	@Test
	void priorityOrderIsHarvestPlantFertilizeClearTill() {
		assertArrayEquals(
				new GardenDroneAction[] {
					GardenDroneAction.HARVEST,
					GardenDroneAction.PLANT,
					GardenDroneAction.FERTILIZE,
					GardenDroneAction.CLEAR,
					GardenDroneAction.TILL,
				},
				GardenDroneAction.PRIORITY_ORDER);
	}

	@Test
	void priorityOrderCoversEveryActionExactlyOnce() {
		// Guards the failure mode a hand-written array invites: a new action is added to the enum and
		// never reaches PRIORITY_ORDER, so the drone silently never performs it.
		Set<GardenDroneAction> listed = EnumSet.noneOf(GardenDroneAction.class);
		for (GardenDroneAction action : GardenDroneAction.PRIORITY_ORDER) {
			assertTrue(listed.add(action), action + " appears twice in PRIORITY_ORDER");
		}
		assertEquals(EnumSet.allOf(GardenDroneAction.class), listed);
		assertEquals(GardenDroneAction.values().length, GardenDroneAction.PRIORITY_ORDER.length);
	}

	@Test
	void harvestOutranksTill() {
		// The two ends of the contract, asserted by position rather than by identity, so a reordering
		// that keeps the same membership still fails.
		assertEquals(GardenDroneAction.HARVEST, GardenDroneAction.PRIORITY_ORDER[0]);
		assertEquals(GardenDroneAction.TILL,
				GardenDroneAction.PRIORITY_ORDER[GardenDroneAction.PRIORITY_ORDER.length - 1]);
	}

	@Test
	void savedOrdinalsKeepTheirMeaning() {
		// The station persists the job in flight as its ordinal ("DroneJob"). A new action inserted
		// mid-enum would load an old save's TILL as something else, so the order of declaration is part
		// of the save format: the four original actions stay at 0..3 and CLEAR is appended as 4.
		assertEquals(0, GardenDroneAction.HARVEST.ordinal());
		assertEquals(1, GardenDroneAction.PLANT.ordinal());
		assertEquals(2, GardenDroneAction.FERTILIZE.ordinal());
		assertEquals(3, GardenDroneAction.TILL.ordinal());
		assertEquals(4, GardenDroneAction.CLEAR.ordinal());
	}
}
