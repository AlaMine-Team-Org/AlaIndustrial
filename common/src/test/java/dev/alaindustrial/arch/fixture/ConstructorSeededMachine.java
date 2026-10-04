package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.machine.MachineRates;

/**
 * Clean counterpart of {@link StaticRateShortcutViolator} (MOD-435): the static shortcut is called
 * ONLY from the constructor, which is the allowed seeding case. The negative control asserts this
 * class is absent from the report — the regression guard for the {@code <init>} exemption.
 */
public final class ConstructorSeededMachine {
	private final int maxProgress;

	ConstructorSeededMachine() {
		this.maxProgress = MachineRates.duration(200, Config.globalMachineSpeedMultiplier);
	}

	int progressCeiling() {
		return maxProgress;
	}
}
