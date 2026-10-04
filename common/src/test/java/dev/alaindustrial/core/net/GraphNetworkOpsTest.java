package dev.alaindustrial.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link GraphNetworkOps} — the {@link NetworkOps} every network manager now uses instead of
 * an anonymous forwarding class (MOD-715, batch 11): each operation reaches the network's own method, and
 * the three things a manager supplies reach its functions. Then the whole frame runs on it.
 *
 * @implements MOD-715-GO01 — the default network operations forward to the network and the manager
 */
class GraphNetworkOpsTest {

	/** A stand-in network on integers, backed by a {@link NodeSet}, counting what it is asked. */
	static final class Line implements GraphNetwork<Line, Integer> {
		final String level;
		final NodeSet<Integer> nodes = new NodeSet<>();
		final List<String> calls = new ArrayList<>();
		boolean awake = true;
		long moved = 7;

		Line(String level) {
			this.level = level;
		}

		@Override
		public Set<Integer> nodes() {
			return nodes.nodes();
		}

		@Override
		public void addNode(Integer pos) {
			calls.add("add " + pos);
			nodes.add(pos);
		}

		@Override
		public void removeNode(Integer pos) {
			calls.add("remove " + pos);
			nodes.remove(pos);
		}

		@Override
		public void absorb(Line drop) {
			calls.add("absorb");
			nodes.absorb(drop.nodes);
		}

		@Override
		public void markDirty() {
			calls.add("dirty");
			nodes.markDirty();
		}

		@Override
		public boolean isAwake() {
			return awake;
		}

		@Override
		public long tick() {
			calls.add("tick");
			return moved;
		}
	}

	/** Integers on a line: {@code n} touches {@code n - 1} and {@code n + 1}; 10 and 11 are not joined. */
	private static final GraphNetworkOps<String, Line, Integer> OPS = new GraphNetworkOps<>(Line::new,
			pos -> List.of(pos - 1, pos + 1),
			(level, pos) -> pos == 10 ? List.of(9) : pos == 11 ? List.of(12) : List.of(pos - 1, pos + 1));

	@Test
	void everyNetworkOperationReachesTheNetwork() {
		Line keep = OPS.create("overworld");
		Line drop = OPS.create("overworld");
		assertEquals("overworld", keep.level, "create hands the level to the manager's factory");
		assertNotSame(keep, drop, "a fresh network each time");

		OPS.addNode(keep, 1);
		OPS.addNode(drop, 2);
		OPS.removeNode(keep, 1);
		OPS.absorb(keep, drop);
		OPS.markDirty(keep);
		assertEquals(7, OPS.tick(keep), "tick reports what the network moved");
		assertEquals(List.of("add 1", "remove 1", "absorb", "dirty", "tick"), keep.calls);
		assertEquals(List.of("add 2"), drop.calls);
		assertSame(keep.nodes(), OPS.nodes(keep), "the live set, not a copy");
		assertEquals(Set.of(2), OPS.nodes(keep));

		assertTrue(OPS.isAwake(keep));
		keep.awake = false;
		assertFalse(OPS.isAwake(keep));
	}

	@Test
	void candidatesAndConnectionsComeFromTheManager() {
		assertEquals(List.of(4, 6), OPS.candidates(5));
		assertEquals(List.of(4, 6), OPS.connected("overworld", 5));
		assertEquals(List.of(9), OPS.connected("overworld", 10), "the cut between 10 and 11");
	}

	@Test
	void theFrameRunsOnTheDefaultOperations() {
		GraphNetworkManager<String, Line, Integer> manager = new GraphNetworkManager<>("ops-test", OPS,
				() -> Integer.MAX_VALUE, GraphNetworkManager.TickCursor.BY_WINDOW);
		for (int pos = 8; pos <= 13; pos++) {
			manager.register("overworld", pos);
		}
		assertEquals(2, manager.networkCount("overworld"), "8-10 and 11-13: the cut keeps them apart");
		Line left = manager.networkAt("overworld", 8);
		assertEquals(Set.of(8, 9, 10), left.nodes());

		manager.unregister("overworld", 9);
		assertEquals(3, manager.networkCount("overworld"), "removing the middle node splits the left line");

		manager.tickAll("overworld");
		assertEquals(3 * 7, manager.telemetry("overworld").movedLastTick(), "each network's tick is counted");
		manager.clearAll();
	}
}
