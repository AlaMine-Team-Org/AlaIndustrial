package dev.alaindustrial.command.demo.energy;

import static dev.alaindustrial.command.demo.energy.Expectation.allFed;
import static dev.alaindustrial.command.demo.energy.Expectation.charges;
import static dev.alaindustrial.command.demo.energy.Expectation.conserves;
import static dev.alaindustrial.command.demo.energy.Expectation.dark;
import static dev.alaindustrial.command.demo.energy.Expectation.delivers;
import static dev.alaindustrial.command.demo.energy.Expectation.discharges;
import static dev.alaindustrial.command.demo.energy.Expectation.fed;
import static dev.alaindustrial.command.demo.energy.Expectation.nearFirst;
import static dev.alaindustrial.command.demo.energy.Expectation.notMoreThan;
import static dev.alaindustrial.command.demo.energy.Expectation.underFed;
import static dev.alaindustrial.command.demo.energy.Expectation.when;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorStructure;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Bays E16–E30 of the energy stand (MOD-799): lines with loss, storage at night and on a short generator, the
 * consumers with an unknown need, a multiblock generator touched through several cells, mixed cable grades, and
 * two gaps the owner can close with a cable to split or merge networks. Same slot rules as {@link PriorityBays}.
 */
final class NetworkBays {
	private NetworkBays() {
	}

	static List<Bay> all() {
		return List.of(copperTen(), copperTwenty(), nightSolar(), unknownNeeds(), unknownNearerShort(),
				concentrator(), mixedGrades(), electrumTrunk(), gapSplit(), gapMerge(), boxCoversShortSource(),
				sourceOffBoxFeeds(), boxBehindSource(), machineOnEveryCable(), shortBusFourMachines());
	}

	/** Ten copper cables in an L, a machine at the end and a box beside the ninth. */
	private static Bay copperTen() {
		return EnergyStand.slot(15, "E16", "Copper line 10", w -> {
			w.source("S1", 0, 0, 20);
			w.run(1, 0, 9, 0);
			w.cable(9, 1);
			w.machine("M1", 9, 2, SOUTH);
			w.store("B1", 8, 1, NORTH, 0L);
		}, fed("M1"), charges("B1", 3), conserves(0.4));
	}

	/** Twenty copper cables in a snake, a machine at the end and a box beside the last cable. */
	private static Bay copperTwenty() {
		return EnergyStand.slot(16, "E17", "Copper line 20", w -> {
			w.source("S1", 0, 0, 20);
			int[] end = w.snake(1, 0, 20, 9, ModContent.COPPER_CABLE.get());
			w.machine("M1", end[0], end[1], SOUTH);
			w.store("B1", 0, 3, EAST, 0L);
		}, fed("M1"), charges("B1", 1), conserves(0.6));
	}

	/**
	 * Three solar panels and a charged box feed two machines: by day the panels give part of the draw, at night
	 * nothing, and the box covers the rest (ADR-049, rule 3). The box sits on a loop so its IN and OUT faces both
	 * meet the same line. {@code /time set midnight} shows the night case.
	 */
	private static Bay nightSolar() {
		return EnergyStand.slot(17, "E18", "Solar and box at night", w -> {
			w.run(0, 2, 6, 2);
			w.store("B1", 0, 3, NORTH, 10_000L);
			w.run(0, 4, 1, 4);
			w.cable(1, 3);
			for (int x = 2; x <= 4; x++) {
				w.set(x, 1, 1, ModContent.SOLAR_PANEL.get());
				w.generator("G" + (x - 1), w.at(x, 1, 1));
			}
			w.machine("M1", 7, 2, EAST);
			w.machine("M2", 5, 3, SOUTH);
		}, fed("M1"), fed("M2"), discharges("B1"));
	}

	/** A charge pad, a pump and a teleporter (unknown need, planned as stores) on the bus before two machines. */
	private static Bay unknownNeeds() {
		return EnergyStand.slot(18, "E19", "Unknown-need devices", w -> {
			w.source("S1", 0, 2, 20);
			w.run(1, 2, 7, 2);
			w.unknown("U1", 1, 3, ModContent.CHARGE_PAD.get(), SOUTH);
			w.unknown("U2", 2, 1, ModContent.PUMP.get(), NORTH);
			w.unknown("U3", 3, 3, ModContent.TELEPORTER.get(), SOUTH);
			w.machine("M1", 5, 3, SOUTH);
			w.machine("M2", 8, 2, EAST);
		}, fed("M1"), fed("M2"), conserves(0.25));
	}

	/** A charge pad nearer the source than the machine, and only enough supply for the machine. */
	private static Bay unknownNearerShort() {
		return EnergyStand.slot(19, "E20", "Charge pad before machine", w -> {
			w.source("S1", 0, 2, n -> n.get("M1").intValue());
			w.run(1, 2, 4, 2);
			w.unknown("U1", 1, 3, ModContent.CHARGE_PAD.get(), SOUTH);
			w.machine("M1", 5, 2, EAST);
		}, fed("M1"), conserves(0.25));
	}

	/**
	 * Two Radiant concentrators side by side (ADR-027): A meets its line through one cell, B through all six
	 * outer faces of its bottom tier. The network counts B's host once, so B's box gains no more than A's.
	 */
	private static Bay concentrator() {
		return EnergyStand.slot(20, "E21", "Concentrator 1 vs 6 cells", w -> {
			concentratorAt(w, "GA", 1, 1);
			w.cable(3, 2);
			w.store("BA", 3, 3, NORTH, 0L);
			concentratorAt(w, "GB", 6, 1);
			w.run(5, 1, 5, 3);
			w.run(6, 3, 8, 3);
			w.run(8, 2, 8, 1);
			w.store("BB", 9, 1, WEST, 0L);
		}, notMoreThan("BB", "BA", 1.15));
	}

	/** A grown panel at bay-local {@code (x, 1, z)} plus its seven sections, assembled facing north. */
	private static void concentratorAt(BayWriter w, String name, int x, int z) {
		BlockPos core = w.at(x, 1, z);
		w.place(core, ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState());
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part != ConcentratorPart.CORE) {
				w.place(core.offset(part.worldOffset(Direction.NORTH)),
						ModContent.CONCENTRATOR_SECTION.get().defaultBlockState());
			}
		}
		if (!w.dry()) {
			ConcentratorStructure.tryAssemble(w.level(), core);
		}
		w.generator(name, core);
	}

	/** Gold, then copper, then tin to the far machine, a machine on the gold and one on the copper. */
	private static Bay mixedGrades() {
		return EnergyStand.slot(21, "E22", "Mixed grades gold cu tin", w -> {
			w.source("S1", 0, 2, 64);
			w.run(1, 2, 3, 2, ModContent.GOLD_CABLE.get());
			w.run(4, 2, 6, 2);
			w.run(7, 2, 8, 2, ModContent.TIN_CABLE.get());
			w.machine("M1", 9, 2, EAST);
			w.machine("M2", 2, 3, SOUTH);
			w.machine("M3", 5, 3, SOUTH);
		}, allFed(), conserves(0.25));
	}

	/** An electrum trunk with four machines and two boxes. */
	private static Bay electrumTrunk() {
		return EnergyStand.slot(22, "E23", "Electrum trunk", w -> {
			w.source("S1", 0, 2, 128);
			w.run(1, 2, 9, 2, ModContent.ELECTRUM_CABLE.get());
			for (int i = 0; i < 4; i++) {
				w.machine("M" + (i + 1), 2 + 2 * i, 1, NORTH);
			}
			w.store("B1", 3, 3, NORTH, 0L);
			w.store("B2", 7, 3, NORTH, 0L);
		}, allFed(), charges("B1", 8), charges("B2", 8), delivers("S1", 40), conserves(0.25));
	}

	/** One cell of the bus is left empty: no energy crosses it until the owner lays a cable there. */
	private static Bay gapSplit() {
		return EnergyStand.slot(23, "E24", "Gap: fill to connect", w -> {
			w.source("S1", 0, 2, 20);
			w.run(1, 2, 3, 2);
			w.gap("gap", 4, 2);
			w.run(5, 2, 7, 2);
			w.machine("M1", 8, 2, EAST);
		}, when("gap", fed("M1"), dark("M1")));
	}

	/** Two lines with a gap between: the right one's source covers half its machine until the gap is closed. */
	private static Bay gapMerge() {
		return EnergyStand.slot(24, "E25", "Gap: fill to merge", w -> {
			w.source("S1", 0, 2, 20);
			w.run(1, 2, 3, 2);
			w.machine("M1", 2, 3, SOUTH);
			w.gap("gap", 4, 2);
			w.run(5, 2, 8, 2);
			w.source("S2", 9, 2, n -> (int) (n.get("M2") / 2));
			w.machine("M2", 6, 3, SOUTH);
		}, fed("M1"), when("gap", fed("M2"), underFed("M2")));
	}

	/** A charged box on a loop of the line: its IN face north, its OUT face south back into the line. */
	private static void loopedBox(BayWriter w, long charge) {
		w.run(1, 2, 5, 2);
		w.store("B1", 1, 3, NORTH, charge);
		w.run(1, 4, 2, 4);
		w.cable(2, 3);
		w.machine("M1", 4, 3, SOUTH);
		w.machine("M2", 6, 2, EAST);
	}

	private static Bay boxCoversShortSource() {
		return EnergyStand.slot(25, "E26", "Box covers short source", w -> {
			w.source("S1", 0, 2, n -> (int) ((n.get("M1") + n.get("M2")) / 2));
			loopedBox(w, 10_000L);
		}, fed("M1"), fed("M2"), discharges("B1"), conserves(0.25));
	}

	private static Bay sourceOffBoxFeeds() {
		return EnergyStand.slot(26, "E27", "Source off box feeds", w -> {
			w.source("S1", 0, 2, 0);
			loopedBox(w, 10_000L);
		}, fed("M1"), fed("M2"), discharges("B1"), conserves(0.25));
	}

	/** The source in the middle of the bus, a box behind it at one end, a machine at the other. */
	private static Bay boxBehindSource() {
		return EnergyStand.slot(27, "E28", "Box behind the source", w -> {
			w.run(1, 2, 7, 2);
			w.source("S1", 4, 1, 20);
			w.store("B1", 0, 2, EAST, 0L);
			w.machine("M1", 8, 2, EAST);
		}, fed("M1"), charges("B1", 4), conserves(0.25));
	}

	/** MOD-755: a machine on every cable of the bus, each fed from its own cable. */
	private static Bay machineOnEveryCable() {
		return EnergyStand.slot(28, "E29", "Machine on every cable", w -> {
			w.source("S1", 0, 2, 20);
			w.run(1, 2, 4, 2);
			for (int x = 1; x <= 4; x++) {
				w.machine("M" + x, x, 3, SOUTH);
			}
		}, allFed(), conserves(0.25));
	}

	/** The same bus with supply for two machines: the two nearest run, the far two wait. */
	private static Bay shortBusFourMachines() {
		return EnergyStand.slot(29, "E30", "Four machines short bus", w -> {
			w.source("S1", 0, 2, n -> (int) (n.get("M1") + n.get("M2")));
			w.run(1, 2, 4, 2);
			for (int x = 1; x <= 4; x++) {
				w.machine("M" + x, x, 3, SOUTH);
			}
		}, fed("M1"), fed("M2"), underFed("M3"), underFed("M4"), nearFirst("M2", "M3"), nearFirst("M3", "M4"),
				conserves(0.25));
	}
}
