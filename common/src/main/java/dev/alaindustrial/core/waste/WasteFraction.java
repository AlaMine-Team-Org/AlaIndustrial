package dev.alaindustrial.core.waste;

/**
 * The four buckets the Recycler sorts incoming junk into (MOD-145).
 *
 * <p>Three of them are real fractions and decide the grade of the slag briquette; {@link #OTHER} is the
 * classifier's fallback and deliberately does <b>not</b> count as variety. Without that exception a stack
 * of unrecognised items from another mod would read as "a third kind of waste" and hand the player the
 * best grade for free — the machine would reward ignorance instead of sorting.
 *
 * <p>The order is the wire format: the ordinal travels to the screen on a {@code ContainerData} channel,
 * so new constants are appended, never inserted.
 */
public enum WasteFraction {
	/** Stone, dirt, sand, glass — anything that would end up as ash and grit. */
	MINERAL,
	/** Scrap metal: worn tools, armour, rails, ingots, our own machine parts. */
	METAL,
	/** Wood, wool, cloth, paper — the part of the batch that actually burns. */
	COMBUSTIBLE,
	/** Everything the classifier could not place. Counts toward mass, never toward variety. */
	OTHER;

	/** The three fractions that decide the grade — {@link #OTHER} excluded on purpose. */
	public static final int GRADED_COUNT = 3;

	public static WasteFraction byOrdinal(int ordinal) {
		WasteFraction[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OTHER;
	}
}
