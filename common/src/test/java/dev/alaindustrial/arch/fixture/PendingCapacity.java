package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/** Plays {@code ItemEnergy.capacity} for the negative control (MOD-761): the last link of a pending path. */
public final class PendingCapacity {

	private PendingCapacity() {
	}

	public static int capacity() {
		return Config.euPerXp;
	}
}
