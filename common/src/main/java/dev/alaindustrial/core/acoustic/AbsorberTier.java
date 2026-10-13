package dev.alaindustrial.core.acoustic;

/**
 * The sound absorber's scale (MOD-804, owner decision 2026-10-13): how many face-joined absorbers a group
 * holds decides its tier, and the tier decides how far the quiet zone reaches past the group and how much it
 * takes off a sound inside it. The one place these numbers live — the block's state, the client's zones, the
 * tooltip, the HUD and the spec all read them from here.
 *
 * <p>Steps, not a curve: a player sees the lamps change at 2, 4, 8 and 16 blocks and knows what the next
 * block buys. There is no tier past V and no silence: −85 % is the ceiling.
 *
 * <p>Minecraft-free on purpose, so the scale is pinned on the L1 lane ({@code AbsorberTierTest}).
 */
public enum AbsorberTier {
	I(1, 5, 35),
	II(2, 8, 50),
	III(4, 12, 65),
	IV(8, 16, 75),
	V(16, 20, 85);

	/** Group size the readout counts up to; a bigger group reads "32+" and stays tier V. */
	public static final int GROUP_CAP = 32;

	/** The size from which a bigger group no longer changes anything: tier V starts here. */
	public static final int SATURATION = 16;

	private final int minBlocks;
	private final int radius;
	private final int dampeningPercent;

	AbsorberTier(int minBlocks, int radius, int dampeningPercent) {
		this.minBlocks = minBlocks;
		this.radius = radius;
		this.dampeningPercent = dampeningPercent;
	}

	/** The tier of a group of {@code blocks} absorbers; anything below one block is treated as one. */
	public static AbsorberTier forGroupSize(int blocks) {
		AbsorberTier found = I;
		for (AbsorberTier tier : values()) {
			if (blocks >= tier.minBlocks) {
				found = tier;
			}
		}
		return found;
	}

	/** The tier a block-state {@code tier} value (1..5) names. */
	public static AbsorberTier ofLevel(int level) {
		return values()[Math.clamp(level, 1, values().length) - 1];
	}

	/** 1..5, the value of the block's {@code tier} property and the number of lit lamps. */
	public int level() {
		return ordinal() + 1;
	}

	/** Smallest group that reaches this tier. */
	public int minBlocks() {
		return minBlocks;
	}

	/** How far the zone reaches past the group's bounding box, in blocks. */
	public int radius() {
		return radius;
	}

	/** How much a muffled sound loses, in percent. */
	public int dampeningPercent() {
		return dampeningPercent;
	}

	/** The volume multiplier a sound inside this tier's zone gets: {@code 1 - percent / 100}. */
	public float volumeFactor() {
		return 1.0f - dampeningPercent / 100.0f;
	}

	/** Largest group still in this tier, or {@link #GROUP_CAP} for the last one. */
	public int maxBlocks() {
		return ordinal() + 1 < values().length ? values()[ordinal() + 1].minBlocks - 1 : GROUP_CAP;
	}
}
