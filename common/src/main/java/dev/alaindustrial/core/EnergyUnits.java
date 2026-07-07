package dev.alaindustrial.core;

/**
 * The single source of truth for the EU display unit ↔ Team Reborn Energy unit ratio.
 * Defined once and used everywhere so the whole mod stays internally consistent.
 */
public final class EnergyUnits {
	private EnergyUnits() {
	}

	/** Underlying API units per 1 EU. Kept at 1:1 for Stage 1. */
	public static final long UNITS_PER_EU = 1L;
}
