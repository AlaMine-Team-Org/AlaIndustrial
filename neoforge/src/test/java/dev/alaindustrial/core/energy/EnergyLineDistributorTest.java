package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class EnergyLineDistributorTest {

	/**
	 * The loss rate these fixtures run at. Every case here is a copper line, and the distributor now takes
	 * the rate as an argument rather than reading Config (MOD-219) — pinning the literal keeps the existing
	 * expectations meaningful instead of silently tracking whatever the config default becomes.
	 */
	private static final double COPPER_LOSS = 0.02;

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
		b.setAmountUntracked(amount);
		return b;
	}

	/**
	 * A synthetic cable graph + per-cable buffer + flow-potential table for kernel tests.
	 *
	 * <p>MOD-252: the third column is the cable's <em>flow potential</em>, not its distance from a source.
	 * Energy is pulled from strictly higher potential to lower and the sweep runs in ASCENDING potential,
	 * which is why the comparator below is the mirror of the pre-MOD-252 one. In sink mode the potential is
	 * the distance to the nearest waiting sink; in the producer-only fallback it is the negated distance to
	 * the source, so the same ordering rule reproduces the old sweep exactly. None of the pre-existing
	 * cases below depend on the ordering (each has one cable, two non-adjacent cables, or never propagates),
	 * so their expectations are untouched.
	 */
	private static final class LineFixture {
		final Set<BlockPos> cables = new HashSet<>();
		final Map<BlockPos, EnergyBuffer> buffers = new HashMap<>();
		final Map<BlockPos, Integer> flowPotential = new HashMap<>();
		/**
		 * MOD-254 fork tie-break field: distance to the nearest waiting machine. Left empty by every
		 * pre-MOD-254 case, which is what a network with no hungry machine looks like — the lookup then
		 * answers null everywhere and the fork split is plain proportional.
		 */
		final Map<BlockPos, Integer> machinePotential = new HashMap<>();
		/**
		 * MOD-318 stranded-fill configuration. Empty by default, so every fixture that is not about that
		 * pass drives exactly the pre-MOD-318 kernel and its expectations keep their original meaning.
		 */
		List<BlockPos> strandedOrder = List.of();
		final Map<BlockPos, Integer> producerDistance = new HashMap<>();

		void cable(BlockPos p, int potential, long capacity, long fill) {
			cables.add(p);
			buffers.put(p, cableBuffer(capacity, fill));
			flowPotential.put(p, potential);
		}

		/** Same as {@link #cable} plus this cable's distance to the nearest waiting machine (MOD-254). */
		void cable(BlockPos p, int potential, int machineDistance, long capacity, long fill) {
			cable(p, potential, capacity, fill);
			machinePotential.put(p, machineDistance);
		}

		EnergyLineDistributor distributor(Map<BlockPos, Integer> consumerDistance) {
			return distributor(consumerDistance, (p, d) -> true, (p, d) -> true);
		}

		/**
		 * MOD-255 variant: the two face gates. {@code canDraw}/{@code canFeed} answer "may the endpoint at
		 * this position move EU through the face pointing this way" — the permission behind the raw
		 * adjacency the kernel uses to find an endpoint's cables. Every pre-MOD-255 case passes
		 * always-true gates, which is exactly how a block whose every face is role BOTH behaves.
		 */
		EnergyLineDistributor distributor(Map<BlockPos, Integer> consumerDistance,
				BiPredicate<BlockPos, Direction> canDraw, BiPredicate<BlockPos, Direction> canFeed) {
			List<BlockPos> order = new ArrayList<>(cables);
			order.sort((a, b) -> Integer.compare(flowPotential.get(a), flowPotential.get(b)));
			return new EnergyLineDistributor(
					cables::contains,
					buffers::get,
					pos -> consumerDistance.getOrDefault(pos, 0),
					flowPotential::get,
					machinePotential::get,
					order,
					canDraw,
					canFeed,
					strandedOrder,
					producerDistance::get);
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
		long moved = d.serveConsumersFromLine(consumers, 32, COPPER_LOSS, txns.txn, 0);

		// packetCap = 32 caps each consumer's per-tick draw, so the consumer takes 32 of cableA's 50.
		assertEquals(32, moved, "consumer pulled up to packetCap (32) from cableA");
		assertEquals(18, line.buffers.get(cableA).getAmount(), "cableA leftover = 50 − 32");
		assertEquals(50, line.buffers.get(cableB).getAmount(), "cableB untouched (not adjacent to consumer)");
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
		assertEquals(0, d.serveConsumersFromLine(consumers, 32, COPPER_LOSS, txns.txn, 0));
	}

	@Test
	void serveConsumersFromLine_emptyClassReturnsZero() {
		LineFixture line = new LineFixture();
		line.cable(new BlockPos(0, 0, 0), 1, 100, 50);
		EnergyLineDistributor d = line.distributor(Map.of());
		assertEquals(0, d.serveConsumersFromLine(List.of(), 32, COPPER_LOSS, txns.txn, 0));
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
		d.chargeAndPropagateLine(generators, List.of(), 0L, 10_000L, 32, txns.txn, 0);

		// Per-source packet cap = 32, so the generator injects at most 32 EU into the line this tick.
		assertEquals(32, line.buffers.get(new BlockPos(0, 0, 0)).getAmount(),
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
		d.chargeAndPropagateLine(generators, storageSources, 10L, 20L, 32, txns.txn, 0);

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
		d.chargeAndPropagateLine(generators, List.of(), 0L, 10_000L, 32, txns.txn, 0);
		assertEquals(before, generator.amount, "generator must not draw when there are no cables to charge");
	}

	// --- propagateLineOneHop: flow direction + the one-hop-per-tick invariant (MOD-252) ---

	/**
	 * MOD-252 — the sweep moves energy DOWN the flow potential, exactly one cable per pass.
	 *
	 * <p>Four cables in a row at potentials 1..4 (1 = adjacent to the thing that wants the energy), with
	 * the whole charge parked at the far end. Two passes must walk it to potential 3, then to 2 — never
	 * further in a single pass. This pins both halves of the contract at once: the direction (before
	 * MOD-252 the rule pulled from the strictly SMALLER field value, so nothing here would move at all)
	 * and the one-hop-per-tick fill front, which had no test of its own.
	 *
	 * <p>No producers are passed, so the only thing that runs is the propagation sweep.
	 */
	@Test
	void propagateLineOneHop_movesExactlyOneHopDownGradient() {
		LineFixture line = new LineFixture();
		BlockPos p1 = new BlockPos(1, 0, 0);
		BlockPos p2 = new BlockPos(2, 0, 0);
		BlockPos p3 = new BlockPos(3, 0, 0);
		BlockPos p4 = new BlockPos(4, 0, 0);
		line.cable(p1, 1, 12, 0);
		line.cable(p2, 2, 12, 0);
		line.cable(p3, 3, 12, 0);
		line.cable(p4, 4, 12, 12); // the whole charge sits furthest from demand

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(List.of(), List.of(), 0L, 0L, 32, txns.txn, 0);

		assertEquals(0, line.buffers.get(p4).getAmount(), "pass 1: the far cable handed its charge downhill");
		assertEquals(12, line.buffers.get(p3).getAmount(), "pass 1: charge advanced exactly one hop, to potential 3");
		assertEquals(0, line.buffers.get(p2).getAmount(), "pass 1: charge must NOT skip a hop");
		assertEquals(0, line.buffers.get(p1).getAmount(), "pass 1: charge must NOT reach the sink-side cable");

		d.chargeAndPropagateLine(List.of(), List.of(), 0L, 0L, 32, txns.txn, 0);

		assertEquals(12, line.buffers.get(p2).getAmount(), "pass 2: one more hop, to potential 2");
		assertEquals(0, line.buffers.get(p3).getAmount(), "pass 2: the charge left potential 3");
		assertEquals(0, line.buffers.get(p1).getAmount(), "pass 2: still exactly one hop per pass");
	}

	/**
	 * Two adjacent cables at EQUAL potential must not exchange. The strict comparison is what bounds a
	 * unit to one hop per sweep; relaxing it to {@code <} would let a charge cross a plateau in a single
	 * pass and quietly break the fill-front inertia the whole line model rests on.
	 */
	@Test
	void propagateLineOneHop_doesNotMoveBetweenEqualPotential() {
		LineFixture line = new LineFixture();
		BlockPos left = new BlockPos(1, 0, 0);
		BlockPos right = new BlockPos(2, 0, 0);
		line.cable(left, 2, 12, 12);
		line.cable(right, 2, 12, 0);

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(List.of(), List.of(), 0L, 0L, 32, txns.txn, 0);

		assertEquals(12, line.buffers.get(left).getAmount(), "equal-potential neighbours must not exchange");
		assertEquals(0, line.buffers.get(right).getAmount(), "equal-potential neighbours must not exchange");
	}

	/**
	 * MOD-254 (D3) — a buffer two neighbours are entitled to is SPLIT between them, not taken whole by
	 * whichever one the sweep reaches first.
	 *
	 * <p>This is the seam the failing MOD-252 rig sat on, in miniature: potentials 1-2-3-2-1, so the middle
	 * cable is the local maximum on the source's own segment and both of its neighbours may pull from it.
	 * The old sweep gave {@code min(free, donor.amount, packetCap)} to the first claimant it happened to
	 * visit, and the visit order came out of {@code HashMap} bucket order over absolute {@link BlockPos} —
	 * so one whole branch of the bus stayed dead at 0 EU, and which branch depended on where in the world
	 * the base was built.
	 *
	 * <p>Both claimants have identical room and no machine is waiting, so the fair answer is exactly half
	 * each. The donor must end empty: splitting must not strand EU at the seam either.
	 */
	@Test
	void propagateLineOneHop_splitsAForkedBufferBetweenBothClaimants() {
		LineFixture line = new LineFixture();
		BlockPos westFar = new BlockPos(1, 0, 0);
		BlockPos westNear = new BlockPos(2, 0, 0);
		BlockPos fork = new BlockPos(3, 0, 0);
		BlockPos eastNear = new BlockPos(4, 0, 0);
		BlockPos eastFar = new BlockPos(5, 0, 0);
		line.cable(westFar, 1, 12, 0);
		line.cable(westNear, 2, 12, 0);
		line.cable(fork, 3, 12, 12); // the seam on the source's own segment, holding one full packet
		line.cable(eastNear, 2, 12, 0);
		line.cable(eastFar, 1, 12, 0);

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(List.of(), List.of(), 0L, 0L, 32, txns.txn, 0);

		assertEquals(6, line.buffers.get(westNear).getAmount(), "the west branch got its half of the fork");
		assertEquals(6, line.buffers.get(eastNear).getAmount(), "the east branch got its half of the fork");
		assertEquals(0, line.buffers.get(fork).getAmount(), "the whole fork buffer moved — nothing stranded");
		assertEquals(0, line.buffers.get(westFar).getAmount(), "still exactly one hop per pass");
		assertEquals(0, line.buffers.get(eastFar).getAmount(), "still exactly one hop per pass");
	}

	/**
	 * MOD-254 / MOD-009 — at a fork, the branch that carries the unit toward a waiting MACHINE outweighs
	 * the branch that only leads to storage.
	 *
	 * <p>Serving machines before storage sinks is meaningless if the EU never reaches the machine's half of
	 * the wire, so the priority has to exist geometrically too. It is a weight and not a veto: the storage
	 * branch keeps a real share (4 of 12 here) rather than being cut off, which is the failure mode the
	 * whole MOD-252 line of work exists to avoid.
	 *
	 * <p>Same fork as above; only the machine-distance column differs — it falls toward the west, so west
	 * is machine-ward and east is not.
	 */
	@Test
	void propagateLineOneHop_prefersTheBranchLeadingToAMachine() {
		LineFixture line = new LineFixture();
		BlockPos westNear = new BlockPos(2, 0, 0);
		BlockPos fork = new BlockPos(3, 0, 0);
		BlockPos eastNear = new BlockPos(4, 0, 0);
		// Machine distance climbs away from the machine in the west: 2 < 3 < 4.
		line.cable(westNear, 2, 2, 12, 0);
		line.cable(fork, 3, 3, 12, 12);
		line.cable(eastNear, 2, 4, 12, 0);

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(List.of(), List.of(), 0L, 0L, 32, txns.txn, 0);

		assertEquals(8, line.buffers.get(westNear).getAmount(), "the machine-ward branch takes the larger share");
		assertEquals(4, line.buffers.get(eastNear).getAmount(), "the storage-ward branch is still served");
		assertEquals(0, line.buffers.get(fork).getAmount(), "the whole fork buffer moved — nothing stranded");
	}

	/**
	 * MOD-254 (review) — at a fork the INDIVISIBLE remainder rotates; it does not always fall on the same
	 * branch.
	 *
	 * <p>The proportional split alone does not make a fork fair. When the donor holds fewer EU than it has
	 * claimants, every proportional share floors to zero and the whole buffer becomes remainder — and a
	 * remainder awarded in a fixed order is the original 100 %:0 % starvation, just reached through the
	 * rounding path. This is not a corner case: {@code solarEuPerTick} is 1, so a single solar panel wired
	 * to the middle of a symmetric bus lands exactly here, and before the fix one machine ran forever while
	 * the other never saw a single EU.
	 *
	 * <p>Same symmetric fork as above holding ONE EU, run at two consecutive rotation offsets with the seam
	 * refilled in between (what the generator does each tick). Each branch must come out with exactly one
	 * EU: 2/0 either way means the remainder is pinned.
	 */
	@Test
	void propagateLineOneHop_rotatesTheIndivisibleRemainderBetweenBranches() {
		LineFixture line = new LineFixture();
		BlockPos westNear = new BlockPos(2, 0, 0);
		BlockPos fork = new BlockPos(3, 0, 0);
		BlockPos eastNear = new BlockPos(4, 0, 0);
		line.cable(westNear, 2, 12, 0);
		line.cable(fork, 3, 12, 1); // one EU — less than one per claimant, so it is ALL remainder
		line.cable(eastNear, 2, 12, 0);

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(List.of(), List.of(), 0L, 0L, 32, txns.txn, 0);
		assertEquals(0, line.buffers.get(fork).getAmount(), "tick 1: the single EU moved off the seam");

		line.buffers.get(fork).setAmountUntracked(1); // the generator refills the seam for the next tick
		d.chargeAndPropagateLine(List.of(), List.of(), 0L, 0L, 32, txns.txn, 1);

		assertEquals(1, line.buffers.get(westNear).getAmount(),
				"the west branch got the spare EU on exactly one of the two offsets");
		assertEquals(1, line.buffers.get(eastNear).getAmount(),
				"the east branch got it on the other — a fixed award order starves this branch forever");
		assertEquals(0, line.buffers.get(fork).getAmount(), "tick 2: nothing stranded at the seam");
	}

	/**
	 * MOD-254 (review) — the remainder is dealt one EU at a time, not absorbed whole by whoever is served
	 * first.
	 *
	 * <p>A three-way junction holding 2 EU: the proportional pass floors all three shares to zero, so both
	 * EU are remainder. Dealing them greedily would put both on one branch; dealing them one at a time puts
	 * them on two different branches. Pins the granularity independently of the rotation above — a greedy
	 * award that merely rotated its starting point would still fail here.
	 */
	@Test
	void propagateLineOneHop_dealsTheRemainderOneEuAtATime() {
		LineFixture line = new LineFixture();
		BlockPos junction = new BlockPos(0, 0, 0);
		line.cable(junction, 3, 12, 2);
		List<BlockPos> branches = List.of(
				new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1));
		for (BlockPos b : branches) {
			line.cable(b, 2, 12, 0);
		}

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(List.of(), List.of(), 0L, 0L, 32, txns.txn, 0);

		int fed = 0;
		for (BlockPos b : branches) {
			long got = line.buffers.get(b).getAmount();
			assertTrue(got <= 1, "no branch may take a second EU while another has none, got " + got);
			fed += got > 0 ? 1 : 0;
		}
		assertEquals(2, fed, "the two spare EU landed on two different branches");
		assertEquals(0, line.buffers.get(junction).getAmount(), "the whole junction buffer moved");
	}

	/**
	 * MOD-254 (D4) — the source sweep starts at the rotation offset, so a cable with room for one packet
	 * is not fed by the same source on every tick.
	 *
	 * <p>The cable holds exactly one packet of room and two sources sit on it. Whoever the sweep reaches
	 * first takes all of it — that part is unchanged and is fine — but the sweep must not reach the same
	 * one every time, or the other source stands at a full buffer for good. Two passes at consecutive
	 * offsets, with the cable drained in between, must draw from the two sources in turn.
	 */
	@Test
	void chargeLineFrom_rotationLetsEverySourceFeedASaturatedCable() {
		LineFixture line = new LineFixture();
		BlockPos cable = new BlockPos(0, 0, 0);
		line.cable(cable, 1, 12, 0); // room for exactly one packet, well under packetCap

		BlockPos aPos = new BlockPos(-1, 0, 0);
		BlockPos bPos = new BlockPos(1, 0, 0);
		StubPort a = new StubPort(aPos, 10_000, 10_000, true, false);
		StubPort b = new StubPort(bPos, 10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer> sources = List.of(
				new EnergyLineDistributor.LiveProducer(aPos, a),
				new EnergyLineDistributor.LiveProducer(bPos, b));

		EnergyLineDistributor d = line.distributor(Map.of());
		d.chargeAndPropagateLine(sources, List.of(), 0L, 20_000L, 32, txns.txn, 0);
		long drawnAFirst = 10_000L - a.amount;
		long drawnBFirst = 10_000L - b.amount;

		line.buffers.get(cable).setAmountUntracked(0); // the consumer drained the segment again
		d.chargeAndPropagateLine(sources, List.of(), 0L, 20_000L, 32, txns.txn, 1);
		long drawnASecond = 10_000L - a.amount - drawnAFirst;
		long drawnBSecond = 10_000L - b.amount - drawnBFirst;

		assertEquals(12, drawnAFirst + drawnBFirst, "pass 1 filled the segment's one packet of room");
		assertEquals(12, drawnASecond + drawnBSecond, "pass 2 filled it again");
		assertTrue(drawnAFirst + drawnASecond > 0, "source A fed the line on one of the two offsets");
		assertTrue(drawnBFirst + drawnBSecond > 0, "source B fed the line on the other offset — without the"
				+ " rotation it would still be sitting at a full buffer");
	}

	// --- MOD-255: face gates — adjacency is not permission ---

	/**
	 * MOD-255 — a consumer only draws from the cables on faces that can ACCEPT energy.
	 *
	 * <p>Two cables touch the endpoint, one on each side; the gate rejects the east one (a Battery Box's
	 * OUT face, or any inert front). Only the accepted cable may be drained. Without the gate the kernel
	 * treats both as supply, which is how a dual-role box sucked back the EU it had just discharged into
	 * the wire — the line never advanced and the machines past it starved.
	 *
	 * <p>The allowed cable deliberately holds LESS than one packet (10 &lt; 32) and the rejected one far
	 * more: the delivered total then separates the two behaviours by itself (10 gated vs a full 32 packet
	 * ungated) no matter which order the supply pool happens to be built in. Sizing both cables above the
	 * packet cap would let the round-robin start on the allowed one and pass by luck.
	 */
	@Test
	void serveConsumersFromLine_ignoresCablesOnNonDrawableFaces() {
		LineFixture line = new LineFixture();
		BlockPos west = new BlockPos(0, 0, 0);
		BlockPos east = new BlockPos(2, 0, 0);
		line.cable(west, 1, 100, 10);
		line.cable(east, 1, 100, 50);

		BlockPos consumerPos = new BlockPos(1, 0, 0); // between the two cables
		StubPort consumer = new StubPort(consumerPos, 1000, 0, false, true);
		List<EnergyLineDistributor.LiveConsumer> consumers = List.of(
				new EnergyLineDistributor.LiveConsumer(consumerPos, consumer, 1000));

		// Only the WEST face may draw; the EAST face is the discharging one.
		EnergyLineDistributor d = line.distributor(Map.of(consumerPos, 1),
				(pos, dir) -> dir == Direction.WEST, (pos, dir) -> true);
		long moved = d.serveConsumersFromLine(consumers, 32, COPPER_LOSS, txns.txn, 0);

		assertEquals(10, moved, "consumer got only what the one drawable cable held, not a full packet");
		assertEquals(0, line.buffers.get(west).getAmount(), "the drawable cable was drained");
		assertEquals(50, line.buffers.get(east).getAmount(),
				"the cable on a non-accepting face must be untouched — that is the self-churn path");
	}

	/**
	 * MOD-255 — a source only feeds the cables on faces that can EMIT. The port is resolved once for the
	 * endpoint's cached side, so without the gate a Battery Box pushes EU out of its INPUT face into the
	 * cable there: energy leaving through the face the player wired as an input.
	 */
	@Test
	void chargeLineFrom_ignoresCablesOnNonFeedableFaces() {
		LineFixture line = new LineFixture();
		BlockPos west = new BlockPos(0, 0, 0);
		BlockPos east = new BlockPos(2, 0, 0);
		line.cable(west, 1, 100, 0);
		line.cable(east, 1, 100, 0);

		BlockPos sourcePos = new BlockPos(1, 0, 0);
		StubPort source = new StubPort(sourcePos, 10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer> generators = List.of(
				new EnergyLineDistributor.LiveProducer(sourcePos, source));

		// Only the EAST face may emit; WEST is the input side.
		EnergyLineDistributor d = line.distributor(Map.of(),
				(pos, dir) -> true, (pos, dir) -> dir == Direction.EAST);
		d.chargeAndPropagateLine(generators, List.of(), 0L, 10_000L, 32, txns.txn, 0);

		assertEquals(32, line.buffers.get(east).getAmount(), "the emitting face filled its cable up to packetCap");
		assertEquals(0, line.buffers.get(west).getAmount(),
				"the cable on a non-emitting face must stay empty — a source must not leak out of its input");
		assertEquals(10_000L - 32L, source.amount, "exactly one packet left the source, not two");
	}

	/**
	 * MOD-255 acceptance — a full kernel pass neither creates nor destroys EU. Run at zero loss so the
	 * invariant is an exact equality rather than a restatement of {@link EnergyShare#cableLoss} (the loss
	 * term has its own tests; re-deriving it here would make this one tautological).
	 *
	 * <p>Covers the whole tick shape: serve a consumer from the line, propagate a hop, recharge from the
	 * source. The sum over source + every cable + consumer must be what it was before. This is the guard
	 * against the class of fix that "solves" self-churn by quietly dropping EU on the floor.
	 */
	@Test
	void fullPass_conservesTotalEuAtZeroLoss() {
		LineFixture line = new LineFixture();
		BlockPos c1 = new BlockPos(1, 0, 0);
		BlockPos c2 = new BlockPos(2, 0, 0);
		BlockPos c3 = new BlockPos(3, 0, 0);
		line.cable(c1, 1, 12, 5);
		line.cable(c2, 2, 12, 12);
		line.cable(c3, 3, 12, 7);

		BlockPos consumerPos = new BlockPos(0, 0, 0); // touches c1 only
		BlockPos sourcePos = new BlockPos(4, 0, 0); // touches c3 only
		StubPort consumer = new StubPort(consumerPos, 1000, 100, false, true);
		StubPort source = new StubPort(sourcePos, 10_000, 10_000, true, false);

		long before = consumer.amount + source.amount
				+ line.buffers.get(c1).getAmount() + line.buffers.get(c2).getAmount() + line.buffers.get(c3).getAmount();

		EnergyLineDistributor d = line.distributor(Map.of(consumerPos, 3));
		d.serveConsumersFromLine(
				List.of(new EnergyLineDistributor.LiveConsumer(consumerPos, consumer, 900)),
				32, 0.0, txns.txn, 0);
		d.chargeAndPropagateLine(
				List.of(new EnergyLineDistributor.LiveProducer(sourcePos, source)), List.of(),
				900L, 10_000L, 32, txns.txn, 0);

		long after = consumer.amount + source.amount
				+ line.buffers.get(c1).getAmount() + line.buffers.get(c2).getAmount() + line.buffers.get(c3).getAmount();
		assertEquals(before, after, "a full kernel pass at zero loss must neither create nor destroy EU");
		assertTrue(consumer.amount > 100, "fixture sanity: the consumer actually received something");
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
		d.chargeAndPropagateLine(sources, List.of(), 0L, 10_000L, 32, txns.txn, 0);

		long drawnA = 10_000L - prodA.amount;
		long drawnB = 10_000L - prodB.amount;
		// Per-source packetCap = 32: each source's draw is bounded individually by packetCap.
		// Two co-located producers can together inject up to 2×packetCap (the cap is per-source, not
		// per-target). This distinguishes per-source capping from "treat the co-located pair as one
		// source" — a regression that would silently double-throughput a BatteryBox cluster.
		assertTrue(drawnA <= 32L, "producer A draw bounded by packetCap; got " + drawnA);
		assertTrue(drawnB <= 32L, "producer B draw bounded by packetCap; got " + drawnB);
		assertEquals(drawnA + drawnB, line.buffers.get(new BlockPos(0, 0, 0)).getAmount(),
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
		d.chargeAndPropagateLine(generators, List.of(), 0L, 10_000L, 32, txns.txn, 0);

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
		assertEquals(total, line.buffers.get(cable).getAmount(),
				"all drawn EU lands in the cable buffer");
	}

	// --- MOD-318: segments the downhill rule can never reach ---

	/**
	 * The five-cable spur rig shared by the MOD-318 cases. Potential is distance to the waiting sink,
	 * producer distance is hops from the generator:
	 *
	 * <pre>
	 *   sink ── c1(p1,d3) ── c2(p2,d2) ── c3(p3,d1) ── generator
	 *                                      └── s1(p4,d2) ── s2(p5,d3)
	 * </pre>
	 *
	 * <p>Nothing waits on the s-branch, so every cable on it sits ABOVE the only cable that could feed it.
	 */
	private static final BlockPos C1 = new BlockPos(1, 0, 0);
	private static final BlockPos C2 = new BlockPos(2, 0, 0);
	private static final BlockPos C3 = new BlockPos(3, 0, 0);
	private static final BlockPos S1 = new BlockPos(3, 1, 0);
	private static final BlockPos S2 = new BlockPos(3, 2, 0);
	private static final BlockPos GEN = new BlockPos(4, 0, 0);
	/** Hops from the generator — the direction the stranded fill walks. */
	private static final Map<BlockPos, Integer> PRODUCER_DISTANCE =
			Map.of(C3, 1, C2, 2, C1, 3, S1, 2, S2, 3);
	/** Farthest from the generator first, so a claimant is visited before the cable that feeds it. */
	private static final List<BlockPos> STRANDED_ORDER = List.of(S2, S1);

	private static LineFixture spurRig() {
		LineFixture line = new LineFixture();
		line.cable(C1, 1, 12, 0);
		line.cable(C2, 2, 12, 0);
		line.cable(C3, 3, 12, 0);
		line.cable(S1, 4, 12, 0);
		line.cable(S2, 5, 12, 0);
		return line;
	}

	/**
	 * The defect itself, pinned as behaviour (MOD-318). Energy only ever moves to a STRICTLY lower flow
	 * potential and a cable is only ever charged directly if it touches a producer, so a branch with
	 * nothing waiting on it has no filling path at all — it reads 0 EU forever while the far end of the
	 * very same line is demonstrably full. This is the negative control for the fill pass below: without
	 * it, {@code fillStrandedOneHop} could be a no-op and its tests would still pass.
	 */
	@Test
	void chargeAndPropagateLine_strandsASpurWithNothingWaitingOnIt() {
		LineFixture line = spurRig();
		StubPort gen = new StubPort(GEN, 10_000, 10_000, true, false);
		EnergyLineDistributor d = line.distributor(Map.of());
		for (int i = 0; i < 20; i++) {
			d.chargeAndPropagateLine(List.of(new EnergyLineDistributor.LiveProducer(GEN, gen)), List.of(),
					0L, 10_000L, 32, txns.txn, 0);
		}

		assertEquals(12, line.buffers.get(C1).getAmount(), "negative control: the corridor saturates end to end");
		assertEquals(12, line.buffers.get(C3).getAmount(), "negative control: the source-side cable is full");
		assertEquals(0, line.buffers.get(S1).getAmount(), "MOD-318: the spur has no downhill path and stays dead");
		assertEquals(0, line.buffers.get(S2).getAmount(), "MOD-318: and so does the rest of it");
	}

	/**
	 * The fix, driven through the real tick shape: once the corridor is saturated, the surplus reaches the
	 * stranded branch too. Same rig as the test above — the ONLY difference is that the kernel is given the
	 * stranded set — so the pair is a clean before/after on one layout.
	 *
	 * <p>Also the regression guard on stage order: with the fill running after the sources instead of
	 * before them, the spur and the corridor swap one packet back and forth forever and {@code S2} stays
	 * at 0 no matter how many ticks pass.
	 */
	@Test
	void fillStrandedOneHop_energizesTheSpurTheDownhillRuleAbandoned() {
		LineFixture line = spurRig();
		line.strandedOrder = STRANDED_ORDER;
		line.producerDistance.putAll(PRODUCER_DISTANCE);
		StubPort gen = new StubPort(GEN, 10_000, 10_000, true, false);
		EnergyLineDistributor d = line.distributor(Map.of());
		for (int i = 0; i < 20; i++) {
			d.chargeAndPropagateLine(List.of(new EnergyLineDistributor.LiveProducer(GEN, gen)), List.of(),
					0L, 10_000L, 32, txns.txn, 0);
		}

		assertEquals(12, line.buffers.get(S1).getAmount(), "the stranded segment now carries charge");
		assertEquals(12, line.buffers.get(S2).getAmount(), "and the fill front reached the end of the spur");
		assertEquals(12, line.buffers.get(C1).getAmount(), "the corridor is still served end to end");
		assertEquals(12, line.buffers.get(C2).getAmount(), "the fill pass did not rob the corridor");
	}

	// --- MOD-419: the one donor the MOD-413 guard structurally cannot protect ---

	/** Terminal-corridor rig: the machine hangs off C1, and a spur hangs off C1 as well. */
	private static final BlockPos T_MACHINE = new BlockPos(0, 0, 0);
	private static final BlockPos T_C1 = new BlockPos(1, 0, 0);
	private static final BlockPos T_C2 = new BlockPos(2, 0, 0);
	private static final BlockPos T_C3 = new BlockPos(3, 0, 0);
	private static final BlockPos T_SPUR = new BlockPos(1, 1, 0);
	private static final BlockPos T_GEN = new BlockPos(4, 0, 0);

	/**
	 * A waiting machine on the LAST cable of the corridor must keep being fed (MOD-419).
	 *
	 * <p>The rig is the player's world reduced to five blocks: a machine on {@code C1}, and a spur cable
	 * hanging off that same {@code C1} — the shape you get for free by stacking two machines, because the
	 * cable feeding the lower one is then also adjacent to the cable column serving the upper one.
	 *
	 * <p>{@code C1} is the terminal corridor cable, so its flow potential is 1 — the field minimum, since
	 * {@code floodFromSinks} seeds every cable touching a waiting sink at exactly 1. That makes it the one
	 * donor {@link EnergyLineDistributor#donorStillOwedDownhill} can never defend: the guard only looks
	 * for a strictly-lower CABLE neighbour with room, and nothing can be strictly lower than the minimum.
	 * The packet therefore ping-pongs forever — the sweep pushes it spur→C1 downhill, the stranded fill
	 * takes it straight back C1→spur — and the machine, served at the START of the tick, always finds C1
	 * empty.
	 *
	 * <p>Started from the exact frozen state read out of the save: spur brim-full, terminal cable at zero.
	 * On the code this test was written against the machine receives <b>nothing, ever</b>.
	 */
	@Test
	void fullTick_keepsFeedingAMachineOnTheTerminalCorridorCable() {
		LineFixture line = new LineFixture();
		line.cable(T_C1, 1, 12, 0);    // terminal cable, emptied by the previous tick's siphon
		line.cable(T_C2, 2, 12, 12);
		line.cable(T_C3, 3, 12, 12);
		line.cable(T_SPUR, 2, 12, 12); // the spur is already full — it wants nothing, it just churns
		line.strandedOrder = List.of(T_SPUR);
		line.producerDistance.putAll(Map.of(T_C3, 1, T_C2, 2, T_C1, 3, T_SPUR, 4));

		StubPort machine = new StubPort(T_MACHINE, 10_000, 0, false, true);
		List<EnergyLineDistributor.LiveConsumer> consumers =
				List.of(new EnergyLineDistributor.LiveConsumer(T_MACHINE, machine, 10_000));
		StubPort gen = new StubPort(T_GEN, 10_000, 10_000, true, false);
		EnergyLineDistributor d = line.distributor(Map.of(T_MACHINE, 1));

		long delivered = 0;
		for (int i = 0; i < 40; i++) {
			delivered += d.serveConsumersFromLine(consumers, 32, COPPER_LOSS, txns.txn, 0);
			d.chargeAndPropagateLine(List.of(new EnergyLineDistributor.LiveProducer(T_GEN, gen)), List.of(),
					0L, 10_000L, 32, txns.txn, 0);
		}

		assertTrue(delivered > 0,
				"MOD-419: the machine on the terminal corridor cable was starved for 40 ticks while the "
						+ "generator was full — the stranded fill siphoned its cable every tick");
		assertTrue(machine.getAmount() >= 12,
				"the machine should keep filling, not just catch one stray packet; got " + machine.getAmount());
	}

	/**
	 * One hop per pass, the same inertia the downhill sweep has. The corridor is saturated end to end
	 * (MOD-413: a donor with downhill room is delivery in transit and untouchable, so only a saturated
	 * corridor holds genuine surplus), and {@code C3} is manually refilled between passes the way
	 * {@code chargeLineFrom} closes a real tick. A pass that filled the whole spur at once would put
	 * 12 EU in {@code S2} on the first call.
	 */
	@Test
	void fillStrandedOneHop_advancesTheFillFrontOneCablePerPass() {
		LineFixture line = spurRig();
		line.buffers.get(C1).setAmountUntracked(12);
		line.buffers.get(C2).setAmountUntracked(12);
		line.buffers.get(C3).setAmountUntracked(12); // saturated corridor: C3's packet is true surplus

		EnergyLineDistributor d = line.distributor(Map.of());
		d.fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txns.txn);
		assertEquals(12, line.buffers.get(S1).getAmount(), "pass 1: the packet advanced exactly one hop");
		assertEquals(0, line.buffers.get(S2).getAmount(), "pass 1: it must NOT skip down the whole spur");
		assertEquals(0, line.buffers.get(C3).getAmount(), "pass 1: it came out of the saturated corridor cable");

		// The source closes the tick by refilling the corridor (chargeLineFrom); without it S1 would be
		// a donor the sweep still owes downhill (C3 has room now) and the front would rightly wait.
		line.buffers.get(C3).setAmountUntracked(12);
		d.fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txns.txn);
		assertEquals(12, line.buffers.get(S2).getAmount(), "pass 2: one more hop outward");
		assertEquals(12, line.buffers.get(S1).getAmount(), "pass 2: S1 handed its packet on and was refilled from C3");
		assertEquals(0, line.buffers.get(C3).getAmount(), "pass 2: still exactly one hop per unit per pass");
	}

	/**
	 * The MOD-413 defect, pinned: "brim-full" alone is not "surplus". A corridor cable can be full only
	 * because the sweep filled it THIS tick with a packet still on its way to a waiting machine — on the
	 * player rig, the spur toward a topped-off machine and the junction swapped one packet back and forth
	 * forever while the machine on the other branch starved at 24/800 EU behind a fully saturated line.
	 * A donor with a strictly-downhill cable neighbour that still has room must not be siphoned.
	 */
	@Test
	void fillStrandedOneHop_leavesAloneADonorTheSweepStillOwesDownhill() {
		LineFixture line = spurRig();
		line.buffers.get(C3).setAmountUntracked(12); // full — but C2 below it is empty: delivery in transit

		EnergyLineDistributor d = line.distributor(Map.of());
		d.fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txns.txn);

		assertEquals(12, line.buffers.get(C3).getAmount(),
				"a full donor with downhill room keeps its packet for the sweep");
		assertEquals(0, line.buffers.get(S1).getAmount(), "the spur waits until the corridor is saturated");
	}

	/**
	 * Surplus only. A donor below its capacity is still moving energy toward demand, and tapping it would
	 * make this pass compete with delivery instead of mopping up after it — the throughput regression that
	 * sank the alternative designs for this task.
	 */
	@Test
	void fillStrandedOneHop_leavesADonorThatIsNotBrimFullAlone() {
		LineFixture line = spurRig();
		line.buffers.get(C3).setAmountUntracked(11); // one EU short of full: still working for the machines

		EnergyLineDistributor d = line.distributor(Map.of());
		d.fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txns.txn);

		assertEquals(11, line.buffers.get(C3).getAmount(), "a partly drained donor must not be tapped");
		assertEquals(0, line.buffers.get(S1).getAmount(), "so the spur waits for genuine surplus");
	}

	/** The pass moves EU between buffers and neither mints nor destroys any. */
	@Test
	void fillStrandedOneHop_conservesEu() {
		LineFixture line = spurRig();
		line.buffers.get(C1).setAmountUntracked(12);
		line.buffers.get(C2).setAmountUntracked(12);
		line.buffers.get(C3).setAmountUntracked(12); // saturated, so the pass genuinely moves EU (MOD-413)
		long before = line.buffers.values().stream().mapToLong(b -> b.getAmount()).sum();

		EnergyLineDistributor d = line.distributor(Map.of());
		for (int i = 0; i < 5; i++) {
			d.fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txns.txn);
		}

		assertEquals(before, line.buffers.values().stream().mapToLong(b -> b.getAmount()).sum(),
				"the stranded fill must neither create nor destroy EU");
	}
}
