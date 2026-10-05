package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidTank;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * L2 tick-by-tick traces of the four machines whose cycle is not {@code ProcessingCycle} (MOD-712,
 * batch BE-3): the thermal centrifuge and the distillation column ("pre-stage first": spin-up, warm-up)
 * and the incubator and the assembler ("completion may refuse").
 *
 * <p>Each machine runs three cases from a fresh rig with today's {@code Config}: a funded run until one
 * operation completes; a run whose supply is cut to {@link #CUT_EU} EU once the operation is half done;
 * and a run whose output is full from the start. Every tick records what the buffer paid and how each
 * sync channel from index 2 on (progress, length, then the machine's own: spin, heat, status, …) and the
 * completed-operation counter moved. Identical consecutive ticks are folded into one line, so a phase —
 * forty working ticks, a four-hundred-tick spin-up — reads as one line with its length. The trace is
 * compared with a reviewed reference per machine: moving the shared completion steps into the base
 * (BE-3) must leave every one of them alone, down to the tick an operation finishes on.
 *
 * <p>The rigs are {@link OperationEnergyScenarios}'s, owned by a player without Mechanic skills.
 */
public final class MachineCycleTraceScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MachineCycleTraceScenarios::assemblerTraceMatchesReference,
								"machine_char_cycle_trace_assembler").ticks(100),
				RosterEntry.of(MachineCycleTraceScenarios::incubatorTraceMatchesReference,
								"machine_char_cycle_trace_incubator").ticks(100),
				RosterEntry.of(MachineCycleTraceScenarios::distillationColumnTraceMatchesReference,
								"machine_char_cycle_trace_distillation_column").ticks(100),
				RosterEntry.of(MachineCycleTraceScenarios::thermalCentrifugeTraceMatchesReference,
								"machine_char_cycle_trace_thermal_centrifuge").ticks(100));

		private Roster() {}
	}

	private MachineCycleTraceScenarios() {}

	/** The three rigs of one machine, far enough apart that none touches another. */
	private static final BlockPos FUNDED = new BlockPos(1, 2, 1);
	private static final BlockPos CUT = new BlockPos(1, 2, 4);
	private static final BlockPos BLOCKED = new BlockPos(4, 2, 1);

	/** What the buffer is left with when the supply is cut halfway: less than any of the four's tick. */
	private static final long CUT_EU = 1;

	/** Ticks a cut or blocked run goes on after the cut (or for its whole length when blocked). */
	private static final int AFTER_CUT_TICKS = 40;

	/**
	 * How the buffer is fed through a run. A funded run goes on three ticks past its first completed
	 * operation, to show the next one starting — except the incubator's: what it does after an attempt
	 * depends on the dice the attempt rolled (a graded result may not stack with the next), so its funded run
	 * stops on the completing tick ({@link #FULL_UNTIL_DONE}) and stays the same on every run.
	 */
	private enum Funding { FULL, FULL_UNTIL_DONE, CUT_HALFWAY }

	/**
	 * @implements MOD-712-CH05 — the assembler's planning, working and finishing ticks match the reviewed
	 *     trace, funded, cut halfway and with the output full.
	 */
	public static void assemblerTraceMatchesReference(GameTestHelper helper) {
		ServerPlayer owner = OperationEnergyScenarios.mechanic(helper);
		List<String> lines = new ArrayList<>();
		int max = 120;
		trace("funded", OperationEnergyScenarios.assembler(helper, FUNDED, owner), helper, () -> { },
				Funding.FULL, max, lines);
		trace("cut", OperationEnergyScenarios.assembler(helper, CUT, owner), helper, () -> { },
				Funding.CUT_HALFWAY, max, lines);
		AssemblerBlockEntity blocked = OperationEnergyScenarios.assembler(helper, BLOCKED, owner);
		for (int slot = AssemblerBlockEntity.OUTPUT_SLOT_START; slot < AssemblerBlockEntity.OUTPUT_SLOT_END; slot++) {
			blocked.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		trace("output-full", blocked, helper, () -> { }, Funding.FULL, max, lines);
		compare(helper, "MachineCycleTraceAssembler", "assembler", lines, MachineCycleTraceAssembler.LINES);
	}

	/**
	 * @implements MOD-712-CH06 — the incubator's attempt matches the reviewed trace, funded, cut halfway
	 *     and with the output full.
	 */
	public static void incubatorTraceMatchesReference(GameTestHelper helper) {
		ServerPlayer owner = OperationEnergyScenarios.mechanic(helper);
		List<String> lines = new ArrayList<>();
		int max = 400;
		trace("funded", OperationEnergyScenarios.incubator(helper, FUNDED, owner), helper, () -> { },
				Funding.FULL_UNTIL_DONE, max, lines);
		trace("cut", OperationEnergyScenarios.incubator(helper, CUT, owner), helper, () -> { },
				Funding.CUT_HALFWAY, max, lines);
		IncubatorBlockEntity blocked = OperationEnergyScenarios.incubator(helper, BLOCKED, owner);
		blocked.setItem(IncubatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.COBBLESTONE, 64));
		trace("output-full", blocked, helper, () -> { }, Funding.FULL, max, lines);
		compare(helper, "MachineCycleTraceIncubator", "incubator", lines, MachineCycleTraceIncubator.LINES);
	}

	/**
	 * @implements MOD-712-CH07 — the distillation column's warm-up and run match the reviewed trace,
	 *     funded, cut halfway and with both product tanks full.
	 */
	public static void distillationColumnTraceMatchesReference(GameTestHelper helper) {
		ServerPlayer owner = OperationEnergyScenarios.mechanic(helper);
		List<String> lines = new ArrayList<>();
		int max = 500;
		trace("funded", OperationEnergyScenarios.column(helper, FUNDED, owner), helper, () -> { },
				Funding.FULL, max, lines);
		trace("cut", OperationEnergyScenarios.column(helper, CUT, owner), helper, () -> { },
				Funding.CUT_HALFWAY, max, lines);
		DistillationColumnBlockEntity blocked = OperationEnergyScenarios.column(helper, BLOCKED, owner);
		fill(blocked.dieselTank);
		fill(blocked.fuelOilTank);
		trace("output-full", blocked, helper, () -> { }, Funding.FULL, max, lines);
		compare(helper, "MachineCycleTraceDistillationColumn", "distillation_column", lines,
				MachineCycleTraceDistillationColumn.LINES);
	}

	/**
	 * @implements MOD-712-CH08 — the thermal centrifuge's spin-up and run match the reviewed trace,
	 *     funded, cut halfway and with the output full.
	 */
	public static void thermalCentrifugeTraceMatchesReference(GameTestHelper helper) {
		ServerPlayer owner = OperationEnergyScenarios.mechanic(helper);
		List<String> lines = new ArrayList<>();
		int max = 700;
		OperationEnergyScenarios.Centrifuge funded = OperationEnergyScenarios.centrifuge(helper, FUNDED, owner);
		trace("funded", funded.be(), helper, funded::topUp, Funding.FULL, max, lines);
		OperationEnergyScenarios.Centrifuge cut = OperationEnergyScenarios.centrifuge(helper, CUT, owner);
		trace("cut", cut.be(), helper, cut::topUp, Funding.CUT_HALFWAY, max, lines);
		OperationEnergyScenarios.Centrifuge blocked = OperationEnergyScenarios.centrifuge(helper, BLOCKED, owner);
		blocked.be().setItem(ThermalCentrifugeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.COBBLESTONE, 64));
		trace("output-full", blocked.be(), helper, blocked::topUp, Funding.FULL, max, lines);
		compare(helper, "MachineCycleTraceThermalCentrifuge", "thermal_centrifuge", lines,
				MachineCycleTraceThermalCentrifuge.LINES);
	}

	private static void fill(FluidTank tank) {
		tank.fluid = FluidHolder.of(Fluids.WATER);
		tank.amount = tank.capacity;
	}

	private static void compare(GameTestHelper helper, String reference, String machine, List<String> lines,
			List<String> expected) {
		ReferenceLines.compare(helper, reference,
				"Reviewed tick trace of the " + machine + "'s cycle (MOD-712, BE-3) — the reference"
						+ " {@link MachineCycleTraceScenarios} compares against.",
				"*:machine_char_cycle_trace_" + machine + "*", lines, expected);
	}

	/**
	 * Drive {@code be} one tick at a time and append its folded trace to {@code lines}. A funded run stops
	 * after its first completed operation (see {@link Funding}), a cut one {@link #AFTER_CUT_TICKS} ticks after the
	 * cut; every run stops at {@code maxTicks}.
	 */
	private static void trace(String label, MachineBlockEntity be, GameTestHelper helper, Runnable topUp,
			Funding funding, int maxTicks, List<String> lines) {
		EnergyBuffer energy = be.getEnergyStorage();
		ContainerData data = be.getDataAccess();
		int[] previous = observe(be, data);
		lines.add(label + " t0: " + absolute(previous));
		List<String> records = new ArrayList<>();
		boolean cut = false;
		int stopAt = maxTicks;
		for (int tick = 1; tick <= stopAt; tick++) {
			topUp.run();
			String mark = "";
			if (!cut) {
				energy.setAmountUntracked(energy.getCapacity());
				if (funding == Funding.CUT_HALFWAY && data.get(3) > 0 && data.get(2) * 2 >= data.get(3)) {
					energy.setAmountUntracked(CUT_EU);
					cut = true;
					stopAt = Math.min(maxTicks, tick + AFTER_CUT_TICKS);
					mark = "CUT to " + CUT_EU + " EU, ";
				}
			}
			long before = energy.getAmount();
			drive(be, helper, 1);
			int[] now = observe(be, data);
			records.add(mark + "spent=" + (before - energy.getAmount()) + delta(previous, now));
			if (funding != Funding.CUT_HALFWAY && now[now.length - 1] != previous[previous.length - 1]
					&& stopAt == maxTicks) {
				stopAt = Math.min(maxTicks, funding == Funding.FULL ? tick + 3 : tick);
			}
			previous = now;
		}
		fold(label, records, lines);
	}

	/** Channels 2.. of the bridge, then the completed-operation counter. */
	private static int[] observe(MachineBlockEntity be, ContainerData data) {
		int[] values = new int[data.getCount() - 1];
		for (int i = 2; i < data.getCount(); i++) {
			values[i - 2] = data.get(i);
		}
		values[values.length - 1] = (int) be.totalItemsProcessed();
		return values;
	}

	private static String absolute(int[] values) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < values.length - 1; i++) {
			out.append("c").append(i + 2).append('=').append(values[i]).append(' ');
		}
		return out.append("done=").append(values[values.length - 1]).toString();
	}

	private static String delta(int[] from, int[] to) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < to.length; i++) {
			int d = to[i] - from[i];
			if (d != 0) {
				out.append(' ').append(i == to.length - 1 ? "done" : "c" + (i + 2)).append(d > 0 ? "+" : "").append(d);
			}
		}
		return out.toString();
	}

	/** Longest repeating pattern {@link #fold} looks for: a spin permille alternating +2/+3 is period 2. */
	private static final int MAX_PERIOD = 4;

	/**
	 * Append {@code records} (record {@code i} is tick {@code i + 1}) folded: a run of one record repeated
	 * becomes {@code label tA-tB (n): record}, a run of a short pattern repeated becomes
	 * {@code label tA-tB (k x p): r1 / r2 …}, whichever covers the most ticks from where it starts.
	 */
	private static void fold(String label, List<String> records, List<String> lines) {
		int i = 0;
		while (i < records.size()) {
			int bestPeriod = 1;
			int bestRepeats = repeats(records, i, 1);
			for (int period = 2; period <= MAX_PERIOD; period++) {
				int k = repeats(records, i, period);
				if (k >= 2 && k * period > bestRepeats * bestPeriod) {
					bestPeriod = period;
					bestRepeats = k;
				}
			}
			int span = bestRepeats * bestPeriod;
			int from = i + 1;
			int to = i + span;
			String range = to > from ? "t" + from + "-" + to : "t" + from;
			String count = bestPeriod == 1 ? (span > 1 ? " (" + span + ")" : "")
					: " (" + bestRepeats + " x " + bestPeriod + ")";
			lines.add(label + " " + range + count + ": " + String.join(" / ", records.subList(i, i + bestPeriod)));
			i += span;
		}
	}

	/** How many times the {@code period} records starting at {@code from} repeat back to back. */
	private static int repeats(List<String> records, int from, int period) {
		if (from + period > records.size()) {
			return from < records.size() ? 1 : 0;
		}
		int k = 1;
		while (from + (k + 1) * period <= records.size()
				&& records.subList(from + k * period, from + (k + 1) * period)
						.equals(records.subList(from, from + period))) {
			k++;
		}
		return k;
	}
}
