package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A line on a toy grid driven tick by tick through production's {@link LinePlanner} and {@link EnergyLineDistributor}
 * with a small replica of {@code EnergyNetwork.tick()} (MOD-789): several generators, copper cables, machines that
 * draw a fixed work rate, boxes. No world and no storage discharge — so no discharge channel opens. The caller
 * spends the machines' work after each tick ({@link #work}), so every gain the network made is visible; every tick
 * checks that no EU is created or destroyed outside the delivery loss.
 */
final class LineRig {

	/** A position of the toy grid. */
	record Cell(int x, int z) {
	}

	private static UnaryOperator<Cell> step(int dx, int dz) {
		return c -> new Cell(c.x() + dx, c.z() + dz);
	}

	/** A block on top of cell (x, z) sits at (x, z + OVER): down leads to the cell under it ({@link #over}). */
	static final int OVER = 1 << 20;

	/**
	 * Down leads to the cell under a block set on top of the sheet ({@link #over}), nowhere otherwise; up leads
	 * nowhere; then north, south, west, east — the network's face order.
	 */
	static final List<UnaryOperator<Cell>> FACES = List.of(
			c -> c.z() >= OVER / 2 ? new Cell(c.x(), c.z() - OVER) : new Cell(Integer.MIN_VALUE, 0),
			c -> new Cell(Integer.MIN_VALUE, 1), step(0, -1), step(0, 1), step(-1, 0), step(1, 0));

	static final long CABLE = 12;
	static final long PACKET = 32;

	private static final class Txn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	/**
	 * One endpoint: a machine with a work rate, or a box. {@code need} is what the machine tells the network it
	 * draws per tick ({@link PerTickDemand}); 0 — it cannot say (a foreign mod's block), its need is unknown.
	 */
	record End(String name, Cell pos, EnergyBuffer buf, boolean machine, long work, long need) {
	}

	/** One generator: it offers {@code out} EU every tick. */
	record Source(Cell pos, EnergyBuffer buf, long out) {
	}

	final String name;
	final double loss;
	final Set<Cell> cables = new LinkedHashSet<>();
	final Map<Cell, EnergyBuffer> buffers = new LinkedHashMap<>();
	final List<Source> sources = new ArrayList<>();
	final List<End> ends = new ArrayList<>();
	/** The boxes that discharge on backup ticks ({@link #battery}). */
	final Set<String> batteries = new LinkedHashSet<>();
	final dev.alaindustrial.core.net.DistanceField<Cell> producer = new dev.alaindustrial.core.net.DistanceField<>(
			this::neighbours, (a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
	final LinePlanner<Cell> planner = new LinePlanner<>();
	private List<Cell> cableList = List.of();
	private final Map<Cell, Integer> consumerDistance = new LinkedHashMap<>();
	int cursor;
	/** The network's packet cap: copper's 32 unless a rig sets another grade's ({@link #packet(long)}). */
	long packet = PACKET;
	/** A cable's segment buffer, a generator's per-call output, a box's intake: copper's unless {@link #grade}. */
	private long cableBuffer = CABLE;
	private long sourceOut = PACKET;
	private long boxIntake = PACKET;
	long injected;
	long lost;
	/** What each endpoint gained in the latest tick, and whether a generator kept output back in it. */
	final Map<String, Long> gain = new LinkedHashMap<>();
	boolean held;
	/** Time and heap the kernel's part of the ticks took so far: serve, stores, sweep and recharge (MOD-789 perf). */
	long kernelNanos;
	long kernelBytes;
	private static final com.sun.management.ThreadMXBean THREADS =
			(com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();

	LineRig(String name, double loss) {
		this.name = name;
		this.loss = loss;
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

	LineRig cable(int x, int z) {
		Cell c = new Cell(x, z);
		if (cables.add(c)) {
			buffers.put(c, new EnergyBuffer(cableBuffer, cableBuffer, cableBuffer, () -> { }));
		}
		return this;
	}

	LineRig run(int x0, int z0, int x1, int z1) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				cable(x, z);
			}
		}
		return this;
	}

	LineRig source(int x, int z, long out) {
		sources.add(new Source(new Cell(x, z), new EnergyBuffer(Long.MAX_VALUE / 4, 0, sourceOut, () -> { }), out));
		return this;
	}

	LineRig machine(String id, int x, int z, long work) {
		ends.add(new End(id, new Cell(x, z), new EnergyBuffer(800, 32, 0, () -> { }), true, work, work));
		return this;
	}

	/**
	 * A consumer that spends {@code use} EU per tick but cannot tell the network its need (no {@link
	 * PerTickDemand}: a foreign mod's machine or battery); {@code capacity} and {@code maxInsert} are its buffer's.
	 */
	LineRig foreign(String id, int x, int z, long use, long capacity, long maxInsert) {
		ends.add(new End(id, new Cell(x, z), new EnergyBuffer(capacity, maxInsert, 0, () -> { }), true, use, 0));
		return this;
	}

	/** A machine that declares {@code need} per tick, spends {@code use}, with its own buffer. */
	LineRig consumer(String id, int x, int z, long need, long use, long capacity, long maxInsert) {
		ends.add(new End(id, new Cell(x, z), new EnergyBuffer(capacity, maxInsert, 0, () -> { }), true, use, need));
		return this;
	}

	/**
	 * Another cable grade, set before any cable, generator or box: its segment buffer and its packet, which the
	 * generators' per-call output and the boxes' intake follow (an HV line: 192 and 512).
	 */
	LineRig grade(long segmentBuffer, long packetCap) {
		this.cableBuffer = segmentBuffer;
		this.packet = packetCap;
		this.sourceOut = packetCap;
		this.boxIntake = packetCap;
		return this;
	}

	/** Set the network's packet cap (a tin network: 8). */
	LineRig packet(long cap) {
		this.packet = cap;
		return this;
	}

	/** The position on top of cell (x, z): a block there touches only that cell, from above. */
	static Cell over(int x, int z) {
		return new Cell(x, z + OVER);
	}

	/** A machine of {@code work} EU/t set on top of cell (x, z) of a sheet. */
	LineRig machineOver(String id, int x, int z, long work) {
		ends.add(new End(id, over(x, z), new EnergyBuffer(800, 32, 0, () -> { }), true, work, work));
		return this;
	}

	/**
	 * A box holding {@code charge} that also discharges into the line on backup ticks (ADR-004): when the machines'
	 * room exceeds what the generators offer, as {@code DischargePlan.decide} opens the channel, the boxes sit out
	 * of the serve pass and release up to the shortfall through {@code dischargeStorage}.
	 */
	LineRig battery(String id, int x, int z, long capacity, long charge) {
		EnergyBuffer b = new EnergyBuffer(capacity, boxIntake, packet, () -> { });
		b.setAmountUntracked(charge);
		ends.add(new End(id, new Cell(x, z), b, false, 0, 0));
		batteries.add(id);
		return this;
	}

	LineRig box(String id, int x, int z, long capacity) {
		ends.add(new End(id, new Cell(x, z), new EnergyBuffer(capacity, boxIntake, 0, () -> { }), false, 0, 0));
		return this;
	}

	long end(String id) {
		for (End e : ends) {
			if (e.name().equals(id)) {
				return e.buf().getAmount();
			}
		}
		throw new IllegalArgumentException(id);
	}

	long charge(int x, int z) {
		return buffers.get(new Cell(x, z)).getAmount();
	}

	long inCables() {
		long sum = 0;
		for (EnergyBuffer b : buffers.values()) {
			sum += b.getAmount();
		}
		return sum;
	}

	long inEnds() {
		long sum = 0;
		for (End e : ends) {
			sum += e.buf().getAmount();
		}
		return sum;
	}

	private void seed(dev.alaindustrial.core.net.DistanceField<Cell> field, List<Cell> around) {
		field.clear();
		for (Cell p : around) {
			for (Cell n : neighbours(p)) {
				field.seed(n, 1);
			}
		}
		field.flood();
	}

	/** One network tick: the line plan carried out on the rig (ADR-049); the cables create and lose nothing. */
	void tick() {
		List<EnergyLineDistributor.LiveConsumer<Cell>> machines = new ArrayList<>();
		List<EnergyLineDistributor.LiveConsumer<Cell>> stores = new ArrayList<>();
		long machineRoom = 0;
		long genSupply = 0;
		for (End e : ends) {
			if (e.machine()) {
				machineRoom += Math.min(e.buf().maxInsert, e.buf().getCapacity() - e.buf().getAmount());
			}
		}
		for (Source s : sources) {
			genSupply += Math.min(s.out(), packet);
		}
		long backup = batteries.isEmpty() ? 0L : Math.max(0L, machineRoom - genSupply);
		for (End e : ends) {
			if (backup > 0 && batteries.contains(e.name())) {
				continue; // a discharging box sits out of the serve pass (MOD-756)
			}
			long room = Math.min(e.buf().maxInsert, e.buf().getCapacity() - e.buf().getAmount());
			if (room <= 0) {
				continue;
			}
			if (e.machine()) {
				machines.add(new EnergyLineDistributor.LiveConsumer<>(e.pos(), e.buf(), room, e.need()));
			} else {
				stores.add(new EnergyLineDistributor.LiveConsumer<>(e.pos(), e.buf(), room));
			}
		}
		List<EnergyLineDistributor.LiveProducer<Cell>> producers = new ArrayList<>();
		List<Cell> sourcePos = new ArrayList<>();
		for (Source s : sources) {
			s.buf().setAmountUntracked(s.out());
			producers.add(new EnergyLineDistributor.LiveProducer<>(s.pos(), s.buf()));
			sourcePos.add(s.pos());
		}
		if (producer.isEmpty()) {
			seed(producer, sourcePos);
			for (End e : ends) {
				int best = 0;
				for (Cell n : neighbours(e.pos())) {
					Integer d = producer.distanceOrNull(n);
					if (d != null) {
						best = best == 0 ? d : Math.min(best, d);
					}
				}
				consumerDistance.put(e.pos(), best);
			}
		}
		if (cableList.size() != cables.size()) {
			cableList = List.copyOf(cables);
		}
		Txn txn = new Txn();
		long cablesBefore = inCables();
		long endsBefore = inEnds();
		Map<String, Long> before = new LinkedHashMap<>();
		for (End e : ends) {
			before.put(e.name(), e.buf().getAmount());
		}
		long t0 = System.nanoTime();
		long b0 = THREADS.getCurrentThreadAllocatedBytes();
		EnergyLineDistributor<Cell> kernel = new EnergyLineDistributor<>(new LineView<>(FACES, cables::contains,
				buffers::get, p -> consumerDistance.getOrDefault(p, 0), producer::distanceOrNull, cableList,
				(p, f) -> true, (p, f) -> true, planner));
		EnergyLineDistributor.Moved m = kernel.distribute(machines, stores, List.of(), 0L, producers, packet, loss,
				txn, cursor);
		long discharged = 0;
		if (backup > 0) {
			List<EnergyLineDistributor.LiveProducer<Cell>> boxes = new ArrayList<>();
			for (End e : ends) {
				if (batteries.contains(e.name())) {
					boxes.add(new EnergyLineDistributor.LiveProducer<>(e.pos(), e.buf()));
				}
			}
			discharged = kernel.dischargeStorage(boxes, new DischargePlan<>(backup, Map.of(), Map.of()), packet, txn,
					cursor);
		}
		long moved = m.delivered();
		long drawn = m.drew();
		kernelNanos += System.nanoTime() - t0;
		kernelBytes += THREADS.getCurrentThreadAllocatedBytes() - b0;
		long in = 0;
		held = false;
		for (Source s : sources) {
			in += s.out() - s.buf().getAmount();
			// the line refused it, not the packet cap
			held |= s.buf().getAmount() > 0 && s.out() - s.buf().getAmount() < packet;
		}
		injected += in;
		lost += drawn - moved;
		assertEquals(cablesBefore + in + discharged - drawn, inCables(),
				name + ": the cables neither create nor lose EU");
		assertEquals(endsBefore + moved - discharged, inEnds(), name + ": what left the cables reached the endpoints");
		for (End e : ends) {
			gain.put(e.name(), e.buf().getAmount() - before.get(e.name()));
		}
		cursor++;
	}

	/** Spend each machine's work if its buffer covers it; returns what was spent per machine name. */
	Map<String, Long> work() {
		Map<String, Long> done = new LinkedHashMap<>();
		for (End e : ends) {
			if (e.machine() && e.work() > 0 && e.buf().getAmount() >= e.work()) {
				e.buf().drainInternal(e.work());
				done.put(e.name(), e.work());
			} else if (e.machine()) {
				done.put(e.name(), 0L);
			}
		}
		return done;
	}
}
