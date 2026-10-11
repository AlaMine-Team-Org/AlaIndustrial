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
 * L1 tests for {@link EnergyLineDistributor}, the per-tick distribution kernel, on a toy position type (MOD-715,
 * batch 8), as it carries out the line plan of {@link LinePlanner} (MOD-789, ADR-049). The cables are {@link Cell}s
 * of an integer grid whose six faces are numbered the way the network numbers a block's ({@code Direction} order —
 * down, up, north, south, west, east), plus a two-faced line of plain integers for the cases where the shape of the
 * graph is the point. Run under pitest.
 *
 * @implements MOD-715-LD01 — the line kernel on any position type: locality, one hop per tick, sharing, face gates,
 *     packet cap, the outward fill
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

	private final EnergyPort.Txn txn = participant -> {
	};

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

	private static Cell at(int x, int y, int z) {
		return new Cell(x, y, z);
	}

	/** A synthetic line: cables with buffers, their distance from the generators, one planner across its ticks. */
	private static final class Line<P> {
		final List<UnaryOperator<P>> faces;
		final Set<P> cables = new LinkedHashSet<>();
		final Map<P, EnergyBuffer> buffers = new HashMap<>();
		final Map<P, Integer> producerDistance = new HashMap<>();
		final LinePlanner<P> planner = new LinePlanner<>();
		private List<P> list = List.of();

		Line(List<UnaryOperator<P>> faces) {
			this.faces = faces;
		}

		void cable(P p, int producerDistance, long capacity, long fill) {
			cables.add(p);
			EnergyBuffer b = new EnergyBuffer(capacity, capacity, capacity, () -> {
			});
			b.setAmountUntracked(fill);
			buffers.put(p, b);
			this.producerDistance.put(p, producerDistance);
		}

		long amount(P p) {
			return buffers.get(p).getAmount();
		}

		EnergyLineDistributor<P> distributor(Map<P, Integer> consumerDistance) {
			return distributor(consumerDistance, (p, f) -> true, (p, f) -> true);
		}

		EnergyLineDistributor<P> distributor(Map<P, Integer> consumerDistance, LineView.FaceGate<P> canDraw,
				LineView.FaceGate<P> canFeed) {
			if (list.size() != cables.size()) {
				list = new ArrayList<>(cables);
			}
			return new EnergyLineDistributor<>(new LineView<>(faces, cables::contains, buffers::get,
					pos -> consumerDistance.getOrDefault(pos, 0), producerDistance::get, list, canDraw, canFeed,
					planner));
		}
	}

	private static Line<Cell> grid() {
		return new Line<>(GRID);
	}

	private static List<UnaryOperator<Integer>> twoFaced() {
		return List.of(pos -> pos - 1, pos -> pos + 1);
	}

	private static <P> List<EnergyLineDistributor.LiveProducer<P>> sources(P pos, EnergyPort port) {
		return List.of(new EnergyLineDistributor.LiveProducer<>(pos, port));
	}

	/** A machine that tells its need (its per-tick draw). */
	private static <P> List<EnergyLineDistributor.LiveConsumer<P>> machines(P pos, EnergyPort port, long room,
			long need) {
		return List.of(new EnergyLineDistributor.LiveConsumer<>(pos, port, room, need));
	}

	/** A storage sink: its need is its room. */
	private static <P> List<EnergyLineDistributor.LiveConsumer<P>> stores(P pos, EnergyPort port, long room) {
		return List.of(new EnergyLineDistributor.LiveConsumer<>(pos, port, room));
	}

	private <P> EnergyLineDistributor.Moved tick(EnergyLineDistributor<P> d,
			List<EnergyLineDistributor.LiveConsumer<P>> machines, List<EnergyLineDistributor.LiveConsumer<P>> stores,
			List<EnergyLineDistributor.LiveProducer<P>> generators, double loss, int rotation) {
		return d.distribute(machines, stores, List.of(), 0L, generators, 32, loss, txn, rotation);
	}

	// --- locality (MOD-755) and the consumer's draw ---

	@Test
	void aConsumerDrawsOnlyFromTheCablesItTouches() {
		Line<Cell> line = grid();
		Cell cableA = at(0, 0, 0);
		Cell cableB = at(5, 0, 0);
		line.cable(cableA, 1, 100, 50);
		line.cable(cableB, 2, 100, 50);
		Cell consumerPos = at(0, 1, 0);

		long moved = tick(line.distributor(Map.of(consumerPos, 1)), machines(consumerPos, machine(1000, 0), 1000, 32),
				List.of(), List.of(), COPPER_LOSS, 0).delivered();

		assertEquals(32, moved, "consumer pulled up to packetCap (32) from cableA");
		assertEquals(18, line.amount(cableA), "cableA leftover = 50 - 32");
		assertEquals(50, line.amount(cableB), "cableB untouched (not adjacent to consumer)");
	}

	@Test
	void nothingMovesWhenNoCableHasEnergyAndNoGeneratorRuns() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 0);
		line.cable(at(1, 0, 0), 2, 100, 0);
		Cell consumerPos = at(0, 1, 0);
		assertEquals(0, tick(line.distributor(Map.of(consumerPos, 1)),
				machines(consumerPos, machine(1000, 0), 1000, 32), List.of(), List.of(), COPPER_LOSS, 0).delivered());
	}

	@Test
	void noConsumerDrawsNothing() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 50);
		assertEquals(0, tick(line.distributor(Map.of()), List.of(), List.of(), List.of(), COPPER_LOSS, 0).delivered());
		assertEquals(50, line.amount(at(0, 0, 0)));
	}

	/** MOD-021: the resistive loss is charged once per consumer on delivery, at its own distance. */
	@Test
	void theLossIsChargedAtTheConsumersDistance() {
		Line<Cell> line = grid();
		Cell cable = at(0, 0, 0);
		line.cable(cable, 1, 100, 50);
		Cell consumerPos = at(0, 1, 0);
		StubPort consumer = machine(1000, 0);
		long loss = EnergyShare.cableLoss(32, COPPER_LOSS, 10);
		assertTrue(loss > 0, "precondition: ten blocks of copper lose something on a 32 EU packet");

		EnergyLineDistributor.Moved m = tick(line.distributor(Map.of(consumerPos, 10)),
				machines(consumerPos, consumer, 1000, 32), List.of(), List.of(), COPPER_LOSS, 0);

		assertEquals(32 - loss, m.delivered(), "delivered = the packet minus the loss");
		assertEquals(32 - loss, consumer.amount);
		assertEquals(32, m.drew(), "the draw is reported with its loss");
		assertEquals(18, line.amount(cable), "the whole packet left the cable; the loss is not returned");
	}

	/**
	 * MOD-731/756: the sinks set aside draw at most their budget between them, and the kernel reports that draw —
	 * the account the network keeps of the generators' surplus. They draw in stage A only: the generators' output.
	 */
	@Test
	void theSurplusTakersDrawNoMoreThanTheirBudgetAndReportTheDraw() {
		Line<Cell> line = grid();
		Cell cable = at(0, 0, 0);
		line.cable(cable, 1, 100, 0);
		Cell fundPos = at(0, 1, 0);
		StubPort fund = machine(1000, 0);
		StubPort gen = generator();
		EnergyLineDistributor<Cell> d = line.distributor(Map.of(fundPos, 1));

		d.distribute(List.of(), List.of(), stores(fundPos, fund, 1000), 20L, sources(at(-1, 0, 0), gen), 32, 0.0,
				txn, 0);
		EnergyLineDistributor.Moved m = d.distribute(List.of(), List.of(), stores(fundPos, fund, 1000), 20L,
				sources(at(-1, 0, 0), gen), 32, 0.0, txn, 1);

		assertEquals(20, m.takersDrew(), "a budget of 20 caps a draw the packet cap would let reach 32");
		assertEquals(20, m.intoStorage(), "a surplus taker is storage in the telemetry");
		m = d.distribute(List.of(), List.of(), stores(fundPos, fund, 1000), 0L, sources(at(-1, 0, 0), gen), 32, 0.0,
				txn, 2);
		assertEquals(0, m.takersDrew(), "a budget of 0 draws nothing");
	}

	/** MOD-731: the kernel reports what the generators put into the line — and only theirs, not a store's. */
	@Test
	void theGeneratorsDrawIsReportedApartFromTheStores() {
		Line<Cell> line = grid();
		line.cable(at(1, 0, 0), 1, 12, 5);
		line.cable(at(3, 0, 0), 1, 12, 0);
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());
		StubPort store = new StubPort(10_000, 10_000, true, true);

		tick(d, List.of(), List.of(), sources(at(0, 0, 0), generator()), 0.0, 0);
		long fromStore = d.dischargeStorage(sources(at(4, 0, 0), store), DischargePlan.backupOnly(100, 0), 32, txn, 0);

		assertEquals(7, d.generatorDrawn(), "the generator filled the 7 EU of room on its cable");
		assertEquals(12, fromStore, "the store's backup is reported apart");
	}

	/** A consumer that takes less than it was offered gives the rest back to the cable, not to the floor. */
	@Test
	void whatAConsumerRefusesGoesBackToTheCable() {
		Line<Cell> line = grid();
		Cell cable = at(0, 0, 0);
		line.cable(cable, 1, 100, 50);
		Cell consumerPos = at(0, 1, 0);
		StubPort consumer = machine(10, 0); // takes 10, although it claims room for 1000

		long moved = tick(line.distributor(Map.of(consumerPos, 1)), machines(consumerPos, consumer, 1000, 32),
				List.of(), List.of(), 0.0, 0).delivered();

		assertEquals(10, moved);
		assertEquals(40, line.amount(cable), "the 22 EU the consumer refused went back into the cable");
	}

	// --- the generators: the plan, the fill beside them, the stores' channels (ADR-004, ADR-007) ---

	@Test
	void aGeneratorFillsItsCableWithNobodyWaiting() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 0);
		StubPort generator = generator();

		tick(line.distributor(Map.of()), List.of(), List.of(), sources(at(-1, 0, 0), generator), 0.0, 0);

		assertEquals(32, line.amount(at(0, 0, 0)), "source-adjacent cable filled up to packetCap (32) this tick");
		assertEquals(10_000L - 32L, generator.amount, "generator drawn exactly 32 EU");
	}

	@Test
	void aStoreDoesNotDischargeWithoutABackupBudget() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 0);
		StubPort storage = new StubPort(10_000, 10_000, true, false);

		long drawn = line.distributor(Map.of()).dischargeStorage(sources(at(0, 1, 0), storage),
				DischargePlan.backupOnly(10L, 20L), 32, txn, 0);

		assertEquals(0, drawn);
		assertEquals(10_000L, storage.amount, "storage must NOT discharge when generators cover the machine demand");
	}

	/** Backup open: a store covers the deficit, and the call reports what it drew out of storage. */
	@Test
	void aStoreCoversTheDeficitAndReportsTheDraw() {
		Line<Cell> line = grid();
		line.cable(at(0, 0, 0), 1, 100, 0);
		StubPort storage = new StubPort(10_000, 10_000, true, false);

		long drawn = line.distributor(Map.of()).dischargeStorage(sources(at(0, 1, 0), storage),
				DischargePlan.backupOnly(20L, 0L), 32, txn, 0);

		assertEquals(20, drawn, "the store released exactly the 20 EU the generators fell short of");
		assertEquals(10_000L - 20L, storage.amount);
		assertEquals(20, line.amount(at(0, 0, 0)));
	}

	/** The cascade and the feed charge the line per donor, each at most its own allowance (MOD-314, MOD-353). */
	@Test
	void theCascadeAndTheFeedChargePerDonorAllowance() {
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

			long drawn = line.distributor(Map.of()).dischargeStorage(stores, plan, 32, txn, 0);

			assertEquals(7, drawn, "only the donor with an allowance gives, and only its allowance");
			assertEquals(10_000L - 7L, donorA.amount);
			assertEquals(10_000L, donorB.amount, "a store without an allowance stays shut");
		}
	}

	@Test
	void aGeneratorWithNoCableDrawsNothing() {
		Line<Cell> line = grid();
		StubPort generator = generator();
		tick(line.distributor(Map.of()), List.of(), List.of(), sources(at(-1, 0, 0), generator), 0.0, 0);
		assertEquals(10_000L, generator.amount, "generator must not draw when there are no cables to charge");
	}

	// --- one hop a tick (ADR-001) ---

	/**
	 * A charge four cables from the machine travels one cable a tick: an intermediate cable really carries it, and
	 * the machine gets it on the fourth tick, not on the first.
	 */
	@Test
	void energyMovesOneCableATick() {
		Line<Cell> line = grid();
		Cell p1 = at(1, 0, 0);
		Cell p2 = at(2, 0, 0);
		Cell p3 = at(3, 0, 0);
		Cell p4 = at(4, 0, 0);
		line.cable(p1, 4, 12, 0);
		line.cable(p2, 3, 12, 0);
		line.cable(p3, 2, 12, 0);
		line.cable(p4, 1, 12, 12);
		Cell machinePos = at(0, 0, 0);
		StubPort m = machine(1000, 0);
		EnergyLineDistributor<Cell> d = line.distributor(Map.of(machinePos, 4));

		tick(d, machines(machinePos, m, 1000, 12), List.of(), List.of(), 0.0, 0);
		assertEquals(0, line.amount(p4), "tick 1: the far cable handed its charge on");
		assertEquals(12, line.amount(p3), "tick 1: one hop");
		assertEquals(0, m.amount);
		tick(d, machines(machinePos, m, 1000, 12), List.of(), List.of(), 0.0, 1);
		assertEquals(12, line.amount(p2), "tick 2: one more hop");
		tick(d, machines(machinePos, m, 1000, 12), List.of(), List.of(), 0.0, 2);
		assertEquals(12, line.amount(p1), "tick 3: the machine's cable");
		assertEquals(0, m.amount, "the machine has nothing before the charge reaches its cable");
		tick(d, machines(machinePos, m, 1000, 12), List.of(), List.of(), 0.0, 3);
		assertEquals(12, m.amount, "tick 4: the machine draws it");
	}

	/** A hop moves what the plan routes, at most one packet, and the receiving cable passes it on next tick. */
	@Test
	void aHopMovesAtMostOnePacket() {
		Line<Cell> line = grid();
		Cell near = at(1, 0, 0);
		Cell far = at(2, 0, 0);
		line.cable(near, 2, 100, 0);
		line.cable(far, 1, 100, 100);
		Cell machinePos = at(0, 0, 0);
		StubPort m = machine(1000, 0);
		EnergyLineDistributor<Cell> d = line.distributor(Map.of(machinePos, 1));

		tick(d, machines(machinePos, m, 1000, 1000), List.of(), List.of(), 0.0, 0);
		assertEquals(0, m.amount, "nothing on the machine's own cable yet");
		assertEquals(32, line.amount(near), "one packet hopped");
		assertEquals(68, line.amount(far));
		tick(d, machines(machinePos, m, 1000, 1000), List.of(), List.of(), 0.0, 1);
		assertEquals(32, m.amount, "the next tick the machine draws it");
	}

	// --- sharing: one distance alike, the remainder rotates (MOD-254) ---

	@Test
	void twoMachinesAtOneDistanceShareAShortLineAlike() {
		Line<Integer> line = new Line<>(twoFaced());
		line.cable(0, 1, 100, 10);
		StubPort left = machine(1000, 0);
		StubPort right = machine(1000, 0);
		List<EnergyLineDistributor.LiveConsumer<Integer>> ms = List.of(
				new EnergyLineDistributor.LiveConsumer<>(-1, left, 1000, 32),
				new EnergyLineDistributor.LiveConsumer<>(1, right, 1000, 32));

		tick(line.distributor(Map.of()), ms, List.of(), List.of(), 0.0, 0);

		assertEquals(5, left.amount);
		assertEquals(5, right.amount);
	}

	@Test
	void theIndivisibleRemainderRotatesBetweenThem() {
		Line<Integer> line = new Line<>(twoFaced());
		line.cable(0, 1, 100, 1);
		StubPort left = machine(1000, 0);
		StubPort right = machine(1000, 0);
		List<EnergyLineDistributor.LiveConsumer<Integer>> ms = List.of(
				new EnergyLineDistributor.LiveConsumer<>(-1, left, 1000, 32),
				new EnergyLineDistributor.LiveConsumer<>(1, right, 1000, 32));
		EnergyLineDistributor<Integer> d = line.distributor(Map.of());

		tick(d, ms, List.of(), List.of(), 0.0, 0);
		line.buffers.get(0).setAmountUntracked(1);
		tick(d, ms, List.of(), List.of(), 0.0, 1);

		assertEquals(1, left.amount, "each one gets the odd unit on one of the two ticks");
		assertEquals(1, right.amount);
	}

	/** Rule 1 before rule 2: a store beside the machine's cable gets only what the machine does not need. */
	@Test
	void aStoreGetsOnlyWhatTheMachineDoesNotNeed() {
		Line<Integer> line = new Line<>(twoFaced());
		line.cable(0, 1, 100, 10);
		StubPort machine = machine(1000, 0);
		StubPort store = machine(20_000, 0);

		tick(line.distributor(Map.of()), List.of(new EnergyLineDistributor.LiveConsumer<>(-1, machine, 32, 8)),
				List.of(new EnergyLineDistributor.LiveConsumer<>(1, store, 32)), List.of(), 0.0, 0);

		assertEquals(8, machine.amount, "the machine its need first");
		assertEquals(2, store.amount, "the store the rest at once");
	}

	/** A consumer that does not tell its need is served as a store (ADR-049): after every machine's need. */
	@Test
	void aConsumerOfUnknownNeedComesAfterTheMachines() {
		Line<Integer> line = new Line<>(twoFaced());
		line.cable(0, 1, 100, 10);
		StubPort machine = machine(1000, 0);
		StubPort foreign = machine(20_000, 0);

		tick(line.distributor(Map.of()), List.of(new EnergyLineDistributor.LiveConsumer<>(1, foreign, 32, 0),
				new EnergyLineDistributor.LiveConsumer<>(-1, machine, 32, 8)), List.of(), List.of(), 0.0, 0);

		assertEquals(8, machine.amount, "the machine its need first, though it is listed second");
		assertEquals(2, foreign.amount, "the unknown need takes what is left, like a store");
	}

	// --- the generator charge: rotation (MOD-254), face gates (MOD-255), the packet per host (MOD-608) ---

	@Test
	void everyStoreFeedsASaturatedCableInTurn() {
		Line<Cell> line = grid();
		Cell cable = at(0, 0, 0);
		line.cable(cable, 1, 12, 0);
		StubPort a = new StubPort(10_000, 10_000, true, false);
		StubPort b = new StubPort(10_000, 10_000, true, false);
		List<EnergyLineDistributor.LiveProducer<Cell>> stores = List.of(
				new EnergyLineDistributor.LiveProducer<>(at(-1, 0, 0), a),
				new EnergyLineDistributor.LiveProducer<>(at(1, 0, 0), b));
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());

		d.dischargeStorage(stores, DischargePlan.backupOnly(20_000L, 0L), 32, txn, 0);
		long drawnAFirst = 10_000L - a.amount;
		long drawnBFirst = 10_000L - b.amount;
		line.buffers.get(cable).setAmountUntracked(0);
		d.dischargeStorage(stores, DischargePlan.backupOnly(20_000L, 0L), 32, txn, 1);
		long drawnASecond = 10_000L - a.amount - drawnAFirst;
		long drawnBSecond = 10_000L - b.amount - drawnBFirst;

		assertEquals(12, drawnAFirst + drawnBFirst, "pass 1 filled the segment's one packet of room");
		assertEquals(12, drawnASecond + drawnBSecond, "pass 2 filled it again");
		assertTrue(drawnAFirst + drawnASecond > 0, "store A fed the line on one of the two offsets");
		assertTrue(drawnBFirst + drawnBSecond > 0, "store B fed the line on the other offset");
	}

	/** The fill beside a generator starts at the rotation: a generator between two cables alternates. */
	@Test
	void theFillBesideAGeneratorTurnsItsFaces() {
		Line<Integer> line = new Line<>(twoFaced());
		line.cable(-1, 1, 100, 0);
		line.cable(1, 1, 100, 0);
		StubPort source = generator();
		EnergyLineDistributor<Integer> d = line.distributor(Map.of());

		tick(d, List.of(), List.of(), sources(0, source), 0.0, 0);
		assertEquals(32, line.amount(-1), "offset 0 starts at the first cable");
		assertEquals(0, line.amount(1));
		tick(d, List.of(), List.of(), sources(0, source), 0.0, 1);
		assertEquals(32, line.amount(1), "offset 1 starts at the second");
		assertEquals(32, line.amount(-1));
	}

	@Test
	void aConsumerIgnoresCablesOnFacesThatCannotDraw() {
		Line<Cell> line = grid();
		Cell west = at(0, 0, 0);
		Cell east = at(2, 0, 0);
		line.cable(west, 1, 100, 10);
		line.cable(east, 1, 100, 50);
		Cell consumerPos = at(1, 0, 0);

		long moved = tick(line.distributor(Map.of(consumerPos, 1), (pos, face) -> face == WEST, (pos, face) -> true),
				machines(consumerPos, machine(1000, 0), 1000, 32), List.of(), List.of(), COPPER_LOSS, 0).delivered();

		assertEquals(10, moved, "consumer got only what the one drawable cable held, not a full packet");
		assertEquals(0, line.amount(west), "the drawable cable was drained");
		assertEquals(50, line.amount(east), "the cable on a non-accepting face must be untouched");
	}

	@Test
	void aGeneratorIgnoresCablesOnFacesThatCannotFeed() {
		Line<Cell> line = grid();
		Cell west = at(0, 0, 0);
		Cell east = at(2, 0, 0);
		line.cable(west, 1, 100, 0);
		line.cable(east, 1, 100, 0);
		StubPort source = generator();

		tick(line.distributor(Map.of(), (pos, face) -> true, (pos, face) -> face == EAST), List.of(), List.of(),
				sources(at(1, 0, 0), source), 0.0, 0);

		assertEquals(32, line.amount(east), "the emitting face filled its cable up to packetCap");
		assertEquals(0, line.amount(west), "the cable on a non-emitting face must stay empty");
		assertEquals(10_000L - 32L, source.amount, "exactly one packet left the source, not two");
	}

	@Test
	void aFullTickConservesEuAtZeroLoss() {
		Line<Cell> line = grid();
		Cell c1 = at(1, 0, 0);
		Cell c2 = at(2, 0, 0);
		Cell c3 = at(3, 0, 0);
		line.cable(c1, 3, 12, 5);
		line.cable(c2, 2, 12, 12);
		line.cable(c3, 1, 12, 7);
		Cell consumerPos = at(0, 0, 0);
		StubPort consumer = machine(1000, 100);
		StubPort source = generator();
		long before = consumer.amount + source.amount + line.amount(c1) + line.amount(c2) + line.amount(c3);

		tick(line.distributor(Map.of(consumerPos, 3)), machines(consumerPos, consumer, 900, 32), List.of(),
				sources(at(4, 0, 0), source), 0.0, 0);

		long after = consumer.amount + source.amount + line.amount(c1) + line.amount(c2) + line.amount(c3);
		assertEquals(before, after, "a full kernel pass at zero loss must neither create nor destroy EU");
		assertTrue(consumer.amount > 100, "fixture sanity: the consumer actually received something");
	}

	@Test
	void eachGeneratorPutsInAtMostOnePacket() {
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

		tick(line.distributor(Map.of()), List.of(), List.of(), generators, 0.0, 0);

		long drawn1 = 10_000L - gen1.amount;
		long drawn2 = 10_000L - gen2.amount;
		long drawn3 = 10_000L - gen3.amount;
		assertTrue(drawn1 <= 32L && drawn2 <= 32L && drawn3 <= 32L, "each source bounded by packetCap");
		assertEquals(96L, drawn1 + drawn2 + drawn3, "per-source cap: 3 sources each give 32 → total 96");
		assertEquals(96L, line.amount(cable), "all drawn EU lands in the cable buffer");
	}

	/**
	 * MOD-608: the cells of one multiblock share one packet. Two endpoints lending the same host's port touch two
	 * cables; together they inject one packet, not two — and the record of who fed lists the cell that fed.
	 */
	@Test
	void theCellsOfOneHostPutInOnePacket() {
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

		tick(d, List.of(), List.of(), cells, 0.0, 0);

		assertEquals(32, line.amount(cableA) + line.amount(cableB), "one host, one packet");
		assertEquals(10_000L - 32L, core.amount);
		assertEquals(Set.of(at(-1, 0, 0)), d.fed(), "only the cell that actually fed is recorded (MOD-665)");
	}

	/** ADR-027: two cells of one consumer over one buffer are one receiver: one packet a tick between them. */
	@Test
	void theCellsOfOneConsumerTakeOnePacket() {
		Line<Cell> line = grid();
		Cell cableA = at(0, 0, 0);
		Cell cableB = at(0, 0, 5);
		line.cable(cableA, 1, 100, 100);
		line.cable(cableB, 1, 100, 100);
		StubPort core = machine(10_000, 0);
		// A loader hands out a new port object on every lookup (FaceEnergyPort, FabricEnergyPort.of): the cells are
		// one receiver by their host, not by the port.
		Cell host = at(0, 1, 2);
		List<EnergyLineDistributor.LiveConsumer<Cell>> cells = List.of(
				new EnergyLineDistributor.LiveConsumer<>(at(0, 1, 0), new Window(core), 1000, 1000, host),
				new EnergyLineDistributor.LiveConsumer<>(at(0, 1, 5), new Window(core), 1000, 1000, host));

		tick(line.distributor(Map.of()), cells, List.of(), List.of(), 0.0, 0);

		assertEquals(32, core.amount, "one receiver, one packet");
	}

	/**
	 * A receiver that takes less than it was planned (its port refuses part of the insert): what it refuses goes
	 * back into the cables it was drawn from — even when the line's hops would fill those cables this tick — and no
	 * EU vanishes (review of Variant 1, finding 2).
	 */
	@Test
	void whatAReceiverRefusesStaysInTheLine() {
		Line<Cell> line = grid();
		Cell[] run = {at(0, 0, 0), at(1, 0, 0), at(2, 0, 0), at(3, 0, 0)};
		for (int i = 0; i < run.length; i++) {
			line.cable(run[i], i + 1, 12, 12);
		}
		StubPort gen = generator();
		StubPort buffer = machine(10_000, 0);
		Stingy stingy = new Stingy(buffer, 3);
		long before = gen.amount + 4 * 12;
		for (int t = 0; t < 40; t++) {
			tick(line.distributor(Map.of()), List.of(new EnergyLineDistributor.LiveConsumer<>(at(3, 1, 0), stingy, 12,
					12)), List.of(), sources(at(-1, 0, 0), gen), 0.0, t);
		}
		long inCables = 0;
		for (Cell c : run) {
			inCables += line.amount(c);
		}
		assertEquals(before, gen.amount + inCables + buffer.amount, "the line creates and loses nothing: generator "
				+ gen.amount + ", cables " + inCables + ", machine " + buffer.amount);
	}

	/** A second port object over the same buffer — what a loader hands out on each lookup. */
	private static final class Window implements EnergyPort {
		private final EnergyPort inner;

		Window(EnergyPort inner) {
			this.inner = inner;
		}

		@Override
		public long insert(long maxAmount, EnergyPort.Txn txn) {
			return inner.insert(maxAmount, txn);
		}

		@Override
		public long extract(long maxAmount, EnergyPort.Txn txn) {
			return inner.extract(maxAmount, txn);
		}

		@Override
		public long getAmount() {
			return inner.getAmount();
		}

		@Override
		public long getCapacity() {
			return inner.getCapacity();
		}

		@Override
		public boolean supportsInsertion() {
			return inner.supportsInsertion();
		}

		@Override
		public boolean supportsExtraction() {
			return inner.supportsExtraction();
		}
	}

	/** A port that takes at most {@code most} EU an insert, whatever room it reports. */
	private static final class Stingy implements EnergyPort {
		private final EnergyPort inner;
		private final long most;

		Stingy(EnergyPort inner, long most) {
			this.inner = inner;
			this.most = most;
		}

		@Override
		public long insert(long maxAmount, EnergyPort.Txn txn) {
			return inner.insert(Math.min(maxAmount, most), txn);
		}

		@Override
		public long extract(long maxAmount, EnergyPort.Txn txn) {
			return 0;
		}

		@Override
		public long getAmount() {
			return inner.getAmount();
		}

		@Override
		public long getCapacity() {
			return inner.getCapacity();
		}

		@Override
		public boolean supportsInsertion() {
			return true;
		}

		@Override
		public boolean supportsExtraction() {
			return false;
		}
	}

	// --- the outward fill (MOD-070, MOD-318, ADR-007) ---

	private static final Cell C1 = at(1, 0, 0);
	private static final Cell C2 = at(2, 0, 0);
	private static final Cell C3 = at(3, 0, 0);
	private static final Cell S1 = at(3, 1, 0);
	private static final Cell S2 = at(3, 2, 0);
	private static final Cell GEN = at(4, 0, 0);

	private static Line<Cell> spurRig() {
		Line<Cell> line = grid();
		line.cable(C1, 3, 12, 0);
		line.cable(C2, 2, 12, 0);
		line.cable(C3, 1, 12, 0);
		line.cable(S1, 2, 12, 0);
		line.cable(S2, 3, 12, 0);
		return line;
	}

	@Test
	void withNobodyWaitingTheLineFillsOutwardOneCableATick() {
		Line<Cell> line = spurRig();
		StubPort gen = generator();
		EnergyLineDistributor<Cell> d = line.distributor(Map.of());
		tick(d, List.of(), List.of(), sources(GEN, gen), 0.0, 0);
		assertEquals(12, line.amount(C3), "tick 1: only the generator's own cable");
		assertEquals(0, line.amount(C2) + line.amount(S1));
		tick(d, List.of(), List.of(), sources(GEN, gen), 0.0, 1);
		assertEquals(12, line.amount(C2) + line.amount(S1), "tick 2: one hop out, one packet of the cable");
		for (int i = 2; i < 20; i++) {
			tick(d, List.of(), List.of(), sources(GEN, gen), 0.0, i);
		}
		for (Cell c : List.of(C1, C2, C3, S1, S2)) {
			assertEquals(12, line.amount(c), "the whole line fills: " + c);
		}
		long before = gen.amount;
		tick(d, List.of(), List.of(), sources(GEN, gen), 0.0, 20);
		assertEquals(before, gen.amount, "a full line takes nothing more (ADR-007: it sleeps)");
	}

	/** A spur off a running line fills only from output nobody can take — never out of a packet on its way. */
	@Test
	void aSpurFillsOnlyFromOutputNobodyCanTake() {
		Line<Cell> line = spurRig();
		StubPort gen = new StubPort(10_000, 10_000, true, false);
		Cell machinePos = at(0, 0, 0);
		StubPort running = machine(1_000_000, 0);
		EnergyLineDistributor<Cell> d = line.distributor(Map.of(machinePos, 3));
		for (int i = 0; i < 30; i++) {
			tick(d, machines(machinePos, running, 4, 4), List.of(), sources(GEN, gen), 0.0, i);
		}
		assertEquals(12, line.amount(S1), "what the generator's cable carries beyond the machine's 4 fills the spur");
		assertEquals(12, line.amount(S2));
		long got = running.amount;
		tick(d, machines(machinePos, running, 4, 4), List.of(), sources(GEN, gen), 0.0, 30);
		assertEquals(4, running.amount - got, "the machine still gets its need every tick");
	}

	@Test
	void aLineOfTwoFacedPositionsRunsTheSameKernel() {
		Line<Integer> line = new Line<>(twoFaced());
		line.cable(1, 1, 12, 0);
		line.cable(2, 2, 12, 0);
		line.cable(3, 3, 12, 0);
		StubPort gen = generator();
		StubPort m = machine(1000, 0);
		EnergyLineDistributor<Integer> d = line.distributor(Map.of(4, 3));
		for (int i = 0; i < 10; i++) {
			tick(d, machines(4, m, 1000, 2), List.of(), sources(0, gen), 0.0, i);
		}
		assertTrue(m.amount >= 2 * 6, "the machine at the end of the integer line is fed: " + m.amount);
		assertTrue(line.amount(2) > 0, "the middle cable carries charge");
	}
}
