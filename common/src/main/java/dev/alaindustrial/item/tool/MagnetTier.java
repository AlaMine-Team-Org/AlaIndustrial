package dev.alaindustrial.item.tool;

import dev.alaindustrial.item.ToolConfig;

/**
 * The two grades of Electromagnet (MOD-580) — everything that differs between them, in one place.
 *
 * <p>Before this, {@link MagnetItem} read {@code Config.magnet*} directly, which is exactly right
 * while there is one magnet and exactly wrong the moment there are two: a second tier would either
 * share the first one's numbers or grow an {@code if} at every read. The same shape as
 * {@link dev.alaindustrial.block.entity.BladeTier} (MOD-145), for the same reason.
 *
 * <p>The values are read live rather than stored because {@code Config} is mutable at runtime — a
 * reloaded config must reach a tier that was resolved at class-init, or the knobs would silently stop
 * working for magnets. The tooltip, drawn on the client, passes the server's numbers to the same
 * {@link #pick} (MOD-761): one picking, two sources.
 */
public enum MagnetTier {

	/** The original (MOD-132): five blocks, items only. */
	BASIC(1),

	/**
	 * The second grade (MOD-580): reaches further and picks up experience.
	 *
	 * <p>Experience is the tier's own mechanic rather than a bigger number, and it is deliberately
	 * priced separately from items: an orb is worth more to the player than a cobblestone, and a mob
	 * farm should not be a free ride.
	 */
	ADVANCED(3);

	private final int moduleSlots;

	MagnetTier(int moduleSlots) {
		this.moduleSlots = moduleSlots;
	}

	/**
	 * This grade's value among the two its caller read — the one picking both sources go through: the live
	 * knobs below, and the server's numbers the magnet's tooltip passes in from {@code ServerBalance}
	 * (MOD-761).
	 */
	public int pick(int basic, int advanced) {
		return this == BASIC ? basic : advanced;
	}

	/** Pull radius in blocks around the carrier. */
	public int range() {
		return pick(ToolConfig.magnetRange, ToolConfig.magnetAdvancedRange);
	}

	/** EU the magnet holds. */
	public int buffer() {
		return pick(ToolConfig.magnetBuffer, ToolConfig.magnetAdvancedBuffer);
	}

	/** Max EU/tick it accepts while charging in a slot. */
	public int inputRate() {
		return pick(ToolConfig.magnetInputRate, ToolConfig.magnetAdvancedInputRate);
	}

	/** EU spent per item actually nudged. */
	public int euPerItem() {
		return pick(ToolConfig.magnetEuPerItem, ToolConfig.magnetAdvancedEuPerItem);
	}

	/** EU spent per experience orb nudged; {@code 0} means this tier does not pull experience. */
	public int euPerOrb() {
		return euPerOrb(ToolConfig.magnetAdvancedEuPerOrb);
	}

	/** {@link #euPerOrb()} given the advanced grade's price its caller read: the basic grade pulls no orb. */
	public int euPerOrb(int advanced) {
		return pick(0, advanced);
	}

	/**
	 * Module slots on the magnet's own screen (MOD-592): one on the basic grade, three on the advanced.
	 * A plain int, not a config knob — it is a slot count on a screen, and a stack must never find
	 * itself holding more modules than a changed config lets it show.
	 */
	public int moduleSlots() {
		return moduleSlots;
	}

	/** Whether this grade reaches experience orbs at all. */
	public boolean pullsExperience() {
		return euPerOrb() > 0;
	}
}
