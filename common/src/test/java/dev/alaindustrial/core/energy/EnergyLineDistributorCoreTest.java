package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link EnergyLineDistributor}, the per-tick distribution kernel, on a toy position type
 * (MOD-715, batch 8). Since the kernel became generic over its position it needs no Minecraft class: the
 * cables here are {@link Cell}s of an integer grid whose six faces are numbered the way the network numbers
 * a block's ({@code Direction} order — down, up, north, south, west, east), plus a two-faced line of plain
 * integers for the cases where the shape of the graph is the point.
 *
 * <p>The cases are the L1.5 {@code EnergyLineDistributorTest} ones, carried over one for one with the same
 * numbers — that suite stays in {@code :neoforge:test} as the proof that the network's {@code BlockPos}
 * adapter drives the kernel identically — plus the rules it never pinned on its own: the per-host packet
 * cap of a multiblock (MOD-608), the record of who fed the line (MOD-665), the per-consumer loss (MOD-021),
 * a refused surplus going back to the cable it came from, and the positional no-self-churn rule. Here they
 * run under pitest.
 *
 * @implements MOD-715-LD01 — the line kernel on any position type: locality, one hop per tick, fork split,
 *     face gates, packet cap, stranded fill
 */
class EnergyLineDistributorCoreTest {

	/** A position of the toy grid. */
	record Cell(int x, int y, int z) {
	}

	private static UnaryOperator<Cell> step(int dx, int dy, int dz) {
		return c -> new Cell(c.x() + dx, c.y() + dy, c.z() + dz);
	}

	/** The six faces of a cell, in the order the network numbers a block's faces. */
	private static final List<UnaryOperator<Cell>> GRID = List.of(step(0, -1, 0), step(0, 1, 0), step(0, 0, -1),
			step(0, 0, 1), step(-1, 0, 0), step(1, 0, 0));
	private static final int WEST = 4;
	private static final int EAST = 5;

	private static final double COPPER_LOSS = 0.02;

	private static final class FakeTxn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	private final EnergyPort.Txn txn = new FakeTxn();

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

	private static StubPort generator() {
		return new StubPort(10_000, 10_000, true, false);
	}

	private static StubPort machine(long capacity, long amount) {
		return new StubPort(capacity, amount, false, true);
	}

	private static EnergyBuffer cableBuffer(long capacity, long amount) {
		EnergyBuffer b = new EnergyBuffer(capacity, capacity, capacity, () -> {
		});
		b.setAmountUntracked(amount);
		return b;
	}

	private static Cell at(int x, int y, int z) {
		return new Cell(x, y, z);
	}

	/** A synthetic line: cables with buffers and a flow potential, swept in ascending potential. */
	private static final class Line<P> {
		final List<UnaryOperator<P>> faces;
		final Set<P> cables = new LinkedHashSet<>();
		final Map<P, EnergyBuffer> buffers = new HashMap<>();
		final Map<P, Integer> flowPotential = new HashMap<>();
		final Map<P, Integer> machinePotential = new HashMap<>();
		List<P> strandedOrder = List.of();
		final Map<P, Integer> producerDistance = new HashMap<>();

		Line(List<UnaryOperator<P>> faces) {
			this.faces = faces;
		}

		void cable(P p, int potential, long capacity, long fill) {
			cables.add(p);
			buffers.put(p, cableBuffer(capacity, fill));
			flowPotential.put(p, potential);
		}

		void cable(P p, int potential, int machineDistance, long capacity, long fill) {
			cable(p, potential, capacity, fill);
			machinePotential.put(p, machineDistance);
		}

		long amount(P p) {
			return buffers.get(p).getAmount();
		}

		EnergyLineDistributor<P> distributor(Map<P, Integer> consumerDistance) {
			return distributor(consumerDistance, (p, f) -> true, (p, f) -> true);
		}

		EnergyLineDistributor<P> distributor(Map<P, Integer> consumerDistance, LineView.FaceGate<P> canDraw,
				LineView.FaceGate<P> canFeed) {
			List<P> order = new ArrayList<>(cables);
			order.sort((a, b) -> Integer.compare(flowPotential.get(a), flowPotential.get(b)));
			return new EnergyLineDistributor<>(new LineView<>(faces, cables::contains, buffers::get,
					pos -> consumerDistance.getOrDefault(pos, 0), flowPotential::get, machinePotential::get, order,
					canDraw, canFeed, strandedOrder, producerDistance::get));
		}
	}

	private static Line<Cell> grid() {
		return new Line<>(GRID);
	}

	private static <P> List<EnergyLineDistributor.LiveProducer<P>> sources(P pos, EnergyPort port) {
		return List.of(new EnergyLineDistributor.LiveProducer<>(pos, port));
	}

	private static <P> List<EnergyLineDistributor.LiveConsumer<P>> consumers(P pos, EnergyPort port, long room) {
		return List.of(new EnergyLineDistributor.LiveConsumer<>(pos, port, room));
	}

	private void sweep(EnergyLineDistributor<?> d, int rotation) {
		d.chargeAndPropagateLine(List.of(), List.of(), DischargePlan.backupOnly(0L, 0L), 32, txn, rotation);
	}

	// --- serveConsumersFromLine: locality (MOD-070) ---

	@Test
	void serveConsumersFromLine_pullsOnlyFromTouchedCables() {
		Line<Cell> line = grid();
		Cell cableA = at(0, 0, 0);
		Cell cableB = at(5, 0, 0);
		line.cable(cableA, 1, 100, 50);
		line.cable(cableB, 2, 100, 50);
		Cell consumerPos = at(0, 1, 0);

		long moved = line.distributor(Map.of(consumerPos, 1)).serveConsumersFromLine(
				consumers(consumerPos, machine(1000, 0), 1000), 32, COPPER_LOSS, txn, 0);

		assertEquals(32, moved, "consumer pulled up to packetCap (32) from cableA");
		assertEquals(18, line.amount(cableA), "cableA leftover = 50 - 32");
		assertEquals(50, line.amount(cableB), "cableB untouched (not adjacent to consumer)");
	}

	@Test
	void serveConsumersFromLine_returnsZeroWhenNoCableHasEnergy() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 0);
		line.cable(at(1, 0, 0), 2, 100, 0);
		Cell consumerPos = at(0, 1, 0);
		assertEquals(0, line.distributor(Map.of(consumerPos, 1)).serveConsumersFromLine(
				consumers(consumerPos, machine(1000, 0), 1000), 32, COPPER_LOSS, txn, 0));
	}

	@Test
	void serveConsumersFromLine_emptyClassReturnsZero() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 50);
		assertEquals(0, line.distributor(Map.of()).serveConsumersFromLine(List.of(), 32, COPPER_LOSS, txn, 0));
		assertEquals(50, line.amount(at(0, 0, 0)));
	}

	/** MOD-021: the resistive loss is charged once per consumer on delivery, at its own distance. */
	@Test
	void serveConsumersFromLine_chargesTheLossAtTheConsumersDistance() {
		Line<Cell> line = grid();
		Cell cable = at(0, 0, 0);
		line.cable(cable, 1, 100, 50);
		Cell consumerPos = at(0, 1, 0);
		StubPort consumer = machine(1000, 0);
		long loss = EnergyShare.cableLoss(32, COPPER_LOSS, 10);
		assertTrue(loss > 0, "precondition: ten blocks of copper lose something on a 32 EU packet");

		long moved = line.distributor(Map.of(consumerPos, 10)).serveConsumersFromLine(
				consumers(consumerPos, consumer, 1000), 32, COPPER_LOSS, txn, 0);

		assertEquals(32 - loss, moved, "delivered = the packet minus the loss");
		assertEquals(32 - loss, consumer.amount);
		assertEquals(18, line.amount(cable), "the whole packet left the cable; the loss is not returned");
	}

	/** A consumer that takes less than it was offered gives the rest back to the cable, not to the floor. */
	@Test
	void serveConsumersFromLine_returnsWhatTheConsumerRefusedToTheLine() {
		Line<Cell> line = grid();
		Cell cable = at(0, 0, 0);
		line.cable(cable, 1, 100, 50);
		Cell consumerPos = at(0, 1, 0);
		StubPort consumer = machine(10, 0); // takes 10, although it claims room for 1000

		long moved = line.distributor(Map.of(consumerPos, 1)).serveConsumersFromLine(
				consumers(consumerPos, consumer, 1000), 32, 0.0, txn, 0);

		assertEquals(10, moved);
		assertEquals(40, line.amount(cable), "the 22 EU the consumer refused went back into the cable");
	}

	/**
	 * The positional no-self-churn rule: a supply at the consumer's own position is skipped. A real grid
	 * never reaches it on the line path (a cable is never where its consumer is); a position that is its own
	 * neighbour does, and that is what this toy topology is for.
	 */
	@Test
	void serveConsumersFromLine_neverPullsFromTheConsumersOwnPosition() {
		Line<Integer> line = new Line<>(List.of(pos -> pos, pos -> pos + 1)); // face 0 is the position itself
		line.cable(0, 1, 100, 50); // the consumer stands on this cable
		line.cable(1, 1, 100, 5);
		StubPort consumer = machine(1000, 0);

		long moved = line.distributor(Map.of(0, 1)).serveConsumersFromLine(consumers(0, consumer, 1000), 32,
				0.0, txn, 0);

		assertEquals(5, moved, "only the neighbouring cable's 5 EU; the one under the consumer is skipped");
		assertEquals(50, line.amount(0));
		assertEquals(0, line.amount(1));
	}

	// --- chargeAndPropagateLine: producer/storage partitioning (MOD-070) ---

	@Test
	void chargeAndPropagateLine_generatorsAlwaysFillAdjacentLine() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 0);
		StubPort generator = generator();

		line.distributor(Map.of()).chargeAndPropagateLine(sources(at(-1, 0, 0), generator), List.of(),
				DischargePlan.backupOnly(0L, 10_000L), 32, txn, 0);

		assertEquals(32, line.amount(at(0, 0, 0)), "source-adjacent cable filled up to packetCap (32) this tick");
		assertEquals(10_000L - 32L, generator.amount, "generator drawn exactly 32 EU");
	}

	@Test
	void chargeAndPropagateLine_storageMustNotDischargeWhenGeneratorsCoverDemand() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 0);
		StubPort generator = generator();
		StubPort storage = new StubPort(10_000, 10_000, true, false);

		line.distributor(Map.of()).chargeAndPropagateLine(sources(at(-1, 0, 0), generator),
				sources(at(0, 1, 0), storage), DischargePlan.backupOnly(10L, 20L), 32, txn, 0);

		assertEquals(10_000L, storage.amount, "storage must NOT discharge when generators cover the machine demand");
		assertTrue(generator.amount < 10_000L, "generator did charge the line (it always can)");
	}

	/** Backup open: a store covers the deficit, and the call reports what it drew out of storage. */
	@Test
	void chargeAndPropagateLine_storageCoversTheDeficitAndReportsTheDraw() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 0);
		StubPort storage = new StubPort(10_000, 10_000, true, false);

		long drawn = line.distributor(Map.of()).chargeAndPropagateLine(List.of(), sources(at(0, 1, 0), storage),
				DischargePlan.backupOnly(20L, 0L), 32, txn, 0);

		assertEquals(20, drawn, "the store released exactly the 20 EU the generators fell short of");
		assertEquals(10_000L - 20L, storage.amount);
		assertEquals(20, line.amount(at(0, 0, 0)));
	}

	/** The cascade and the feed charge the line per donor, each at most its own allowance (MOD-314, MOD-353). */
	@Test
	void chargeAndPropagateLine_cascadeAndFeedChargePerDonorAllowance() {
		for (boolean cascade : new boolean[] {true, false}) {
			Line<Cell> line = grid();
			line.cable(at(0, 0, 0), 1, 100, 0);
			Cell a = at(-1, 0, 0);
			Cell b = at(1, 0, 0);
			StubPort donorA = new StubPort(10_000, 10_000, true, false);
			StubPort donorB = new StubPort(10_000, 10_000, true, false);
			Map<Cell, Long> allowances = Map.of(a, 7L);
			DischargePlan<Cell> plan = cascade ? new DischargePlan<>(0, allowances, Map.of())
					: new DischargePlan<>(0, Map.of(), allowances);
			List<EnergyLineDistributor.LiveProducer<Cell>> stores = List.of(
					new EnergyLineDistributor.LiveProducer<>(a, donorA),
					new EnergyLineDistributor.LiveProducer<>(b, donorB));

			long drawn = line.distributor(Map.of()).chargeAndPropagateLine(List.of(), stores, plan, 32, txn, 0);

			assertEquals(7, drawn, "only the donor with an allowance gives, and only its allowance");
			assertEquals(10_000L - 7L, donorA.amount);
			assertEquals(10_000L, donorB.amount, "a store without an allowance stays shut");
		}
	}

	@Test
	void chargeAndPropagateLine_emptyOrderIsNoOp() {
		Line<Cell> line = grid();
		StubPort generator = generator();
		line.distributor(Map.of()).chargeAndPropagateLine(sources(at(-1, 0, 0), generator), List.of(),
				DischargePlan.backupOnly(0L, 10_000L), 32, txn, 0);
		assertEquals(10_000L, generator.amount, "generator must not draw when there are no cables to charge");
	}

	// --- propagateLineOneHop: flow direction + the one-hop-per-tick invariant (MOD-252) ---

	@Test
	void propagateLineOneHop_movesExactlyOneHopDownGradient() {
		Line<Cell> line = grid();
		Cell p1 = at(1, 0, 0);
		Cell p2 = at(2, 0, 0);
		Cell p3 = at(3, 0, 0);
		Cell p4 = at(4, 0, 0);
		line.cable(p1, 1, 12, 0);
		line.cable(p2, 2, 12, 0);
		line.cable(p3, 3, 12, 0);
		line.cable(p4, 4, 12, 12);
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());

		sweep(d, 0);
		assertEquals(0, line.amount(p4), "pass 1: the far cable handed its charge downhill");
		assertEquals(12, line.amount(p3), "pass 1: charge advanced exactly one hop, to potential 3");
		assertEquals(0, line.amount(p2), "pass 1: charge must NOT skip a hop");
		assertEquals(0, line.amount(p1), "pass 1: charge must NOT reach the sink-side cable");

		sweep(d, 0);
		assertEquals(12, line.amount(p2), "pass 2: one more hop, to potential 2");
		assertEquals(0, line.amount(p3), "pass 2: the charge left potential 3");
		assertEquals(0, line.amount(p1), "pass 2: still exactly one hop per pass");
	}

	@Test
	void propagateLineOneHop_doesNotMoveBetweenEqualPotential() {
		Line<Cell> line = grid();
		Cell left = at(1, 0, 0);
		Cell right = at(2, 0, 0);
		line.cable(left, 2, 12, 12);
		line.cable(right, 2, 12, 0);

		sweep(line.distributor(Map.of()), 0);

		assertEquals(12, line.amount(left), "equal-potential neighbours must not exchange");
		assertEquals(0, line.amount(right), "equal-potential neighbours must not exchange");
	}

	/**
	 * The same rule on a plateau of three, in both sweep orders. Two equal cables can hide a relaxed
	 * comparison — the charge crosses and, one visit later, crosses back — so the pair above alone does not
	 * pin the strictness; three cables with the charge at one end do: a relaxed rule leaves it spread.
	 */
	@Test
	void propagateLineOneHop_aPlateauHoldsItsChargeInPlace() {
		for (boolean chargedFirst : new boolean[] {true, false}) {
			Line<Cell> line = grid();
			Cell charged = at(1, 0, 0);
			Cell middle = at(2, 0, 0);
			Cell far = at(3, 0, 0);
			if (chargedFirst) {
				line.cable(charged, 2, 12, 12);
			}
			line.cable(middle, 2, 12, 0);
			line.cable(far, 2, 12, 0);
			if (!chargedFirst) {
				line.cable(charged, 2, 12, 12);
			}

			sweep(line.distributor(Map.of()), 0);

			assertEquals(12, line.amount(charged), "a plateau holds its charge, sweep order " + chargedFirst);
			assertEquals(0, line.amount(middle));
			assertEquals(0, line.amount(far));
		}
	}

	/** A claimant's share is capped by the tier packet: a 100 EU donor hands one packet down one hop. */
	@Test
	void propagateLineOneHop_movesAtMostOnePacketPerHop() {
		Line<Cell> line = grid();
		Cell low = at(1, 0, 0);
		Cell high = at(2, 0, 0);
		line.cable(low, 1, 100, 0);
		line.cable(high, 2, 100, 100);

		sweep(line.distributor(Map.of()), 0);

		assertEquals(32, line.amount(low));
		assertEquals(68, line.amount(high));
	}

	@Test
	void propagateLineOneHop_splitsAForkedBufferBetweenBothClaimants() {
		Line<Cell> line = grid();
		Cell westFar = at(1, 0, 0);
		Cell westNear = at(2, 0, 0);
		Cell fork = at(3, 0, 0);
		Cell eastNear = at(4, 0, 0);
		Cell eastFar = at(5, 0, 0);
		line.cable(westFar, 1, 12, 0);
		line.cable(westNear, 2, 12, 0);
		line.cable(fork, 3, 12, 12);
		line.cable(eastNear, 2, 12, 0);
		line.cable(eastFar, 1, 12, 0);

		sweep(line.distributor(Map.of()), 0);

		assertEquals(6, line.amount(westNear), "the west branch got its half of the fork");
		assertEquals(6, line.amount(eastNear), "the east branch got its half of the fork");
		assertEquals(0, line.amount(fork), "the whole fork buffer moved — nothing stranded");
		assertEquals(0, line.amount(westFar), "still exactly one hop per pass");
		assertEquals(0, line.amount(eastFar), "still exactly one hop per pass");
	}

	@Test
	void propagateLineOneHop_prefersTheBranchLeadingToAMachine() {
		Line<Cell> line = grid();
		Cell westNear = at(2, 0, 0);
		Cell fork = at(3, 0, 0);
		Cell eastNear = at(4, 0, 0);
		line.cable(westNear, 2, 2, 12, 0);
		line.cable(fork, 3, 3, 12, 12);
		line.cable(eastNear, 2, 4, 12, 0);

		sweep(line.distributor(Map.of()), 0);

		assertEquals(8, line.amount(westNear), "the machine-ward branch takes the larger share");
		assertEquals(4, line.amount(eastNear), "the storage-ward branch is still served");
		assertEquals(0, line.amount(fork), "the whole fork buffer moved — nothing stranded");
	}

	/** The spare EU of a split goes machine-ward first, whatever the rotation (MOD-254). */
	@Test
	void propagateLineOneHop_givesTheSpareEuToTheMachineWardBranchFirst() {
		for (int rotation = 0; rotation < 2; rotation++) {
			Line<Cell> line = grid();
			Cell westNear = at(2, 0, 0);
			Cell fork = at(3, 0, 0);
			Cell eastNear = at(4, 0, 0);
			line.cable(westNear, 2, 4, 12, 0);
			line.cable(fork, 3, 3, 12, 1); // one EU: all remainder
			line.cable(eastNear, 2, 2, 12, 0); // east leads to the machine this time

			sweep(line.distributor(Map.of()), rotation);

			assertEquals(1, line.amount(eastNear), "rotation " + rotation + ": the spare EU went machine-ward");
			assertEquals(0, line.amount(westNear));
		}
	}

	@Test
	void propagateLineOneHop_rotatesTheIndivisibleRemainderBetweenBranches() {
		Line<Cell> line = grid();
		Cell westNear = at(2, 0, 0);
		Cell fork = at(3, 0, 0);
		Cell eastNear = at(4, 0, 0);
		line.cable(westNear, 2, 12, 0);
		line.cable(fork, 3, 12, 1);
		line.cable(eastNear, 2, 12, 0);
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());

		sweep(d, 0);
		assertEquals(0, line.amount(fork), "tick 1: the single EU moved off the seam");
		line.buffers.get(fork).setAmountUntracked(1);
		sweep(d, 1);

		assertEquals(1, line.amount(westNear), "the west branch got the spare EU on exactly one of the two offsets");
		assertEquals(1, line.amount(eastNear), "the east branch got it on the other");
		assertEquals(0, line.amount(fork), "tick 2: nothing stranded at the seam");
	}

	@Test
	void propagateLineOneHop_dealsTheRemainderOneEuAtATime() {
		Line<Cell> line = grid();
		Cell junction = at(0, 0, 0);
		line.cable(junction, 3, 12, 2);
		List<Cell> branches = List.of(at(1, 0, 0), at(-1, 0, 0), at(0, 0, 1));
		for (Cell b : branches) {
			line.cable(b, 2, 12, 0);
		}

		sweep(line.distributor(Map.of()), 0);

		int fed = 0;
		for (Cell b : branches) {
			long got = line.amount(b);
			assertTrue(got <= 1, "no branch may take a second EU while another has none, got " + got);
			fed += got > 0 ? 1 : 0;
		}
		assertEquals(2, fed, "the two spare EU landed on two different branches");
		assertEquals(0, line.amount(junction), "the whole junction buffer moved");
	}

	/** A claimant with no room is no claimant: the donor's whole buffer goes to the one that has room. */
	@Test
	void propagateLineOneHop_skipsAFullClaimant() {
		Line<Cell> line = grid();
		Cell westNear = at(2, 0, 0);
		Cell fork = at(3, 0, 0);
		Cell eastNear = at(4, 0, 0);
		line.cable(westNear, 2, 12, 12);
		line.cable(fork, 3, 12, 12);
		line.cable(eastNear, 2, 12, 0);

		sweep(line.distributor(Map.of()), 0);

		assertEquals(12, line.amount(eastNear), "the one claimant with room takes the whole buffer");
		assertEquals(0, line.amount(fork));
	}

	@Test
	void chargeLineFrom_rotationLetsEverySourceFeedASaturatedCable() {
		Line<Cell> line = grid();
		Cell cable = at(0, 0, 0);
		line.cable(cable, 1, 12, 0);
		StubPort a = generator();
		StubPort b = generator();
		List<EnergyLineDistributor.LiveProducer<Cell>> sources = List.of(
				new EnergyLineDistributor.LiveProducer<>(at(-1, 0, 0), a),
				new EnergyLineDistributor.LiveProducer<>(at(1, 0, 0), b));
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());

		d.chargeAndPropagateLine(sources, List.of(), DischargePlan.backupOnly(0L, 20_000L), 32, txn, 0);
		long drawnAFirst = 10_000L - a.amount;
		long drawnBFirst = 10_000L - b.amount;
		line.buffers.get(cable).setAmountUntracked(0);
		d.chargeAndPropagateLine(sources, List.of(), DischargePlan.backupOnly(0L, 20_000L), 32, txn, 1);
		long drawnASecond = 10_000L - a.amount - drawnAFirst;
		long drawnBSecond = 10_000L - b.amount - drawnBFirst;

		assertEquals(12, drawnAFirst + drawnBFirst, "pass 1 filled the segment's one packet of room");
		assertEquals(12, drawnASecond + drawnBSecond, "pass 2 filled it again");
		assertTrue(drawnAFirst + drawnASecond > 0, "source A fed the line on one of the two offsets");
		assertTrue(drawnBFirst + drawnBSecond > 0, "source B fed the line on the other offset");
	}

	/** The face sweep of one source starts at the rotation too: a source between two cables alternates. */
	@Test
	void chargeLineFrom_rotationAlsoTurnsTheFaceSweep() {
		Line<Integer> line = new Line<>(twoFaced());
		line.cable(-1, 1, 100, 0);
		line.cable(1, 1, 100, 0);
		StubPort source = generator();
		EnergyLineDistributor<Integer> d = line.distributor(Map.of());

		d.chargeAndPropagateLine(sources(0, source), List.of(), DischargePlan.backupOnly(0L, 10_000L), 32, txn, 0);
		assertEquals(32, line.amount(-1), "offset 0 starts at face 0, the left cable");
		assertEquals(0, line.amount(1));
		d.chargeAndPropagateLine(sources(0, source), List.of(), DischargePlan.backupOnly(0L, 10_000L), 32, txn, 1);
		assertEquals(32, line.amount(1), "offset 1 starts at face 1, the right cable");
		assertEquals(32, line.amount(-1));
	}

	// --- MOD-255: face gates — adjacency is not permission ---

	@Test
	void serveConsumersFromLine_ignoresCablesOnNonDrawableFaces() {
		Line<Cell> line = grid();
		Cell west = at(0, 0, 0);
		Cell east = at(2, 0, 0);
		line.cable(west, 1, 100, 10);
		line.cable(east, 1, 100, 50);
		Cell consumerPos = at(1, 0, 0);

		long moved = line.distributor(Map.of(consumerPos, 1), (pos, face) -> face == WEST, (pos, face) -> true)
				.serveConsumersFromLine(consumers(consumerPos, machine(1000, 0), 1000), 32, COPPER_LOSS, txn, 0);

		assertEquals(10, moved, "consumer got only what the one drawable cable held, not a full packet");
		assertEquals(0, line.amount(west), "the drawable cable was drained");
		assertEquals(50, line.amount(east), "the cable on a non-accepting face must be untouched");
	}

	@Test
	void chargeLineFrom_ignoresCablesOnNonFeedableFaces() {
		Line<Cell> line = grid();
		Cell west = at(0, 0, 0);
		Cell east = at(2, 0, 0);
		line.cable(west, 1, 100, 0);
		line.cable(east, 1, 100, 0);
		StubPort source = generator();

		line.distributor(Map.of(), (pos, face) -> true, (pos, face) -> face == EAST).chargeAndPropagateLine(
				sources(at(1, 0, 0), source), List.of(), DischargePlan.backupOnly(0L, 10_000L), 32, txn, 0);

		assertEquals(32, line.amount(east), "the emitting face filled its cable up to packetCap");
		assertEquals(0, line.amount(west), "the cable on a non-emitting face must stay empty");
		assertEquals(10_000L - 32L, source.amount, "exactly one packet left the source, not two");
	}

	@Test
	void fullPass_conservesTotalEuAtZeroLoss() {
		Line<Cell> line = grid();
		Cell c1 = at(1, 0, 0);
		Cell c2 = at(2, 0, 0);
		Cell c3 = at(3, 0, 0);
		line.cable(c1, 1, 12, 5);
		line.cable(c2, 2, 12, 12);
		line.cable(c3, 3, 12, 7);
		Cell consumerPos = at(0, 0, 0);
		Cell sourcePos = at(4, 0, 0);
		StubPort consumer = machine(1000, 100);
		StubPort source = generator();
		long before = consumer.amount + source.amount + line.amount(c1) + line.amount(c2) + line.amount(c3);

		EnergyLineDistributor<Cell> d = line.distributor(Map.of(consumerPos, 3));
		d.serveConsumersFromLine(consumers(consumerPos, consumer, 900), 32, 0.0, txn, 0);
		d.chargeAndPropagateLine(sources(sourcePos, source), List.of(), DischargePlan.backupOnly(900L, 10_000L),
				32, txn, 0);

		long after = consumer.amount + source.amount + line.amount(c1) + line.amount(c2) + line.amount(c3);
		assertEquals(before, after, "a full kernel pass at zero loss must neither create nor destroy EU");
		assertTrue(consumer.amount > 100, "fixture sanity: the consumer actually received something");
	}

	// --- the per-source and per-host packet cap ---

	@Test
	void chargeLineFrom_doesNotPullFromCoLocatedProducer() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 1_000, 0);
		Cell same = at(-1, 0, 0);
		StubPort prodA = generator();
		StubPort prodB = generator();
		List<EnergyLineDistributor.LiveProducer<Cell>> sources = List.of(
				new EnergyLineDistributor.LiveProducer<>(same, prodA),
				new EnergyLineDistributor.LiveProducer<>(same, prodB));

		line.distributor(Map.of()).chargeAndPropagateLine(sources, List.of(), DischargePlan.backupOnly(0L, 10_000L),
				32, txn, 0);

		long drawnA = 10_000L - prodA.amount;
		long drawnB = 10_000L - prodB.amount;
		assertTrue(drawnA <= 32L, "producer A draw bounded by packetCap; got " + drawnA);
		assertTrue(drawnB <= 32L, "producer B draw bounded by packetCap; got " + drawnB);
		assertEquals(drawnA + drawnB, line.amount(at(0, 0, 0)), "all drawn EU lands in the cable buffer");
	}

	@Test
	void chargeAndPropagateLine_packetCapBoundsTotalDrawnPerSource() {
		Line<Cell> line = grid();
		Cell cable = at(0, 0, 0);
		line.cable(cable, 1, 1_000, 0);
		StubPort gen1 = generator();
		StubPort gen2 = generator();
		StubPort gen3 = generator();
		List<EnergyLineDistributor.LiveProducer<Cell>> generators = List.of(
				new EnergyLineDistributor.LiveProducer<>(at(-1, 0, 0), gen1),
				new EnergyLineDistributor.LiveProducer<>(at(0, 1, 0), gen2),
				new EnergyLineDistributor.LiveProducer<>(at(1, 0, 0), gen3));

		line.distributor(Map.of()).chargeAndPropagateLine(generators, List.of(),
				DischargePlan.backupOnly(0L, 10_000L), 32, txn, 0);

		long drawn1 = 10_000L - gen1.amount;
		long drawn2 = 10_000L - gen2.amount;
		long drawn3 = 10_000L - gen3.amount;
		assertTrue(drawn1 <= 32L && drawn2 <= 32L && drawn3 <= 32L, "each source bounded by packetCap");
		assertEquals(96L, drawn1 + drawn2 + drawn3, "per-source cap: 3 sources each give 32 → total 96");
		assertEquals(96L, line.amount(cable), "all drawn EU lands in the cable buffer");
	}

	/**
	 * MOD-608: the cells of one multiblock share one packet. Two endpoints lending the same host's port
	 * touch two cables; together they inject one packet, not two — and the record of who fed lists both.
	 */
	@Test
	void chargeLineFrom_capsTheCellsOfOneHostAtOnePacket() {
		Line<Cell> line = grid();
		Cell cableA = at(0, 0, 0);
		Cell cableB = at(0, 0, 5);
		line.cable(cableA, 1, 1_000, 0);
		line.cable(cableB, 1, 1_000, 0);
		Cell host = at(9, 9, 9);
		StubPort core = generator();
		List<EnergyLineDistributor.LiveProducer<Cell>> cells = List.of(
				new EnergyLineDistributor.LiveProducer<>(at(-1, 0, 0), core, host),
				new EnergyLineDistributor.LiveProducer<>(at(-1, 0, 5), core, host));
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());

		d.chargeAndPropagateLine(cells, List.of(), DischargePlan.backupOnly(0L, 10_000L), 32, txn, 0);

		assertEquals(32, line.amount(cableA) + line.amount(cableB), "one host, one packet");
		assertEquals(10_000L - 32L, core.amount);
		assertEquals(Set.of(at(-1, 0, 0)), d.fed(), "only the cell that actually fed is recorded (MOD-665)");
	}

	// --- MOD-318: segments the downhill rule can never reach ---

	private static final Cell C1 = at(1, 0, 0);
	private static final Cell C2 = at(2, 0, 0);
	private static final Cell C3 = at(3, 0, 0);
	private static final Cell S1 = at(3, 1, 0);
	private static final Cell S2 = at(3, 2, 0);
	private static final Cell GEN = at(4, 0, 0);
	private static final Map<Cell, Integer> PRODUCER_DISTANCE = Map.of(C3, 1, C2, 2, C1, 3, S1, 2, S2, 3);
	private static final List<Cell> STRANDED_ORDER = List.of(S2, S1);

	private static Line<Cell> spurRig() {
		Line<Cell> line = grid();
		line.cable(C1, 1, 12, 0);
		line.cable(C2, 2, 12, 0);
		line.cable(C3, 3, 12, 0);
		line.cable(S1, 4, 12, 0);
		line.cable(S2, 5, 12, 0);
		return line;
	}

	private void runGenerator(Line<Cell> line, int ticks) {
		StubPort gen = generator();
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());
		for (int i = 0; i < ticks; i++) {
			d.chargeAndPropagateLine(sources(GEN, gen), List.of(), DischargePlan.backupOnly(0L, 10_000L), 32, txn, 0);
		}
	}

	@Test
	void chargeAndPropagateLine_strandsASpurWithNothingWaitingOnIt() {
		Line<Cell> line = spurRig();
		runGenerator(line, 20);

		assertEquals(12, line.amount(C1), "negative control: the corridor saturates end to end");
		assertEquals(12, line.amount(C3), "negative control: the source-side cable is full");
		assertEquals(0, line.amount(S1), "MOD-318: the spur has no downhill path and stays dead");
		assertEquals(0, line.amount(S2), "MOD-318: and so does the rest of it");
	}

	@Test
	void fillStrandedOneHop_energizesTheSpurTheDownhillRuleAbandoned() {
		Line<Cell> line = spurRig();
		line.strandedOrder = STRANDED_ORDER;
		line.producerDistance.putAll(PRODUCER_DISTANCE);
		runGenerator(line, 20);

		assertEquals(12, line.amount(S1), "the stranded segment now carries charge");
		assertEquals(12, line.amount(S2), "and the fill front reached the end of the spur");
		assertEquals(12, line.amount(C1), "the corridor is still served end to end");
		assertEquals(12, line.amount(C2), "the fill pass did not rob the corridor");
	}

	@Test
	void fullTick_keepsFeedingAMachineOnTheTerminalCorridorCable() {
		Line<Cell> line = grid();
		Cell machinePos = at(0, 0, 0);
		Cell t1 = at(1, 0, 0);
		Cell t2 = at(2, 0, 0);
		Cell t3 = at(3, 0, 0);
		Cell spur = at(1, 1, 0);
		line.cable(t1, 1, 12, 0);
		line.cable(t2, 2, 12, 12);
		line.cable(t3, 3, 12, 12);
		line.cable(spur, 2, 12, 12);
		line.strandedOrder = List.of(spur);
		line.producerDistance.putAll(Map.of(t3, 1, t2, 2, t1, 3, spur, 4));
		StubPort machine = machine(10_000, 0);
		StubPort gen = generator();
		EnergyLineDistributor<Cell> d = line.distributor(Map.of(machinePos, 1));

		long delivered = 0;
		for (int i = 0; i < 40; i++) {
			delivered += d.serveConsumersFromLine(consumers(machinePos, machine, 10_000), 32, COPPER_LOSS, txn, 0);
			d.chargeAndPropagateLine(sources(at(4, 0, 0), gen), List.of(), DischargePlan.backupOnly(0L, 10_000L),
					32, txn, 0);
		}

		assertTrue(delivered > 0, "MOD-419: the machine on the terminal corridor cable must not be starved");
		assertTrue(machine.getAmount() >= 12, "the machine keeps filling; got " + machine.getAmount());
	}

	@Test
	void fillStrandedOneHop_advancesTheFillFrontOneCablePerPass() {
		Line<Cell> line = spurRig();
		line.buffers.get(C1).setAmountUntracked(12);
		line.buffers.get(C2).setAmountUntracked(12);
		line.buffers.get(C3).setAmountUntracked(12);
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());

		d.fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txn);
		assertEquals(12, line.amount(S1), "pass 1: the packet advanced exactly one hop");
		assertEquals(0, line.amount(S2), "pass 1: it must NOT skip down the whole spur");
		assertEquals(0, line.amount(C3), "pass 1: it came out of the saturated corridor cable");

		line.buffers.get(C3).setAmountUntracked(12);
		d.fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txn);
		assertEquals(12, line.amount(S2), "pass 2: one more hop outward");
		assertEquals(12, line.amount(S1), "pass 2: S1 handed its packet on and was refilled from C3");
		assertEquals(0, line.amount(C3), "pass 2: still exactly one hop per unit per pass");
	}

	/** The stranded fill moves at most one packet per hop, like the sweep. */
	@Test
	void fillStrandedOneHop_movesAtMostOnePacket() {
		Line<Cell> line = grid();
		Cell donor = at(0, 0, 0);
		Cell spur = at(1, 0, 0);
		// The donor sits above the sink-adjacent minimum with no lower neighbour: its packet is true surplus.
		line.cable(donor, 3, 100, 100);
		line.cable(spur, 4, 100, 0);

		line.distributor(Map.of()).fillStrandedOneHop(List.of(spur), Map.of(donor, 1, spur, 2)::get, 32, txn);

		assertEquals(32, line.amount(spur));
		assertEquals(68, line.amount(donor));
	}

	@Test
	void fillStrandedOneHop_leavesAloneADonorTheSweepStillOwesDownhill() {
		Line<Cell> line = spurRig();
		line.buffers.get(C3).setAmountUntracked(12);

		line.distributor(Map.of()).fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txn);

		assertEquals(12, line.amount(C3), "a full donor with downhill room keeps its packet for the sweep");
		assertEquals(0, line.amount(S1), "the spur waits until the corridor is saturated");
	}

	@Test
	void fillStrandedOneHop_leavesADonorThatIsNotBrimFullAlone() {
		Line<Cell> line = spurRig();
		line.buffers.get(C3).setAmountUntracked(11);

		line.distributor(Map.of()).fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txn);

		assertEquals(11, line.amount(C3), "a partly drained donor must not be tapped");
		assertEquals(0, line.amount(S1), "so the spur waits for genuine surplus");
	}

	@Test
	void fillStrandedOneHop_conservesEu() {
		Line<Cell> line = spurRig();
		line.buffers.get(C1).setAmountUntracked(12);
		line.buffers.get(C2).setAmountUntracked(12);
		line.buffers.get(C3).setAmountUntracked(12);
		long before = line.buffers.values().stream().mapToLong(EnergyBuffer::getAmount).sum();

		EnergyLineDistributor<Cell> d = line.distributor(Map.of());
		for (int i = 0; i < 5; i++) {
			d.fillStrandedOneHop(STRANDED_ORDER, PRODUCER_DISTANCE::get, 32, txn);
		}

		assertEquals(before, line.buffers.values().stream().mapToLong(EnergyBuffer::getAmount).sum(),
				"the stranded fill must neither create nor destroy EU");
	}

	// --- the kernel on a graph that is not a block grid ---

	/** Plain integers on a line: face 0 is {@code pos - 1}, face 1 is {@code pos + 1}. */
	private static List<UnaryOperator<Integer>> twoFaced() {
		return List.of(pos -> pos - 1, pos -> pos + 1);
	}

	/**
	 * The same kernel on two faces per position: a source at 0 feeding a run of four cables toward a machine
	 * at 5 fills the run one hop per tick, and the machine is served from the last cable.
	 */
	@Test
	void aLineOfTwoFacedPositionsRunsTheSameKernel() {
		Line<Integer> line = new Line<>(twoFaced());
		for (int pos = 1; pos <= 4; pos++) {
			line.cable(pos, 5 - pos, 12, 0); // potential 4 at the source end, 1 beside the machine
		}
		StubPort source = generator();
		StubPort machine = machine(1_000, 0);
		EnergyLineDistributor<Integer> d = line.distributor(Map.of(5, 4));

		long delivered = 0;
		for (int tick = 0; tick < 4; tick++) {
			delivered += d.serveConsumersFromLine(consumers(5, machine, 1_000), 32, 0.0, txn, tick);
			d.chargeAndPropagateLine(sources(0, source), List.of(), DischargePlan.backupOnly(1_000L, 10_000L), 32,
					txn, tick);
			if (tick < 3) {
				assertEquals(0, delivered, "tick " + tick + ": the front has not reached the machine yet");
			}
		}

		assertEquals(12, line.amount(1), "the source-side cable is refilled every tick");
		assertEquals(12, line.amount(4), "the front reached the cable beside the machine on the fourth tick");
		assertEquals(0, delivered, "and the machine is served from it at the start of the next tick");
		assertEquals(12, d.serveConsumersFromLine(consumers(5, machine, 1_000), 32, 0.0, txn, 4));
	}
}
