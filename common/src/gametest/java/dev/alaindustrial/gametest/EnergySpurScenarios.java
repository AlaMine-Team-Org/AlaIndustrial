package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * MOD-730: a dead-end spur off a cable in the middle of a running bus. A creative source at 20 EU/t, four
 * copper cables along X to a Macerator, and three cables off the second one along Z — the golden
 * {@code spur} circuit. Before the fix the spur took the packet the junction was carrying to the machine,
 * handed it back in the next sweep ahead of the bus, and the bus stood every other tick: the machine got
 * 12 EU every second tick (336 EU over ticks 1–60 against 672 without the spur) and the spur never filled.
 * Turning the build 90° hid it, which is why both spur directions along Z are run.
 *
 * <p>Asserted as rules against the same line without a spur, never as numbers — the numbers are the
 * golden's ({@code EnergyFlowFieldGoldenScenarios}). The last scenario records MOD-754: a spur off the
 * source's cable or off the machine's cable fills only once the machine stops. Every bound comes from the
 * rig: a full cable is the copper segment buffer. The rig is ticked directly ({@link EnergyGoldenRig}),
 * sits inside the 8×8×8 structure and is translation-invariant; no knob is changed.
 */
public final class EnergySpurScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(EnergySpurScenarios::spurDoesNotHalveTheMachine,
						"mod730_spur_does_not_halve_the_machine"),
				RosterEntry.of(EnergySpurScenarios::spurFillsFromSurplusOnceTheMachineIsFull,
						"mod730_spur_fills_from_surplus_once_the_machine_is_full"),
				RosterEntry.of(EnergySpurScenarios::endSpursStayEmptyWhileTheMachineWorks,
						"mod754_end_spurs_stay_empty_while_the_machine_works"));

		private Roster() {}
	}

	/** Which way the spur leaves the second cable of the bus, if at all. */
	private enum Spur {
		NONE(0), SOUTH(1), NORTH(-1);

		final int dz;

		Spur(int dz) {
			this.dz = dz;
		}
	}

	/** The bus runs along X at this Z, so a three-cable spur fits on either side inside the structure. */
	private static final int BUS_Z = 4;
	private static final BlockPos SOURCE = p(0, BUS_Z);
	private static final List<BlockPos> BUS = List.of(p(1, BUS_Z), p(2, BUS_Z), p(3, BUS_Z), p(4, BUS_Z));
	private static final BlockPos MACHINE = p(5, BUS_Z);
	private static final int SPUR_LENGTH = 3;
	private static final int SOURCE_OUTPUT = 20;
	/** The window of the delivery comparison: long enough for the front to arrive and the halving to show. */
	private static final int WINDOW = 60;
	/** Inside the window, after the front arrived: where every bus cable must be carrying a packet. */
	private static final int CHECK_TICK = 40;
	/** Long enough for the Macerator (800 EU, net +10 EU/t) to fill and the surplus to fill the spur. */
	private static final int LONG_RUN = 200;
	/** How many final ticks the full spur must hold still — no packet pumped back and forth. */
	private static final int STILL_TICKS = 20;
	/** MOD-754: the working run — the Macerator fills (net +10 EU/t into 800 EU) and keeps working. */
	private static final int WORK_RUN = 400;
	/** MOD-754: ticks after the work runs out, enough to finish the operation, top the buffer up and fill. */
	private static final int IDLE_RUN = 400;

	private EnergySpurScenarios() {
	}

	private static BlockPos p(int x, int z) {
		return new BlockPos(x, 2, z);
	}

	/** A full copper cable: its segment buffer (ADR-001). */
	private static long fullCable() {
		return CableType.COPPER.segmentBuffer();
	}

	private static List<BlockPos> spurCables(Spur spur) {
		return spurCables(spur, 2);
	}

	/** The spur's cables off the bus cable at {@code x}. */
	private static List<BlockPos> spurCables(Spur spur, int x) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 1; i <= SPUR_LENGTH && spur != Spur.NONE; i++) {
			out.add(p(x, BUS_Z + spur.dz * i));
		}
		return out;
	}

	/** The golden {@code spur} circuit, built at {@link #BUS_Z} with the spur on {@code spur}'s side. */
	private static EnergyGoldenRig build(GameTestHelper helper, Spur spur) {
		return build(helper, spur, 2);
	}

	/** The same circuit with the spur off the bus cable at {@code x} (1 — the source's, 4 — the machine's). */
	private static EnergyGoldenRig build(GameTestHelper helper, Spur spur, int x) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "mod730");
		rig.source(SOURCE, SOURCE_OUTPUT);
		rig.run(BUS.get(0), BUS.get(BUS.size() - 1));
		List<BlockPos> spurCables = spurCables(spur, x);
		if (!spurCables.isEmpty()) {
			rig.run(spurCables.get(0), spurCables.get(spurCables.size() - 1));
		}
		rig.machine(MACHINE, ModContent.MACERATOR.get(), Direction.EAST, new ItemStack(Items.RAW_IRON, 16));
		return rig;
	}

	/** The EU in the cable at {@code pos}, read from the cable's own buffer (ADR-001). */
	private static long cableCharge(GameTestHelper helper, BlockPos pos) {
		return be(helper, pos) instanceof CableBlockEntity cable ? cable.getEnergyStorage().getAmount() : -1L;
	}

	private static long[] charges(GameTestHelper helper, List<BlockPos> cables) {
		return cables.stream().mapToLong(pos -> cableCharge(helper, pos)).toArray();
	}

	/** EU the network moved into consumers on the tick just stepped. */
	private static long moved(EnergyGoldenRig rig) {
		EnergyNetwork net = rig.network();
		return net == null ? 0L : net.lastTickMoved();
	}

	/** Room the Macerator offers the line on the coming tick. */
	private static long machineRoom(GameTestHelper helper) {
		return be(helper, MACHINE) instanceof EnergyBlockEntity e
				? e.getEnergyStorage().getCapacity() - e.getEnergyStorage().getAmount() : -1L;
	}

	/**
	 * Drive {@code rig} for {@link #WINDOW} ticks; return the EU delivered, or fail with the broken rule. With a
	 * spur: on {@link #CHECK_TICK} every bus cable holds a full packet and the spur is empty, and from the tick
	 * the front reaches the machine no tick passes without a delivery.
	 */
	private static long deliverOverWindow(GameTestHelper helper, EnergyGoldenRig rig, Spur spur, StringBuilder fail) {
		long total = 0;
		boolean arrived = false;
		for (int t = 1; t <= WINDOW; t++) {
			rig.step();
			long moved = moved(rig);
			total += moved;
			if (spur == Spur.NONE) {
				continue;
			}
			if (moved > 0) {
				arrived = true;
			} else if (arrived && fail.isEmpty()) {
				fail.append("spur ").append(spur).append(", tick ").append(t)
						.append(": the machine got nothing after the front had reached it — the bus stalled");
			}
			if (t == CHECK_TICK && fail.isEmpty()) {
				long[] bus = charges(helper, BUS);
				long[] side = charges(helper, spurCables(spur));
				if (Arrays.stream(bus).anyMatch(c -> c != fullCable()) || Arrays.stream(side).anyMatch(c -> c != 0)) {
					fail.append("spur ").append(spur).append(", tick ").append(t).append(": bus ")
							.append(Arrays.toString(bus)).append(" (every cable must carry ").append(fullCable())
							.append("), spur ").append(Arrays.toString(side))
							.append(" (empty: a hungry machine leaves no surplus)");
				}
			}
		}
		return total;
	}

	/**
	 * The same bus with and without a spur on the second cable: the spur costs the machine nothing.
	 *
	 * @implements TC-CABLE-001-CON19 — a dead-end spur off a cable of a running bus does not slow the machine
	 */
	public static void spurDoesNotHalveTheMachine(GameTestHelper helper) {
		StringBuilder fail = new StringBuilder();
		long without = deliverOverWindow(helper, build(helper, Spur.NONE), Spur.NONE, fail);
		for (Spur spur : List.of(Spur.SOUTH, Spur.NORTH)) {
			long with = deliverOverWindow(helper, build(helper, spur), spur, fail);
			if (!fail.isEmpty() || with < without) {
				helper.fail((fail.isEmpty() ? "spur " + spur : fail + ";") + " the machine got " + with
						+ " EU over ticks 1-" + WINDOW + ", the same bus without the spur " + without + " EU");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Once the machine is full and only takes its work, the bus has surplus, and the spur fills from it —
	 * only from it: while the machine is hungry the spur stays empty. A full spur then holds still.
	 *
	 * @implements TC-CABLE-001-CON20 — a dead-end spur fills from the surplus once the machine is full
	 */
	public static void spurFillsFromSurplusOnceTheMachineIsFull(GameTestHelper helper) {
		for (Spur spur : List.of(Spur.SOUTH, Spur.NORTH)) {
			EnergyGoldenRig rig = build(helper, spur);
			List<BlockPos> spurCables = spurCables(spur);
			long[] before = null;
			int still = 0;
			int fedAtTheEnd = 0;
			for (int t = 1; t <= LONG_RUN; t++) {
				boolean hungry = machineRoom(helper) >= fullCable();
				rig.step();
				long[] side = charges(helper, spurCables);
				if (hungry && Arrays.stream(side).anyMatch(c -> c != 0)) {
					helper.fail("spur " + spur + ", tick " + t + ": the spur took " + Arrays.toString(side)
							+ " while the machine still had room for a full packet — that was delivery, not surplus");
					return;
				}
				still = Arrays.equals(side, before) ? still + 1 : 0;
				before = side;
				if (t > LONG_RUN - STILL_TICKS && moved(rig) > 0) {
					fedAtTheEnd++;
				}
			}
			if (Arrays.stream(before).anyMatch(c -> c != fullCable())) {
				helper.fail("spur " + spur + ": after " + LONG_RUN + " ticks the spur holds " + Arrays.toString(before)
						+ "; with the machine full the surplus must fill every cable to " + fullCable());
				return;
			}
			if (still < STILL_TICKS) {
				helper.fail("spur " + spur + ": the full spur changed within the last " + STILL_TICKS
						+ " ticks (still for " + still + ") — energy pumped back and forth");
				return;
			}
			if (fedAtTheEnd < STILL_TICKS) {
				helper.fail("spur " + spur + ": the full machine was fed on " + fedAtTheEnd + " of the last "
						+ STILL_TICKS + " ticks; it must get its work every tick");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * MOD-754, the promise as it stands (owner, 2026-10-08): a spur off the cable at the source and a spur off
	 * the last cable before the machine have no surplus to fill from while the machine waits for energy — the
	 * source's cable has already passed its packet down when the dead-end fill runs, and the machine's cable sits
	 * at the field's minimum, which the MOD-419 guard keeps untouched. Both stay empty for the whole working run
	 * (the full machine still takes its work every tick, so it keeps waiting). Once the machine has nothing to
	 * process and its buffer is full, nobody waits, the line floods from the source, and both spurs fill.
	 *
	 * <p>Pins today's behaviour, not a wish: the terminal half is the flow field's wall (MOD-789), and a model
	 * change there rewrites this scenario.
	 *
	 * @implements TC-CABLE-001-CON21 — spurs off the source's cable and off the machine's cable stay empty while
	 *     the machine works and fill once it stops
	 */
	public static void endSpursStayEmptyWhileTheMachineWorks(GameTestHelper helper) {
		for (int x : List.of(1, BUS.size())) {
			for (Spur spur : List.of(Spur.SOUTH, Spur.NORTH)) {
				EnergyGoldenRig rig = build(helper, spur, x);
				List<BlockPos> spurCables = spurCables(spur, x);
				for (int t = 1; t <= WORK_RUN; t++) {
					rig.step();
					long[] side = charges(helper, spurCables);
					if (Arrays.stream(side).anyMatch(c -> c != 0)) {
						helper.fail("spur off bus cable " + x + " " + spur + ", tick " + t + ": "
								+ Arrays.toString(side) + " while the machine works — MOD-754 records it empty");
						return;
					}
				}
				if (be(helper, MACHINE) instanceof MachineBlockEntity machine) {
					machine.setItem(0, ItemStack.EMPTY);
				}
				long[] side = null;
				for (int t = 1; t <= IDLE_RUN; t++) {
					rig.step();
					side = charges(helper, spurCables);
				}
				if (Arrays.stream(side).anyMatch(c -> c != fullCable())) {
					helper.fail("spur off bus cable " + x + " " + spur + ": " + IDLE_RUN + " ticks after the machine"
							+ " stopped, the spur holds " + Arrays.toString(side) + "; nobody waits, so it must fill");
					return;
				}
			}
		}
		EnergyGoldenRig.clear(helper);
		helper.succeed();
	}
}
