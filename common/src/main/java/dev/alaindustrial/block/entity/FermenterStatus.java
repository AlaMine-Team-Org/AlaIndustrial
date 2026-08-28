package dev.alaindustrial.block.entity;

import java.util.Locale;

/**
 * Why a fermenter is not brewing, for the GUI's status line (MOD-146).
 *
 * <p>Travels as an ordinal over one {@code ContainerData} channel, so the declaration order is part
 * of the client contract — append, never reorder.
 */
public enum FermenterStatus {
	/** Brewing, or able to. */
	READY,
	/** The organic input slot is empty or holds too little for the recipe's batch. */
	NO_ORGANIC,
	/** The water tank is below one batch's cost. */
	NO_WATER,
	/** There is something in the slot, but no fermenting recipe accepts it. */
	NO_RECIPE,
	/** The biofuel tank cannot take another batch's brew. */
	BIOFUEL_FULL,
	/** The biomass output slot cannot take another batch's result. */
	OUTPUT_BLOCKED;

	private static final FermenterStatus[] VALUES = values();

	/** Client-side inverse of {@link #ordinal()}; out-of-range ordinals read as {@link #READY}. */
	public static FermenterStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
	}

	public String translationKey() {
		return "gui.alaindustrial.fermenter.status." + name().toLowerCase(Locale.ROOT);
	}
}
