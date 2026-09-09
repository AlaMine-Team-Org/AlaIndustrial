package dev.alaindustrial.core.monitor;

/**
 * What one panel is showing right now (MOD-480).
 *
 * <p>The three failure constants are separate on purpose. A wall that simply goes blank teaches the
 * player nothing, and the three causes have three different fixes: build back to the core, feed the
 * core more power, or fit another capacity card. The renderer draws a different mark for each.
 */
public enum MonitorReadout {

	/** No filter item in the panel — it shows nothing and costs nothing. */
	IDLE,

	/** Live number. */
	OK,

	/** This side of a broken wall has no core any more. */
	NO_CORE,

	/** The core's buffer cannot pay for everything the wall is showing. */
	NO_POWER,

	/** More filled panels than the fitted capacity cards can track. */
	NO_CAPACITY;

	private static final MonitorReadout[] VALUES = values();

	public static MonitorReadout byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
	}
}
