package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.AbstractProcessingMachineBlockEntity;
import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * A processing machine that is already in the world follows a live config reload of its duration knob
 * (MOD-760).
 *
 * <p>{@code /ala config reload} writes the new numbers into the knob fields. The processing family used to
 * copy its duration knob into a final field when the block entity was created, so a loaded furnace kept the
 * old length until its chunk reloaded while a freshly placed one ran by the new number. Each scenario places
 * the machine FIRST and only then changes the knob — exactly the order a reload happens in — inside one
 * synchronous {@link ConfigOverrides#sync()} call, so no other scenario can see the value.
 *
 * <p>The rule for an operation running at the moment of the reload: its accumulated progress (in ticks) is
 * kept, not rescaled, and the bar's length follows the new number from the next tick — the rule the speed
 * multiplier, {@code machineEuPerTick} and the overclocker chips already follow.
 */
public final class MachineConfigReloadScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MachineConfigReloadScenarios::loadedFurnaceRunsByReloadedDuration,
						"machine_config_reload_loaded_furnace_runs_by_reloaded_duration"),
				RosterEntry.of(MachineConfigReloadScenarios::operationInFlightKeepsProgressAcrossReload,
						"machine_config_reload_operation_in_flight_keeps_progress_across_reload"),
				RosterEntry.of(MachineConfigReloadScenarios::idleBarOfEveryProcessingMachineFollowsReload,
						"machine_config_reload_idle_bar_of_every_processing_machine_follows_reload"));

		private Roster() {}
	}

	private static final BlockPos MACHINE = new BlockPos(1, 2, 1);
	/** Enough charge for any single tick of the family; refilled before every tick. */
	private static final long CHARGE = 10_000L;
	/** A bound on the loops below; every duration used here is far shorter. */
	private static final int TICK_LIMIT = 1_000;

	private MachineConfigReloadScenarios() {}

	/**
	 * A furnace placed BEFORE the knob changes smelts its next item in the new number of ticks.
	 *
	 * @implements MOD-760 — a loaded electric furnace takes its duration from the live knob, not from the value
	 * it was created with.
	 */
	public static void loadedFurnaceRunsByReloadedDuration(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalMachineSpeedMultiplier", 1.0f);
			AbstractProcessingMachineBlockEntity furnace = place(helper, ModContent.ELECTRIC_FURNACE, "furnace");
			int before = Config.electricFurnaceDuration;
			int after = differentDuration(before);
			o.set("electricFurnaceDuration", after);
			// Sand has no mod smelting recipe, so the vanilla fallback runs and the knob decides the length.
			furnace.setItem(0, new ItemStack(Items.SAND, 2));
			int ticks = ticksToNextGlass(furnace, helper);
			int expected = MachineRates.duration(after, 1.0f);
			if (ticks != expected) {
				helper.fail("a furnace placed while electricFurnaceDuration was " + before + " smelted in " + ticks
						+ " ticks after the knob became " + after + "; expected " + expected
						+ " (it kept the length it was created with — MOD-760)");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * An operation running when the knob changes keeps the ticks it has done and finishes against the new
	 * length; the operation after it runs the new length in full.
	 *
	 * @implements MOD-760 — progress is kept, not rescaled, across a reload; the next operation runs the new
	 * duration.
	 */
	public static void operationInFlightKeepsProgressAcrossReload(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalMachineSpeedMultiplier", 1.0f);
			o.set("electricFurnaceDuration", 60);
			AbstractProcessingMachineBlockEntity furnace = place(helper, ModContent.ELECTRIC_FURNACE, "furnace");
			furnace.setItem(0, new ItemStack(Items.SAND, 2));
			int done = 20;
			GameTestDrive.drivePowered(furnace, helper, done, CHARGE);
			int progress = furnace.getDataAccess().get(MachineChannels.PROGRESS.ordinal());
			if (progress != done || !furnace.getItem(1).isEmpty()) {
				helper.fail("fixture: after " + done + " paid ticks of a 60-tick smelt expected progress " + done
						+ " and no glass, got progress " + progress + " and " + furnace.getItem(1));
				return;
			}
			o.set("electricFurnaceDuration", 30);
			int rest = ticksToNextGlass(furnace, helper);
			int first = ticksToNextGlass(furnace, helper);
			List<String> problems = new ArrayList<>();
			if (rest != 30 - done) {
				problems.add("the smelt in flight needed " + rest + " more ticks after the knob went 60 -> 30 with "
						+ done + " ticks done; expected " + (30 - done) + " (progress kept, length from the new knob)");
			}
			if (first != 30) {
				problems.add("the next smelt took " + first + " ticks; expected the new 30");
			}
			if (!problems.isEmpty()) {
				helper.fail(String.join("; ", problems));
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Every machine of the processing family shows the reloaded default length on its idle bar after one
	 * tick: the five machines that used to hand their duration knob to the base as a number.
	 *
	 * @implements MOD-760 — the electric furnace, macerator, compressor, extractor and sawmill read their default
	 * duration live.
	 */
	public static void idleBarOfEveryProcessingMachineFollowsReload(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		check(helper, ModContent.ELECTRIC_FURNACE, "electricFurnaceDuration", problems);
		check(helper, ModContent.MACERATOR, "maceratorDuration", problems);
		check(helper, ModContent.COMPRESSOR, "compressorDuration", problems);
		check(helper, ModContent.EXTRACTOR, "extractorDuration", problems);
		check(helper, ModContent.SAWMILL, "sawmillDuration", problems);
		if (!problems.isEmpty()) {
			helper.fail(problems.size() + " machine(s) kept the length they were created with (MOD-760): "
					+ String.join("; ", problems));
			return;
		}
		helper.succeed();
	}

	private static void check(GameTestHelper helper, Supplier<Block> block, String knob, List<String> problems) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalMachineSpeedMultiplier", 1.0f);
			AbstractProcessingMachineBlockEntity be = place(helper, block, knob);
			int before = be.getDataAccess().get(MachineChannels.MAX_PROGRESS.ordinal());
			int after = differentDuration(before);
			o.set(knob, after);
			AlaGameTestHelper.drive(be, helper, 1);
			int bar = be.getDataAccess().get(MachineChannels.MAX_PROGRESS.ordinal());
			if (bar != after) {
				problems.add(knob + ": idle bar " + bar + " after the knob became " + after + " (was " + before + ")");
			}
		}
	}

	/** A valid duration that differs from {@code current}. */
	private static int differentDuration(int current) {
		return current > 20 ? current / 2 : current + 17;
	}

	/** Paid ticks until the output slot gains a glass; fails the scenario past {@link #TICK_LIMIT}. */
	private static int ticksToNextGlass(AbstractProcessingMachineBlockEntity furnace, GameTestHelper helper) {
		int start = furnace.getItem(1).getCount();
		for (int tick = 1; tick <= TICK_LIMIT; tick++) {
			GameTestDrive.drivePowered(furnace, helper, 1, CHARGE);
			ItemStack out = furnace.getItem(1);
			if (out.getCount() > start) {
				if (!out.is(Items.GLASS)) {
					throw new IllegalStateException("fixture: the furnace made " + out + ", not glass");
				}
				return tick;
			}
		}
		throw new IllegalStateException("fixture: no glass in " + TICK_LIMIT + " paid ticks");
	}

	private static AbstractProcessingMachineBlockEntity place(GameTestHelper helper, Supplier<Block> block,
			String what) {
		helper.setBlock(MACHINE, block.get());
		AbstractProcessingMachineBlockEntity be =
				helper.getBlockEntity(MACHINE, AbstractProcessingMachineBlockEntity.class);
		if (be == null) {
			throw new IllegalStateException("fixture: no processing block entity for " + what);
		}
		return be;
	}
}
