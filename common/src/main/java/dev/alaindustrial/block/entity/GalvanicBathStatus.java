package dev.alaindustrial.block.entity;

/**
 * Why the Galvanic Bath is not running, projected to the screen over a {@code ContainerData} channel
 * (MOD-127). Mirrors {@link VulcanizerStatus}: the machine has four independent reasons to idle
 * (fibre, silver, water, blocked output) and "no matching recipe" would be a misleading label for
 * three of them — most of all for water, which is not part of the recipe at all.
 */
public enum GalvanicBathStatus {
	/** Everything needed is present; the machine is plating. */
	READY,
	/** No fibre, or less than one operation's worth. */
	NO_FIBER,
	/** No silver dust, or less than one operation's worth. */
	NO_SILVER,
	/** The tank holds less water than one operation consumes. */
	NO_WATER,
	/** The two inputs do not form a known recipe. */
	NO_RECIPE,
	/** The result slot cannot take the output. */
	OUTPUT_BLOCKED;

	private static final GalvanicBathStatus[] VALUES = values();

	/** Resolve a status from the synced ordinal, falling back to {@link #READY} on a bad index. */
	public static GalvanicBathStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
	}

	/** Translation key for the screen's status line. */
	public String translationKey() {
		return "gui.alaindustrial.galvanic_bath.status." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
