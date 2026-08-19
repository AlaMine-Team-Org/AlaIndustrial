package dev.alaindustrial.block.entity;

import java.util.Locale;

/**
 * Why a one-input processing machine — Compressor, Macerator, Extractor, Electric Furnace, Sawmill —
 * is not turning out product right now (MOD-458).
 *
 * <p><b>Why this one is shared where the other eight are not.</b> Every previous status channel in the
 * mod belongs to a single block, because every one of them names a gate only that block has: a redstone
 * signal, a heater below, a glass dome, a blueprint. The gates here are the opposite — they are the ones
 * {@link AbstractProcessingMachineBlockEntity}'s tick loop already evaluates for all five machines, in
 * one expression. A per-machine copy would be five identical enums and five identical sets of keys in
 * twenty-one languages.
 *
 * <p><b>The order of the constants is the wire format</b> (the ordinal travels to the screen on a
 * {@code ContainerData} channel), so new states must be appended, never inserted.
 *
 * <p><b>{@link #READY} must stay at ordinal 0.</b> A client-side menu stub starts its data array at
 * zeroes, and the L3 shot stands inject only the four base channels — so ordinal 0 is what every frame
 * that does not deliberately set a status reads back. Anything but a silent state there would print a
 * status line over screenshots that are meant to show the machine saying nothing.
 */
public enum ProcessingMachineStatus {
	/** Working, or able to work. Deliberately draws no status line. */
	READY,
	/**
	 * The input slot is empty. Silent by choice: the empty slot is right there, half a centimetre from
	 * where the line would print, and the Incubator already set this precedent with its own NO_INPUT.
	 */
	NO_INPUT,
	/** There is something in the slot, but this machine has no recipe for it. */
	NO_RECIPE,
	/**
	 * A batch recipe matched, but the slot holds less than one operation's worth (MOD-455/MOD-458).
	 *
	 * <p>This is the state the whole channel was built for. {@code AlaProcessingRecipe.matches} ignores
	 * counts on purpose, so a partial batch <em>resolves</em> — the machine simply stops, spending nothing
	 * and saying nothing. The redstone recipe makes it routine rather than exotic: 64 ÷ 9 leaves one item
	 * in the slot after every full stack, which without this line reads as a jam.
	 */
	NOT_ENOUGH_INPUT,
	/** The output slot cannot take another result. */
	OUTPUT_BLOCKED,
	/**
	 * The buffer cannot pay for a tick, and has failed to for long enough that this is not just the gap
	 * between two deliveries — see {@link AbstractProcessingMachineBlockEntity}'s starvation grace.
	 */
	NO_ENERGY;

	private static final ProcessingMachineStatus[] VALUES = values();

	/**
	 * One key namespace for the whole family rather than one per block. The eight single-block channels
	 * key off their own block id because their states are block-specific; these five machines share the
	 * states verbatim, so five copies of every key in twenty-one files would be five chances to drift.
	 */
	public String translationKey() {
		return "gui.alaindustrial.processing_machine.status." + name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Whether this state should print a status line.
	 *
	 * <p>Two states stay silent. {@link #READY} because there is nothing to report, like every other
	 * machine's. {@link #NO_INPUT} because an empty slot is already the loudest thing on the screen —
	 * captioning it would put a line under four machines that spend most of their life idle and empty.
	 */
	public boolean isBlocking() {
		return this != READY && this != NO_INPUT;
	}

	/** Decode a wire ordinal, clamping anything out of range to the silent state. */
	public static ProcessingMachineStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
	}
}
