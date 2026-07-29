package dev.alaindustrial.block.entity;

import java.util.Locale;

/** Player-facing reason why the Vulcanizer is not advancing. */
public enum VulcanizerStatus {
	READY,
	NO_HEAT,
	NO_RAW_RUBBER,
	NO_SULFUR,
	NO_RECIPE,
	OUTPUT_BLOCKED;

	private static final VulcanizerStatus[] VALUES = values();

	public String translationKey() {
		return "gui.alaindustrial.vulcanizer.status." + name().toLowerCase(Locale.ROOT);
	}

	public boolean isBlocking() {
		return this != READY;
	}

	public static VulcanizerStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
	}
}
