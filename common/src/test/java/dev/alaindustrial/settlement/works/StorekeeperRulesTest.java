package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 for what the storekeeper carries (MOD-888; epic MOD-824, R152, R155): the storage is the containers of restored
 * buildings only; a fetch takes what the order lacks and the container holds, at most four stacks; the spoil it hauls
 * is what no order wants, once the buffer is filling.
 */
class StorekeeperRulesTest {

	private static final String BRICKS = "minecraft:bricks";
	private static final String GLASS = "minecraft:glass";
	private static final String DOOR = "minecraft:oak_door";
	private static final String DIRT = "minecraft:dirt";

	private static int stack(String item) {
		return item.equals(DOOR) ? 16 : 64;
	}

	private static BuildStageRules.Need need(String item, int count) {
		return new BuildStageRules.Need(item, count);
	}

	@Test
	void onlyARestoredBuildingIsStorage() {
		assertTrue(StorekeeperRules.isStorage(true, false));
		assertFalse(StorekeeperRules.isStorage(false, false), "a chest in a ruin never counts (R152)");
		assertFalse(StorekeeperRules.isStorage(true, true), "a building given up is no storage");
	}

	@Test
	void aFetchTakesWhatTheOrderLacksAndTheContainerHolds() {
		List<BuildStageRules.Need> batch = StorekeeperRules.fetch(List.of(need(BRICKS, 40), need(GLASS, 10)),
				Map.of(BRICKS, 100, GLASS, 3, DIRT, 64), StorekeeperRulesTest::stack);
		assertEquals(List.of(need(BRICKS, 40), need(GLASS, 3)), batch);
	}

	@Test
	void aFetchIsABatchOfAtMostFourStacks() {
		List<BuildStageRules.Need> batch = StorekeeperRules.fetch(List.of(need(BRICKS, 500), need(GLASS, 64)),
				Map.of(BRICKS, 1000, GLASS, 64), StorekeeperRulesTest::stack);
		assertEquals(List.of(need(BRICKS, StorekeeperRules.CARRY_SLOTS * 64)), batch);
		List<BuildStageRules.Need> doors = StorekeeperRules.fetch(List.of(need(DOOR, 20), need(BRICKS, 200)),
				Map.of(DOOR, 20, BRICKS, 200), StorekeeperRulesTest::stack);
		assertEquals(List.of(need(DOOR, 20), need(BRICKS, 128)), doors,
				"twenty doors take two stacks, the bricks the two left");
	}

	@Test
	void anEmptyContainerGivesNothing() {
		assertTrue(StorekeeperRules.fetch(List.of(need(BRICKS, 4)), Map.of(), StorekeeperRulesTest::stack)
				.isEmpty());
	}

	@Test
	void theBufferIsFillingWithAQuarterLeft() {
		assertFalse(StorekeeperRules.filling(5, 18));
		assertTrue(StorekeeperRules.filling(4, 18));
		assertTrue(StorekeeperRules.filling(0, 18));
		assertFalse(StorekeeperRules.filling(0, 0));
	}

	@Test
	void aHaulTakesOnlyWhatNoOrderWants() {
		Map<String, Integer> buffer = new LinkedHashMap<>();
		buffer.put(BRICKS, 30);
		buffer.put(DIRT, 300);
		buffer.put(GLASS, 5);
		assertEquals(List.of(need(DIRT, 256)),
				StorekeeperRules.haul(buffer, Set.of(BRICKS, GLASS), StorekeeperRulesTest::stack));
		assertTrue(StorekeeperRules.haul(Map.of(BRICKS, 30), Set.of(BRICKS), StorekeeperRulesTest::stack).isEmpty());
	}
}
