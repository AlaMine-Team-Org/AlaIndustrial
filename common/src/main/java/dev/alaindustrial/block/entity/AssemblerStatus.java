package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.machine.AssemblyRefusal;
import dev.alaindustrial.core.machine.StatusLine;

/**
 * Why the assembler is idle — the status line of its GUI (MOD-275, spec: "Interface"). Its own file
 * since MOD-714, like the status enums of the other machines.
 *
 * <p>Since MOD-714 it also names the status a planner refusal shows ({@link #of}) and which of several
 * idle reasons the line keeps ({@link #moreActionable}).
 *
 * <p>Ordinals travel over channel 5 of the machine's {@code ContainerData}, so the order is part of the
 * client/server contract of one session — nothing persists it. The lang keys follow the constant names.
 */
public enum AssemblerStatus implements StatusLine {
	/** Producing (or able to produce) — no complaint. */
	READY,
	/** Every blueprint slot is empty: the machine has nothing to make. */
	NO_BLUEPRINT,
	/** The warehouse cannot supply the active blueprint's ingredients. */
	NO_MATERIALS,
	/** Every output slot is full — production stops without burning EU. */
	OUTPUT_FULL,
	/** The buffer is below one tick's draw. */
	NO_ENERGY,
	/** The blueprint's recipe no longer exists (e.g. the datapack changed under it). */
	NO_RECIPE;

	private static final AssemblerStatus[] VALUES = values();

	/** Resolve a status from the synced ordinal, falling back to {@link #READY} on a bad index. */
	public static AssemblerStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
	}

	/** The status line a refused plan shows: one constant per refusal, of the same name. */
	public static AssemblerStatus of(AssemblyRefusal reason) {
		return switch (reason) {
			case NO_BLUEPRINT -> NO_BLUEPRINT;
			case NO_MATERIALS -> NO_MATERIALS;
			case NO_RECIPE -> NO_RECIPE;
			case OUTPUT_FULL -> OUTPUT_FULL;
		};
	}

	/**
	 * Of two idle reasons, the one worth showing the player — {@code a} when they rank the same.
	 *
	 * <p>With three blueprints stalled for three different reasons, only one line fits — so it shows the
	 * one the player can do something about first: a full output area is a jam they must clear, a
	 * missing recipe is a broken blueprint, and missing materials is the ordinary "waiting for the
	 * pipes" state.
	 */
	public static AssemblerStatus moreActionable(AssemblerStatus a, AssemblerStatus b) {
		return rank(b) > rank(a) ? b : a;
	}

	private static int rank(AssemblerStatus status) {
		return switch (status) {
			case OUTPUT_FULL -> 3;
			case NO_RECIPE -> 2;
			case NO_MATERIALS -> 1;
			default -> 0;
		};
	}

	/** Translation key for the screen's status line. */
	public String translationKey() {
		return "gui.alaindustrial.assembler.status." + name().toLowerCase(java.util.Locale.ROOT);
	}

	/** {@link StatusLine}: whether this state holds the machine up (see the interface). */
	@Override
	public boolean isBlocking() {
		return this != READY;
	}
}
