package dev.alaindustrial.item.tool;

import dev.alaindustrial.Config;
import java.util.function.IntSupplier;

/**
 * The two grades of Electromagnet (MOD-580) — everything that differs between them, in one place.
 *
 * <p>Before this, {@link MagnetItem} read {@code Config.magnet*} directly, which is exactly right
 * while there is one magnet and exactly wrong the moment there are two: a second tier would either
 * share the first one's numbers or grow an {@code if} at every read. The same shape as
 * {@link dev.alaindustrial.core.waste.BladeTier} (MOD-145), for the same reason.
 *
 * <p>The values are {@link IntSupplier}s rather than ints because {@code Config} is mutable at
 * runtime — a reloaded config must reach a tier that was resolved at class-init, or the knobs would
 * silently stop working for magnets.
 */
public enum MagnetTier {

	/** The original (MOD-132): five blocks, items only. */
	BASIC(() -> Config.magnetRange,
			() -> Config.magnetBuffer,
			() -> Config.magnetInputRate,
			() -> Config.magnetEuPerItem,
			() -> 0),

	/**
	 * The second grade (MOD-580): reaches further and picks up experience.
	 *
	 * <p>Experience is the tier's own mechanic rather than a bigger number, and it is deliberately
	 * priced separately from items: an orb is worth more to the player than a cobblestone, and a mob
	 * farm should not be a free ride.
	 */
	ADVANCED(() -> Config.magnetAdvancedRange,
			() -> Config.magnetAdvancedBuffer,
			() -> Config.magnetAdvancedInputRate,
			() -> Config.magnetAdvancedEuPerItem,
			() -> Config.magnetAdvancedEuPerOrb);

	private final IntSupplier range;
	private final IntSupplier buffer;
	private final IntSupplier inputRate;
	private final IntSupplier euPerItem;
	private final IntSupplier euPerOrb;

	MagnetTier(IntSupplier range, IntSupplier buffer, IntSupplier inputRate,
			IntSupplier euPerItem, IntSupplier euPerOrb) {
		this.range = range;
		this.buffer = buffer;
		this.inputRate = inputRate;
		this.euPerItem = euPerItem;
		this.euPerOrb = euPerOrb;
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

	/** Whether this grade reaches experience orbs at all. */
	public boolean pullsExperience() {
		return euPerOrb() > 0;
	}
}
