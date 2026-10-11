package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * L1.5 unit tests for {@link EnergyLineDistributor} on {@link BlockPos}: the kernel driven through the network's
 * face numbering ({@link LineEndpoints#BLOCK_FACES}), the face gates over {@link Direction}. Its L1 twin,
 * {@code EnergyLineDistributorCoreTest}, runs the full set of cases on a toy grid under pitest; this suite proves
 * the {@code BlockPos} adapter drives the same plan (ADR-049). The provider only puts the minecraft jar on the
 * classpath.
 *
 * @implements energy-network line distribution kernel (locality, packet cap, loss, one hop a tick, face gates)
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class EnergyLineDistributorTest {

	private static final double COPPER_LOSS = 0.02;

	/** A fake EnergyTransactions that runs body callbacks synchronously against a shared fake txn. */
	private static final class FakeTransactions implements EnergyTransactions {
		final EnergyPort.Txn txn = participant -> {
		};

		@Override
		public void runCommitting(Consumer<EnergyPort.Txn> body) {
			body.accept(txn);
		}

		@Override
		public <T> T simulate(Function<EnergyPort.Txn, T> body) {
			return body.apply(txn);
		}
	}

	/** A producer or consumer port clamped by a fixed capacity. */
	private static final class StubPort implements EnergyPort {
		final long capacity;
		long amount;
		final boolean extractable;
		final boolean insertable;

		StubPort(long capacity, long amount, boolean extractable, boolean insertable) {
			this.capacity = capacity;
			this.amount = amount;
			this.extractable = extractable;
			this.insertable = insertable;
		}

		@Override
		public long insert(long maxAmount, EnergyPort.Txn txn) {
			if (!insertable) {
				return 0;
			}
			long moved = Math.min(Math.max(0, capacity - amount), maxAmount);
			amount += moved;
			return moved;
		}

		@Override
		public long extract(long maxAmount, EnergyPort.Txn txn) {
			if (!extractable) {
				return 0;
			}
			long moved = Math.min(amount, maxAmount);
			amount -= moved;
			return moved;
		}

		@Override
		public long getAmount() {
			return amount;
		}

		@Override
		public long getCapacity() {
			return capacity;
		}

		@Override
		public boolean supportsInsertion() {
			return insertable;
		}

		@Override
		public boolean supportsExtraction() {
			return extractable;
		}
	}

	private FakeTransactions txns;

	@BeforeEach
	void installFakeTransactions() {
		txns = new FakeTransactions();
		EnergyTransactions.install(txns);
	}

	@AfterEach
	void clearFakeTransactions() {
		EnergyTransactions.install(null);
	}

	/** Cables with buffers and their distance from the generators; one planner across the fixture's ticks. */
	private static final class LineFixture {
		final Set<BlockPos> cables = new LinkedHashSet<>();
		final Map<BlockPos, EnergyBuffer> buffers = new HashMap<>();
		final Map<BlockPos, Integer> producerDistance = new HashMap<>();
		final LinePlanner<BlockPos> planner = new LinePlanner<>();
		private List<BlockPos> list = List.of();

		void cable(BlockPos p, int distance, long capacity, long fill) {
			cables.add(p);
			EnergyBuffer b = new EnergyBuffer(capacity, capacity, capacity, () -> {
			});
			b.setAmountUntracked(fill);
			buffers.put(p, b);
			producerDistance.put(p, distance);
		}

		long amount(BlockPos p) {
			return buffers.get(p).getAmount();
		}

		EnergyLineDistributor<BlockPos> distributor(Map<BlockPos, Integer> consumerDistance) {
			return distributor(consumerDistance, (p, d) -> true, (p, d) -> true);
		}

		EnergyLineDistributor<BlockPos> distributor(Map<BlockPos, Integer> consumerDistance,
				BiPredicate<BlockPos, Direction> canDraw, BiPredicate<BlockPos, Direction> canFeed) {
			if (list.size() != cables.size()) {
				list = new ArrayList<>(cables);
			}
			return new EnergyLineDistributor<>(new LineView<>(LineEndpoints.BLOCK_FACES, cables::contains,
					buffers::get, pos -> consumerDistance.getOrDefault(pos, 0), producerDistance::get, list,
					byDirection(canDraw), byDirection(canFeed), planner));
		}

		/** A gate over {@link Direction} as the kernel's numbered face gate (face i is Direction.values()[i]). */
		private static LineView.FaceGate<BlockPos> byDirection(BiPredicate<BlockPos, Direction> gate) {
			return (pos, face) -> gate.test(pos, Direction.values()[face]);
		}
	}

	private EnergyLineDistributor.Moved tick(EnergyLineDistributor<BlockPos> d,
			List<EnergyLineDistributor.LiveConsumer<BlockPos>> machines,
			List<EnergyLineDistributor.LiveProducer<BlockPos>> generators, double loss, int rotation) {
		return d.distribute(machines, List.of(), List.of(), 0L, generators, 32, loss, txns.txn, rotation);
	}

	private static List<EnergyLineDistributor.LiveConsumer<BlockPos>> machine(BlockPos pos, EnergyPort port,
			long need) {
		return List.of(new EnergyLineDistributor.LiveConsumer<>(pos, port, 1000, need));
	}

	@Test
	void aConsumerDrawsOnlyFromTheCablesItTouches() {
		LineFixture line = new LineFixture();
		BlockPos cableA = new BlockPos(0, 0, 0);
		BlockPos cableB = new BlockPos(5, 0, 0);
		line.cable(cableA, 1, 100, 50);
		line.cable(cableB, 2, 100, 50);
		BlockPos consumerPos = new BlockPos(0, 1, 0);

		long moved = tick(line.distributor(Map.of(consumerPos, 1)),
				machine(consumerPos, new StubPort(1000, 0, false, true), 32), List.of(), COPPER_LOSS, 0).delivered();

		assertEquals(32, moved, "consumer pulled up to packetCap (32) from cableA");
		assertEquals(18, line.amount(cableA));
		assertEquals(50, line.amount(cableB), "cableB untouched (not adjacent to consumer)");
	}

	@Test
	void aGeneratorFillsItsCableWithNobodyWaiting() {
		LineFixture line = new LineFixture();
		line.cable(new BlockPos(0, 0, 0), 1, 100, 0);
		StubPort generator = new StubPort(10_000, 10_000, true, false);

		tick(line.distributor(Map.of()), List.of(),
				List.of(new EnergyLineDistributor.LiveProducer<>(new BlockPos(-1, 0, 0), generator)), 0.0, 0);

		assertEquals(32, line.amount(new BlockPos(0, 0, 0)));
		assertEquals(10_000L - 32L, generator.amount);
	}

	/** ADR-001: a charge travels one cable a tick, and the cable on the way really carries it. */
	@Test
	void energyMovesOneCableATick() {
		LineFixture line = new LineFixture();
		BlockPos p1 = new BlockPos(1, 0, 0);
		BlockPos p2 = new BlockPos(2, 0, 0);
		BlockPos p3 = new BlockPos(3, 0, 0);
		line.cable(p1, 3, 12, 0);
		line.cable(p2, 2, 12, 0);
		line.cable(p3, 1, 12, 12);
		BlockPos machinePos = new BlockPos(0, 0, 0);
		StubPort m = new StubPort(1000, 0, false, true);
		EnergyLineDistributor<BlockPos> d = line.distributor(Map.of(machinePos, 3));

		tick(d, machine(machinePos, m, 12), List.of(), 0.0, 0);
		assertEquals(12, line.amount(p2), "tick 1: one hop");
		tick(d, machine(machinePos, m, 12), List.of(), 0.0, 1);
		assertEquals(12, line.amount(p1), "tick 2: the machine's cable");
		assertEquals(0, m.amount);
		tick(d, machine(machinePos, m, 12), List.of(), 0.0, 2);
		assertEquals(12, m.amount, "tick 3: the machine draws it");
	}

	@Test
	void aConsumerIgnoresCablesOnFacesThatCannotDraw() {
		LineFixture line = new LineFixture();
		BlockPos west = new BlockPos(0, 0, 0);
		BlockPos east = new BlockPos(2, 0, 0);
		line.cable(west, 1, 100, 10);
		line.cable(east, 1, 100, 50);
		BlockPos consumerPos = new BlockPos(1, 0, 0);

		long moved = tick(line.distributor(Map.of(consumerPos, 1), (pos, dir) -> dir == Direction.WEST,
				(pos, dir) -> true), machine(consumerPos, new StubPort(1000, 0, false, true), 32), List.of(),
				COPPER_LOSS, 0).delivered();

		assertEquals(10, moved, "only the drawable WEST cable gave");
		assertEquals(50, line.amount(east), "the cable on a non-accepting face must be untouched");
	}

	@Test
	void aGeneratorIgnoresCablesOnFacesThatCannotFeed() {
		LineFixture line = new LineFixture();
		BlockPos west = new BlockPos(0, 0, 0);
		BlockPos east = new BlockPos(2, 0, 0);
		line.cable(west, 1, 100, 0);
		line.cable(east, 1, 100, 0);
		StubPort source = new StubPort(10_000, 10_000, true, false);

		tick(line.distributor(Map.of(), (pos, dir) -> true, (pos, dir) -> dir == Direction.EAST), List.of(),
				List.of(new EnergyLineDistributor.LiveProducer<>(new BlockPos(1, 0, 0), source)), 0.0, 0);

		assertEquals(32, line.amount(east));
		assertEquals(0, line.amount(west), "the cable on a non-emitting face must stay empty");
	}

	@Test
	void aFullTickConservesEuAtZeroLoss() {
		LineFixture line = new LineFixture();
		BlockPos c1 = new BlockPos(1, 0, 0);
		BlockPos c2 = new BlockPos(2, 0, 0);
		line.cable(c1, 2, 12, 5);
		line.cable(c2, 1, 12, 12);
		BlockPos consumerPos = new BlockPos(0, 0, 0);
		StubPort consumer = new StubPort(1000, 100, false, true);
		StubPort source = new StubPort(10_000, 10_000, true, false);
		long before = consumer.amount + source.amount + line.amount(c1) + line.amount(c2);

		tick(line.distributor(Map.of(consumerPos, 2)), machine(consumerPos, consumer, 32),
				List.of(new EnergyLineDistributor.LiveProducer<>(new BlockPos(3, 0, 0), source)), 0.0, 0);

		assertEquals(before, consumer.amount + source.amount + line.amount(c1) + line.amount(c2));
		assertTrue(consumer.amount > 100);
	}

	@Test
	void eachGeneratorPutsInAtMostOnePacket() {
		LineFixture line = new LineFixture();
		BlockPos cable = new BlockPos(0, 0, 0);
		line.cable(cable, 1, 1_000, 0);
		List<StubPort> gens = List.of(new StubPort(10_000, 10_000, true, false),
				new StubPort(10_000, 10_000, true, false), new StubPort(10_000, 10_000, true, false));
		List<EnergyLineDistributor.LiveProducer<BlockPos>> generators = List.of(
				new EnergyLineDistributor.LiveProducer<>(new BlockPos(-1, 0, 0), gens.get(0)),
				new EnergyLineDistributor.LiveProducer<>(new BlockPos(0, 1, 0), gens.get(1)),
				new EnergyLineDistributor.LiveProducer<>(new BlockPos(1, 0, 0), gens.get(2)));

		tick(line.distributor(Map.of()), List.of(), generators, 0.0, 0);

		for (StubPort g : gens) {
			assertEquals(10_000L - 32L, g.amount, "each source gives one packet");
		}
		assertEquals(96L, line.amount(cable));
	}

	/** The outward fill on BlockPos: with nobody waiting a generator fills its line one cable a tick (ADR-007). */
	@Test
	void withNobodyWaitingTheLineFillsOutward() {
		LineFixture line = new LineFixture();
		BlockPos near = new BlockPos(1, 0, 0);
		BlockPos far = new BlockPos(2, 0, 0);
		line.cable(near, 1, 12, 0);
		line.cable(far, 2, 12, 0);
		StubPort source = new StubPort(10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer<BlockPos>> gen =
				List.of(new EnergyLineDistributor.LiveProducer<>(new BlockPos(0, 0, 0), source));
		EnergyLineDistributor<BlockPos> d = line.distributor(Map.of());

		tick(d, List.of(), gen, 0.0, 0);
		assertEquals(12, line.amount(near));
		assertEquals(0, line.amount(far));
		tick(d, List.of(), gen, 0.0, 1);
		assertEquals(12, line.amount(far), "one hop out");
		assertEquals(12, line.amount(near), "refilled");
	}
}
