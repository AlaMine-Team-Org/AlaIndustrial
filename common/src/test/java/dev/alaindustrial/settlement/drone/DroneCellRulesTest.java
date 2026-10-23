package dev.alaindustrial.settlement.drone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.drone.DroneCellRules.Action;
import dev.alaindustrial.settlement.drone.DroneCellRules.Cell;
import dev.alaindustrial.settlement.drone.DroneCellRules.Job;
import org.junit.jupiter.api.Test;

/**
 * L1 for what a builder drone does with a cell (MOD-858; epic MOD-824, R85, R93): clearing takes away only rubble and
 * liquid where air belongs, building puts the plan's block, a protected block is never touched, anything else in the
 * way stops the job.
 */
class DroneCellRulesTest {

	// matches, worldAir, worldLiquid, worldProtected, worldRemovable, wantAir, wantSkipped
	private static final Cell RIGHT = new Cell(true, false, false, false, false, false, false);
	private static final Cell RUBBLE_WHERE_AIR = new Cell(false, false, false, false, true, true, false);
	private static final Cell RUBBLE_WHERE_BLOCK = new Cell(false, false, false, false, true, false, false);
	private static final Cell STRANGER_WHERE_AIR = new Cell(false, false, false, false, false, true, false);
	private static final Cell STRANGER_WHERE_BLOCK = new Cell(false, false, false, false, false, false, false);
	private static final Cell WATER_WHERE_AIR = new Cell(false, false, true, false, false, true, false);
	private static final Cell WATER_WHERE_BLOCK = new Cell(false, false, true, false, false, false, false);
	private static final Cell AIR_WHERE_BLOCK = new Cell(false, true, false, false, false, false, false);
	private static final Cell CHEST_WHERE_AIR = new Cell(false, false, false, true, false, true, false);
	private static final Cell CHEST_WHERE_BLOCK = new Cell(false, false, false, true, false, false, false);
	private static final Cell AIR_WHERE_CORE = new Cell(false, true, false, false, false, false, true);

	@Test
	void clearingTakesOnlyRubbleAndLiquidWhereAirBelongs() {
		assertEquals(Action.NONE, DroneCellRules.decide(Job.CLEAR, RIGHT));
		assertEquals(Action.REMOVE, DroneCellRules.decide(Job.CLEAR, RUBBLE_WHERE_AIR));
		assertEquals(Action.REMOVE, DroneCellRules.decide(Job.CLEAR, RUBBLE_WHERE_BLOCK));
		assertEquals(Action.REMOVE, DroneCellRules.decide(Job.CLEAR, WATER_WHERE_AIR));
		assertEquals(Action.NONE, DroneCellRules.decide(Job.CLEAR, WATER_WHERE_BLOCK));
		assertEquals(Action.NONE, DroneCellRules.decide(Job.CLEAR, AIR_WHERE_BLOCK));
		assertEquals(Action.OBSTRUCTED, DroneCellRules.decide(Job.CLEAR, STRANGER_WHERE_AIR));
		assertEquals(Action.NONE, DroneCellRules.decide(Job.CLEAR, STRANGER_WHERE_BLOCK));
	}

	@Test
	void buildingPutsThePlanBlockOrStops() {
		assertEquals(Action.NONE, DroneCellRules.decide(Job.BUILD, RIGHT));
		assertEquals(Action.PLACE, DroneCellRules.decide(Job.BUILD, AIR_WHERE_BLOCK));
		assertEquals(Action.PLACE, DroneCellRules.decide(Job.BUILD, RUBBLE_WHERE_BLOCK));
		assertEquals(Action.PLACE, DroneCellRules.decide(Job.BUILD, WATER_WHERE_BLOCK));
		assertEquals(Action.REMOVE, DroneCellRules.decide(Job.BUILD, RUBBLE_WHERE_AIR));
		assertEquals(Action.REMOVE, DroneCellRules.decide(Job.BUILD, WATER_WHERE_AIR));
		assertEquals(Action.OBSTRUCTED, DroneCellRules.decide(Job.BUILD, STRANGER_WHERE_BLOCK));
		assertEquals(Action.OBSTRUCTED, DroneCellRules.decide(Job.BUILD, STRANGER_WHERE_AIR));
	}

	/**
	 * R93: a protected block is never touched; where air belongs it is passed by, where the plan wants a block the
	 * building job stops naming it (owner's round 5 of 0.1.219: never a silent pass).
	 */
	@Test
	void protectedBlocksAreNeverTouched() {
		for (Job job : Job.values()) {
			assertEquals(Action.NONE, DroneCellRules.decide(job, CHEST_WHERE_AIR), job.name());
			assertEquals(Action.NONE, DroneCellRules.decide(job, AIR_WHERE_CORE), job.name());
		}
		assertEquals(Action.NONE, DroneCellRules.decide(Job.CLEAR, CHEST_WHERE_BLOCK));
		assertEquals(Action.OBSTRUCTED, DroneCellRules.decide(Job.BUILD, CHEST_WHERE_BLOCK));
	}

	@Test
	void onlyARemovableSolidBlockMakesAPlacementPayARemoval() {
		assertTrue(DroneCellRules.placementRemoves(RUBBLE_WHERE_BLOCK));
		assertFalse(DroneCellRules.placementRemoves(AIR_WHERE_BLOCK));
		assertFalse(DroneCellRules.placementRemoves(WATER_WHERE_BLOCK));
	}
}
