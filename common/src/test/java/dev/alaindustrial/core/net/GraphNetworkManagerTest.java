package dev.alaindustrial.core.net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.net.GraphNetworkManager.TickCursor;
import dev.alaindustrial.core.net.LineGraphSupport.Level;
import dev.alaindustrial.core.net.LineGraphSupport.LineOps;
import dev.alaindustrial.core.net.LineGraphSupport.Net;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link GraphNetworkManager} — the topology bookkeeping the energy, fluid and item
 * managers used to keep three copies of (MOD-401).
 *
 * <p>Before this class the whole algorithm was reachable only from the L2 gametests, because every
 * copy was typed on {@code ServerLevel}/{@code BlockPos}. That is precisely how the copies drifted
 * unnoticed until one of them was found leaking a level per world unload. Here it runs against a line
 * graph of plain {@code Integer}s, so union, split, the tick budget, the cursor policy and the level
 * sweep are all checked in milliseconds.
 */
class GraphNetworkManagerTest {

	private LineOps ops;
	private Level level;
	private int budget;

	@BeforeEach
	void setUp() {
		ops = new LineOps();
		level = new Level("overworld");
		budget = Integer.MAX_VALUE;
	}

	private GraphNetworkManager<Level, Net, Integer> manager(TickCursor policy) {
		return new GraphNetworkManager<>("test", ops, () -> budget, policy);
	}

	private GraphNetworkManager<Level, Net, Integer> manager() {
		return manager(TickCursor.BY_WINDOW);
	}

	// --- topology: register / union -------------------------------------------------------------

	@Test
	void adjacentNodesFormOneNetworkAndDistantOnesDoNot() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.register(level, 2);
		mgr.register(level, 3);
		assertEquals(1, mgr.networkCount(level));
		assertSame(mgr.networkAt(level, 1), mgr.networkAt(level, 3));
		assertEquals(Set.of(1, 2, 3), mgr.networkAt(level, 1).nodes);

		mgr.register(level, 10);
		assertEquals(2, mgr.networkCount(level));
		assertNotSame(mgr.networkAt(level, 1), mgr.networkAt(level, 10));
	}

	@Test
	void registeringAKnownPositionOnlyWakesItAndBuildsNothing() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		Net net = mgr.networkAt(level, 1);
		int dirtiedBefore = net.dirtied;
		int createdBefore = ops.created;

		mgr.register(level, 1);

		assertEquals(1, mgr.networkCount(level));
		assertEquals(Set.of(1), net.nodes);
		assertEquals(createdBefore, ops.created, "a known position must not build a second network");
		assertTrue(net.dirtied > dirtiedBefore, "a known position must still be woken");
	}

	@Test
	void unionKeepsTheLargerNetworkAsTheSurvivor() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.register(level, 2);
		mgr.register(level, 3);
		Net big = mgr.networkAt(level, 1);
		mgr.register(level, 5);
		Net small = mgr.networkAt(level, 5);
		assertNotSame(big, small);

		// 4 bridges the run of three and the lone node: both merges must fold into the run of three.
		mgr.register(level, 4);

		assertEquals(1, mgr.networkCount(level));
		assertSame(big, mgr.networkAt(level, 5), "the bigger network must absorb the smaller, not vice versa");
		assertEquals(Set.of(1, 2, 3, 4, 5), big.nodes);
	}

	// --- topology: unregister / split -----------------------------------------------------------

	@Test
	void removingAMiddleNodeSplitsTheNetwork() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.register(level, 2);
		mgr.register(level, 3);

		mgr.unregister(level, 2);

		assertEquals(2, mgr.networkCount(level));
		assertNull(mgr.networkAt(level, 2));
		assertNotSame(mgr.networkAt(level, 1), mgr.networkAt(level, 3));
		assertEquals(Set.of(1), mgr.networkAt(level, 1).nodes);
		assertEquals(Set.of(3), mgr.networkAt(level, 3).nodes);
	}

	@Test
	void removingALeafSkipsTheRePartitionEntirely() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.register(level, 2);
		mgr.register(level, 3);
		Net net = mgr.networkAt(level, 1);
		int addedBefore = net.added;

		mgr.unregister(level, 3);

		assertEquals(1, mgr.networkCount(level));
		assertEquals(Set.of(1, 2), net.nodes);
		// A node with one surviving neighbour cannot have been a cut vertex. The BFS would only
		// rediscover the same component, and re-adding every node is exactly what it would look like.
		assertEquals(addedBefore, net.added, "a leaf removal must not re-partition the network");
	}

	@Test
	void removingTheLastNodeDropsTheNetwork() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);

		mgr.unregister(level, 1);

		assertEquals(0, mgr.networkCount(level), "an empty network must be dropped, not kept as a husk");
		assertNull(mgr.networkAt(level, 1));
	}

	@Test
	void unregisteringSomethingUnknownIsANoOp() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);

		assertDoesNotThrow(() -> mgr.unregister(level, 42));
		assertDoesNotThrow(() -> mgr.unregister(new Level("never seen"), 1));
		assertEquals(1, mgr.networkCount(level));
	}

	// --- connectivity is a predicate: split AND restore (MOD-282) -------------------------------

	@Test
	void aClosedFaceSplitsTheNetwork() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.register(level, 2);
		mgr.register(level, 3);

		ops.cut(1, 2);
		mgr.rebuildAt(level, 2);

		assertEquals(2, mgr.networkCount(level));
		assertNotSame(mgr.networkAt(level, 1), mgr.networkAt(level, 2));
	}

	@Test
	void aReopenedFaceIsRejoinedOnlyBecauseTheUnionFollowsTheRePartition() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.register(level, 2);
		mgr.register(level, 3);
		ops.cut(1, 2);
		mgr.rebuildAt(level, 2);
		assertEquals(2, mgr.networkCount(level));

		// A re-partition alone can only ever split: its traversal is bounded by the network's own
		// nodes, so it cannot see the other half. This is the MOD-282 failure — a line that looks
		// whole and silently moves nothing.
		ops.restore(1, 2);
		mgr.rebuildAt(level, 2);
		assertEquals(2, mgr.networkCount(level), "a re-partition must not be able to rejoin halves");

		mgr.retopologise(level, 2);
		assertEquals(1, mgr.networkCount(level), "re-partition + union must rejoin them");
		assertSame(mgr.networkAt(level, 1), mgr.networkAt(level, 3));
	}

	@Test
	void retopologiseAndRebuildIgnoreUnknownPositionsAndLevels() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);

		assertDoesNotThrow(() -> mgr.retopologise(level, 42));
		assertDoesNotThrow(() -> mgr.retopologise(new Level("never seen"), 1));
		assertDoesNotThrow(() -> mgr.rebuildAt(level, 42));
		assertDoesNotThrow(() -> mgr.rebuildAt(new Level("never seen"), 1));
		assertEquals(1, mgr.networkCount(level));
	}

	@Test
	void markDirtyAtWakesOnlyTheOwningNetwork() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.register(level, 10);
		Net owner = mgr.networkAt(level, 1);
		Net other = mgr.networkAt(level, 10);
		int ownerBefore = owner.dirtied;
		int otherBefore = other.dirtied;

		mgr.markDirtyAt(level, 1);

		assertTrue(owner.dirtied > ownerBefore);
		assertEquals(otherBefore, other.dirtied);
		assertDoesNotThrow(() -> mgr.markDirtyAt(level, 42));
		assertDoesNotThrow(() -> mgr.markDirtyAt(new Level("never seen"), 1));
	}

	// --- the round-robin tick -------------------------------------------------------------------

	/** Three networks that cannot touch each other on a line graph: 1, 3 and 5. */
	private List<Net> threeIsolatedNetworks(GraphNetworkManager<Level, Net, Integer> mgr) {
		mgr.register(level, 1);
		mgr.register(level, 3);
		mgr.register(level, 5);
		assertEquals(3, mgr.networkCount(level));
		return List.of(mgr.networkAt(level, 1), mgr.networkAt(level, 3), mgr.networkAt(level, 5));
	}

	@Test
	void theBudgetCapsHowManyNetworksTickAndTheCursorCarriesTheRest() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager(TickCursor.BY_WINDOW);
		List<Net> nets = threeIsolatedNetworks(mgr);
		budget = 1;

		mgr.tickAll(level);
		assertEquals(List.of(1, 0, 0), ticks(nets), "one tick, one network");
		mgr.tickAll(level);
		assertEquals(List.of(1, 1, 0), ticks(nets), "the next tick must resume, not restart");
		mgr.tickAll(level);
		assertEquals(List.of(1, 1, 1), ticks(nets));
		mgr.tickAll(level);
		assertEquals(List.of(2, 1, 1), ticks(nets), "and then wrap around");
	}

	@Test
	void asleepNetworksAreVisitedButSpendNoBudget() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager(TickCursor.BY_WINDOW);
		List<Net> nets = threeIsolatedNetworks(mgr);
		nets.get(0).awake = false;
		budget = 1;

		mgr.tickAll(level);

		assertEquals(List.of(0, 1, 0), ticks(nets),
				"the asleep network must be skipped without consuming the tick's budget");
	}

	@Test
	void anUnbudgetedManagerTicksEverythingAndStillRotatesTheOrder() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager(TickCursor.BY_ONE);
		List<Net> nets = threeIsolatedNetworks(mgr);

		mgr.tickAll(level);
		mgr.tickAll(level);

		assertEquals(List.of(2, 2, 2), ticks(nets), "with no budget every awake network ticks every tick");
		assertEquals(List.of(nets.get(0), nets.get(1), nets.get(2), nets.get(1), nets.get(2), nets.get(0)),
				ops.tickOrder, "BY_ONE must rotate the serving order between ticks");
	}

	@Test
	void aWindowCursorLeavesTheOrderAloneWhenNothingIsBudgetedOut() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager(TickCursor.BY_WINDOW);
		List<Net> nets = threeIsolatedNetworks(mgr);

		mgr.tickAll(level);
		mgr.tickAll(level);

		// The negative control for the test above: the difference between the two policies is real and
		// visible only when every network is visited.
		assertEquals(List.of(nets.get(0), nets.get(1), nets.get(2), nets.get(0), nets.get(1), nets.get(2)),
				ops.tickOrder, "BY_WINDOW advances past the visited window, i.e. back to where it started");
	}

	@Test
	void tickingAnEmptyOrUnknownLevelDoesNothing() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		assertDoesNotThrow(() -> mgr.tickAll(new Level("never seen")));
		mgr.register(level, 1);
		mgr.unregister(level, 1);
		assertDoesNotThrow(() -> mgr.tickAll(level));
		assertTrue(ops.tickOrder.isEmpty());
	}

	@Test
	void aThrowingNetworkIsIsolatedAndTheRestStillTick() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		List<Net> nets = threeIsolatedNetworks(mgr);
		nets.get(0).failure = new IllegalStateException("a neighbouring mod's capability blew up");

		assertDoesNotThrow(() -> mgr.tickAll(level),
				"a throw inside one network must not unwind into the server tick (MOD-186)");
		assertEquals(List.of(1, 1, 1), ticks(nets), "the other networks must still be served");
	}

	// --- telemetry ------------------------------------------------------------------------------

	@Test
	void telemetryAccumulatesWhatTheTickBodiesReport() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.networkAt(level, 1).movesPerTick = 7L;

		mgr.tickAll(level);
		assertEquals(new GraphNetworkManager.Telemetry(1, 7L, 7L), mgr.telemetry(level));
		mgr.tickAll(level);
		assertEquals(new GraphNetworkManager.Telemetry(1, 7L, 14L), mgr.telemetry(level));
	}

	@Test
	void aThrownTickContributesNothingToTheTelemetry() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		Net net = mgr.networkAt(level, 1);
		net.movesPerTick = 7L;
		net.failure = new IllegalStateException("boom");

		mgr.tickAll(level);

		assertEquals(new GraphNetworkManager.Telemetry(1, 0L, 0L), mgr.telemetry(level));
	}

	@Test
	void telemetryOfAnUnknownLevelIsZeroAndAnEmptyLevelKeepsItsLastFigures() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		assertEquals(new GraphNetworkManager.Telemetry(0, 0L, 0L), mgr.telemetry(new Level("never seen")));

		mgr.register(level, 1);
		mgr.networkAt(level, 1).movesPerTick = 5L;
		mgr.tickAll(level);
		mgr.unregister(level, 1);
		mgr.tickAll(level);

		// Deliberate: /ala net keeps showing the last real throughput instead of blanking to zero the
		// moment the last cable is broken.
		assertEquals(new GraphNetworkManager.Telemetry(1, 5L, 5L), mgr.telemetry(level));
	}

	// --- the cursor policy itself ---------------------------------------------------------------

	@Test
	void cursorPolicyAdvancesByWindowOrByOne() {
		assertEquals(2, TickCursor.BY_WINDOW.next(0, 2, 5));
		assertEquals(0, TickCursor.BY_WINDOW.next(2, 3, 5));
		assertEquals(3, TickCursor.BY_WINDOW.next(4, 4, 5));
		assertEquals(1, TickCursor.BY_ONE.next(0, 2, 5));
		assertEquals(3, TickCursor.BY_ONE.next(2, 3, 5));
		assertEquals(0, TickCursor.BY_ONE.next(4, 4, 5));
	}

	@Test
	void cursorPolicyHandlesAnEmptyRingWithoutDividingByZero() {
		assertEquals(0, TickCursor.BY_WINDOW.next(3, 1, 0));
		assertEquals(0, TickCursor.BY_ONE.next(3, 1, 0));
	}

	// --- the level sweep ------------------------------------------------------------------------

	@Test
	void clearingOneLevelLeavesTheOthersAlone() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		Level nether = new Level("nether");
		mgr.register(level, 1);
		mgr.register(nether, 1);
		assertEquals(2, mgr.retained());

		mgr.clearLevel(level);

		assertEquals(1, mgr.retained());
		assertNull(mgr.networkAt(level, 1));
		assertNotNull(mgr.networkAt(nether, 1));
		assertEquals(0, mgr.networkCount(level));
		assertTrue(mgr.networks(level).isEmpty());
	}

	@Test
	void clearingEverythingLeavesNoLevelBehind() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		mgr.register(level, 1);
		mgr.register(new Level("nether"), 1);
		assertEquals(2, mgr.retained());

		mgr.clearAll();

		assertEquals(0, mgr.retained());
		assertNull(mgr.networkAt(level, 1));
	}

	@Test
	void networksSnapshotMirrorsTheLiveSet() {
		GraphNetworkManager<Level, Net, Integer> mgr = manager();
		List<Net> nets = threeIsolatedNetworks(mgr);
		assertEquals(3, mgr.networks(level).size());
		assertTrue(mgr.networks(level).containsAll(nets));
		assertTrue(mgr.networks(new Level("never seen")).isEmpty());
		assertEquals("test", mgr.stateId());
	}

	private static List<Integer> ticks(List<Net> nets) {
		return nets.stream().map(net -> net.ticks).toList();
	}
}
