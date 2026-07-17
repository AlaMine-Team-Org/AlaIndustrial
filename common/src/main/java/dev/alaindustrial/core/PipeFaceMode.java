package dev.alaindustrial.core;

import net.minecraft.util.StringRepresentable;

/** Per-face item-pipe routing mode. Ordinals are persisted in two-bit NBT fields. */
public enum PipeFaceMode implements StringRepresentable {
	NEUTRAL("neutral"),
	EXTRACT("extract"),
	INSERT("insert"),
	DISABLED("disabled");

	private final String serializedName;

	PipeFaceMode(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/**
	 * Order the wrench walks through (MOD-108), deliberately NOT the enum order.
	 *
	 * <p>A fresh face is {@link #NEUTRAL}, so putting the useful modes right after it means the first
	 * click sets EXTRACT and the second INSERT — the two a player actually wants. {@link #DISABLED} sits
	 * last, where it is reached on purpose rather than stumbled into. Before this the wrench walked the
	 * enum backwards and the first click landed on DISABLED.
	 *
	 * <p>The enum's own ordinals are the on-disk format (two bits per face), so this ladder lives here
	 * instead of reordering the constants — renumbering them would rewrite every saved pipe.
	 */
	private static final PipeFaceMode[] WRENCH_CYCLE = { NEUTRAL, EXTRACT, INSERT, DISABLED };

	/** The mode a wrench click moves this face to. */
	public PipeFaceMode nextInCycle() {
		for (int i = 0; i < WRENCH_CYCLE.length; i++) {
			if (WRENCH_CYCLE[i] == this) {
				return WRENCH_CYCLE[(i + 1) % WRENCH_CYCLE.length];
			}
		}
		return NEUTRAL;
	}

	/** Translation key used by the wrench overlay on every supported locale. */
	public String translationKey() {
		return switch (this) {
			case NEUTRAL -> "message.alaindustrial.item_pipe.mode.neutral";
			case EXTRACT -> "message.alaindustrial.item_pipe.mode.extract";
			case INSERT -> "message.alaindustrial.item_pipe.mode.insert";
			case DISABLED -> "message.alaindustrial.item_pipe.mode.disabled";
		};
	}
}
