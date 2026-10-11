package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.net.DistanceField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * L1 lab for the line plan (MOD-789, ADR-049) and cable locality (MOD-755): production's {@link LinePlanner} and
 * {@link EnergyLineDistributor}, driven tick by tick on the rigs of the MOD-789 characterization
 * ({@code FlowWallScenarios}) with a small replica of {@code EnergyNetwork.tick()} — a generator, copper cables,
 * machines that work 2 EU/t, empty boxes. No world: the rigs sit on an integer grid, and the replica seeds the
 * producer field the way {@code EnergyTopologyCache} does (every face accepts). Only what the rules need is
 * modelled: there is no storage discharge here, so no discharge channel opens.
 *
 * <p>Every rule reads a cable mid-run as well as the endpoints (ADR-001), and every tick checks that no EU is
 * created or destroyed outside the delivery loss.
 *
 * @implements MOD-789-FW02 — a machine behind a store, or at the end of a longer branch, is fed before the store
 * @implements MOD-755-LC01 — every machine on a bus draws from its own cable, and the bus still feeds them all
 */
class FlowPlanLabTest {

	/** A position of the toy grid. */
	record Cell(int x, int z) {
	}

	private static UnaryOperator<Cell> step(int dx, int dz) {
		return c -> new Cell(c.x() + dx, c.z() + dz);
	}

	/** Down and up lead nowhere on a flat rig; then north, south, west, east — the network's face order. */
	private static final List<UnaryOperator<Cell>> FACES = List.of(c -> new Cell(Integer.MIN_VALUE, 0),
			c -> new Cell(Integer.MIN_VALUE, 1), step(0, -1), step(0, 1), step(-1, 0), step(1, 0));

	private static final long CABLE = 12;
	private static final long PACKET = 32;
	private static final double LOSS = 0.02;
	private static final int Z = 3;

	private static final class Txn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	/** One endpoint of a rig. */
	private record End(String name, Cell pos, EnergyBuffer buf, boolean machine, long drain) {
	}

	/** A rig and the replica of one network tick over it. */
	static final class Rig {
		final String name;
		final Set<Cell> cables = new LinkedHashSet<>();
		final Map<Cell, EnergyBuffer> buffers = new LinkedHashMap<>();
		final List<Cell> cableList = new ArrayList<>();
		final List<End> ends = new ArrayList<>();
		Cell sourcePos;
		EnergyBuffer source;
		long sourceOut;
		final DistanceField<Cell> producer = new DistanceField<>(this::neighbours,
				(a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
		final LinePlanner<Cell> planner = new LinePlanner<>();
		final Map<Cell, Integer> consumerDistance = new LinkedHashMap<>();
		int cursor;
		long injected;
		long delivered;
		long lost;
		long machineDelivered;
		/** Ends that sit out the serve and draw the generators' surplus apart (MOD-731/756, a backup tick's fund). */
		final Set<String> funds = new LinkedHashSet<>();
		/** {@code EnergyNetwork.generatorSurplus}: what the source put in last tick beyond the machines' draw. */
		long generatorSurplus;

		Rig(String name) {
			this.name = name;
		}

		List<Cell> neighbours(Cell c) {
			List<Cell> out = new ArrayList<>(4);
			for (UnaryOperator<Cell> f : FACES) {
				Cell n = f.apply(c);
				if (cables.contains(n)) {
					out.add(n);
				}
			}
			return out;
		}

		Rig cable(int x, int z) {
			Cell c = new Cell(x, z);
			cables.add(c);
			cableList.add(c);
			buffers.put(c, new EnergyBuffer(CABLE, CABLE, CABLE, () -> { }));
			return this;
		}

		Rig run(int x0, int z0, int x1, int z1) {
			if (x0 == x1) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					cable(x0, z);
				}
			} else {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					cable(x, z0);
				}
			}
			return this;
		}

		Rig source(int x, int z, long out) {
			sourcePos = new Cell(x, z);
			source = new EnergyBuffer(Long.MAX_VALUE / 4, 0, PACKET, () -> { });
			sourceOut = out;
			return this;
		}

		Rig machine(String id, int x, int z) {
			return machine(id, x, z, 2);
		}

		/** A machine spending {@code work} EU on every tick it holds that much. */
		Rig machine(String id, int x, int z, long work) {
			ends.add(new End(id, new Cell(x, z), new EnergyBuffer(800, 32, 0, () -> { }), true, work));
			return this;
		}

		Rig box(String id, int x, int z) {
			ends.add(new End(id, new Cell(x, z), new EnergyBuffer(20_000, 32, 0, () -> { }), false, 0));
			return this;
		}

		/** A machine whose network behind it empties it every tick: it always waits for a full packet. */
		Rig hungry(String id, int x, int z) {
			ends.add(new End(id, new Cell(x, z), new EnergyBuffer(800, 32, 0, () -> { }), true, -1));
			return this;
		}

		/**
		 * A storage sink that sits the serve out and draws only the generators' surplus, apart — what a fund does on
		 * a backup tick (MOD-756, {@code DischargePlan.setAside}); it seeds no field.
		 */
		Rig fund(String id, int x, int z) {
			ends.add(new End(id, new Cell(x, z), new EnergyBuffer(1_000_000, 32, 0, () -> { }), false, 0));
			funds.add(id);
			return this;
		}

		long charge(int x, int z) {
			return buffers.get(new Cell(x, z)).getAmount();
		}

		long end(String id) {
			for (End e : ends) {
				if (e.name().equals(id)) {
					return e.buf().getAmount();
				}
			}
			throw new IllegalArgumentException(id);
		}

		long inCables() {
			long sum = 0;
			for (EnergyBuffer b : buffers.values()) {
				sum += b.getAmount();
			}
			return sum;
		}

		private void seed(DistanceField<Cell> field, List<Cell> around) {
			field.clear();
			for (Cell p : around) {
				for (UnaryOperator<Cell> f : FACES) {
					Cell n = f.apply(p);
					if (cables.contains(n)) {
						field.seed(n, 1);
					}
				}
			}
			field.flood();
		}

		void tick() {
			source.setAmountUntracked(sourceOut);
			List<EnergyLineDistributor.LiveConsumer<Cell>> machines = new ArrayList<>();
			List<EnergyLineDistributor.LiveConsumer<Cell>> stores = new ArrayList<>();
			List<EnergyLineDistributor.LiveConsumer<Cell>> apart = new ArrayList<>();
			for (End e : ends) {
				long room = Math.min(32, e.buf().getCapacity() - e.buf().getAmount());
				if (room <= 0) {
					continue;
				}
				if (funds.contains(e.name())) {
					apart.add(new EnergyLineDistributor.LiveConsumer<>(e.pos(), e.buf(), room));
					continue;
				}
				(e.machine() ? machines : stores).add(new EnergyLineDistributor.LiveConsumer<>(e.pos(), e.buf(), room,
						e.machine() && e.drain() > 0 ? e.drain() : room));
			}
			if (producer.isEmpty()) {
				seed(producer, List.of(sourcePos));
				for (End e : ends) {
					int best = 0;
					for (Cell n : neighbours(e.pos())) {
						int d = producer.distanceOrNull(n);
						best = best == 0 ? d : Math.min(best, d);
					}
					consumerDistance.put(e.pos(), best);
				}
			}
			EnergyLineDistributor<Cell> kernel = new EnergyLineDistributor<>(new LineView<>(FACES, cables::contains,
					buffers::get, p -> consumerDistance.getOrDefault(p, 0), producer::distanceOrNull, cableList,
					(p, f) -> true, (p, f) -> true, planner));
			Txn txn = new Txn();
			long before = inCables();
			long sourceBefore = source.getAmount();
			long endsBefore = endsTotal();
			EnergyLineDistributor.Moved m = kernel.distribute(machines, stores, apart, generatorSurplus,
					List.of(new EnergyLineDistributor.LiveProducer<>(sourcePos, source)), PACKET, LOSS, txn, cursor);
			long moved = m.delivered();
			long drawn = m.drew();
			machineDelivered += moved - m.intoStorage();
			generatorSurplus = Math.max(0, kernel.generatorDrawn() - m.machinesDrew());
			long in = sourceBefore - source.getAmount();
			injected += in;
			delivered += moved;
			lost += drawn - moved;
			assertEquals(before + in - drawn, inCables(), name + ": the cables neither create nor lose EU");
			assertEquals(endsBefore + moved, endsTotal(), name + ": what left the cables reached the endpoints");
			cursor++;
			for (End e : ends) {
				if (e.drain() < 0) {
					e.buf().drainInternal(e.buf().getAmount());
				} else if (e.drain() > 0 && e.buf().getAmount() >= e.drain()) {
					e.buf().drainInternal(e.drain());
				}
			}
		}

		private long endsTotal() {
			long sum = 0;
			for (End e : ends) {
				sum += e.buf().getAmount();
			}
			return sum;
		}

		Rig ticks(int n) {
			for (int i = 0; i < n; i++) {
				tick();
			}
			return this;
		}
	}

	private static Rig bus(String name) {
		return new Rig(name).source(0, Z, 20).run(1, Z, 4, Z);
	}

	private static Rig boxBeside(int x) {
		return bus("box" + x).machine("M", 5, Z).box("B", x, Z + 1);
	}

	private static Rig fork(boolean shortBox) {
		Rig r = new Rig(shortBox ? "forkbm" : "forkmm").source(0, Z, 24).run(1, Z, 3, Z).cable(3, Z - 1)
				.run(3, Z + 1, 3, Z + 3);
		if (shortBox) {
			r.box("B", 3, Z - 2);
		} else {
			r.machine("A", 3, Z - 2);
		}
		return r.machine("M", 3, Z + 4);
	}

	private static Rig bus4() {
		Rig r = bus("bus4");
		for (int x = 1; x <= 4; x++) {
			r.machine("M" + x, x, Z + 1);
		}
		return r;
	}

	/** What the machines of the rig got over {@code ticks} more ticks. */
	private static long machinesOver(Rig rig, int ticks) {
		long before = rig.machineDelivered;
		rig.ticks(ticks);
		return rig.machineDelivered - before;
	}

	/**
	 * A store beside the second or third bus cable, the machine at the end: the machine gets its 2 EU/t on every
	 * tick (rule 1), the store the other 10 the cable carries (rule 2), and the cables between them carry it.
	 */
	@Test
	void aMachineBehindAStoreIsFedBeforeIt() {
		for (int x : new int[] {2, 3}) {
			Rig rig = boxBeside(x).ticks(50);
			assertTrue(rig.charge(3, Z) > 0, "box" + x + ": at t50 the cable between box and machine carries charge");
			assertTrue(rig.charge(4, Z) > 0, "box" + x + ": at t50 the machine's cable carries charge");
			assertEquals(2 * 100, machinesOver(rig, 100), "box" + x + ": the machine gets its need on every tick");
			long box = rig.end("B");
			rig.ticks(100);
			assertEquals(10 * 100, rig.end("B") - box, "box" + x + ": the store gets the rest of the line at once");
		}
	}

	@Test
	void aLongBranchMachineBeatsAShortBranchStore() {
		Rig rig = fork(true).ticks(50);
		assertTrue(rig.charge(3, Z + 2) > 0, "at t50 the middle cable of the long branch carries charge");
		assertEquals(2 * 100, machinesOver(rig, 100), "the machine at the end of the long branch gets its need");
		long box = rig.end("B");
		rig.ticks(100);
		assertEquals(10 * 100, rig.end("B") - box, "the store on the short branch takes what the junction has left");
	}

	@Test
	void everyMachineOfABusDrawsFromItsOwnCableInTurn() {
		// every machine gets its 2 EU/t first; the 12 EU/t left fills the buffers nearer first
		Rig rig = bus4();
		boolean[] seen = new boolean[5];
		for (int t = 0; t < 1000; t++) {
			rig.tick();
			for (int x = 2; x <= 4; x++) {
				seen[x] |= rig.charge(x, Z) > 0;
			}
		}
		for (int x = 1; x <= 4; x++) {
			assertTrue(rig.end("M" + x) >= 700, "machine " + x + " full by t1000: " + rig.end("M" + x));
		}
		for (int x = 2; x <= 4; x++) {
			assertTrue(seen[x], "bus cable " + x + " carried charge at some tick (the plateau is broken)");
		}
	}

	@Test
	void theMachineFartherUpTheForkStillFills() {
		Rig rig = fork(false).ticks(400);
		assertTrue(rig.end("A") >= 700 && rig.end("M") >= 700,
				"MOD-413: both machines of the fork fill, A=" + rig.end("A") + " M=" + rig.end("M"));
	}

	@Test
	void aBoxBehindTheSourceStillCharges() {
		// ADR-003, rejected alternative 2: seeding only from machines fenced off the bus behind the source.
		Rig rig = new Rig("behind").source(3, Z - 1, 20).run(0, Z, 6, Z).box("B", -1, Z).machine("M", 7, Z)
				.ticks(200);
		assertEquals(2 * 100, machinesOver(rig, 100), "the machine gets its need");
		assertTrue(rig.end("B") > 0, "the box behind the source charges, B=" + rig.end("B"));
		assertTrue(rig.charge(1, Z) > 0, "the cable toward it carries charge");
	}

	@Test
	void aDeadEndSpurStillFillsFromSurplus() {
		Rig rig = bus("spurmid").machine("M", 5, Z).cable(2, Z + 1).cable(2, Z + 2).ticks(300);
		assertEquals(12, rig.charge(2, Z + 1), "MOD-318: the spur fills from the output nobody can take");
		assertEquals(12, rig.charge(2, Z + 2));
		assertTrue(rig.end("M") >= 700);
	}

	@Test
	void aStoreAtTheEndOfAMachineBusGetsTheSurplus() {
		Rig rig = bus("bus4box");
		for (int x = 1; x <= 4; x++) {
			rig.machine("M" + x, x, Z + 1);
		}
		rig.box("B", 5, Z).ticks(100);
		assertEquals(8 * 100, machinesOver(rig, 100), "the four machines get 2 EU/t each");
		long box = rig.end("B");
		rig.ticks(100);
		assertEquals(4 * 100, rig.end("B") - box, "the box past the last machine gets the other 4 at once");
	}

	/**
	 * The ring of {@code mod756_fund_keeps_generator_surplus_on_a_backup_tick}, reduced: a 24 EU/t source among
	 * three cables of one network, east to a machine working 8 EU/t, west to a fund, south a loop joining them.
	 * The fund draws the generators' surplus apart (MOD-756, a budget of last tick's surplus): it takes what the
	 * machine leaves while the machine keeps its need.
	 */
	@Test
	void aFundOnARingGetsTheGeneratorsSurplus() {
		Rig rig = new Rig("ring").source(3, Z, 24).run(4, Z, 5, Z).cable(2, Z).run(2, Z + 1, 4, Z + 1)
				.machine("M", 6, Z, 8).fund("T", 1, Z);
		rig.ticks(300);
		long floor = (300 - 10) * (24 - 12);
		assertTrue(rig.end("T") >= floor, "the fund takes the source's surplus, T=" + rig.end("T") + " < " + floor);
		assertTrue(rig.machineDelivered >= (300 - 10) * 8L, "the machine still gets its need a tick, M="
				+ rig.machineDelivered);
	}

	/**
	 * Stores only, on one line: the near box takes the line's whole throughput first (stores nearest first), and the
	 * far box is charged once the near one is full, within the time the near box needs plus the run to the far one.
	 */
	@Test
	void theFarBoxChargesOnceTheNearOneIsFull() {
		Rig rig = bus("boxend").box("F", 5, Z).box("N", 2, Z + 1);
		int full = -1;
		for (int t = 1; t <= 2200 && rig.end("F") == 0; t++) {
			rig.tick();
			if (full < 0 && rig.end("N") == 20_000) {
				full = t;
			}
		}
		assertTrue(full > 0 && full <= 1700, "the near box fills first at the line's 12 EU/t, at t" + full);
		assertTrue(rig.end("F") > 0, "the far box starts charging once the near one is full");
	}

	/**
	 * A machine behind a running machine: M1 beside the second bus cable, M2 at the end. Each gets its 2 EU/t on
	 * every tick; what is left fills their buffers.
	 */
	@Test
	void aMachineBehindARunningMachineIsFed() {
		Rig rig = bus("gap").machine("M1", 2, Z + 1).machine("M2", 5, Z).ticks(100);
		assertTrue(rig.charge(4, Z) > 0, "at t100 the cable before M2 carries charge, c4=" + rig.charge(4, Z));
		rig.ticks(200);
		assertTrue(rig.end("M1") >= 700 && rig.end("M2") >= 700,
				"both machines run, M1=" + rig.end("M1") + " M2=" + rig.end("M2"));
	}

	/**
	 * The same line with M1 spending 14 EU/t, more than the line carries (12): the supply is short, so the nearer
	 * machine takes all of it every tick and M2 waits (rule 1: the nearest first when short).
	 */
	@Test
	void aNearMachineThatTakesTheWholeLineLeavesNothingBehindIt() {
		Rig rig = bus("gapH").machine("M1", 2, Z + 1, 14).machine("M2", 5, Z).ticks(300);
		assertEquals(0, rig.end("M2"), "short supply: the nearer machine wins");
		assertTrue(rig.machineDelivered >= 290 * 12L, "the line still delivers its full throughput to M1");
	}

	/**
	 * A store before a running machine and a store after it: the machine its need, then the near store the rest; the
	 * far one waits for it (stores fill nearest first).
	 */
	@Test
	void theNearStoreTakesTheSurplusBeforeTheFarOne() {
		Rig rig = bus("ss").box("B1", 2, Z + 1).box("B2", 5, Z).machine("M", 4, Z - 1).ticks(200);
		assertEquals(2 * 100, machinesOver(rig, 100), "the machine gets its need");
		assertTrue(rig.end("B1") > 1500, "the near store charges on the surplus, B1=" + rig.end("B1"));
		assertTrue(rig.end("B2") < 100, "the far store waits for the near one, B2=" + rig.end("B2"));
	}
}
