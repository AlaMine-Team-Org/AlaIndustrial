package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.EnergyBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * How a scenario runs a powered machine by hand (MOD-717, TST-3): the loops every processing-machine scenario
 * class used to copy as its own private {@code drivePowered}/{@code driveUnpowered}. Ticking itself stays in
 * {@link AlaGameTestHelper#drive} — the version seam, which takes no more helpers — and is reached from there,
 * not copied either.
 *
 * <p>A new machine scenario calls these and passes its own EU figure; it does not declare a private copy.
 */
public final class GameTestDrive {

	private GameTestDrive() {}

	/**
	 * Ticks {@code be} {@code ticks} times, setting its buffer to {@code eu} before EVERY tick, so supply never
	 * limits the operation under test. The buffer is written directly ({@code setAmountUntracked}), past the
	 * intake cap, the way the copies did.
	 */
	public static void drivePowered(EnergyBlockEntity be, GameTestHelper helper, int ticks, long eu) {
		for (int i = 0; i < ticks; i++) {
			be.getEnergyStorage().setAmountUntracked(eu);
			AlaGameTestHelper.drive(be, helper, 1);
		}
	}

	/** Empties the buffer of {@code be} once, then ticks it {@code ticks} times: the machine must sit still. */
	public static void driveUnpowered(EnergyBlockEntity be, GameTestHelper helper, int ticks) {
		be.getEnergyStorage().setAmountUntracked(0L);
		AlaGameTestHelper.drive(be, helper, ticks);
	}
}
