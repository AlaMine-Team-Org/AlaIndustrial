package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * L2 for what the overclocker chip actually DOES to a running machine (MOD-392).
 *
 * <p>Until this suite the chip's central promise — shorter operation, higher draw — was covered only
 * by {@code OverclockMathTest}, which multiplies numbers in isolation, and by the slot tests, which
 * only decide what may be put where. Neither would notice a machine that never consulted the helpers:
 * the arithmetic would stay correct while the macerator ground on at its old speed, and the release
 * notes would still promise a speed-up. So this measures a real machine in a real world.
 */
public final class OverclockerEffectScenarios {

	private OverclockerEffectScenarios() {}

	private static final BlockPos PLAIN = new BlockPos(1, 2, 1);
	private static final BlockPos CHIPPED = new BlockPos(3, 2, 1);
	/** Panel arm the overclocker sits on. */
	private static final int OVERCLOCK_ARM = 1;
	/** Data channel indices on every machine: 0 energy, 2 progress. */
	private static final int DATA_ENERGY = 0;
	private static final int DATA_PROGRESS = 2;
	/** Generous ceiling; the un-chipped run is the slow one and still finishes far inside this. */
	private static final int TICK_BUDGET = 400;

	/**
	 * A chip makes the same machine finish sooner and spend more EU per tick.
	 *
	 * <p>Two macerators side by side, identical but for the chip, measured against EACH OTHER rather
	 * than against a number recomputed here. Comparing to a formula would re-derive
	 * {@code Config.overclockerSpeedFactor} on the test side and then agree with itself no matter what
	 * the machine did — the tautology this project has been bitten by before. What is asserted is the
	 * DIRECTION of both effects, plus a floor that catches the opposite failure.
	 */
	public static void overclocker_chipSpeedsTheMachineAndCostsMore(GameTestHelper helper) {
		Run plain = runOneOperation(helper, PLAIN, null);
		Run chipped = runOneOperation(helper, CHIPPED, ModContent.OVERCLOCKER_CHIP_I.get());

		if (chipped.ticks >= plain.ticks) {
			helper.fail("a chipped macerator took " + chipped.ticks + " ticks against " + plain.ticks
					+ " unchipped — the chip is in the slot but the machine never asks for it");
		}
		if (chipped.euPerTick <= plain.euPerTick) {
			helper.fail("a chipped macerator drew " + chipped.euPerTick + " EU/t against "
					+ plain.euPerTick + " unchipped — speed without a price is not the deal");
		}
		// Direction alone is necessary but not sufficient: a machine finishing in one tick satisfies it
		// too. Bound the speed-up by what the HIGHEST tier could reach, so counting one chip as several
		// fails here instead of shipping.
		int floor = (int) (plain.ticks * Math.pow(Config.overclockerSpeedFactor,
				Config.overclockerMaxPerMachine)) - 1;
		if (chipped.ticks < floor) {
			helper.fail("one tier-I chip cut the operation to " + chipped.ticks + " ticks, under " + floor
					+ " — the floor for the top tier. A single chip is being counted as several");
		}
		helper.succeed();
	}

	/**
	 * Progress and the installed chip survive a save/load round trip.
	 *
	 * <p>Mid-operation is the case that matters: a chunk unloading halfway through a grind must not
	 * hand the player a machine that restarts from zero, nor one that quietly dropped the chip and
	 * finished at base speed.
	 */
	public static void overclocker_chipAndProgressSurviveReload(GameTestHelper helper) {
		MachineBlockEntity machine = AlaGameTestHelper.place(helper, PLAIN, ModContent.MACERATOR.get());
		machine.setItem(0, new ItemStack(Items.RAW_IRON, 8));
		machine.setItem(machine.upgradeSlotStart() + OVERCLOCK_ARM,
				new ItemStack(ModContent.OVERCLOCKER_CHIP_I.get()));
		machine.getEnergyStorage().amount = machine.getEnergyStorage().getCapacity();
		AlaGameTestHelper.drive(machine, helper, 3);

		int progressBefore = machine.dataAccess.get(DATA_PROGRESS);
		if (progressBefore <= 0) {
			helper.fail("the machine made no progress in three ticks, so a round trip of it proves nothing");
		}

		BlockPos abs = helper.absolutePos(PLAIN);
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = machine.saveCustomOnly(registries);
		MaceratorBlockEntity restored =
				new MaceratorBlockEntity(abs, helper.getLevel().getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.dataAccess.get(DATA_PROGRESS) != progressBefore) {
			helper.fail("progress was " + progressBefore + " before the round trip and "
					+ restored.dataAccess.get(DATA_PROGRESS) + " after");
		}
		ItemStack chip = restored.getItem(restored.upgradeSlotStart() + OVERCLOCK_ARM);
		if (!chip.is(ModContent.OVERCLOCKER_CHIP_I.get())) {
			helper.fail("the overclocker chip did not survive the round trip, got " + chip);
		}
		helper.succeed();
	}

	/** How one grind went: how many ticks it took, and the EU the machine burned on a working tick. */
	private record Run(int ticks, int euPerTick) {}

	/** Drives one macerator through a single grind and reports how long it took and what it drew. */
	private static Run runOneOperation(GameTestHelper helper, BlockPos at, @Nullable Item chip) {
		MachineBlockEntity machine = AlaGameTestHelper.place(helper, at, ModContent.MACERATOR.get());
		machine.setItem(0, new ItemStack(Items.RAW_IRON, 8));
		if (chip != null) {
			machine.setItem(machine.upgradeSlotStart() + OVERCLOCK_ARM, new ItemStack(chip));
		}

		int ticks = 0;
		int perTick = 0;
		while (machine.getItem(1).isEmpty() && ticks < TICK_BUDGET) {
			// Refill before every tick: this measures the RATE the machine burns, not how long its
			// buffer lasts. Left to drain, the overclocked one would starve and finish LATER, turning
			// the comparison upside down.
			machine.getEnergyStorage().amount = machine.getEnergyStorage().getCapacity();
			int before = machine.dataAccess.get(DATA_ENERGY);
			AlaGameTestHelper.drive(machine, helper, 1);
			int drawn = before - machine.dataAccess.get(DATA_ENERGY);
			if (drawn > 0) {
				perTick = drawn;
			}
			ticks++;
		}
		if (ticks >= TICK_BUDGET) {
			helper.fail("the macerator at " + at + " never finished an operation in " + TICK_BUDGET
					+ " ticks — the rig is broken, so any comparison against it is meaningless");
		}
		if (perTick <= 0) {
			helper.fail("the macerator at " + at + " finished without ever drawing EU — the measurement "
					+ "is empty and the comparison would be between two zeroes");
		}
		return new Run(ticks, perTick);
	}
}
