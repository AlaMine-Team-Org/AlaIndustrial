package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for the locality of the line plan (MOD-755, ADR-049): a consumer draws only from the cables it touches. On a
 * line of integers whose two faces are the positions on either side.
 *
 * @implements MOD-755-LC01 — a consumer draws only from its own cables; consumers sharing a cable share it
 */
class LineLocalityTest {

	private static final List<UnaryOperator<Integer>> FACES = List.of(p -> p - 1, p -> p + 1);

	private static final EnergyPort.Txn TXN = participant -> { };

	/** A consumer port: takes whatever fits. */
	private static EnergyBuffer machine() {
		return new EnergyBuffer(1000, 1000, 0, () -> { });
	}

	private static EnergyBuffer cable(long amount) {
		EnergyBuffer b = new EnergyBuffer(100, 100, 100, () -> { });
		b.setAmountUntracked(amount);
		return b;
	}

	private static EnergyLineDistributor<Integer> kernel(Map<Integer, EnergyBuffer> cables) {
		Set<Integer> isCable = cables.keySet();
		return new EnergyLineDistributor<>(new LineView<>(FACES, isCable::contains, cables::get, p -> 0, p -> 1,
				List.copyOf(new java.util.TreeSet<>(isCable)), (p, f) -> true, (p, f) -> true, new LinePlanner<>()));
	}

	/**
	 * Two machines on two different cables, one of them empty. The class used to be one pool, and the machine on
	 * the empty cable was fed out of its neighbour's cable, which no wire connected it to.
	 */
	@Test
	void aConsumerDrawsOnlyFromItsOwnCable() {
		Map<Integer, EnergyBuffer> cables = new HashMap<>();
		cables.put(0, cable(50));
		cables.put(10, cable(0));
		EnergyBuffer onFull = machine();
		EnergyBuffer onEmpty = machine();

		long moved = kernel(cables).distribute(List.of(
				new EnergyLineDistributor.LiveConsumer<>(1, onFull, 1000),
				new EnergyLineDistributor.LiveConsumer<>(11, onEmpty, 1000)), List.of(), List.of(), 0L, List.of(), 32,
				0.0, TXN, 0).delivered();

		assertEquals(0, onEmpty.getAmount(), "the machine on the empty cable gets nothing out of the other one");
		assertEquals(32, onFull.getAmount(), "the machine on the full cable takes its whole packet");
		assertEquals(32, moved);
		assertEquals(18, cables.get(0).getAmount());
	}

	/** Two consumers touching one cable still share it: one distance, alike (ADR-049). */
	@Test
	void twoConsumersOnOneCableShareIt() {
		Map<Integer, EnergyBuffer> cables = new HashMap<>();
		cables.put(5, cable(20));
		EnergyBuffer left = machine();
		EnergyBuffer right = machine();

		kernel(cables).distribute(List.of(new EnergyLineDistributor.LiveConsumer<>(4, left, 1000),
				new EnergyLineDistributor.LiveConsumer<>(6, right, 1000)), List.of(), List.of(), 0L, List.of(), 32, 0.0,
				TXN, 0);

		assertEquals(10, left.getAmount());
		assertEquals(10, right.getAmount());
		assertEquals(0, cables.get(5).getAmount());
	}
}
