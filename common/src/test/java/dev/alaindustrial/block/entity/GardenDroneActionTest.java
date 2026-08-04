package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 contract for the drone's job priority (MOD-324).
 *
 * <p>{@code PRIORITY_ORDER} is declared by its own class javadoc as a contract rather than an
 * implementation detail, and nothing verified it. The order is behaviour a player can see: harvest
 * before plant keeps a finished farm from stalling on a full-but-unripe field, and till last means
 * the drone only widens the plot once the existing one is fully tended.
 */
class GardenDroneActionTest {

	@Test
	void priorityOrderIsHarvestPlantFertilizeTill() {
		assertArrayEquals(
				new GardenDroneAction[] {
					GardenDroneAction.HARVEST,
					GardenDroneAction.PLANT,
					GardenDroneAction.FERTILIZE,
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
}
