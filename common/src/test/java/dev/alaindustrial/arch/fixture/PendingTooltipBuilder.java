package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/**
 * Plays {@code DrillTooltips.of} reverted (MOD-761): it asks the pending method for the capacity, AND reads a
 * knob of its own that should have come from {@code ServerBalance}. The pending marker must not hide that read.
 */
public final class PendingTooltipBuilder {

	private PendingTooltipBuilder() {
	}

	public static int of() {
		return PendingCapacity.capacity() + Config.teleporterMaxPoints;
	}
}
