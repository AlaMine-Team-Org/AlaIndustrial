package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * MOD-789, MOD-755: the "wall" of the flow field. The field used to be the distance to the NEAREST waiting
 * endpoint and energy moved strictly downhill, so the cable a waiting endpoint touches was a minimum: an endpoint
 * behind it on the bus sat on a plateau, a short branch to a store put the cables of a longer branch behind a
 * seam, and a store closer to the source than a machine kept the machine dark until the store was full — against
 * "machines before storage" (MOD-009, ADR-002). The line plan (ADR-049) replaced that model.
 *
 * <p>The circuits are pinned as a golden ({@link FlowWallGolden}, rewritten only by the command in its javadoc,
 * ADR-032), and the rule the owner chose (2026-10-10, variant 1) is asserted on its own: a machine behind a store,
 * at the end of a longer branch or of a fork gets its need on every tick — it works at least as fast as on the same
 * rig with no store at all — and the store gets what is left at once; every machine of a bus draws from its own
 * cable; a store behind the source still charges. Each rule reads a cable between the endpoints mid-run (ADR-001).
 * Every rig sits inside the 8×8×8 structure, is translation-invariant and ticked directly ({@link EnergyGoldenRig});
 * no knob is changed.
 */
public final class FlowWallScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FlowWallScenarios::circuitsMatchGolden, "mod789_flow_wall_golden"),
				RosterEntry.of(FlowWallScenarios::machineBehindABoxIsFedFirst,
						"mod789_machine_behind_box_is_fed_first"),
				RosterEntry.of(FlowWallScenarios::longBranchMachineBeatsShortBranchBox,
						"mod789_long_branch_machine_beats_short_branch_box"),
				RosterEntry.of(FlowWallScenarios::everyBusMachineDrawsFromItsOwnCable,
						"mod755_every_bus_machine_draws_from_its_own_cable"),
				RosterEntry.of(FlowWallScenarios::boxBehindTheSourceStillCharges,
						"mod789_box_behind_source_still_charges"),
				RosterEntry.of(FlowWallScenarios::machineBehindARunningMachineIsFed,
						"mod789_machine_behind_running_machine_is_fed"),
				RosterEntry.of(FlowWallScenarios::farMachineOfAForkBeatsTheBoxBehindTheNearOne,
						"mod789_far_machine_of_fork_beats_box_behind_near_one"));

		private Roster() {}
	}

	/** System property naming the file the explicit update command writes the golden into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.flowWallGolden.writeTo";

	private static final int BUS_Z = 3;
	private static final int CABLES = 4;
	/** Long enough to show the wall standing: a 20 000 EU box takes 12 EU/t, so it is far from full at the end. */
	private static final int TICKS = 600;
	private static final int EVERY = 50;
	/**
	 * The fork's bus: its south branch ends at z = FORK_Z + 5 = 7, inside the 8x8x8 structure that
	 * {@link EnergyGoldenRig#clear} empties — on the shared bus row the far machine sat at z = 8, outside it, and
	 * carried its buffer and progress from the control run into the run with the box (review of Variant 1).
	 */
	private static final int FORK_Z = 2;
	/** Two progress ticks: the box run and the control may start a tick apart. */
	private static final int WORK_SLACK = 2;

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
	 * ADR-003's rejected alternative 2: a source in the middle of a bus, an empty box at one end behind it, a
	 * Macerator at the other. Five cables along X, the source above the third — two cables on either side, so the
	 * old field fed both and the guard holds on old and new code alike.
	 */
	private static EnergyGoldenRig behind(GameTestHelper helper) {
		return behind(helper, true);
	}

	/** {@link #behind(GameTestHelper)}, with or without its box. */
	private static EnergyGoldenRig behind(GameTestHelper helper, boolean withBox) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "behind");
		rig.source(p(3, BUS_Z - 1), 20);
		rig.run(p(1, BUS_Z), p(5, BUS_Z));
		if (withBox) {
			rig.store(p(0, BUS_Z), ModContent.BATTERY_BOX.get(), Direction.EAST, 0L);
		}
		rig.machine(p(6, BUS_Z), ModContent.MACERATOR.get(), Direction.EAST, ore());
		return rig;
	}

	/** Stores only: an empty box at the end of the bus and one beside the second cable. */
	private static List<String> storesOnly(GameTestHelper helper) {
		EnergyGoldenRig rig = bus(helper, "boxend");
		rig.store(p(CABLES + 1, BUS_Z), ModContent.BATTERY_BOX.get(), Direction.WEST, 0L);
		rig.store(p(2, BUS_Z + 1), ModContent.BATTERY_BOX.get(), Direction.NORTH, 0L);
		return rig.driveSampled(TICKS, EVERY);
	}

	/** A Macerator on every bus cable and an empty box at the end of the bus. */
	private static List<String> busAndBox(GameTestHelper helper) {
		EnergyGoldenRig rig = bus(helper, "bus4box");
		for (int x = 1; x <= CABLES; x++) {
			rig.machine(p(x, BUS_Z + 1), ModContent.MACERATOR.get(), Direction.SOUTH, ore());
		}
		rig.store(p(CABLES + 1, BUS_Z), ModContent.BATTERY_BOX.get(), Direction.WEST, 0L);
		return rig.driveSampled(TICKS, EVERY);
	}

	/** A Macerator beside the second bus cable and one at the end: a machine behind a running machine. */
	private static EnergyGoldenRig twoMachines(GameTestHelper helper) {
		EnergyGoldenRig rig = bus(helper, "gap");
		rig.machine(p(2, BUS_Z + 1), ModContent.MACERATOR.get(), Direction.SOUTH, ore());
		rig.machine(p(CABLES + 1, BUS_Z), ModContent.MACERATOR.get(), Direction.EAST, ore());
		return rig;
	}

	/** An empty box beside the second cable, a Macerator beside the fourth, an empty box at the end. */
	private static List<String> storesAroundAMachine(GameTestHelper helper) {
		EnergyGoldenRig rig = bus(helper, "ss");
		rig.store(p(2, BUS_Z + 1), ModContent.BATTERY_BOX.get(), Direction.NORTH, 0L);
		rig.store(p(CABLES + 1, BUS_Z), ModContent.BATTERY_BOX.get(), Direction.WEST, 0L);
		rig.machine(p(4, BUS_Z - 1), ModContent.MACERATOR.get(), Direction.NORTH, ore());
		return rig.driveSampled(TICKS, EVERY);
	}

	/**
	 * Review finding 1: a Macerator beside the second cable and one at the end. The first version of the corridor
	 * stopped at the nearer machine's cable, which waits every tick it works, so the far one got nothing, ever.
	 * Priority is per tick: the nearer machine takes what it can take this tick and the rest goes on. On tick 100
	 * the cable before the far machine carries charge; by tick 300 both run.
	 *
	 * @implements MOD-789-FW05 — a machine behind a running machine on the bus is fed
	 */
	public static void machineBehindARunningMachineIsFed(GameTestHelper helper) {
		EnergyGoldenRig rig = twoMachines(helper);
		steps(rig, 100);
		long before = charge(rig, CABLES, BUS_Z);
		steps(rig, 200);
		long near = rig.amountAt(p(2, BUS_Z + 1));
		long far = rig.amountAt(p(CABLES + 1, BUS_Z));
		EnergyGoldenRig.clear(helper);
		if (before <= 0 || near < 700 || far < 700) {
			helper.fail("cable before the far machine at t100 = " + before + " (> 0), near machine at t300 = " + near
					+ ", far machine = " + far + " (both >= 700)");
			return;
		}
		helper.succeed();
	}

	/**
	 * Review finding R2-1 (revision 2 of ADR-048): a fork, a Macerator beside the east branch with a box at its
	 * end, a Macerator at the end of the four-cable south branch. Revision 2 gave the east branch all its free
	 * room — which the box behind the near machine freed every tick — and the far machine got nothing, ever.
	 * Variant 1: on tick 50 the middle of the south branch carries charge, by tick 300 both machines have worked
	 * as many ticks as on the same fork with no box (within two), and the box has charged on what they left.
	 *
	 * @implements MOD-789-FW06 — the far machine of a fork is fed before a box behind the near one
	 */
	public static void farMachineOfAForkBeatsTheBoxBehindTheNearOne(GameTestHelper helper) {
		int[] control = forkWithBox(helper, false);
		int[] withBox = forkWithBox(helper, true);
		EnergyGoldenRig.clear(helper);
		// Work done, in progress ticks (operations x length + progress), not operations: a whole operation is 150
		// ticks, so "made" alone could not see a machine short for a hundred ticks. A box must change nothing.
		if (withBox[0] <= 0 || Math.abs(withBox[1] - control[1]) > WORK_SLACK
				|| Math.abs(withBox[2] - control[2]) > WORK_SLACK || withBox[3] <= 0) {
			helper.fail("south-branch cable at t50 = " + withBox[0] + " (must carry charge); by t300 the near machine"
					+ " worked " + withBox[1] + " ticks (without the box " + control[1] + "), the far one " + withBox[2]
					+ " (without " + control[2] + "), box = " + withBox[3] + " (> 0)");
			return;
		}
		helper.succeed();
	}

	/** The fork of {@link #farMachineOfAForkBeatsTheBoxBehindTheNearOne}: south-branch charge at t50, made, box. */
	private static int[] forkWithBox(GameTestHelper helper, boolean withBox) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "forkstore");
		rig.source(p(0, FORK_Z), 24);
		rig.run(p(1, FORK_Z), p(CABLES, FORK_Z));
		BlockPos near = p(2, FORK_Z - 1);
		rig.machine(near, ModContent.MACERATOR.get(), Direction.NORTH, ore());
		BlockPos box = p(CABLES + 1, FORK_Z);
		if (withBox) {
			rig.store(box, ModContent.BATTERY_BOX.get(), Direction.WEST, 0L);
		}
		rig.run(p(1, FORK_Z + 1), p(1, FORK_Z + 4));
		BlockPos far = p(1, FORK_Z + 5);
		rig.machine(far, ModContent.MACERATOR.get(), Direction.SOUTH, ore());
		steps(rig, 50);
		int mid = (int) charge(rig, 1, FORK_Z + 2);
		steps(rig, 250);
		return new int[] {mid, worked(helper, near), worked(helper, far), withBox ? (int) rig.amountAt(box) : 0};
	}

	/** The progress ticks the Macerator at {@code pos} has worked: operations x their length + the current one. */
	private static int worked(GameTestHelper helper, BlockPos pos) {
		if (!(EnergyScenarioSupport.be(helper, pos) instanceof MachineBlockEntity m)) {
			return -1;
		}
		return made(helper, pos) * m.getDataAccess().get(MachineChannels.MAX_PROGRESS.ordinal())
				+ m.getDataAccess().get(MachineChannels.PROGRESS.ordinal());
	}

	/** How many operations the Macerator at {@code pos} has finished: the ore it took out of its input stack. */
	private static int made(GameTestHelper helper, BlockPos pos) {
		return EnergyScenarioSupport.be(helper, pos) instanceof MachineBlockEntity m
				? ore().getCount() - m.getItem(MaceratorBlockEntity.INPUT_SLOT).getCount() : -1;
	}

	/** The EU in the cable at {@code (x, z)}, read from its own buffer (ADR-001). */
	private static long charge(EnergyGoldenRig rig, int x, int z) {
		return rig.amountAt(p(x, z));
	}

	private static void steps(EnergyGoldenRig rig, int ticks) {
		for (int t = 0; t < ticks; t++) {
			rig.step();
		}
	}

	/**
	 * A box beside the second or the third bus cable and a Macerator at the end: on tick 50 the cable between the
	 * box and the machine carries charge; by tick 200 the machine has made at least what it makes on the bus with no
	 * box (it got its need every tick), and the box has charged on the rest of the line at once.
	 *
	 * @implements MOD-789-FW02 — a machine behind a store on the bus is fed before the store
	 */
	public static void machineBehindABoxIsFedFirst(GameTestHelper helper) {
		EnergyGoldenRig control = bus(helper, "line");
		BlockPos machine = p(CABLES + 1, BUS_Z);
		control.machine(machine, ModContent.MACERATOR.get(), Direction.EAST, ore());
		steps(control, 200);
		int alone = made(helper, machine);
		for (int x : new int[] {2, 3}) {
			EnergyGoldenRig rig = bus(helper, "box" + x);
			BlockPos box = p(x, BUS_Z + 1);
			rig.machine(machine, ModContent.MACERATOR.get(), Direction.EAST, ore());
			rig.store(box, ModContent.BATTERY_BOX.get(), Direction.NORTH, 0L);
			steps(rig, 50);
			long mid = charge(rig, 3, BUS_Z);
			steps(rig, 150);
			int fed = made(helper, machine);
			long stored = rig.amountAt(box);
			if (mid <= 0 || fed < alone || stored <= 0) {
				EnergyGoldenRig.clear(helper);
				helper.fail("box beside cable " + x + ": cable 3 at t50 = " + mid + " (must carry charge), the machine"
						+ " made " + fed + " by t200 (alone on the bus " + alone + "), box = " + stored + " (> 0)");
				return;
			}
		}
		EnergyGoldenRig.clear(helper);
		helper.succeed();
	}

	/**
	 * The asymmetric fork: an empty box ends the one-cable branch, a Macerator the three-cable branch. On tick 50
	 * the middle cable of the long branch carries charge; by tick 200 the machine has made at least what it makes on
	 * the same fork with no box, and the box has charged on what it left.
	 *
	 * @implements MOD-789-FW03 — a machine at the end of a longer branch is fed before a store on the shorter one
	 */
	public static void longBranchMachineBeatsShortBranchBox(GameTestHelper helper) {
		int[] control = asymmetricFork(helper, false);
		int[] withBox = asymmetricFork(helper, true);
		EnergyGoldenRig.clear(helper);
		if (withBox[0] <= 0 || withBox[1] < control[1] || withBox[2] <= 0) {
			helper.fail("long-branch cable at t50 = " + withBox[0] + " (must carry charge), the machine made "
					+ withBox[1] + " by t200 (without the box " + control[1] + "), box = " + withBox[2] + " (> 0)");
			return;
		}
		helper.succeed();
	}

	/** The asymmetric fork, with or without its box: long-branch charge at t50, what the machine made, the box. */
	private static int[] asymmetricFork(GameTestHelper helper, boolean withBox) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "forkbm");
		rig.source(p(0, BUS_Z), 24);
		rig.run(p(1, BUS_Z), p(3, BUS_Z));
		rig.cable(p(3, BUS_Z - 1));
		rig.run(p(3, BUS_Z + 1), p(3, BUS_Z + 3));
		BlockPos box = p(3, BUS_Z - 2);
		if (withBox) {
			rig.store(box, ModContent.BATTERY_BOX.get(), Direction.SOUTH, 0L);
		}
		BlockPos machine = p(3, BUS_Z + 4);
		rig.machine(machine, ModContent.MACERATOR.get(), Direction.SOUTH, ore());
		steps(rig, 50);
		int mid = (int) charge(rig, 3, BUS_Z + 2);
		steps(rig, 150);
		return new int[] {mid, made(helper, machine), withBox ? (int) rig.amountAt(box) : 0};
	}

	/**
	 * A Macerator on every bus cable: each cable after the first carries charge at some tick of the run — every
	 * machine draws from its own cable, not out of its neighbour's (MOD-755) — and by tick 1000 every machine is
	 * nearly full. Every machine gets its work rate each tick; the buffers fill in turn, nearest first (revision 3
	 * of ADR-048: the 12 EU/t beyond the four rates goes to one buffer after another).
	 *
	 * @implements MOD-755-LC02 — every machine of a bus draws from its own cable, and all of them fill
	 */
	public static void everyBusMachineDrawsFromItsOwnCable(GameTestHelper helper) {
		EnergyGoldenRig rig = bus(helper, "bus4");
		for (int x = 1; x <= CABLES; x++) {
			rig.machine(p(x, BUS_Z + 1), ModContent.MACERATOR.get(), Direction.SOUTH, ore());
		}
		boolean[] carried = new boolean[CABLES + 1];
		int ticks = 1000;
		for (int t = 0; t < ticks; t++) {
			rig.step();
			for (int x = 2; x <= CABLES; x++) {
				carried[x] |= charge(rig, x, BUS_Z) > 0;
			}
		}
		StringBuilder fail = new StringBuilder();
		for (int x = 1; x <= CABLES; x++) {
			long fed = rig.amountAt(p(x, BUS_Z + 1));
			if (fed < 700 || (x >= 2 && !carried[x])) {
				fail.append(" machine ").append(x).append('=').append(fed);
				if (x >= 2) {
					fail.append(" cable carried ").append(carried[x]);
				}
			}
		}
		EnergyGoldenRig.clear(helper);
		if (!fail.isEmpty()) {
			helper.fail("after " + ticks + " ticks every machine must hold >= 700 and every bus cable must have"
					+ " carried charge:" + fail);
			return;
		}
		helper.succeed();
	}

	/**
	 * ADR-003's rejected alternative 2 must stay rejected: the box behind the source charges while the machine in
	 * front of it makes at least what it makes with no box, and the cable beside the box carries charge.
	 *
	 * @implements MOD-789-FW04 — a store on the far side of the source still charges while a machine waits
	 */
	public static void boxBehindTheSourceStillCharges(GameTestHelper helper) {
		EnergyGoldenRig control = behind(helper, false);
		steps(control, 300);
		int alone = made(helper, p(6, BUS_Z));
		EnergyGoldenRig rig = behind(helper);
		steps(rig, 300);
		long box = rig.amountAt(p(0, BUS_Z));
		int fed = made(helper, p(6, BUS_Z));
		long toward = charge(rig, 1, BUS_Z);
		EnergyGoldenRig.clear(helper);
		if (box <= 0 || fed < alone || toward <= 0) {
			helper.fail("t300: box behind the source = " + box + " (> 0), the machine made " + fed + " (without the"
					+ " box " + alone + "), cable beside the box = " + toward + " (> 0)");
			return;
		}
		helper.succeed();
	}

	/**
	 * Every circuit in turn, sampled to tick {@link #TICKS}, compared with the golden.
	 *
	 * @implements MOD-789-FW01 — the flow field: per-window machine delivery and store charge on the bus,
	 *     box-beside, asymmetric-fork, behind-the-source, store-only and bus-and-box circuits are unchanged
	 */
	public static void circuitsMatchGolden(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		lines.addAll(line(helper));
		lines.addAll(machinePerCable(helper));
		lines.addAll(boxBeside(helper, 2));
		lines.addAll(boxBeside(helper, 3));
		lines.addAll(fork(helper, true));
		lines.addAll(fork(helper, false));
		lines.addAll(behind(helper).driveSampled(TICKS, EVERY));
		lines.addAll(boxBeside(helper, CABLES));
		lines.addAll(storesOnly(helper));
		lines.addAll(busAndBox(helper));
		lines.addAll(twoMachines(helper).driveSampled(TICKS, EVERY));
		lines.addAll(storesAroundAMachine(helper));
		EnergyGoldenRig.clear(helper);
		GoldenLines.check(helper, "FlowWallGolden", WRITE_TO_PROPERTY, "*:mod789_flow_wall_golden",
				FlowWallGolden.LINES, lines);
	}
}
