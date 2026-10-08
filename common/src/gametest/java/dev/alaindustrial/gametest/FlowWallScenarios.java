package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * MOD-789, characterization only: the "wall" of the flow field. The field is the distance to the NEAREST waiting
 * endpoint (ADR-003) and energy moves strictly downhill, so the cable a waiting endpoint touches (potential 1) is
 * a minimum: an endpoint behind it on the bus sits on a plateau and nothing flows to it, and a short branch to a
 * store puts the cables of a longer branch behind a seam. A store closer to the source than a machine then keeps
 * the machine dark until the store is full — against "machines before storage" (MOD-009, ADR-002).
 *
 * <p>These circuits pin what the network does TODAY, defect included, as a golden ({@link FlowWallGolden},
 * rewritten only by the command in its javadoc, ADR-032): a model change for MOD-789 rewrites it and explains
 * every moved line. No rule is asserted here on purpose — the owner chooses the model first. Every rig sits
 * inside the 8×8×8 structure, is translation-invariant and ticked directly ({@link EnergyGoldenRig}); no knob is
 * changed.
 */
public final class FlowWallScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FlowWallScenarios::circuitsMatchGolden, "mod789_flow_wall_golden"));

		private Roster() {}
	}

	/** System property naming the file the explicit update command writes the golden into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.flowWallGolden.writeTo";

	private static final int BUS_Z = 3;
	private static final int CABLES = 4;
	/** Long enough to show the wall standing: a 20 000 EU box takes 12 EU/t, so it is far from full at the end. */
	private static final int TICKS = 600;
	private static final int EVERY = 50;

	private FlowWallScenarios() {
	}

	private static BlockPos p(int x, int z) {
		return new BlockPos(x, 2, z);
	}

	private static ItemStack ore() {
		return new ItemStack(Items.RAW_IRON, 64);
	}

	/** A 20 EU/t source and four copper cables along X at {@link #BUS_Z}. */
	private static EnergyGoldenRig bus(GameTestHelper helper, String name) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, name);
		rig.source(p(0, BUS_Z), 20);
		rig.run(p(1, BUS_Z), p(CABLES, BUS_Z));
		return rig;
	}

	/** Control: the bus with one Macerator at its end. */
	private static List<String> line(GameTestHelper helper) {
		EnergyGoldenRig rig = bus(helper, "line");
		rig.machine(p(CABLES + 1, BUS_Z), ModContent.MACERATOR.get(), Direction.EAST, ore());
		return rig.driveSampled(TICKS, EVERY);
	}

	/** A Macerator south of every bus cable, its back to the cable. */
	private static List<String> machinePerCable(GameTestHelper helper) {
		EnergyGoldenRig rig = bus(helper, "bus4");
		for (int x = 1; x <= CABLES; x++) {
			rig.machine(p(x, BUS_Z + 1), ModContent.MACERATOR.get(), Direction.SOUTH, ore());
		}
		return rig.driveSampled(TICKS, EVERY);
	}

	/** The bus with a Macerator at its end and an empty Battery Box, IN face to the cable, beside cable {@code x}. */
	private static List<String> boxBeside(GameTestHelper helper, int x) {
		EnergyGoldenRig rig = bus(helper, "box" + x);
		rig.machine(p(CABLES + 1, BUS_Z), ModContent.MACERATOR.get(), Direction.EAST, ore());
		rig.store(p(x, BUS_Z + 1), ModContent.BATTERY_BOX.get(), Direction.NORTH, 0L);
		return rig.driveSampled(TICKS, EVERY);
	}

	/**
	 * A 24 EU/t source, three cables to a junction, a one-cable branch north and a three-cable branch south.
	 * {@code shortBox}: an empty Battery Box ends the short branch, otherwise a Macerator (the control); a
	 * Macerator ends the long one.
	 */
	private static List<String> fork(GameTestHelper helper, boolean shortBox) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, shortBox ? "forkbm" : "forkmm");
		rig.source(p(0, BUS_Z), 24);
		rig.run(p(1, BUS_Z), p(3, BUS_Z));
		rig.cable(p(3, BUS_Z - 1));
		rig.run(p(3, BUS_Z + 1), p(3, BUS_Z + 3));
		if (shortBox) {
			rig.store(p(3, BUS_Z - 2), ModContent.BATTERY_BOX.get(), Direction.SOUTH, 0L);
		} else {
			rig.machine(p(3, BUS_Z - 2), ModContent.MACERATOR.get(), Direction.NORTH, ore());
		}
		rig.machine(p(3, BUS_Z + 4), ModContent.MACERATOR.get(), Direction.SOUTH, ore());
		return rig.driveSampled(TICKS, EVERY);
	}

	/**
	 * Every circuit in turn, sampled to tick {@link #TICKS}, compared with the golden.
	 *
	 * @implements MOD-789-FW01 — the flow field's wall: per-window machine delivery and store charge on the bus,
	 *     box-beside and asymmetric-fork circuits are unchanged
	 */
	public static void circuitsMatchGolden(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		lines.addAll(line(helper));
		lines.addAll(machinePerCable(helper));
		lines.addAll(boxBeside(helper, 2));
		lines.addAll(boxBeside(helper, 3));
		lines.addAll(fork(helper, true));
		lines.addAll(fork(helper, false));
		EnergyGoldenRig.clear(helper);
		GoldenLines.check(helper, "FlowWallGolden", WRITE_TO_PROPERTY, "*:mod789_flow_wall_golden",
				FlowWallGolden.LINES, lines);
	}
}
