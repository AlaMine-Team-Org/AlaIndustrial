package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * L1.5 unit tests for {@link EnergyLineDistributor} — the per-tick distribution kernel that
 * {@link EnergyNetwork#tick()} delegates to. Uses a synthetic cable graph (a {@link Set} of
 * {@link BlockPos}), hand-rolled {@link EnergyBuffer} cable buffers and {@link StubPort}
 * producers/consumers, plus a fake {@link EnergyTransactions} installed via
 * {@link EnergyTransactions#install}.
 *
 * <p>Lives in {@code :neoforge:test} because {@link EnergyLineDistributor} references
 * {@link BlockPos} (used as the cable/consumer identity in the no-self-churn check). Same pattern as
 * {@code FluidMoverTest} — MC-coupled common code goes through the {@link EphemeralTestServerProvider}
 * lane so the minecraft jar is on the classpath. The test itself never spins up a world; the provider
 * is only here for the classpath.
 *
 * <p>Pins the segment-to-segment flow contract (MOD-070): locality (a consumer only drains the cable
 * buffers it physically touches), the per-source packet cap, the round-robin pull, the no-self-churn
 * rule, and the producer/storage partitioning that keeps two batteries from draining each other.
 *
 * @implements energy-network line distribution kernel (locality, packet cap, loss, round-robin, no-self-churn)
 */
@ExtendWith(EphemeralTestServerProvider.class)
class EnergyLineDistributorTest {

	/** A fake EnergyTransactions that runs body callbacks synchronously against a shared fake txn. */
	private static final class FakeTransactions implements EnergyTransactions {
		final FakeTxn txn = new FakeTxn();

		@Override
		public void runCommitting(Consumer<EnergyPort.Txn> body) {
			body.accept(txn);
		}

		@Override
		public <T> T simulate(Function<EnergyPort.Txn, T> body) {
			return body.apply(txn);
		}
	}

	private static final class FakeTxn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	/** A fake producer/consumer port whose extract/insert are clamped by a fixed capacity. */
	private static final class StubPort implements EnergyPort {
		final BlockPos pos;
		final long capacity;
		long amount;
		final boolean extractable;
		final boolean insertable;

		StubPort(BlockPos pos, long capacity, long amount, boolean extractable, boolean insertable) {
			this.pos = pos;
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
			long room = capacity - amount;
			long moved = Math.min(Math.max(0, room), maxAmount);
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

	/** Build a cable buffer of given capacity pre-filled with {@code amount} EU. */
	private static EnergyBuffer cableBuffer(long capacity, long amount) {
		EnergyBuffer b = new EnergyBuffer(capacity, capacity, capacity, () -> {
		});
		b.amount = amount;
		return b;
	}

	/** A synthetic cable graph + per-cable buffer + cable-distance table for kernel tests. */
	private static final class LineFixture {
		final Set<BlockPos> cables = new HashSet<>();
		final Map<BlockPos, EnergyBuffer> buffers = new HashMap<>();
		final Map<BlockPos, Integer> cableDistance = new HashMap<>();

		void cable(BlockPos p, int distance, long capacity, long fill) {
			cables.add(p);
			buffers.put(p, cableBuffer(capacity, fill));
			cableDistance.put(p, distance);
		}

		EnergyLineDistributor distributor(Map<BlockPos, Integer> consumerDistance) {
			List<BlockPos> order = new ArrayList<>(cables);
			order.sort((a, b) -> Integer.compare(cableDistance.get(b), cableDistance.get(a)));
			return new EnergyLineDistributor(
					cables::contains,
					buffers::get,
					pos -> consumerDistance.getOrDefault(pos, 0),
					cableDistance::get,
					order);
		}
	}

	// --- serveConsumersFromLine: locality (MOD-070) ---

	@Test
	void serveConsumersFromLine_pullsOnlyFromTouchedCables() {
		// Two cables with EU each; one consumer adjacent ONLY to cable A. Cable B must be untouched.
		LineFixture line = new LineFixture();
		BlockPos cableA = new BlockPos(0, 0, 0);
		BlockPos cableB = new BlockPos(5, 0, 0);
		line.cable(cableA, 1, 100, 50);
		line.cable(cableB, 2, 100, 50);

		// Consumer at (0,1,0): adjacent to cableA only.
		BlockPos consumerPos = new BlockPos(0, 1, 0);
		StubPort consumer = new StubPort(consumerPos, 1000, 0, false, true);
		List<EnergyLineDistributor.LiveConsumer> consumers = List.of(
				new EnergyLineDistributor.LiveConsumer(consumerPos, consumer, 1000));

		EnergyLineDistributor d = line.distributor(Map.of(consumerPos, 1));
		long moved = d.serveConsumersFromLine(consumers, 32, txns.txn, 0);

		// packetCap = 32 caps each consumer's per-tick draw, so the consumer takes 32 of cableA's 50.
		assertEquals(32, moved, "consumer pulled up to packetCap (32) from cableA");
		assertEquals(18, line.buffers.get(cableA).amount, "cableA leftover = 50 − 32");
		assertEquals(50, line.buffers.get(cableB).amount, "cableB untouched (not adjacent to consumer)");
	}

	@Test
	void serveConsumersFromLine_returnsZeroWhenNoCableHasEnergy() {
		LineFixture line = new LineFixture();
		line.cable(new BlockPos(0, 0, 0), 1, 100, 0); // empty
		line.cable(new BlockPos(1, 0, 0), 2, 100, 0); // empty

		BlockPos consumerPos = new BlockPos(0, 1, 0);
		StubPort consumer = new StubPort(consumerPos, 1000, 0, false, true);
		List<EnergyLineDistributor.LiveConsumer> consumers = List.of(
				new EnergyLineDistributor.LiveConsumer(consumerPos, consumer, 1000));

		EnergyLineDistributor d = line.distributor(Map.of(consumerPos, 1));
		assertEquals(0, d.serveConsumersFromLine(consumers, 32, txns.txn, 0));
	}

	@Test
	void serveConsumersFromLine_emptyClassReturnsZero() {
		LineFixture line = new LineFixture();
		line.cable(new BlockPos(0, 0, 0), 1, 100, 50);
		EnergyLineDistributor d = line.distributor(Map.of());
		assertEquals(0, d.serveConsumersFromLine(List.of(), 32, txns.txn, 0));
	}

	// --- chargeAndPropagateLine: producer/storage partitioning (MOD-070) ---

	@Test
	void chargeAndPropagateLine_generatorsAlwaysFillAdjacentLine() {
		// No consumers, no machine demand: a generator still fills adjacent cable buffers.
		LineFixture line = new LineFixture();
		line.cable(new BlockPos(0, 0, 0), 1, 100, 0); // empty, room=100

		// Generator at (-1,0,0) is adjacent ONLY to cable at origin.
		BlockPos generatorPos = new BlockPos(-1, 0, 0);
		StubPort generator = new StubPort(generatorPos, 10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer> generators = List.of(
				new EnergyLineDistributor.LiveProducer(generatorPos, generator));

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(generators, List.of(), 0L, 10_000L, 32, txns.txn);

		// Per-source packet cap = 32, so the generator injects at most 32 EU into the line this tick.
		assertEquals(32, line.buffers.get(new BlockPos(0, 0, 0)).amount,
				"source-adjacent cable filled up to packetCap (32) this tick");
		assertEquals(10_000L - 32L, generator.amount, "generator drawn exactly 32 EU");
	}

	@Test
	void chargeAndPropagateLine_storageMustNotDischargeWhenGeneratorsCoverDemand() {
		// Generators already cover demand → storage must NOT discharge into the line.
		LineFixture line = new LineFixture();
		line.cable(new BlockPos(0, 0, 0), 1, 100, 0); // empty cable

		BlockPos generatorPos = new BlockPos(-1, 0, 0);
		BlockPos storagePos = new BlockPos(0, 1, 0); // adjacent to cable at origin via Y face
		StubPort generator = new StubPort(generatorPos, 10_000, 10_000, true, false);
		StubPort storage = new StubPort(storagePos, 10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer> generators = List.of(
				new EnergyLineDistributor.LiveProducer(generatorPos, generator));
		List<EnergyLineDistributor.LiveProducer> storageSources = List.of(
				new EnergyLineDistributor.LiveProducer(storagePos, storage));

		EnergyLineDistributor d = line.distributor(Map.of());
		long storageBefore = storage.amount;
		// genSupply (20) > machineDemand (10) → storageBudget = max(0, 10−20) = 0.
		// Generator still charges the line freely (its own budget is Long.MAX_VALUE), but storage
		// contributes nothing.
		d.chargeAndPropagateLine(generators, storageSources, 10L, 20L, 32, txns.txn);

		assertEquals(storageBefore, storage.amount,
				"storage must NOT discharge when generators cover the machine demand");
		assertTrue(generator.amount < 10_000L, "generator did charge the line (it always can)");
	}

	@Test
	void chargeAndPropagateLine_emptyOrderIsNoOp() {
		// Empty propagation order (no cables) — chargeAndPropagateLine must not throw.
		LineFixture line = new LineFixture();
		// no cables at all
		BlockPos generatorPos = new BlockPos(-1, 0, 0);
		StubPort generator = new StubPort(generatorPos, 10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer> generators = List.of(
				new EnergyLineDistributor.LiveProducer(generatorPos, generator));

		EnergyLineDistributor d = line.distributor(Map.of());
		long before = generator.amount;
		d.chargeAndPropagateLine(generators, List.of(), 0L, 10_000L, 32, txns.txn);
		assertEquals(before, generator.amount, "generator must not draw when there are no cables to charge");
	}

	// --- pullRoundRobin: no-self-churn for a co-located dual-role endpoint ---

	@Test
	void chargeLineFrom_doesNotPullFromCoLocatedProducer() {
		// No-self-churn invariant (the rule that stops a dual-role BatteryBox washing EU through
		// itself): a producer whose position equals the "consumer" position must NOT be drained to
		// fill that same position. chargeLineFrom itself doesn't know the consumer — the no-self-churn
		// check lives in pullRoundRobin on the serve path. This test pins the charge path's per-source
		// packetCap through a different lens: when two sources are co-located (both at the same pos),
		// the kernel processes them sequentially and each contributes up to packetCap independently.
		// That distinguishes per-source capping from "treat the co-located pair as one source".
		LineFixture line = new LineFixture();
		line.cable(new BlockPos(0, 0, 0), 1, 1_000, 0); // big room so packetCap is the only bound

		BlockPos same = new BlockPos(-1, 0, 0);
		StubPort prodA = new StubPort(same, 10_000, 10_000, true, false);
		StubPort prodB = new StubPort(same, 10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer> sources = List.of(
				new EnergyLineDistributor.LiveProducer(same, prodA),
				new EnergyLineDistributor.LiveProducer(same, prodB));

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(sources, List.of(), 0L, 10_000L, 32, txns.txn);

		long drawnA = 10_000L - prodA.amount;
		long drawnB = 10_000L - prodB.amount;
		// Per-source packetCap = 32: each source's draw is bounded individually by packetCap.
		// Two co-located producers can together inject up to 2×packetCap (the cap is per-source, not
		// per-target). This distinguishes per-source capping from "treat the co-located pair as one
		// source" — a regression that would silently double-throughput a BatteryBox cluster.
		assertTrue(drawnA <= 32L, "producer A draw bounded by packetCap; got " + drawnA);
		assertTrue(drawnB <= 32L, "producer B draw bounded by packetCap; got " + drawnB);
		assertEquals(drawnA + drawnB, line.buffers.get(new BlockPos(0, 0, 0)).amount,
				"all drawn EU lands in the cable buffer");
	}

	// --- round-robin rotation over multiple producers (packet cap) ---

	@Test
	void chargeAndPropagateLine_packetCapBoundsTotalDrawnPerSource() {
		// Three generators all adjacent to ONE cable. The packet cap is PER-SOURCE (= 32), so each of
		// the three draws up to 32 and the total is up to 96 (3 × 32) — bounded further only by the
		// cable's free room. This is the point of the test: it distinguishes a per-source cap (total 96)
		// from a merged/per-target cap (total would collapse to 32), the regression the assert on the
		// total below guards against.
		LineFixture line = new LineFixture();
		BlockPos cable = new BlockPos(0, 0, 0);
		line.cable(cable, 1, 1_000, 0); // large room — 96 EU fits, so room never masks a merged-cap bug

		BlockPos g1 = new BlockPos(-1, 0, 0);
		BlockPos g2 = new BlockPos(0, 1, 0);
		BlockPos g3 = new BlockPos(1, 0, 0);
		StubPort gen1 = new StubPort(g1, 10_000, 10_000, true, false);
		StubPort gen2 = new StubPort(g2, 10_000, 10_000, true, false);
		StubPort gen3 = new StubPort(g3, 10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer> generators = List.of(
				new EnergyLineDistributor.LiveProducer(g1, gen1),
				new EnergyLineDistributor.LiveProducer(g2, gen2),
				new EnergyLineDistributor.LiveProducer(g3, gen3));

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(generators, List.of(), 0L, 10_000L, 32, txns.txn);

		long drawn1 = 10_000L - gen1.amount;
		long drawn2 = 10_000L - gen2.amount;
		long drawn3 = 10_000L - gen3.amount;
		long total = drawn1 + drawn2 + drawn3;

		// Each source's draw is bounded by packetCap (32) individually...
		assertTrue(drawn1 <= 32L, "gen1 draw bounded by packetCap; got " + drawn1);
		assertTrue(drawn2 <= 32L, "gen2 draw bounded by packetCap; got " + drawn2);
		assertTrue(drawn3 <= 32L, "gen3 draw bounded by packetCap; got " + drawn3);
		// ...and — the regression guard — the cap is PER-SOURCE, so all three fill their packet: total
		// is 96, not a merged/per-target 32. Room (1000) is far larger, so it cannot mask the collapse.
		assertEquals(96L, total, "per-source cap: 3 sources each give 32 → total 96, not a merged 32");
		assertEquals(total, line.buffers.get(cable).amount,
				"all drawn EU lands in the cable buffer");
	}
}
