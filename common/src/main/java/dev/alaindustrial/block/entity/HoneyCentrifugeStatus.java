package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.machine.StatusLine;
import java.util.Locale;

/**
 * Why a honey centrifuge is not spinning, for the GUI's status line (MOD-924).
 *
 * <p>Travels as an ordinal over one {@code ContainerData} channel, so the declaration order is part of the
 * client contract — append, never reorder.
 */
public enum HoneyCentrifugeStatus implements StatusLine {
	/** Spinning, or able to. */
	READY,
	/** The input slot is empty. */
	NO_INPUT,
	/** There is something in the slot, but no honey centrifuging recipe accepts it. */
	NO_RECIPE,
	/** The honey tank cannot take another operation's yield. */
	TANK_FULL,
	/** The container slot cannot take another empty bottle. */
	OUTPUT_BLOCKED;

	private static final HoneyCentrifugeStatus[] VALUES = values();

	/** Client-side inverse of {@link #ordinal()}; out-of-range ordinals read as {@link #READY}. */
	public static HoneyCentrifugeStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
	}

	@Override
	public String translationKey() {
		return "gui.alaindustrial.honey_centrifuge.status." + name().toLowerCase(Locale.ROOT);
	}

	/** {@link StatusLine}: whether this state holds the machine up (see the interface). */
	@Override
	public boolean isBlocking() {
		return this != READY;
	}
}
