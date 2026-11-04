package dev.alaindustrial.block;

import dev.alaindustrial.core.structure.ReactorCore;
import net.minecraft.util.StringRepresentable;

/**
 * The alarm a reactor antenna's boom is showing (MOD-945), held in its block state so that {@code getSignal} reads
 * its own state and never looks for a block entity.
 */
public enum ReactorAlarmLevel implements StringRepresentable {
	/** The reactor is fine, or the antenna is silent. */
	NONE("none", ReactorCore.ALARM_SIGNAL_NONE),
	/** The warning latch holds. */
	WARN("warn", ReactorCore.ALARM_SIGNAL_WARN),
	/** The meltdown latch holds or the countdown is armed. */
	CRITICAL("critical", ReactorCore.ALARM_SIGNAL_CRITICAL);

	private final String serializedName;
	private final int signal;

	ReactorAlarmLevel(String serializedName, int signal) {
		this.serializedName = serializedName;
		this.signal = signal;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** The redstone level the boom gives out. */
	public int signal() {
		return signal;
	}

	/** The state showing redstone level {@code signal}: 15 and over is critical, anything else above 0 a warning. */
	public static ReactorAlarmLevel of(int signal) {
		if (signal >= CRITICAL.signal) {
			return CRITICAL;
		}
		return signal > NONE.signal ? WARN : NONE;
	}
}
