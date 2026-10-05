package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorStructure;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * World golden master of the energy network (MOD-715, batch 0): the real topology cache, the real
 * discharge channels and the real distribution kernel, on the reference circuits — a straight line, a fork
 * to a machine and a store (MOD-254), a dead-end spur (MOD-318), a ring, a multiblock lending one buffer
 * through two cells (MOD-608), and the storage channels (backup, closed backup, cascade, cascade beside a
 * fund, feed; ADR-004).
 *
 * <p>Each tick records every cable and endpoint buffer and the network's telemetry; the first tick also
 * records the endpoint order the cache discovered, and chosen ticks the flow faces the cache's fields give
 * ({@code EnergyNetworkDiagnostics.cableFlowFaces} — downhill on the flow potential, outward along the
 * producer distance into stranded cables). Those are the fields {@code EnergyTopologyCache} computes; this
 * is where they are pinned, because the L1.5 lane has no level to build the cache on (MOD-485) — the
 * kernel alone is pinned there by {@code EnergyLineDistributorGoldenTest}.
 *
 * <p>The golden {@link EnergyNetworkGolden} is rewritten only by the command in its javadoc (ADR-032).
 */
public final class EnergyFlowFieldGoldenScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(EnergyFlowFieldGoldenScenarios::networkMatchesGolden, "mod715_energy_network_golden"));

		private Roster() {}
	}

	/** System property naming the file the explicit update command writes the golden into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.energyNetworkGolden.writeTo";

	private static final int TICKS = 40;
	private static final int[] FLOW_AT = {1, 10, 40};
	/** How far the storage-channel circuits run on, sampled every {@link #LONG_EVERY} ticks. */
	private static final int LONG_TICKS = 1200;
	private static final int LONG_EVERY = 40;

	private EnergyFlowFieldGoldenScenarios() {
	}

	private static BlockPos p(int x, int z) {
		return new BlockPos(x, 2, z);
	}

	private static ItemStack ore() {
		return new ItemStack(Items.RAW_IRON, 16);
	}

	/**
	 * Every circuit in turn, the rig cleared between them, compared with the golden.
	 *
	 * @implements MOD-715-CH02 — the energy network's per-tick buffers, endpoint order and flow faces on
	 *     the reference circuits are unchanged
	 */
	public static void networkMatchesGolden(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		lines.addAll(line(helper));
		lines.addAll(fork(helper));
		lines.addAll(spur(helper));
		lines.addAll(ring(helper));
		lines.addAll(host(helper));
		lines.addAll(storageChannels(helper));
		EnergyGoldenRig.clear(helper);
		GoldenLines.check(helper, "EnergyNetworkGolden", WRITE_TO_PROPERTY, "*:mod715_energy_network_golden",
				EnergyNetworkGolden.LINES, lines);
	}

	private static List<String> line(GameTestHelper helper) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "line");
		rig.source(p(0, 1), 20);
		rig.run(p(1, 1), p(5, 1));
		rig.machine(p(6, 1), ModContent.MACERATOR.get(), Direction.EAST, ore());
		return rig.drive(TICKS, FLOW_AT);
	}

	private static List<String> fork(GameTestHelper helper) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "fork");
		rig.source(p(0, 3), 24);
		rig.run(p(1, 3), p(3, 3));
		rig.run(p(3, 2), p(3, 1));
		rig.run(p(3, 4), p(3, 5));
		rig.machine(p(3, 0), ModContent.MACERATOR.get(), Direction.NORTH, ore());
		rig.store(p(3, 6), ModContent.BATTERY_BOX.get(), Direction.NORTH, 0L);
		return rig.drive(TICKS, FLOW_AT);
	}

	private static List<String> spur(GameTestHelper helper) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "spur");
		rig.source(p(0, 1), 20);
		rig.run(p(1, 1), p(4, 1));
		rig.run(p(2, 2), p(2, 4));
		rig.machine(p(5, 1), ModContent.MACERATOR.get(), Direction.EAST, ore());
		return rig.drive(TICKS, FLOW_AT);
	}

	private static List<String> ring(GameTestHelper helper) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "ring");
		rig.source(p(0, 1), 16);
		rig.run(p(1, 1), p(3, 1));
		rig.run(p(3, 2), p(3, 3));
		rig.run(p(2, 3), p(1, 3));
		rig.cable(p(1, 2));
		rig.machine(p(4, 3), ModContent.MACERATOR.get(), Direction.EAST, ore());
		return rig.drive(TICKS, FLOW_AT);
	}

	/**
	 * MOD-608: an assembled concentrator whose core is charged by hand and never ticked (no daylight
	 * enters the golden), cabled through two of its bottom cells — one host behind two endpoints.
	 */
	private static List<String> host(GameTestHelper helper) {
		EnergyGoldenRig.clear(helper);
		BlockPos core = p(3, 3);
		Direction facing = Direction.NORTH;
		helper.setBlock(core, ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState());
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part != ConcentratorPart.CORE) {
				helper.setBlock(core.offset(part.worldOffset(facing)),
						ModContent.CONCENTRATOR_SECTION.get().defaultBlockState());
			}
		}
		ConcentratorStructure.tryAssemble(helper.getLevel(), helper.absolutePos(core));
		Direction out = facing.getOpposite();
		BlockPos back = core.offset(ConcentratorPart.BACK.worldOffset(facing));
		BlockPos backRight = core.offset(ConcentratorPart.BACK_RIGHT.worldOffset(facing));
		if (ConcentratorStructure.neighbourPart(ConcentratorPart.BACK, facing, out) != null
				|| ConcentratorStructure.neighbourPart(ConcentratorPart.BACK_RIGHT, facing, out) != null
				|| !(EnergyScenarioSupport.be(helper, core) instanceof EnergyBlockEntity panel)) {
			throw new IllegalStateException("host rig: the concentrator did not assemble as expected");
		}
		panel.getEnergyStorage().setAmountUntracked(panel.getEnergyStorage().getCapacity());
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "host");
		rig.passive(core);
		rig.cable(back.relative(out));
		rig.cable(backRight.relative(out));
		Direction along = facing.getClockWise();
		rig.cable(backRight.relative(out).relative(along));
		rig.machine(backRight.relative(out).relative(along, 2), ModContent.MACERATOR.get(), along, ore());
		return rig.drive(TICKS, FLOW_AT);
	}

	/**
	 * The storage channels of ADR-004, each on its own circuit — the first 40 ticks one by one, then sampled
	 * to tick 1200 so the golden also holds where a channel closes and the next one opens.
	 */
	private static List<String> storageChannels(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		long full = Config.batteryBoxBuffer;

		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig backup = new EnergyGoldenRig(helper, "backup");
		backup.source(p(0, 1), 4);
		backup.run(p(1, 1), p(4, 1));
		backup.machine(p(5, 1), ModContent.MACERATOR.get(), Direction.EAST, ore());
		backup.store(p(2, 0), ModContent.BATTERY_BOX.get(), Direction.NORTH, full);
		lines.addAll(backup.drive(TICKS, FLOW_AT));
		lines.addAll(backup.driveOn(TICKS, LONG_TICKS, LONG_EVERY));

		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig covered = new EnergyGoldenRig(helper, "covered");
		covered.source(p(0, 1), 64);
		covered.run(p(1, 1), p(4, 1));
		covered.machine(p(5, 1), ModContent.MACERATOR.get(), Direction.EAST, ore());
		covered.store(p(2, 0), ModContent.BATTERY_BOX.get(), Direction.NORTH, full);
		lines.addAll(covered.drive(TICKS, FLOW_AT));
		lines.addAll(covered.driveOn(TICKS, LONG_TICKS, LONG_EVERY));

		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig cascade = new EnergyGoldenRig(helper, "cascade");
		cascade.store(p(0, 1), ModContent.BATTERY_BOX.get(), Direction.WEST, full);
		cascade.run(p(1, 1), p(4, 1));
		cascade.store(p(5, 1), ModContent.BATTERY_BOX.get(), Direction.WEST, 0L);
		lines.addAll(cascade.drive(TICKS, FLOW_AT));
		lines.addAll(cascade.driveOn(TICKS, LONG_TICKS, LONG_EVERY));

		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig beside = new EnergyGoldenRig(helper, "fund");
		beside.store(p(0, 1), ModContent.BATTERY_BOX.get(), Direction.WEST, full * 6 / 10);
		beside.run(p(1, 1), p(4, 1));
		beside.store(p(5, 1), ModContent.BATTERY_BOX.get(), Direction.WEST, 0L);
		beside.store(p(2, 2), ModContent.TELEPORTER.get(), Direction.SOUTH, 0L);
		lines.addAll(beside.drive(TICKS, FLOW_AT));
		lines.addAll(beside.driveOn(TICKS, LONG_TICKS, LONG_EVERY));

		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig feed = new EnergyGoldenRig(helper, "feed");
		feed.store(p(0, 1), ModContent.BATTERY_BOX.get(), Direction.WEST, full);
		feed.run(p(1, 1), p(3, 1));
		feed.store(p(4, 1), ModContent.TELEPORTER.get(), Direction.EAST, 0L);
		lines.addAll(feed.drive(TICKS, FLOW_AT));
		lines.addAll(feed.driveOn(TICKS, LONG_TICKS, LONG_EVERY));
		return lines;
	}
}
