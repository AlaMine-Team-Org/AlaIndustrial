package dev.alaindustrial.settlement.works;

import java.util.Locale;

/**
 * Where one building of an ALA town stands on its way back (MOD-857, epic decision R79). Derived from the world
 * and the work queue by {@link BuildingStateRules}, never taken on trust: a block broken by a player or an explosion
 * moves the building back by itself.
 *
 * <p>Minecraft-free on purpose: the L1 lane reads it.
 */
public enum BuildingState {
	/** Left as the town was found: no job open on it, never restored. */
	RUIN,
	/** A clearing job is open on it and it was never restored (R85): the debris goes first. */
	CLEARING,
	/** A building job is open on it, or it was restored once and has lost blocks since. */
	REPAIR,
	/** Restored (R86): at least the definition's share of its intact template, every key point in place. */
	INTACT,
	/** Reserved for the building upgrades of a later stage; no rule produces it yet. */
	UPGRADED;

	/** The name in saves and in translation keys: {@code ruin}, {@code clearing}, … */
	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The state saved as {@code name}, or {@link #RUIN} for a name this build does not know. */
	public static BuildingState byName(String name) {
		for (BuildingState state : values()) {
			if (state.serializedName().equals(name)) {
				return state;
			}
		}
		return RUIN;
	}
}
