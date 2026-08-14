package dev.alaindustrial.core.heat;

import java.util.Locale;

/**
 * Loader-neutral heat classification used by externally heated machines.
 *
 * <p>This enum deliberately has no Minecraft types, so its level/output contract stays usable from
 * L1 tests. {@link WorldHeatSources} is the thin world-facing adapter.
 */
public enum HeatSource {
	NONE(0, -1),
	CAMPFIRE(1, 0),
	LAVA(2, 1),
	MAGMA(2, 1),
	LAVA_CAULDRON(2, 1),
	ELECTRIC_HEATER(3, 2);

	private static final HeatSource[] VALUES = values();
	private final int level;
	private final int iconIndex;

	HeatSource(int level, int iconIndex) {
		this.level = level;
		this.iconIndex = iconIndex;
	}

	public int level() {
		return level;
	}

	/**
	 * Column of this source's icon in the Vulcanizer atlas, or {@code -1} for "draw nothing".
	 *
	 * <p>Keyed on the source rather than derived from {@link #level()}, which is what the screen used to
	 * do. The two happen to agree today, and the derivation is the kind that stops being true quietly:
	 * it only holds while every level maps to exactly one picture, so the first source that shares a
	 * level with a different-looking one would silently draw the wrong icon rather than fail to compile.
	 */
	public int iconIndex() {
		return iconIndex;
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
