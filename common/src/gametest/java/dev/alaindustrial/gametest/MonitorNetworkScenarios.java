package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.block.entity.SmartWireBlockEntity;
import dev.alaindustrial.core.monitor.MonitorNetwork;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.core.monitor.MonitorReadout;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * The monitor wall as a network (MOD-715, batch 0): until now no world scenario touched
 * {@code MonitorNetwork} at all, and the network core's shared node bookkeeping is about to be moved under
 * it. Pins what the wall does with its graph: a wall assembles and counts what its wires reach, the reading
 * follows the chest on the scan interval, breaking the core or a wire splits the wall the way a player sees
 * it, and a panel placed between two walls merges them.
 *
 * <p>Rig, west to east along {@code z = 1}: chest, smart wire, core, panel, panel. Each network is ticked
 * directly, never through {@code tickAll}, so a scan happens exactly when the scenario says it does.
 */
public final class MonitorNetworkScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MonitorNetworkScenarios::assembledWallCountsTheChest,
						"mod715_monitor_wall_counts_the_chest"),
				RosterEntry.of(MonitorNetworkScenarios::readingFollowsTheChestOnTheScanInterval,
						"mod715_monitor_reading_follows_the_chest"),
				RosterEntry.of(MonitorNetworkScenarios::breakingTheCoreLeavesPanelsWithoutACore,
						"mod715_monitor_breaking_the_core_splits_the_wall"),
				RosterEntry.of(MonitorNetworkScenarios::breakingTheWireCutsTheChestOff,
						"mod715_monitor_breaking_the_wire_cuts_the_chest_off"),
				RosterEntry.of(MonitorNetworkScenarios::aPanelBetweenTwoWallsMergesThem,
						"mod715_monitor_panel_merges_two_walls"));

		private Roster() {}
	}

	private static final BlockPos CHEST = new BlockPos(1, 2, 1);
	private static final BlockPos WIRE = new BlockPos(2, 2, 1);
	private static final BlockPos CORE = new BlockPos(3, 2, 1);
	private static final BlockPos PANEL = new BlockPos(4, 2, 1);
	private static final BlockPos PANEL_2 = new BlockPos(5, 2, 1);
	private static final BlockPos FAR_PANEL = new BlockPos(6, 2, 1);

	private MonitorNetworkScenarios() {
	}

	private static BlockEntity be(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	private static MonitorPanelBlockEntity panel(GameTestHelper helper, BlockPos pos) {
		if (be(helper, pos) instanceof MonitorPanelBlockEntity panel) {
			return panel;
		}
		throw new IllegalStateException("no monitor panel at " + pos);
	}

	private static void register(GameTestHelper helper, BlockPos pos) {
		switch (be(helper, pos)) {
			case SmartWireBlockEntity wire -> wire.ensureRegistered();
			case MonitorCoreBlockEntity core -> core.ensureRegistered();
			case MonitorPanelBlockEntity panel -> panel.ensureRegistered();
			case null, default -> throw new IllegalStateException("not a monitor node at " + pos);
		}
	}

	/** A panel watching {@code item}, placed and registered. */
	private static void placePanel(GameTestHelper helper, BlockPos pos, ItemStack item) {
		helper.setBlock(pos, ModContent.MONITOR_PANEL.get());
		register(helper, pos);
		panel(helper, pos).setFilter(item);
	}

	/** Chest with 37 cobblestone and 5 dirt, wire, a powered core with one card, two panels. */
	private static void buildWall(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		chest.setItem(1, new ItemStack(Items.COBBLESTONE, 5));
		chest.setItem(2, new ItemStack(Items.DIRT, 5));
		helper.setBlock(WIRE, ModContent.SMART_WIRE.get());
		register(helper, WIRE);
		helper.setBlock(CORE, ModContent.MONITOR_CORE.get());
		register(helper, CORE);
		MonitorCoreBlockEntity core = helper.getBlockEntity(CORE, MonitorCoreBlockEntity.class);
		core.insertCard(new ItemStack(ModContent.CAPACITY_CARD.get()));
		core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
		placePanel(helper, PANEL, new ItemStack(Items.COBBLESTONE));
		placePanel(helper, PANEL_2, new ItemStack(Items.DIRT));
	}

	/** Tick the network that owns {@code pos} once; fails the test when there is none. */
	private static MonitorNetwork tickAt(GameTestHelper helper, BlockPos pos) {
		MonitorNetwork network = MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(pos));
		if (network == null) {
			throw new IllegalStateException("no monitor network at " + pos);
		}
		network.tick();
		return network;
	}

	private static String reading(GameTestHelper helper, BlockPos pos) {
		MonitorPanelBlockEntity panel = panel(helper, pos);
		return panel.getReadout() + " " + panel.getCount();
	}

	private static boolean expect(GameTestHelper helper, BlockPos pos, MonitorReadout readout, long count,
			String what) {
		MonitorPanelBlockEntity panel = panel(helper, pos);
		if (panel.getReadout() != readout || panel.getCount() != count) {
			helper.fail(what + ": panel at " + pos + " reads " + reading(helper, pos) + ", expected " + readout + " "
					+ count);
			return false;
		}
		return true;
	}

	/**
	 * Wire, core and panels form one network; the first scan counts the chest per watched type.
	 *
	 * @implements MOD-715-MN01 — an assembled wall counts what its wires reach
	 */
	public static void assembledWallCountsTheChest(GameTestHelper helper) {
		buildWall(helper);
		MonitorNetwork network = tickAt(helper, CORE);
		if (MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(WIRE)) != network
				|| MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(PANEL_2)) != network) {
			helper.fail("wire, core and both panels must be one monitor network");
			return;
		}
		if (expect(helper, PANEL, MonitorReadout.OK, 37, "first scan")
				&& expect(helper, PANEL_2, MonitorReadout.OK, 5, "first scan")) {
			helper.succeed();
		}
	}

	/**
	 * After a scan the next one waits the configured interval: a changed chest shows up on the scan
	 * {@code monitorScanIntervalTicks} ticks later, not on the next tick.
	 *
	 * @implements MOD-715-MN02 — the reading follows the chest on the scan interval
	 */
	public static void readingFollowsTheChestOnTheScanInterval(GameTestHelper helper) {
		buildWall(helper);
		tickAt(helper, CORE);
		helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(3, new ItemStack(Items.COBBLESTONE, 10));
		tickAt(helper, CORE);
		if (!expect(helper, PANEL, MonitorReadout.OK, 37, "one tick after the first scan")) {
			return;
		}
		int interval = Math.max(1, Config.monitorScanIntervalTicks);
		for (int t = 2; t < interval; t++) {
			tickAt(helper, CORE);
		}
		if (!expect(helper, PANEL, MonitorReadout.OK, 37, "one tick before the interval ends")) {
			return;
		}
		tickAt(helper, CORE);
		if (expect(helper, PANEL, MonitorReadout.OK, 47, "on the next scan")) {
			helper.succeed();
		}
	}

	/**
	 * Breaking the core splits the wall: the panels form a network of their own with no core and say so.
	 *
	 * @implements MOD-715-MN03 — breaking the core leaves the panels without a core
	 */
	public static void breakingTheCoreLeavesPanelsWithoutACore(GameTestHelper helper) {
		buildWall(helper);
		tickAt(helper, CORE);
		helper.setBlock(CORE, Blocks.AIR);
		MonitorNetwork panels = tickAt(helper, PANEL);
		if (MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(WIRE)) == panels) {
			helper.fail("with the core gone the wire and the panels must no longer share a network");
			return;
		}
		if (expect(helper, PANEL, MonitorReadout.NO_CORE, 0, "core broken")
				&& expect(helper, PANEL_2, MonitorReadout.NO_CORE, 0, "core broken")) {
			helper.succeed();
		}
	}

	/**
	 * Breaking the only wire takes the chest out of the wall: the panels still run and now count nothing.
	 *
	 * @implements MOD-715-MN04 — breaking the wire cuts the chest off
	 */
	public static void breakingTheWireCutsTheChestOff(GameTestHelper helper) {
		buildWall(helper);
		tickAt(helper, CORE);
		helper.setBlock(WIRE, Blocks.AIR);
		tickAt(helper, CORE);
		if (expect(helper, PANEL, MonitorReadout.OK, 0, "wire broken")
				&& expect(helper, PANEL_2, MonitorReadout.OK, 0, "wire broken")) {
			helper.succeed();
		}
	}

	/**
	 * A panel standing apart is a wall of its own with no core; a panel placed between it and the wall
	 * merges the two, and the far panel then reads the chest.
	 *
	 * @implements MOD-715-MN05 — a panel between two walls merges them
	 */
	public static void aPanelBetweenTwoWallsMergesThem(GameTestHelper helper) {
		buildWall(helper);
		helper.setBlock(PANEL_2, Blocks.AIR);
		placePanel(helper, FAR_PANEL, new ItemStack(Items.DIRT));
		MonitorNetwork far = tickAt(helper, FAR_PANEL);
		if (far == MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CORE))) {
			helper.fail("a panel two cells from the wall must not belong to it");
			return;
		}
		if (!expect(helper, FAR_PANEL, MonitorReadout.NO_CORE, 0, "apart from the wall")) {
			return;
		}
		placePanel(helper, PANEL_2, new ItemStack(Items.DIRT));
		MonitorNetwork merged = tickAt(helper, CORE);
		if (MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(FAR_PANEL)) != merged) {
			helper.fail("the panel placed between them must merge the far panel into the wall");
			return;
		}
		if (expect(helper, FAR_PANEL, MonitorReadout.OK, 5, "merged")) {
			helper.succeed();
		}
	}
}
