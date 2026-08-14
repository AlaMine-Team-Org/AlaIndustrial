package dev.alaindustrial.block.entity;

import java.util.Locale;

/**
 * What the Electric Heater's screen is reporting (MOD-418).
 *
 * <p>The block has no slots and never blocks on one, so unlike {@link VulcanizerStatus} this is not a
 * list of stalls: it is the heater's own state of matter. Two of the six are things the player must
 * fix ({@link #NO_CONSUMER}, {@link #NO_ENERGY}); the rest describe where on the ramp it currently is.
 */
public enum ElectricHeaterStatus {
	/** Nothing above that takes heat — the heater is furniture until a Vulcanizer sits on it. */
	NO_CONSUMER,
	/** A consumer is there, but the buffer cannot pay for even one heat tick. */
	NO_ENERGY,
	/** Working and climbing: supplying tier 2 (x2 output) while it earns tier 3. */
	WARMING,
	/** Working at full temperature: tier 3, x3 output. */
	HOT,
	/** Not working, but still holds heat — it will resume from here if work returns in time. */
	COOLING,
	/** Not working and fully cold. The resting state, and the one that costs nothing. */
	COLD;

	private static final ElectricHeaterStatus[] VALUES = values();

	public String translationKey() {
		return "gui.alaindustrial.electric_heater.status." + name().toLowerCase(Locale.ROOT);
	}

	/** Whether this state wants the player to do something, which the screen prints in red. */
	public boolean needsAttention() {
		return this == NO_CONSUMER || this == NO_ENERGY;
	}

	/** Whether the heater is actively serving a machine — the switch between real numbers and a dash. */
	public boolean isWorking() {
		return this == WARMING || this == HOT;
	}

	public static ElectricHeaterStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : COLD;
	}
}
