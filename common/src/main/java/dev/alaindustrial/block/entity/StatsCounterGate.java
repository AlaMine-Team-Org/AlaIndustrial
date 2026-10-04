package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.energy.EnergyBuffer;
import java.util.function.BooleanSupplier;

/**
 * Whether a block's lifetime energy counters run (MOD-692) — the one rule, kept free of Minecraft types
 * so an L1 test can pin it without a world.
 *
 * <p>Before MOD-692 the counters were switched on only from {@link MachineBlockEntity#recordEuRate}, so a
 * block whose tick never reported a rate — the pump, the Garden Drone Station, the Battery Box, the
 * CESU — showed zeros on a statistics panel it plainly had. The base tick now applies this rule before
 * every {@code onServerTick}, whatever the block does in it.
 *
 * <p><b>No panel, no counters.</b> Free Telemetry (MOD-483) makes {@link MachineBlockEntity#hasStatsChip()}
 * true for every block its owner placed, including the ones with no upgrade panel at all. Those blocks
 * keep their counters off: they never measured before this task, and switching telemetry on where no
 * panel promised it would be a new feature, not this fix.
 */
public final class StatsCounterGate {

	private StatsCounterGate() {}

	/**
	 * Point {@code energy}'s counters at the block's statistics state for this tick.
	 *
	 * @param hasUpgradePanel whether the block carries the upgrade panel
	 * @param statsEnabled    {@link MachineBlockEntity#hasStatsChip()} — asked only when there is a panel,
	 *                        because it reads the owner's skills and a panelless block has no use for them
	 */
	public static void sync(EnergyBuffer energy, boolean hasUpgradePanel, BooleanSupplier statsEnabled) {
		energy.setCountersEnabled(hasUpgradePanel && statsEnabled.getAsBoolean());
	}
}
