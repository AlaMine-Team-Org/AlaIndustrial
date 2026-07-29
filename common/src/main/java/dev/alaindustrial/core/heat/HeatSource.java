package dev.alaindustrial.core.heat;

import java.util.Locale;

/**
 * Loader-neutral heat classification used by externally heated machines.
 *
 * <p>This enum deliberately has no Minecraft types, so its level/output contract stays usable from
 * L1 tests. {@link WorldHeatSources} is the thin world-facing adapter.
 */
public enum HeatSource {
	NONE(0),
	CAMPFIRE(1),
	LAVA(2),
	MAGMA(2),
	LAVA_CAULDRON(2),
	ELECTRIC_HEATER(3);

	private static final HeatSource[] VALUES = values();
	private final int level;

	HeatSource(int level) {
		this.level = level;
	}

	public int level() {
		return level;
	}

	/** One unit of recipe output per heat level; no heat produces nothing. */
	public int outputMultiplier() {
		return level;
	}

	public String translationKey() {
		return "gui.alaindustrial.vulcanizer.heat." + name().toLowerCase(Locale.ROOT);
	}

	public static HeatSource byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
	}
}
