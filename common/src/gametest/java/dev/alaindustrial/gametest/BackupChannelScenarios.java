package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.StorageFeedShare;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * MOD-756: the backup channel ("reserve for machines", ADR-004) feeds the machines only. A creative source at
 * 4 EU/t on the first of four copper cables, a Macerator with a full stack of ore at the end, a Battery Box whose
 * OUT face touches the first cable, and a Teleporter on the line — on a branch off the second cable, or against
 * the third or the fourth one. The source falls short of the machine while its buffer fills, so the box opens
 * backup; before the fix the Teleporter stayed in the serve pass and in the seeds of the flow field on those
 * ticks, took its share of what the box released, and where it sat closer to the source than the machine it
 * walled the machine off for good: the box drained into the fund below its own reserve and the machine got
 * nothing. Now a backup tick serves the machines alone — no store or fund is served or seeded on it.
 *
 * <p>Two scenarios: the circuits' numbers as a golden ({@link BackupChannelGolden}, rewritten only by the command
 * in its javadoc, ADR-032) and the rule. Every rig sits inside the 8×8×8 structure, is translation-invariant and
 * ticked directly ({@link EnergyGoldenRig}); no knob is changed.
 */
public final class BackupChannelScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(BackupChannelScenarios::circuitsMatchGolden, "mod756_backup_channel_golden"),
				RosterEntry.of(BackupChannelScenarios::backupTickFeedsOnlyTheMachine,
						"mod756_backup_tick_feeds_only_the_machine"),
				RosterEntry.of(BackupChannelScenarios::fundKeepsGeneratorSurplusOnABackupTick,
						"mod756_fund_keeps_generator_surplus_on_a_backup_tick"));

		private Roster() {}
	}

	/** System property naming the file the explicit update command writes the golden into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.backupChannelGolden.writeTo";

	/** Where the Teleporter sits on the line. */
	private enum Fund {
		/** At the end of a two-cable branch off the second bus cable. */
		BRANCH2_C2("br2c2"),
		/** At the end of a one-cable branch off the second bus cable. */
		BRANCH1_C2("br1c2"),
		/** Against the third bus cable. */
		AT_C3("at3"),
		/** Against the fourth bus cable — the one the machine draws from. */
		AT_C4("at4");

		final String code;

		Fund(String code) {
			this.code = code;
		}
	}

	private static final int BUS_Z = 3;
	private static final BlockPos SOURCE = p(0, BUS_Z);
	private static final BlockPos MACHINE = p(5, BUS_Z);
	/** South of the first bus cable, its OUT face (the back) to the north. */
	private static final BlockPos BOX = p(1, BUS_Z + 1);
	private static final int CABLES = 4;
	private static final int SOURCE_OUTPUT = 4;
	/** Golden: long enough for a full box to fall below the feed's reserve where the fund drains it. */
	private static final int GOLDEN_TICKS = 4000;
	private static final int GOLDEN_EVERY = 100;
	/** The surplus scenario's run. */
	private static final int TICKS = 1200;
	/** The rule's run: as long as the golden, so a slow trickle cannot hide in a short one. */
	private static final int RULE_TICKS = 4000;

	private BackupChannelScenarios() {
	}

	private static BlockPos p(int x, int z) {
		return new BlockPos(x, 2, z);
	}

	/** The circuit with the Teleporter at {@code fund} and the box holding {@code box} EU. */
	private static EnergyGoldenRig build(GameTestHelper helper, Fund fund, long box) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, fund.code);
		rig.source(SOURCE, SOURCE_OUTPUT);
		rig.run(p(1, BUS_Z), p(CABLES, BUS_Z));
		if (fund == Fund.BRANCH2_C2) {
			rig.run(p(2, BUS_Z - 1), p(2, BUS_Z - 2));
		} else if (fund == Fund.BRANCH1_C2) {
			rig.cable(p(2, BUS_Z - 1));
		}
		rig.machine(MACHINE, ModContent.MACERATOR.get(), Direction.EAST, new ItemStack(Items.RAW_IRON, 64));
		rig.store(BOX, ModContent.BATTERY_BOX.get(), Direction.SOUTH, box);
		rig.store(fundAt(fund), ModContent.TELEPORTER.get(), Direction.NORTH, 0L);
		return rig;
	}

	/** Where the Teleporter of {@code fund} stands, facing away from the cable it touches. */
	private static BlockPos fundAt(Fund fund) {
		return switch (fund) {
			case BRANCH2_C2 -> p(2, BUS_Z - 3);
			case BRANCH1_C2 -> p(2, BUS_Z - 2);
			case AT_C3 -> p(3, BUS_Z - 1);
			case AT_C4 -> p(4, BUS_Z - 1);
		};
	}

	/**
	 * Every circuit with a full box, sampled to tick {@link #GOLDEN_TICKS}, compared with the golden.
	 *
	 * @implements MOD-756-BC01 — the backup channel's circuits keep their per-window box, fund and machine numbers
	 */
	public static void circuitsMatchGolden(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		for (Fund fund : Fund.values()) {
			lines.addAll(build(helper, fund, Config.batteryBoxBuffer).driveSampled(GOLDEN_TICKS, GOLDEN_EVERY));
		}
		EnergyGoldenRig.clear(helper);
		GoldenLines.check(helper, "BackupChannelGolden", WRITE_TO_PROPERTY, "*:mod756_backup_channel_golden",
				BackupChannelGolden.LINES, lines);
	}

	/**
	 * The rule, on every circuit, as two invariants that do not read the code's own budget (second review,
	 * 2026-10-08). From a box below the feed's reserve — whatever it releases is the backup channel's — the
	 * Teleporter gains over the whole run no more than the source produced beyond what the machine got: the
	 * box's charge is the machine's, and only the generator's surplus may reach a fund. From a full box, once
	 * the box has come down to the reserve (the feed is legal above it), it stays there to within one machine
	 * buffer — the most the machine may legitimately have drawn out of it as backup. The machine gets at least
	 * its work, and the network never sleeps.
	 *
	 * @implements MOD-756-BC02 — on a line with a fund, a box's backup reaches the machines only
	 */
	public static void backupTickFeedsOnlyTheMachine(GameTestHelper helper) {
		long reserve = StorageFeedShare.reserveFloor(Config.batteryBoxBuffer, Config.storageFeedReserveFraction);
		long work = (RULE_TICKS - 2L * CABLES) * Config.machineEuPerTick;
		List<String> broken = new ArrayList<>();
		for (Fund fund : Fund.values()) {
			EnergyGoldenRig rig = build(helper, fund, reserve - 1000);
			BlockPos at = fundAt(fund);
			long toMachine = 0;
			int asleep = 0;
			for (int t = 1; t <= RULE_TICKS; t++) {
				asleep += rig.step() ? 0 : 1;
				EnergyNetwork net = rig.network();
				toMachine += net == null ? 0 : net.lastTickMoved() - net.lastTickToStorage();
			}
			long produced = (long) SOURCE_OUTPUT * RULE_TICKS;
			String state = fund.code + " (box " + (reserve - 1000) + " -> " + rig.amountAt(BOX) + "): Teleporter "
					+ rig.amountAt(at) + ", machine got " + toMachine + " of " + produced + " produced";
			if (asleep > 0) {
				broken.add(state + " — the network slept on " + asleep + " ticks");
			} else if (rig.amountAt(at) > produced - toMachine) {
				broken.add(state + " — the Teleporter took " + (rig.amountAt(at) - (produced - toMachine))
						+ " EU more than the source's surplus");
			} else if (toMachine < work) {
				broken.add(state + " — under its work of " + work + " EU");
			}

			rig = build(helper, fund, Config.batteryBoxBuffer);
			long lowest = Long.MAX_VALUE;
			boolean reached = false;
			for (int t = 1; t <= RULE_TICKS; t++) {
				rig.step();
				long box = rig.amountAt(BOX);
				reached |= box <= reserve;
				lowest = reached ? Math.min(lowest, box) : lowest;
			}
			long floor = reserve - machineBuffer(helper);
			if (reached && lowest < floor) {
				broken.add(fund.code + " (full box): once at the reserve " + reserve + " the box fell to " + lowest
						+ ", under " + floor + "; Teleporter " + rig.amountAt(at));
			}
		}
		EnergyGoldenRig.clear(helper);
		if (!broken.isEmpty()) {
			helper.fail("a box's backup reached the fund: " + String.join("; ", broken));
			return;
		}
		helper.succeed();
	}

	/** The capacity of the rig's machine buffer, read from the block. */
	private static long machineBuffer(GameTestHelper helper) {
		return EnergyScenarioSupport.be(helper, MACHINE) instanceof EnergyBlockEntity e
				? e.getEnergyStorage().getCapacity() : 0L;
	}

	/**
	 * The surplus rig's generator: three cables of one network around it — east toward the machine, west to the
	 * fund, and south, a loop joining the two.
	 */
	private static final BlockPos SURPLUS_SOURCE = p(3, BUS_Z);
	private static final BlockPos SURPLUS_MACHINE = p(6, BUS_Z);
	private static final BlockPos SURPLUS_FUND = p(1, BUS_Z);
	/** Above the copper segment the machine's one cable carries, so the generator always has a surplus. */
	private static final int SURPLUS_OUTPUT = 24;
	/** What the machine's network behind it takes every tick: its buffer never fills, so backup never closes. */
	private static final long HUNGRY = 32;

	/**
	 * The review's counter-case (2026-10-08): backup opens on the machines' free room, not on what the line can
	 * carry to them. A 24 EU/t source among three cables of one network — east to a Macerator whose buffer the
	 * test empties every tick (a hungrier machine network behind it), west to a Teleporter, south a loop joining
	 * them — and a Battery Box whose OUT face touches the east cable. The machine's room stays at its intake,
	 * above the source, so every tick is a backup tick; its one copper cable carries a segment a tick, so the
	 * source has the rest to spare. The Teleporter takes that surplus on backup ticks too: at least the source's
	 * output beyond the segment for every tick past the fill. The first cut of MOD-756 gave it 0 EU here.
	 *
	 * @implements MOD-756-BC03 — on a backup tick the sinks still take the generators' surplus, apart
	 */
	public static void fundKeepsGeneratorSurplusOnABackupTick(GameTestHelper helper) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "surplus");
		rig.source(SURPLUS_SOURCE, SURPLUS_OUTPUT);
		rig.run(p(4, BUS_Z), p(5, BUS_Z));
		rig.cable(p(2, BUS_Z));
		rig.run(p(2, BUS_Z + 1), p(4, BUS_Z + 1));
		rig.machine(SURPLUS_MACHINE, ModContent.MACERATOR.get(), Direction.EAST, new ItemStack(Items.RAW_IRON, 64));
		rig.store(p(4, BUS_Z - 1), ModContent.BATTERY_BOX.get(), Direction.NORTH, Config.batteryBoxBuffer);
		rig.store(SURPLUS_FUND, ModContent.TELEPORTER.get(), Direction.WEST, 0L);
		long released = 0;
		int backupTicks = 0;
		for (int t = 1; t <= TICKS; t++) {
			rig.step();
			EnergyNetwork net = rig.network();
			if (net != null && net.lastTickFromStorage() > 0) {
				released += net.lastTickFromStorage();
				backupTicks++;
			}
			if (EnergyScenarioSupport.be(helper, SURPLUS_MACHINE) instanceof EnergyBlockEntity machine) {
				machine.getEnergyStorage().drainInternal(HUNGRY);
			}
		}
		long fund = rig.amountAt(SURPLUS_FUND);
		long floor = (TICKS - 2L * 2) * (SURPLUS_OUTPUT - CableType.COPPER.segmentBuffer());
		EnergyGoldenRig.clear(helper);
		if (backupTicks == 0) {
			helper.fail("precondition: the box never backed the machine up — the rig checks nothing");
			return;
		}
		if (fund < floor) {
			helper.fail("the Teleporter got " + fund + " EU in " + TICKS + " ticks beside a " + SURPLUS_OUTPUT
					+ " EU/t source whose machine takes one segment a tick, under " + floor + "; the box released "
					+ released + " EU on " + backupTicks + " ticks");
			return;
		}
		helper.succeed();
	}
}
