package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.NoUpgradePanel;
import dev.alaindustrial.core.machine.MachineRates;

/**
 * Clean counterpart of {@link StaticRateShortcutViolator} (MOD-710 batch 4b): the same static tariff call
 * from an ordinary method, in a class that has no upgrade panel — the electric heater's shape. With no
 * panel there is no overclocker chip the static formula could miss, so the negative control asserts this
 * class is absent from the report; drop the {@code NoUpgradePanel} exemption and it appears.
 */
public final class NoPanelRateReader implements NoUpgradePanel {

	int heatTickCost() {
		return MachineRates.euPerTick(Config.electricHeaterEuPerTick, Config.globalMachineSpeedMultiplier);
	}
}
