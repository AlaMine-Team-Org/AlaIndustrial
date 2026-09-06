package dev.alaindustrial.core.item;

import dev.alaindustrial.Config;
import java.util.function.IntSupplier;

/**
 * The two grades of item pipe (MOD-581) — everything that differs between them, in one place.
 *
 * <p>Same shape as {@link dev.alaindustrial.core.energy.CableType}, {@code BladeTier} and
 * {@code MagnetTier}: the values are {@link IntSupplier}s rather than ints because {@code Config} is
 * mutable at runtime, and a tier resolved at class-init must still see a reloaded knob.
 *
 * <p><b>A network runs at its WEAKEST grade</b> — see {@link #min}. That is the rule the mod already
 * teaches on cables (ADR-001: a thin segment throttles the line), so a player who has wired energy
 * guesses pipe behaviour without reading anything. It is also why the advanced pipe is visibly
 * thicker: a rule that punishes one forgotten segment must let the player SEE which segment.
 */
public enum PipeTier {

	/** The original (MOD-104/108): 2 items per transfer, slim body. */
	BASIC(() -> Config.itemPipeItemsPerTransfer),

	/** The second grade (MOD-581): twice the throughput, visibly thicker body. */
	ADVANCED(() -> Config.itemPipeAdvancedItemsPerTransfer);

	private final IntSupplier itemsPerTransfer;

	PipeTier(IntSupplier itemsPerTransfer) {
		this.itemsPerTransfer = itemsPerTransfer;
	}

	/** Items this grade moves to each target per transfer. */
	public int itemsPerTransfer() {
		return itemsPerTransfer.getAsInt();
	}

	/**
	 * The weaker of two grades — the fold a network applies over its own segments.
	 *
	 * <p>Weaker means "moves fewer items", read from the live knobs rather than from the declaration
	 * order: a pack that raises the basic grade above the advanced one gets the behaviour its numbers
	 * describe, instead of a ladder frozen at the moment this enum was written.
	 */
	public static PipeTier min(PipeTier a, PipeTier b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a.itemsPerTransfer() <= b.itemsPerTransfer() ? a : b;
	}
}
