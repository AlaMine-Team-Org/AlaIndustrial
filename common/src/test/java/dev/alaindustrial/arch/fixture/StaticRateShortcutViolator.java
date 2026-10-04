package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.machine.MachineRates;

/**
 * Deliberate violator for {@code ArchitectureRules.callStaticRateShortcutOutsideConstructor()}
 * (MOD-435): a machine-shaped class that calls the static tariff formula from an ordinary method — the
 * exact defect of an upgrade panel silently ignored (MOD-392). Both methods of the formula's home
 * {@code MachineRates} (MOD-710; the {@code Config} delegates that called them are gone since batch 4b).
 *
 * <p>The constructor ALSO calls a shortcut, on purpose: seeding from {@code <init>} is the one
 * place the rule allows, and the negative control asserts that only the method calls are reported.
 * A rule that flagged the constructor too would be red on every real machine.
 */
public final class StaticRateShortcutViolator {
	private final int maxProgress;

	StaticRateShortcutViolator() {
		this.maxProgress = MachineRates.duration(200, Config.globalMachineSpeedMultiplier);
	}

	int drainPerTick() {
		return MachineRates.euPerTick(Config.machineEuPerTick, Config.globalMachineSpeedMultiplier) + maxProgress;
	}

	int durationTicks() {
		return MachineRates.duration(maxProgress, Config.globalMachineSpeedMultiplier);
	}
}
