package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for a dead-end spur off a cable in the middle of a running corridor (MOD-730, ADR-045): the
 * stranded fill takes only what a donor holds over what it forwarded this tick, and at equal potential the
 * sweep visits the corridor before the spur. Beside {@code EnergyLineDistributorCoreTest} (the kernel's own
 * rules) because the full-tick case composes the kernel with the real {@link FlowField}.
 *
 * <p>Positions are {@link Cell}s of an integer grid whose six faces are numbered the way the network numbers
 * a block's ({@code Direction} order — down, up, north, south, west, east).
 *
 * @implements MOD-730-SP01 — a spur off a running corridor takes surplus only and never stalls the corridor
 */
class EnergyLineDistributorSpurTest {

	/** A position of the toy grid. */
	record Cell(int x, int y, int z) {
	}

	private static UnaryOperator<Cell> step(int dx, int dy, int dz) {
		return c -> new Cell(c.x() + dx, c.y() + dy, c.z() + dz);
	}

	/** The six faces of a cell, in the order the network numbers a block's faces. */
	private static final List<UnaryOperator<Cell>> GRID = List.of(step(0, -1, 0), step(0, 1, 0), step(0, 0, -1),
			step(0, 0, 1), step(-1, 0, 0), step(1, 0, 0));
	private static final long CABLE = 12;
	private static final long PACKET = 32;

	private final EnergyPort.Txn txn = participant -> {
	};

	private final Set<Cell> cables = new LinkedHashSet<>();
	private final Map<Cell, EnergyBuffer> buffers = new LinkedHashMap<>();

	private static Cell at(int x, int y, int z) {
		return new Cell(x, y, z);
	}

	private void cable(Cell pos, long fill) {
		EnergyBuffer b = new EnergyBuffer(CABLE, CABLE, CABLE, () -> {
		});
		b.setAmountUntracked(fill);
		cables.add(pos);
		buffers.put(pos, b);
	}

	private long amount(Cell pos) {
		return buffers.get(pos).getAmount();
	}

	private static EnergyBuffer generator() {
		EnergyBuffer b = new EnergyBuffer(100_000, 0, 100_000, () -> {
		});
		b.setAmountUntracked(100_000);
		return b;
	}

	private static List<EnergyLineDistributor.LiveProducer<Cell>> sources(Cell pos, EnergyPort port) {
		return List.of(new EnergyLineDistributor.LiveProducer<>(pos, port));
	}

	@Test
	void fillStrandedOneHop_leavesTheJunctionThePacketItForwardedThisTick() {
		// Machine at x=0; corridor C1 (x=1) - J (x=2) - T (x=3); generator at x=4; spur S south of J. The
		// potentials are given by hand with T listed before S, so the sweep is already corridor-first: this
		// isolates the fill's rule. In one tick J forwards its packet to C1 and T refills it — J is brim-full
		// and C1 below it is full too, which is exactly what a stalled corridor looked like to the fill.
		Cell c1 = at(1, 0, 0);
		Cell junction = at(2, 0, 0);
		Cell trunk = at(3, 0, 0);
		Cell spur = at(2, 0, 1);
		cable(c1, 0);
		cable(junction, CABLE);
		cable(trunk, CABLE);
		cable(spur, 0);
		Map<Cell, Integer> potential = Map.of(c1, 1, junction, 2, trunk, 3, spur, 3);
		Map<Cell, Integer> producerDistance = Map.of(trunk, 1, junction, 2, c1, 3, spur, 3);
		EnergyLineDistributor<Cell> d = new EnergyLineDistributor<>(new LineView<>(GRID, cables::contains,
				buffers::get, pos -> 0, potential::get, pos -> null, List.of(c1, junction, trunk, spur),
				(p, f) -> true, (p, f) -> true, List.of(spur), producerDistance::get));

		d.chargeAndPropagateLine(sources(at(4, 0, 0), generator()), List.of(),
				DischargePlan.backupOnly(CABLE, 100_000L), PACKET, txn, 0);

		assertEquals(CABLE, amount(c1), "precondition: the junction forwarded its packet downhill");
		assertEquals(CABLE, amount(junction), "the junction keeps the next packet the trunk just handed it");
		assertEquals(0, amount(spur), "a packet on its way to the machine is not surplus");
	}

	@Test
	void fullTick_aSpurOffAMidCorridorJunctionDoesNotHalveTheMachine() {
		// Generator at x=0, bus M1..M4 along X, machine at x=5, spur S1..S3 south (+z) off M2. The real
		// FlowField, so the sweep order is the flood's own: from the machine the flood reaches M2 and lists
		// SOUTH (the spur) before WEST (the corridor) — the orientation that halved delivery before MOD-730.
		List<Cell> bus = List.of(at(1, 0, 0), at(2, 0, 0), at(3, 0, 0), at(4, 0, 0));
		List<Cell> spur = List.of(at(2, 0, 1), at(2, 0, 2), at(2, 0, 3));
		bus.forEach(c -> cable(c, 0));
		spur.forEach(c -> cable(c, 0));
		FlowField<Cell> flow = new FlowField<>(cell -> {
			List<Cell> out = new ArrayList<>();
			for (UnaryOperator<Cell> face : GRID) {
				if (cables.contains(face.apply(cell))) {
					out.add(face.apply(cell));
				}
			}
			return out;
		}, Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z));
		flow.producer().seed(bus.get(0), 1);
		flow.producer().flood();
		flow.sink().seed(bus.get(3), 1);
		flow.sink().flood();
		flow.machine().seed(bus.get(3), 1);
		flow.machine().flood();
		flow.rebuild(cables);
		assertEquals(new LinkedHashSet<>(spur), new LinkedHashSet<>(flow.strandedOrder()),
				"precondition: the whole spur is stranded");
		EnergyLineDistributor<Cell> d = new EnergyLineDistributor<>(new LineView<>(GRID, cables::contains,
				buffers::get, pos -> 4, flow::flowPotential, flow::machinePotential, flow.propagationOrder(),
				(p, f) -> true, (p, f) -> true, flow.strandedOrder(), flow::producerDistance));
		Cell machinePos = at(5, 0, 0);
		EnergyBuffer machine = new EnergyBuffer(100, 100, 0, () -> {
		});
		EnergyBuffer gen = generator();
		long[] spurBefore = null;
		int still = 0;
		for (int tick = 0; tick < 200; tick++) {
			long room = machine.getCapacity() - machine.getAmount();
			List<EnergyLineDistributor.LiveConsumer<Cell>> consumer =
					List.of(new EnergyLineDistributor.LiveConsumer<>(machinePos, machine, room));
			long delivered = d.serveConsumersFromLine(consumer, PACKET, 0.0, txn, tick);
			d.chargeAndPropagateLine(sources(at(0, 0, 0), gen), List.of(), DischargePlan.backupOnly(room, 100_000L),
					PACKET, txn, tick);
			machine.drainInternal(2);
			long[] side = spur.stream().mapToLong(this::amount).toArray();
			if (room >= CABLE) {
				if (tick >= 4) {
					assertEquals(CABLE, delivered, "tick " + tick + ": the hungry machine gets a packet every tick");
				}
				assertEquals(0, Arrays.stream(side).sum(),
						"tick " + tick + ": while the machine is hungry there is no surplus for the spur");
			}
			still = Arrays.equals(side, spurBefore) ? still + 1 : 0;
			spurBefore = side;
		}
		assertTrue(machine.getAmount() >= machine.getCapacity() - CABLE,
				"the machine filled up: " + machine.getAmount());
		for (Cell c : spur) {
			assertEquals(CABLE, amount(c), "once the machine is full the surplus fills the spur: " + c);
		}
		assertTrue(still >= 20, "the full spur does not pump back and forth; still for " + still + " ticks");
	}
}
