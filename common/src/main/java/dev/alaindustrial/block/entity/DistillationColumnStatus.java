package dev.alaindustrial.block.entity;

/**
 * Why the Distillation Column is (not) running, projected to the screen over a {@code ContainerData}
 * channel (MOD-251). Mirrors {@link GalvanicBathStatus}: the column has several independent reasons
 * to idle — and two of them ("diesel tank full" / "fuel-oil tank full") point at different faces of
 * the tower, so a single "output blocked" label would send the player to the wrong pump.
 */
public enum DistillationColumnStatus {
	/** Hot and distilling. */
	WORKING,
	/** Powered and fed, but still heating up to operating temperature. */
	WARMING,
	/** The energy buffer cannot cover even one tick's draw. */
	NO_ENERGY,
	/** The oil tank holds less than one distillation's volume. */
	NO_OIL,
	/** The diesel tank (top segment) has no room for the next run's yield. */
	DIESEL_FULL,
	/** The fuel-oil tank (bottom segment) has no room for the next run's yield. */
	FUEL_OIL_FULL,
	/** Coked up (round 2): fouling hit 100 % — nothing runs until a wrench cleaning. */
	FOULED;

	private static final DistillationColumnStatus[] VALUES = values();

	/** Resolve a status from the synced ordinal, falling back to {@link #WORKING} on a bad index. */
	public static DistillationColumnStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : WORKING;
	}

	/** Translation key for the screen's status line. */
	public String translationKey() {
		return "gui.alaindustrial.distillation_column.status." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
