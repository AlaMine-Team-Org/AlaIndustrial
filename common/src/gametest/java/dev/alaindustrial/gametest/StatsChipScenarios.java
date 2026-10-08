package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.menu.GardenDroneStationMenu;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.stats.StatsWindow;
import dev.alaindustrial.network.MachineStatsPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.skill.SkillStore;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/**
 * L2 suite for the statistics chip (MOD-125) — the gate that decides whether a machine measures at all.
 *
 * <p>The L1 tests cover the counter arithmetic inside {@code EnergyBuffer}. What they cannot reach is the
 * wiring this suite exists for: that a chip sitting in a slot actually turns that arithmetic on, and that
 * an un-instrumented machine stays at zero while doing real work. That link runs through the block
 * entity's inventory and its tick, neither of which exists without Minecraft.
 */
public final class StatsChipScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(StatsChipScenarios::statsChip_withoutChipNothingIsMeasured,
								"stats_chip_without_chip_nothing_is_measured")
						.fabricId("StatsChipGameTest", "statsChip_withoutChipNothingIsMeasured").ticks(20, 120),
				RosterEntry.of(StatsChipScenarios::statsChip_withChipTheCountersRun,
								"stats_chip_with_chip_the_counters_run")
						.fabricId("StatsChipGameTest", "statsChip_withChipTheCountersRun").ticks(20, 120),
				RosterEntry.of(StatsChipScenarios::statsChip_countingStartsWhenTheChipIsFitted,
								"stats_chip_counting_starts_when_the_chip_is_fitted")
						.fabricId("StatsChipGameTest", "statsChip_countingStartsWhenTheChipIsFitted").ticks(20, 140),
				RosterEntry.of(StatsChipScenarios::statsChip_removingTheChipKeepsWhatWasCounted,
								"stats_chip_removing_the_chip_keeps_what_was_counted")
						.fabricId("StatsChipGameTest", "statsChip_removingTheChipKeepsWhatWasCounted").ticks(20, 140),
				RosterEntry.of(StatsChipScenarios::statsChip_countersRunOnHandRolledMachine,
								"stats_chip_counters_run_on_hand_rolled_machine")
						.fabricId("StatsChipGameTest", "statsChip_countersRunOnHandRolledMachine").ticks(400),
				RosterEntry.of(StatsChipScenarios::statsChip_batteryBoxCountsTransfers,
								"stats_chip_battery_box_counts_transfers")
						.fabricId("StatsChipGameTest", "statsChip_batteryBoxCountsTransfers").ticks(120),
				RosterEntry.of(StatsChipScenarios::statsChip_cesuCountsTransfers, "stats_chip_cesu_counts_transfers")
						.fabricId("StatsChipGameTest", "statsChip_cesuCountsTransfers").ticks(120),
				RosterEntry.of(StatsChipScenarios::statsChip_pumpCountsItsDraw, "stats_chip_pump_counts_its_draw")
						.fabricId("StatsChipGameTest", "statsChip_pumpCountsItsDraw").ticks(120),
				RosterEntry.of(StatsChipScenarios::statsChip_pumpStopsCountingWhenTheChipIsPulled,
								"stats_chip_pump_stops_counting_when_the_chip_is_pulled")
						.fabricId("StatsChipGameTest", "statsChip_pumpStopsCountingWhenTheChipIsPulled").ticks(120),
				RosterEntry.of(StatsChipScenarios::statsChip_gardenDroneCountsItsActions,
								"stats_chip_garden_drone_counts_its_actions")
						.fabricId("StatsChipGameTest", "statsChip_gardenDroneCountsItsActions").ticks(120),
				RosterEntry.of(StatsChipScenarios::statsChip_gardenDroneWindowRateIsNotZero,
								"stats_chip_garden_drone_window_rate_is_not_zero")
						.fabricId("StatsChipGameTest", "statsChip_gardenDroneWindowRateIsNotZero").ticks(120),
				RosterEntry.of(StatsChipScenarios::statsChip_freeTelemetryLeavesPanellessBlocksUncounted,
								"stats_chip_free_telemetry_leaves_panelless_blocks_uncounted")
						.fabricId("StatsChipGameTest", "statsChip_freeTelemetryLeavesPanellessBlocksUncounted")
						.ticks(120));

		private Roster() {}
	}

	private StatsChipScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/** Panel arm that takes the statistics chip (MOD-125) — index 2, the right arm. */
	private static final int STATS_ARM = 2;

	private static void fitChip(MachineBlockEntity be) {
		be.setItem(be.upgradeSlotStart() + STATS_ARM, new ItemStack(ModContent.STATS_CHIP.get()));
	}

	/** Give the machine something to chew on, so its tick has real work to measure. */
	private static void loadWork(MaceratorBlockEntity be) {
		be.setItem(0, new ItemStack(Items.IRON_ORE, 8));
		be.getEnergyStorage().setAmountUntracked(be.getEnergyStorage().getCapacity());
	}

	/**
	 * The headline promise: no chip, no measurement. A machine that runs a full workload without a chip
	 * must report exactly nothing — not "a bit less", nothing.
	 */
	public static void statsChip_withoutChipNothingIsMeasured(GameTestHelper helper) {
		MaceratorBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get(),
				MaceratorBlockEntity.class);
		loadWork(be);
		if (be.hasStatsChip()) {
			helper.fail("a freshly placed machine must not report a statistics chip");
		}
		AlaGameTestHelper.drive(be, helper, 60);

		if (be.activeTicks() != 0) {
			helper.fail("working time advanced without a chip: " + be.activeTicks());
		}
		if (be.getEnergyStorage().getTotalEnergyConsumed() != 0) {
			helper.fail("energy was counted without a chip: "
					+ be.getEnergyStorage().getTotalEnergyConsumed());
		}
		if (be.currentEuRate() != 0) {
			helper.fail("a rate was published without a chip: " + be.currentEuRate());
		}
		helper.succeed();
	}

	/** With the chip fitted the same workload has to move the counters. */
	public static void statsChip_withChipTheCountersRun(GameTestHelper helper) {
		MaceratorBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get(),
				MaceratorBlockEntity.class);
		loadWork(be);
		fitChip(be);
		if (!be.hasStatsChip()) {
			helper.fail("the chip in the stats arm was not detected");
		}
		AlaGameTestHelper.drive(be, helper, 60);

		if (be.activeTicks() <= 0) {
			helper.fail("working time did not advance with a chip fitted");
		}
		if (be.getEnergyStorage().getTotalEnergyConsumed() <= 0) {
			helper.fail("energy consumption was not counted with a chip fitted");
		}
		helper.succeed();
	}

	/**
	 * Measuring starts when the chip goes in, not retroactively — the whole point of the "the instrument
	 * knows nothing of the block's past" rule. The machine works first WITHOUT a chip, then gets one; the
	 * pre-chip work must not appear afterwards.
	 */
	public static void statsChip_countingStartsWhenTheChipIsFitted(GameTestHelper helper) {
		MaceratorBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get(),
				MaceratorBlockEntity.class);
		loadWork(be);
		AlaGameTestHelper.drive(be, helper, 40);
		if (be.activeTicks() != 0 || be.getEnergyStorage().getTotalEnergyConsumed() != 0) {
			helper.fail("pre-chip work was measured");
		}

		fitChip(be);
		AlaGameTestHelper.drive(be, helper, 40);

		long counted = be.getEnergyStorage().getTotalEnergyConsumed();
		if (counted <= 0) {
			helper.fail("counting did not start after the chip was fitted");
		}
		// Everything counted has to belong to the post-chip window. The machine drew energy for the same
		// number of ticks on both sides, so a total that also swallowed the first window would be roughly
		// double — this catches a baseline that was not being maintained while measuring was off.
		long perTick = be.effectiveEuPerTick(dev.alaindustrial.Config.machineEuPerTick);
		if (counted > perTick * 60L) {
			helper.fail("the pre-chip window leaked into the total: " + counted
					+ " EU counted over ~40 ticks of work at " + perTick + " EU/t");
		}
		helper.succeed();
	}

	/** Pulling the chip freezes the totals but must not wipe them. */
	public static void statsChip_removingTheChipKeepsWhatWasCounted(GameTestHelper helper) {
		MaceratorBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get(),
				MaceratorBlockEntity.class);
		loadWork(be);
		fitChip(be);
		AlaGameTestHelper.drive(be, helper, 40);
		long counted = be.getEnergyStorage().getTotalEnergyConsumed();
		long worked = be.activeTicks();
		if (counted <= 0 || worked <= 0) {
			helper.fail("nothing was counted while the chip was in");
		}

		be.setItem(be.upgradeSlotStart() + STATS_ARM, ItemStack.EMPTY);
		AlaGameTestHelper.drive(be, helper, 40);

		if (be.getEnergyStorage().getTotalEnergyConsumed() != counted) {
			helper.fail("the total moved after the chip was pulled: " + counted + " -> "
					+ be.getEnergyStorage().getTotalEnergyConsumed());
		}
		if (be.activeTicks() != worked) {
			helper.fail("working time moved after the chip was pulled");
		}
		helper.succeed();
	}

	/**
	 * The chip has to work on a machine that owns its tick loop, not only on the shared processing base
	 * (MOD-440). The four cases above all ride {@code AbstractProcessingMachineBlockEntity}, which was the
	 * only consumer wired to {@code recordEuRate}/{@code recordItemProcessed} when MOD-125 shipped — so a
	 * chip in any of the ten machines extending {@code MachineBlockEntity} directly showed 0 EU/t, zero
	 * working time and a frozen consumption total while the machine visibly ran. The alloy smelter stands
	 * in for all of them: same rig as {@code AlloySmelterScenarios.fun01BronzeIsSmelted}, plus a chip.
	 */
	public static void statsChip_countersRunOnHandRolledMachine(GameTestHelper helper) {
		AlloySmelterBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.ALLOY_SMELTER.get(),
				AlloySmelterBlockEntity.class);
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_0, new ItemStack(Items.COPPER_INGOT, 3));
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_1, new ItemStack(ModContent.TIN_INGOT.get(), 1));
		fitChip(be);
		if (!be.hasStatsChip()) {
			helper.fail("the chip in the stats arm was not detected on the alloy smelter");
		}
		// One operation costs 1200 EU against an 800 EU buffer, so the buffer is topped up every tick the
		// way a connected cable would keep it fed (the alloy suite's drivePowered contract).
		int ticks = MachineRates.duration(Config.alloySmelterDuration, Config.globalMachineSpeedMultiplier) + 20;
		for (int i = 0; i < ticks; i++) {
			be.getEnergyStorage().setAmountUntracked(be.getEnergyStorage().getCapacity());
			AlaGameTestHelper.drive(be, helper, 1);
		}

		// The rig itself must have run, or every zero below would be vacuous.
		if (!be.getItem(AlloySmelterBlockEntity.OUTPUT_SLOT).is(ModContent.BRONZE_INGOT.get())) {
			helper.fail("the alloy smelter rig produced no bronze — the counters below would be meaningless");
		}
		if (be.activeTicks() <= 0) {
			helper.fail("working time did not advance on a hand-rolled machine with a chip fitted");
		}
		if (be.getEnergyStorage().getTotalEnergyConsumed() <= 0) {
			helper.fail("energy consumption was not counted on a hand-rolled machine with a chip fitted");
		}
		if (be.totalItemsProcessed() <= 0) {
			helper.fail("a completed operation was not counted on a hand-rolled machine with a chip fitted");
		}
		helper.succeed();
	}

	// --- MOD-692: blocks that own their tick and never reach recordEuRate --------------------------

	/**
	 * Energy that crosses {@code be}'s buffer in committed transactions, the way a cable or a neighbouring
	 * machine moves it: {@code in} EU inserted, then {@code out} EU extracted, each in its own transaction
	 * so neither nets the other out at settlement.
	 */
	private static void moveEnergyThrough(MachineBlockEntity be, long in, long out) {
		EnergyBuffer buffer = be.getEnergyStorage();
		EnergyTransactions.get().runCommitting(txn -> buffer.insert(in, txn));
		EnergyTransactions.get().runCommitting(txn -> buffer.extract(out, txn));
	}

	/**
	 * The storage half of MOD-692: a Battery Box or CESU has the panel and the chip arm, but its tick
	 * never called {@code recordEuRate} — the only place the buffer's counters were ever switched on — so
	 * energy flowed through it all day while the panel read zero in and zero out.
	 *
	 * <p>First without a chip, where the transfers must stay uncounted (MOD-125 unchanged), then with one.
	 */
	private static void storageCountsTransfers(GameTestHelper helper, Block block,
			Class<? extends MachineBlockEntity> type) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, block, type);
		AlaGameTestHelper.drive(be, helper, 1);
		moveEnergyThrough(be, 32L, 16L);
		EnergyBuffer buffer = be.getEnergyStorage();
		if (buffer.getAmount() <= 0) {
			helper.fail("the rig moved no energy into " + block + " — every counter below would be vacuous");
		}
		if (buffer.countersEnabled() || buffer.getTotalEnergyIn() != 0 || buffer.getTotalEnergyOut() != 0) {
			helper.fail(block + " measured without a chip: in=" + buffer.getTotalEnergyIn()
					+ " out=" + buffer.getTotalEnergyOut());
		}

		fitChip(be);
		AlaGameTestHelper.drive(be, helper, 1);
		moveEnergyThrough(be, 32L, 16L);
		if (buffer.getTotalEnergyIn() <= 0) {
			helper.fail(block + " with a chip did not count the energy delivered into it");
		}
		if (buffer.getTotalEnergyOut() <= 0) {
			helper.fail(block + " with a chip did not count the energy drawn out of it");
		}
		helper.succeed();
	}

	/** MOD-692: the Battery Box counts what passes through it once a chip is fitted. */
	public static void statsChip_batteryBoxCountsTransfers(GameTestHelper helper) {
		storageCountsTransfers(helper, ModContent.BATTERY_BOX.get(), BatteryBoxBlockEntity.class);
	}

	/** MOD-692: the CESU counts what passes through it once a chip is fitted. */
	public static void statsChip_cesuCountsTransfers(GameTestHelper helper) {
		storageCountsTransfers(helper, ModContent.CESU.get(), CesuBlockEntity.class);
	}

	/** A pump facing EAST with a water source in front of it and exactly one bucket's price in its buffer. */
	private static PumpBlockEntity rigPump(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.PUMP.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		PumpBlockEntity pump = helper.getBlockEntity(POS, PumpBlockEntity.class);
		if (pump == null) {
			helper.fail("pump block entity missing after placement");
		}
		refillPumpSource(helper, pump);
		return pump;
	}

	/** Put the water source back in front of the pump and pay for one more bucket. */
	private static void refillPumpSource(GameTestHelper helper, PumpBlockEntity pump) {
		helper.getLevel().setBlockAndUpdate(helper.absolutePos(POS.relative(Direction.EAST)),
				Blocks.WATER.defaultBlockState());
		pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket);
	}

	private static boolean pumpSourceDrained(GameTestHelper helper) {
		return !helper.getLevel().getFluidState(helper.absolutePos(POS.relative(Direction.EAST)))
				.isSourceOfType(Fluids.WATER);
	}

	/**
	 * MOD-692: the pump spends {@code Config.pumpEuPerBucket} per bucket straight out of its buffer and never
	 * reported it, so a chip in a pump read 0 EU/t, zero consumption and zero working time. One bucket with
	 * a chip fitted has to show on all three.
	 */
	public static void statsChip_pumpCountsItsDraw(GameTestHelper helper) {
		PumpBlockEntity pump = rigPump(helper);
		fitChip(pump);
		AlaGameTestHelper.drive(pump, helper, 1);

		if (!pumpSourceDrained(helper)) {
			helper.fail("the pump rig pumped nothing — the counters below would be meaningless");
		}
		if (pump.getEnergyStorage().getTotalEnergyConsumed() <= 0) {
			helper.fail("the energy a bucket cost was not counted on a pump with a chip fitted");
		}
		if (pump.activeTicks() <= 0) {
			helper.fail("working time did not advance on a pump that just moved a bucket");
		}
		// The panel's first snapshot reads this instantaneous rate, later ones the consumption total above.
		if (pump.currentEuRate() <= 0 || pump.peakEuRate() <= 0) {
			helper.fail("the pump published no EU/t for the tick it paid for a bucket: now="
					+ pump.currentEuRate() + " peak=" + pump.peakEuRate());
		}
		helper.succeed();
	}

	/**
	 * MOD-692: pulling the chip switches the counters off and zeroes the "now" line even while the block
	 * keeps working — the {@code !measuring} branch, on a block that reaches it only since this task. The
	 * pump pumps a second bucket after the chip is gone; that bucket must not be counted.
	 */
	public static void statsChip_pumpStopsCountingWhenTheChipIsPulled(GameTestHelper helper) {
		PumpBlockEntity pump = rigPump(helper);
		fitChip(pump);
		AlaGameTestHelper.drive(pump, helper, 1);
		long counted = pump.getEnergyStorage().getTotalEnergyConsumed();
		long worked = pump.activeTicks();
		if (counted <= 0 || pump.currentEuRate() <= 0) {
			helper.fail("nothing was measured while the chip was in: consumed=" + counted
					+ " now=" + pump.currentEuRate());
		}

		pump.setItem(pump.upgradeSlotStart() + STATS_ARM, ItemStack.EMPTY);
		refillPumpSource(helper, pump);
		// Past the scan cooldown the pump fires again; the rate is checked on every tick, the pumping one
		// included.
		for (int i = 0; i < Config.pumpScanCooldownTicks + 2; i++) {
			AlaGameTestHelper.drive(pump, helper, 1);
			if (pump.currentEuRate() != 0) {
				helper.fail("the pump still published " + pump.currentEuRate() + " EU/t with the chip pulled");
			}
		}

		if (!pumpSourceDrained(helper)) {
			helper.fail("the pump did not pump its second bucket — the frozen totals below prove nothing");
		}
		if (pump.getEnergyStorage().countersEnabled()) {
			helper.fail("the energy counters stayed on after the chip was pulled");
		}
		if (pump.getEnergyStorage().getTotalEnergyConsumed() != counted) {
			helper.fail("a bucket pumped without a chip was counted: " + counted + " -> "
					+ pump.getEnergyStorage().getTotalEnergyConsumed());
		}
		if (pump.activeTicks() != worked) {
			helper.fail("working time moved after the chip was pulled: " + worked + " -> " + pump.activeTicks());
		}
		helper.succeed();
	}

	/**
	 * MOD-692: the Garden Drone Station pays {@code Config.gardenDroneEuPerAction} for each action it lands
	 * and never reported it. Same rig as {@code GardenDroneScenarios.fun01TillsDirtAndSpendsEu}, plus a chip.
	 */
	public static void statsChip_gardenDroneCountsItsActions(GameTestHelper helper) {
		GardenDroneScenarios.withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = GardenDroneScenarios.place(helper);
			GardenDroneScenarios.charge(station);
			fitChip(station);
			helper.setBlock(GardenDroneScenarios.PLOT, Blocks.DIRT);
			helper.setBlock(GardenDroneScenarios.PLOT.above(), Blocks.AIR);

			AlaGameTestHelper.drive(station, helper, GardenDroneScenarios.TICKS_PER_JOB);

			if (!helper.getLevel().getBlockState(helper.absolutePos(GardenDroneScenarios.PLOT))
					.is(Blocks.FARMLAND)) {
				helper.fail("the drone rig landed no action — the counters below would be meaningless");
			}
			if (station.getEnergyStorage().getTotalEnergyConsumed() != Config.gardenDroneEuPerAction) {
				helper.fail("one landed action should count exactly " + Config.gardenDroneEuPerAction
						+ " EU, counted " + station.getEnergyStorage().getTotalEnergyConsumed());
			}
			if (station.activeTicks() <= 0) {
				helper.fail("working time did not advance on a station that landed an action");
			}
			if (station.peakEuRate() <= 0) {
				helper.fail("the station never published an EU/t for the action it paid for");
			}
			helper.succeed();
		});
	}

	/**
	 * MOD-722: the panel's "now" row is the average over the last statistics window, read from the menu's
	 * own snapshot — the number the panel draws, not {@code currentEuRate}. The station pays
	 * {@code Config.gardenDroneEuPerAction} once per flight of at least {@code MIN_FLIGHT_TICKS}, well under
	 * 1 EU/t on average, and the integer rate the snapshot used to carry turned that into "0 EU/t" on a
	 * station that was working. The control: a window in which nothing was spent still reads zero.
	 *
	 * @implements MOD-722-WINDOW — the menu's snapshot carries the window's EU, and the panel shows it
	 * @covers MOD-722
	 */
	public static void statsChip_gardenDroneWindowRateIsNotZero(GameTestHelper helper) {
		GardenDroneScenarios.withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = GardenDroneScenarios.place(helper);
			GardenDroneScenarios.charge(station);
			fitChip(station);
			helper.setBlock(GardenDroneScenarios.PLOT, Blocks.DIRT);
			helper.setBlock(GardenDroneScenarios.PLOT.above(), Blocks.AIR);
			ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
			GardenDroneStationMenu menu = new GardenDroneStationMenu(1, player.getInventory(), station,
					ContainerLevelAccess.create(helper.getLevel(), station.getBlockPos()));

			// The first snapshot has no previous sample: it only opens the window. drive() ticks the block
			// without moving the level's clock, so the scenario names the window's game time itself.
			long opened = helper.getLevel().getGameTime();
			menu.statsSnapshot(station, opened);
			AlaGameTestHelper.drive(station, helper, StatsWindow.INTERVAL_TICKS);
			long spent = station.getEnergyStorage().getTotalEnergyConsumed();
			if (spent <= 0) {
				helper.fail("the drone rig spent nothing in the window — the rate below would be meaningless");
			}
			MachineStatsPayload working = menu.statsSnapshot(station, opened + StatsWindow.INTERVAL_TICKS);
			String shown = ReadoutFormat.rate(working.euOverWindow(), working.windowTicks());
			if (working.euOverWindow() != spent || working.windowTicks() != StatsWindow.INTERVAL_TICKS
					|| "0".equals(shown)) {
				helper.fail("a station that spent " + spent + " EU in the window carried "
						+ working.euOverWindow() + " EU over " + working.windowTicks() + " ticks and shows \""
						+ shown + " EU/t\"");
			}

			// Control: a chipped macerator with nothing to grind moves no EU, and must still read zero.
			MaceratorBlockEntity idle = AlaGameTestHelper.place(helper, POS.east(3), ModContent.MACERATOR.get(),
					MaceratorBlockEntity.class);
			fitChip(idle);
			MaceratorMenu idleMenu = new MaceratorMenu(2, player.getInventory(), idle,
					ContainerLevelAccess.create(helper.getLevel(), idle.getBlockPos()));
			idleMenu.statsSnapshot(idle, opened);
			AlaGameTestHelper.drive(idle, helper, StatsWindow.INTERVAL_TICKS);
			MachineStatsPayload quiet = idleMenu.statsSnapshot(idle, opened + StatsWindow.INTERVAL_TICKS);
			String quietShown = ReadoutFormat.rate(quiet.euOverWindow(), quiet.windowTicks());
			if (quiet.euOverWindow() != 0 || !"0".equals(quietShown)) {
				helper.fail("an idle machine shows \"" + quietShown + " EU/t\" (" + quiet.euOverWindow() + " EU)");
			}
			helper.succeed();
		});
	}

	/**
	 * MOD-692, open question 3: Free Telemetry (MECH/B2) makes {@code hasStatsChip()} true on EVERY block the
	 * owner placed, including those without an upgrade panel. Decision: the counters follow the panel — a
	 * block with no panel keeps them off, so nothing new is counted into its save tag.
	 *
	 * <p>The Battery Box, owned by the same player and carrying no chip, is the control: it must count, or
	 * the heater staying silent would only prove that the skill never switched on.
	 */
	public static void statsChip_freeTelemetryLeavesPanellessBlocksUncounted(GameTestHelper helper) {
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		SkillStore.set(owner, new PlayerSkills(SkillBuild.EMPTY.with(SkillBranch.MECH, SkillSlot.B2)));

		MachineBlockEntity box = AlaGameTestHelper.place(helper, POS, ModContent.BATTERY_BOX.get(),
				BatteryBoxBlockEntity.class);
		box.setOwner(owner.getUUID(), owner.getGameProfile().name());
		// Three blocks apart: the box pushes into adjacent machines and must not feed the heater.
		MachineBlockEntity heater = AlaGameTestHelper.place(helper, POS.east(3), ModContent.ELECTRIC_HEATER.get(),
				ElectricHeaterBlockEntity.class);
		heater.setOwner(owner.getUUID(), owner.getGameProfile().name());
		if (heater.hasUpgradeSlots()) {
			helper.fail("the electric heater grew an upgrade panel — pick another panelless block for this rig");
		}
		if (!heater.hasStatsChip() || !box.hasStatsChip()) {
			helper.fail("Free Telemetry did not switch the statistics on for its owner's blocks");
		}

		AlaGameTestHelper.drive(box, helper, 1);
		AlaGameTestHelper.drive(heater, helper, 1);
		moveEnergyThrough(box, 32L, 16L);
		EnergyTransactions.get().runCommitting(txn -> heater.getEnergyStorage().insert(32L, txn));

		if (box.getEnergyStorage().getTotalEnergyIn() <= 0) {
			helper.fail("the skill did not start the counters on a block with a panel (the control)");
		}
		if (heater.getEnergyStorage().getAmount() <= 0) {
			helper.fail("the rig delivered nothing to the heater — its silence below would be vacuous");
		}
		if (heater.getEnergyStorage().countersEnabled() || heater.getEnergyStorage().getTotalEnergyIn() != 0) {
			helper.fail("a block without an upgrade panel started counting under Free Telemetry: in="
					+ heater.getEnergyStorage().getTotalEnergyIn());
		}
		helper.succeed();
	}
}
