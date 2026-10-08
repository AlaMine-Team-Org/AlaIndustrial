package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/**
 * An ordinary class — not a knob holder — that reads {@code Config} on a caller's behalf (MOD-761): the
 * shape the one-step rules could not see, two calls away from the client stand-in.
 */
public final class KnobHelper {

	private KnobHelper() {
	}

	static int shown() {
		return Config.euPerXp;
	}

	/** Two steps from the knob: the client calls this, this calls {@link #shown()}. */
	public static int twice() {
		return shown() * 2;
	}

	/** Reached by the stand-in through a path the negative control accepts by name. */
	public static int accepted() {
		return Config.teleporterMaxPoints;
	}
}
