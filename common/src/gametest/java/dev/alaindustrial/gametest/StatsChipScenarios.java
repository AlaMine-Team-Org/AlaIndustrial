package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * L2 suite for the statistics chip (MOD-125) — the gate that decides whether a machine measures at all.
 *
 * <p>The L1 tests cover the counter arithmetic inside {@code EnergyBuffer}. What they cannot reach is the
 * wiring this suite exists for: that a chip sitting in a slot actually turns that arithmetic on, and that
 * an un-instrumented machine stays at zero while doing real work. That link runs through the block
 * entity's inventory and its tick, neither of which exists without Minecraft.
 */
public final class StatsChipScenarios {

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
}
