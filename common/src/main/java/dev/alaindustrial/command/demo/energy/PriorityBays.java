package dev.alaindustrial.command.demo.energy;

import static dev.alaindustrial.command.demo.energy.Expectation.charges;
import static dev.alaindustrial.command.demo.energy.Expectation.conserves;
import static dev.alaindustrial.command.demo.energy.Expectation.delivers;
import static dev.alaindustrial.command.demo.energy.Expectation.fed;
import static dev.alaindustrial.command.demo.energy.Expectation.fedUpTo;
import static dev.alaindustrial.command.demo.energy.Expectation.nearFirst;
import static dev.alaindustrial.command.demo.energy.Expectation.storeOrder;
import static dev.alaindustrial.command.demo.energy.Expectation.underFed;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

import java.util.List;

/**
 * Bays E01–E15 of the energy stand (MOD-799): the player rule of ADR-049 on small circuits — machines first,
 * nearest first when short, what machines cannot take goes to the stores at once. The circuits come from the
 * MOD-789 rigs ({@code FlowWallScenarios}, {@code sim/} rigs gap, ss, behind, fork, ring, 2genbox, oc2), built
 * here from real blocks in a world that ticks on its own.
 *
 * <p>Each bay writes inside {@code x 0..9, z 0..6} of its slot, the bus usually on row {@code z = 2}; the sign
 * stands at {@code (0, 6)}. A Macerator's front is its inert face, so every machine turns its front away from
 * the cable that feeds it; a Battery Box's IN face is its FACING.
 */
final class PriorityBays {
	private PriorityBays() {
	}

	static List<Bay> all() {
		return List.of(control(), behindBox(), boxBeside("E03", 2), boxBeside("E04", 3), boxOnEveryCable(),
				forkShortBox(), forkMachines(), twoMachines(), twoMachinesShort(), threeMachinesShort(),
				forkBoxBehindNearMachine(), ring(), twoSources(), heavyMachineThinCable(), storageOnly());
	}

	/** A 20 EU/t source at {@code (0, 2)} and copper along row 2 to {@code x}. */
	private static void bus(BayWriter w, int to) {
		w.source("S1", 0, 2, 20);
		w.run(1, 2, to, 2);
	}

	private static Bay control() {
		return EnergyStand.slot(0, "E01", "Control: one machine", w -> {
			bus(w, 4);
			w.machine("M1", 5, 2, EAST);
		}, fed("M1"), conserves(0.25));
	}

	private static Bay behindBox() {
		return EnergyStand.slot(1, "E02", "Machine behind a box", w -> {
			bus(w, 2);
			w.store("B1", 3, 2, WEST, 0L);
			w.run(4, 2, 5, 2);
			w.machine("M1", 6, 2, EAST);
		}, fed("M1"), charges("B1", 4), conserves(0.25));
	}

	private static Bay boxBeside(String id, int cable) {
		return EnergyStand.slot(id.equals("E03") ? 2 : 3, id, "Box beside cable " + cable, w -> {
			bus(w, 4);
			w.store("B1", cable, 3, NORTH, 0L);
			w.machine("M1", 5, 2, EAST);
		}, fed("M1"), charges("B1", 4), conserves(0.25));
	}

	private static Bay boxOnEveryCable() {
		return EnergyStand.slot(4, "E05", "Box on every cable", w -> {
			bus(w, 4);
			for (int x = 1; x <= 3; x++) {
				w.store("B" + x, x, 3, NORTH, 0L);
			}
			w.machine("M1", 5, 2, EAST);
		}, fed("M1"), storeOrder("B1", "B2"), storeOrder("B2", "B3"), conserves(0.25));
	}

	/** A 24 EU/t source, three cables to a junction at {@code (3, 2)}, a short branch north, a longer one south. */
	private static void fork(BayWriter w) {
		w.source("S1", 0, 2, 24);
		w.run(1, 2, 3, 2);
		w.cable(3, 1);
		w.run(3, 3, 3, 4);
	}

	private static Bay forkShortBox() {
		return EnergyStand.slot(5, "E06", "Fork: box short machine long", w -> {
			fork(w);
			w.store("B1", 3, 0, SOUTH, 0L);
			w.machine("M1", 3, 5, SOUTH);
		}, fed("M1"), charges("B1", 4), conserves(0.25));
	}

	private static Bay forkMachines() {
		return EnergyStand.slot(6, "E07", "Fork: machine / machine", w -> {
			fork(w);
			w.machine("M1", 3, 0, NORTH);
			w.machine("M2", 3, 5, SOUTH);
		}, fed("M1"), fed("M2"), conserves(0.25));
	}

	private static void twoOnBus(BayWriter w) {
		w.run(1, 2, 5, 2);
		w.machine("M1", 2, 3, SOUTH);
		w.machine("M2", 6, 2, EAST);
	}

	private static Bay twoMachines() {
		return EnergyStand.slot(7, "E08", "Two machines enough supply", w -> {
			w.source("S1", 0, 2, 20);
			twoOnBus(w);
		}, fed("M1"), fed("M2"), conserves(0.25));
	}

	private static Bay twoMachinesShort() {
		return EnergyStand.slot(8, "E09", "Two machines short supply", w -> {
			w.source("S1", 0, 2, n -> (int) (n.get("M1") + n.get("M2") / 2));
			twoOnBus(w);
		}, fed("M1"), underFed("M2"), nearFirst("M1", "M2"), conserves(0.25));
	}

	private static Bay threeMachinesShort() {
		return EnergyStand.slot(9, "E10", "Three machines short supply", w -> {
			w.source("S1", 0, 2, n -> (int) (n.get("M1") + n.get("M2") + n.get("M3") / 2));
			w.run(1, 2, 5, 2);
			w.machine("M1", 2, 3, SOUTH);
			w.machine("M2", 4, 3, SOUTH);
			w.machine("M3", 6, 2, EAST);
		}, fed("M1"), fed("M2"), underFed("M3"), nearFirst("M2", "M3"), conserves(0.25));
	}

	/** R2-1: the near branch has a machine and a box behind it, the far branch a machine. */
	private static Bay forkBoxBehindNearMachine() {
		return EnergyStand.slot(10, "E11", "Fork: box behind near machine", w -> {
			w.source("S1", 0, 3, 24);
			w.run(1, 3, 2, 3);
			w.run(2, 2, 2, 1);
			w.machine("M1", 1, 1, WEST);
			w.store("B1", 2, 0, SOUTH, 0L);
			w.run(3, 3, 6, 3);
			w.machine("M2", 7, 3, EAST);
		}, fed("M1"), fed("M2"), charges("B1", 2), conserves(0.25));
	}

	/** A ring of cables, machines on both arcs, a box on the far side. */
	private static Bay ring() {
		return EnergyStand.slot(11, "E12", "Ring machines both arcs", w -> {
			w.source("S1", 0, 2, 24);
			w.run(1, 1, 6, 1);
			w.run(1, 4, 6, 4);
			w.run(1, 2, 1, 3);
			w.run(6, 2, 6, 3);
			w.machine("M1", 3, 0, NORTH);
			w.machine("M2", 3, 5, SOUTH);
			w.store("B1", 7, 3, WEST, 0L);
		}, fed("M1"), fed("M2"), charges("B1", 2), conserves(0.25));
	}

	/** R2-2: a source at each end of the bus, a machine near one, a box near the other. */
	private static Bay twoSources() {
		return EnergyStand.slot(12, "E13", "Two sources box near one", w -> {
			w.source("S1", 0, 2, 20);
			w.run(1, 2, 8, 2);
			w.source("S2", 9, 2, 20);
			w.machine("M1", 2, 3, SOUTH);
			w.store("B1", 8, 3, NORTH, 0L);
		}, fed("M1"), charges("B1", 6), delivers("S1", 2), delivers("S2", 4), conserves(0.25));
	}

	/**
	 * oc2: a machine with three Overclocker Chips draws more than one copper cable carries; the source feeds the
	 * line through two cables, and what the machine's single path cannot take goes to the box beside the source.
	 */
	private static Bay heavyMachineThinCable() {
		return EnergyStand.slot(13, "E14", "Heavy machine thin cable", w -> {
			w.source("S1", 1, 2, 40);
			w.cable(1, 1);
			w.run(2, 1, 2, 2);
			w.run(3, 2, 6, 2);
			w.machine("M1", 7, 2, EAST, 3);
			w.store("B1", 1, 0, SOUTH, 0L);
		}, fedUpTo("M1", 12), charges("B1", 6), delivers("S1", 18), conserves(0.25));
	}

	/** Stores only: they charge nearest first. */
	private static Bay storageOnly() {
		return EnergyStand.slot(14, "E15", "Stores only charge order", w -> {
			bus(w, 6);
			w.store("B1", 1, 3, NORTH, 0L);
			w.store("B2", 4, 3, NORTH, 0L);
			w.store("B3", 7, 2, WEST, 0L);
		}, storeOrder("B1", "B2"), storeOrder("B2", "B3"), delivers("S1", 10), conserves(0.25));
	}
}
