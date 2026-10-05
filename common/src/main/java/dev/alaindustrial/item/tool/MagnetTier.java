package dev.alaindustrial.item.tool;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.ToolConfig;
import java.util.function.IntSupplier;

/**
 * The two grades of Electromagnet (MOD-580) — everything that differs between them, in one place.
 *
 * <p>Before this, {@link MagnetItem} read {@code Config.magnet*} directly, which is exactly right
 * while there is one magnet and exactly wrong the moment there are two: a second tier would either
 * share the first one's numbers or grow an {@code if} at every read. The same shape as
 * {@link dev.alaindustrial.block.entity.BladeTier} (MOD-145), for the same reason.
 *
 * <p>The values are {@link IntSupplier}s rather than ints because {@code Config} is mutable at
 * runtime — a reloaded config must reach a tier that was resolved at class-init, or the knobs would
 * silently stop working for magnets.
 */
public enum MagnetTier {

	/** The original (MOD-132): five blocks, items only. */
	BASIC(() -> ToolConfig.magnetRange,
			() -> ToolConfig.magnetBuffer,
			() -> ToolConfig.magnetInputRate,
			() -> ToolConfig.magnetEuPerItem,
			() -> 0,
			1),

	/**
	 * The second grade (MOD-580): reaches further and picks up experience.
	 *
	 * <p>Experience is the tier's own mechanic rather than a bigger number, and it is deliberately
	 * priced separately from items: an orb is worth more to the player than a cobblestone, and a mob
	 * farm should not be a free ride.
	 */
	ADVANCED(() -> ToolConfig.magnetAdvancedRange,
			() -> ToolConfig.magnetAdvancedBuffer,
			() -> ToolConfig.magnetAdvancedInputRate,
			() -> ToolConfig.magnetAdvancedEuPerItem,
			() -> ToolConfig.magnetAdvancedEuPerOrb,
			3);

	private final IntSupplier range;
	private final IntSupplier buffer;
	private final IntSupplier inputRate;
	private final IntSupplier euPerItem;
	private final IntSupplier euPerOrb;
	private final int moduleSlots;

	MagnetTier(IntSupplier range, IntSupplier buffer, IntSupplier inputRate,
			IntSupplier euPerItem, IntSupplier euPerOrb, int moduleSlots) {
		this.range = range;
		this.buffer = buffer;
		this.inputRate = inputRate;
		this.euPerItem = euPerItem;
		this.euPerOrb = euPerOrb;
		this.moduleSlots = moduleSlots;
	}

	/** Pull radius in blocks around the carrier. */
	public int range() {
		return range.getAsInt();
	}

	/** EU the magnet holds. */
	public int buffer() {
		return buffer.getAsInt();
	}

	/** Max EU/tick it accepts while charging in a slot. */
	public int inputRate() {
		return inputRate.getAsInt();
	}

	/** EU spent per item actually nudged. */
	public int euPerItem() {
		return euPerItem.getAsInt();
	}

	/** EU spent per experience orb nudged; {@code 0} means this tier does not pull experience. */
	public int euPerOrb() {
		return euPerOrb.getAsInt();
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
