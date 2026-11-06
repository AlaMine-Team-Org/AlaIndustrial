package dev.alaindustrial.block.entity.quarry;

import dev.alaindustrial.core.machine.StatusLine;
import java.util.Locale;

/**
 * What a quarry is doing, or why it stands (MOD-951; spec {@code docs/blocks/mining/quarry.md}, "work cycle" and
 * "stops"). The four working phases of one operation are not blocking; every stop is, except {@link #DONE}, which
 * holds nothing up — there is nothing left to dig.
 */
public enum QuarryWorkStatus implements StatusLine {
	/** The frame does not stand; the screen names the first broken rule instead of this caption. */
	NOT_FORMED(true),
	TRAVELLING(false),
	LOWERING(false),
	DRILLING(false),
	RAISING(false),
	NO_POWER(true),
	OUTPUT_FULL(true),
	NO_BIT(true),
	CHUNK_NOT_LOADED(true),
	NO_OWNER(true),
	DONE(false),
	/** The frame stands but the operator switched digging off ({@code Config.quarryDiggingEnabled}). */
	DIGGING_OFF(false),
	/** The drill reached the top of its heat scale and stands until it has cooled to half (MOD-952). */
	OVERHEATED(true),
	/** The redstone mode keeps it standing (MOD-952). */
	REDSTONE_STOPPED(true);

	private final boolean blocking;

	QuarryWorkStatus(boolean blocking) {
		this.blocking = blocking;
	}

	@Override
	public String translationKey() {
		return "gui.alaindustrial.quarry.status." + name().toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean isBlocking() {
		return blocking;
	}

	/** Whether the quarry is in the middle of an operation. */
	public boolean working() {
		return this == TRAVELLING || this == LOWERING || this == DRILLING || this == RAISING;
	}

	public static QuarryWorkStatus byOrdinal(int ordinal) {
		QuarryWorkStatus[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NOT_FORMED;
	}
}
