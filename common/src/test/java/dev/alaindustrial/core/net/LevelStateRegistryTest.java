package dev.alaindustrial.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.net.GraphNetworkManager.TickCursor;
import dev.alaindustrial.core.net.LineGraphSupport.Level;
import dev.alaindustrial.core.net.LineGraphSupport.LineOps;
import dev.alaindustrial.core.net.LineGraphSupport.Net;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for the cleanup roster (MOD-401) — the mechanism that replaced the by-name list of
 * managers in the two loader entry points.
 *
 * <p><b>What actually went wrong, and what these tests are guarding.</b> Each loader named the
 * managers to clear on level unload and on server stop, and each named two of the three. The fluid
 * manager's {@code clear}/{@code clearAll} were written, reviewed and called from nowhere, so every
 * unloaded {@code ServerLevel} stayed reachable as a key in its map — in single player, a whole level
 * with its chunks and block entities leaked on every trip back to the main menu. Nothing could catch
 * that, because "somebody must add two lines in two other files" is not a checkable property.
 *
 * <p>It is now: {@link GraphNetworkManager}'s constructor enrols the manager in
 * {@link LevelStateRegistry}, so a network kind that exists is a network kind that gets swept. The
 * first test below is the guard for exactly that — delete the {@code register(this)} call from the
 * constructor and it goes red.
 */
class LevelStateRegistryTest {

	private static GraphNetworkManager<Level, Net, Integer> newManager(String kind) {
		return new GraphNetworkManager<>(kind, new LineOps(), () -> 1, TickCursor.BY_WINDOW);
	}

	@Test
	void buildingAManagerEnrolsItInTheCleanupRoster() {
		int before = LevelStateRegistry.holders().size();

		GraphNetworkManager<Level, Net, Integer> manager = newManager("enrolment-probe");

		List<LevelStateHolder> after = LevelStateRegistry.holders();
		assertEquals(before + 1, after.size(),
				"constructing a network manager must enrol it — a fourth network kind cannot be allowed "
						+ "to depend on somebody remembering to add it to the loaders");
		assertTrue(after.contains(manager), "the roster must hold the manager that was just built");
	}

	@Test
	void theServerStopSweepEmptiesEveryEnrolledHolder() {
		GraphNetworkManager<Level, Net, Integer> first = newManager("sweep-probe-a");
		GraphNetworkManager<Level, Net, Integer> second = newManager("sweep-probe-b");
		first.register(new Level("overworld"), 1);
		first.register(new Level("nether"), 1);
		second.register(new Level("end"), 1);

		// Without these three, "nothing is retained afterwards" would be true of a sweep that does
		// nothing at all — the assertion below would pass on an empty registry and prove nothing.
		assertEquals(2, first.retained());
		assertEquals(1, second.retained());
		List<LevelStateHolder> roster = LevelStateRegistry.holders();
		assertFalse(roster.isEmpty(), "an empty roster would make the loop below vacuous");
		assertTrue(roster.contains(first) && roster.contains(second));

		LevelStateRegistry.clearAll();

		for (LevelStateHolder holder : roster) {
			assertEquals(0, holder.retained(),
					"holder '" + holder.stateId() + "' still holds state after the server-stop sweep");
		}
	}

	@Test
	void theLevelUnloadSweepDropsOnlyTheUnloadedLevel() {
		GraphNetworkManager<Level, Net, Integer> manager = newManager("unload-probe");
		Level unloaded = new Level("unloaded");
		Level kept = new Level("kept");
		manager.register(unloaded, 1);
		manager.register(kept, 1);
		assertEquals(2, manager.retained());

		LevelStateRegistry.clearLevel(unloaded);

		assertEquals(1, manager.retained(), "the unloaded level must not stay reachable as a map key");
		assertNull(manager.networkAt(unloaded, 1));
		assertNotNull(manager.networkAt(kept, 1), "the levels still in play must be untouched");
	}

	@Test
	void sweepingALevelNoHolderKnowsIsHarmless() {
		GraphNetworkManager<Level, Net, Integer> manager = newManager("stranger-probe");
		manager.register(new Level("overworld"), 1);

		LevelStateRegistry.clearLevel(new Level("a level from another server"));

		assertEquals(1, manager.retained());
	}
}
