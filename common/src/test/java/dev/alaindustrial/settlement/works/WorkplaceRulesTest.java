package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.shift.Profession;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 for who works where in a town (MOD-888; epic MOD-824, R150, R151): a free workplace takes the oldest worker
 * without work by number, a place holds as many workers as its trade allows, a worker taken off work is never given a
 * place, the dead lose their work and the waiting keep it, and a workplace that is gone frees its worker.
 */
class WorkplaceRulesTest {

	private static final WorkplaceRules.Place WAREHOUSE = new WorkplaceRules.Place(3, Profession.STOREKEEPER);

	private static WorkplaceRules.Worker free(String key, long rank) {
		return new WorkplaceRules.Worker(key, rank, true, false, -1, false);
	}

	private static WorkplaceRules.Worker at(String key, long rank, int building) {
		return new WorkplaceRules.Worker(key, rank, true, false, building, false);
	}

	@Test
	void theOldestWorkerWithoutWorkTakesTheFreePlace() {
		List<WorkplaceRules.Change> changes = WorkplaceRules.assign(List.of(WAREHOUSE),
				List.of(free("K-300", 300), free("K-117", 117), free("K-200", 200)));
		assertEquals(List.of(new WorkplaceRules.Change("K-117", Profession.STOREKEEPER, 3)), changes);
		assertTrue(changes.getFirst().hired());
	}

	@Test
	void aWarehouseTakesOneWorker() {
		assertEquals(1, Profession.STOREKEEPER.places());
		List<WorkplaceRules.Change> changes = WorkplaceRules.assign(List.of(WAREHOUSE),
				List.of(at("K-300", 300, 3), free("K-117", 117)));
		assertTrue(changes.isEmpty(), "a worker who has the place keeps it, an older one waits: " + changes);
	}

	@Test
	void aWorkerTakenOffWorkIsNotGivenThePlace() {
		WorkplaceRules.Worker off = new WorkplaceRules.Worker("K-117", 117, true, false, -1, true);
		assertEquals(List.of(new WorkplaceRules.Change("K-200", Profession.STOREKEEPER, 3)),
				WorkplaceRules.assign(List.of(WAREHOUSE), List.of(off, free("K-200", 200))));
		assertTrue(WorkplaceRules.assign(List.of(WAREHOUSE), List.of(off)).isEmpty());
	}

	@Test
	void theDeadLoseTheirWorkAndTheWaitingKeepIt() {
		WorkplaceRules.Worker dead = new WorkplaceRules.Worker("K-117", 117, false, false, 3, false);
		assertEquals(List.of(new WorkplaceRules.Change("K-117", Profession.NONE, -1),
				new WorkplaceRules.Change("K-200", Profession.STOREKEEPER, 3)),
				WorkplaceRules.assign(List.of(WAREHOUSE), List.of(dead, free("K-200", 200))));
		WorkplaceRules.Worker waiting = new WorkplaceRules.Worker("K-117", 117, false, true, 3, false);
		assertTrue(WorkplaceRules.assign(List.of(WAREHOUSE), List.of(waiting, free("K-200", 200))).isEmpty());
	}

	@Test
	void aWorkplaceThatIsGoneFreesItsWorker() {
		assertEquals(List.of(new WorkplaceRules.Change("K-117", Profession.NONE, -1)),
				WorkplaceRules.assign(List.of(), List.of(at("K-117", 117, 3))));
	}

	@Test
	void aWorkerOfAGonePlaceMovesToAFreeOneInOneChange() {
		WorkplaceRules.Place other = new WorkplaceRules.Place(5, Profession.STOREKEEPER);
		assertEquals(List.of(new WorkplaceRules.Change("K-117", Profession.STOREKEEPER, 5)),
				WorkplaceRules.assign(List.of(other), List.of(at("K-117", 117, 3))));
	}

	@Test
	void twoWorkersOnOnePlaceKeepTheOlder() {
		List<WorkplaceRules.Change> changes = WorkplaceRules.assign(List.of(WAREHOUSE),
				List.of(at("K-300", 300, 3), at("K-117", 117, 3)));
		assertEquals(List.of(new WorkplaceRules.Change("K-300", Profession.NONE, -1)), changes);
	}

	@Test
	void aTradeIsFoundByItsBuildingAndItsSavedName() {
		assertEquals(Profession.STOREKEEPER, Profession.ofBuilding("warehouse").orElseThrow());
		assertTrue(Profession.ofBuilding("capsule_house_1").isEmpty());
		assertEquals(Profession.STOREKEEPER, Profession.byName("storekeeper"));
		assertEquals(Profession.NONE, Profession.byName(""));
		assertEquals(Profession.NONE, Profession.byName("oilman"), "an unknown trade reads as none");
		assertEquals("", Profession.NONE.serializedName());
		assertEquals("shift_worker.alaindustrial.profession.storekeeper", Profession.STOREKEEPER.langKey());
	}
}
