package dev.alaindustrial.block.entity;

/**
 * The Garden Drone Station's output block (MOD-782): whether harvesting and weeding wait for room in the
 * output slots. Kept free of Minecraft types so its rules are checked by a plain unit test.
 *
 * <p>One int carries both the flag and the snapshot that lifts it: {@value #NOT_BLOCKED} while the
 * output takes drops, otherwise the number of items the output slots held when it was blocked. The
 * station persists that int, so a reload neither forgets the block nor sends the drone out again; a
 * world saved before the key existed loads as not blocked.
 *
 * <p><b>Set</b> when a harvest or a weeding is refused for want of room (the simulated insert says no;
 * a world that refused the block change does not count), and whenever every output slot is a full
 * stack: then nothing at all can land, so there is no point flying to find out. While it is set the
 * station does not harvest or weed; planting, fertilizing and tilling go on, and
 * {@link GardenDroneStatus#OUTPUT_FULL} is shown when none of them has work and a ripe crop or a weed
 * in range is waiting for room. Deliberate
 * price: one refused drop blocks the output even if another tile's drop would still have fitted —
 * knowing in advance would cost a loot-table roll per candidate.
 *
 * <p><b>Lifted</b> by the station's tick, not by a hook on taking an item: as soon as the output holds
 * fewer items than the snapshot, something was taken out. That reads the same whichever way the item
 * left — a click, a shift click or a hotbar swap ({@code setItem}), a hopper or the NeoForge item
 * handler ({@code removeItem}), the Fabric transfer API ({@code setItem} through its slot wrapper). A
 * hook on {@code removeItem} would miss three of them. A station asleep in this state picks the change
 * up on its next full tick, at most the idle back-off later.
 *
 * <p><b>A block set on an empty output</b> (one tile's drop needs more than the four slots) cannot be
 * lifted that way — there is nothing to take out. {@link #retryIfEmpty} lifts it, and the station calls
 * it only from its idle status, so such a tile costs one retry flight per idle back-off instead of
 * stopping harvesting and weeding for good.
 */
final class GardenDroneOutputBlock {

	/** The saved value of an open output; also what a missing key loads as. */
	static final int NOT_BLOCKED = -1;

	private int blockedAt = NOT_BLOCKED;

	/** Whether harvesting and weeding wait for room. */
	boolean isBlocked() {
		return blockedAt != NOT_BLOCKED;
	}

	/** Blocks the output at its current item count; taking anything out lifts it again. */
	void block(int outputItems) {
		blockedAt = Math.max(0, outputItems);
	}

	/**
	 * Settles the block once per full tick: lifts it when the output holds fewer items than when it was
	 * blocked, raises the snapshot when it holds more, and blocks a full output outright.
	 *
	 * <p>The output could grow while blocked although the station inserts nothing then: a shift click
	 * from the player's inventory ran vanilla's {@code moveItemStackTo}, whose merge pass tops up a
	 * matching stack without asking {@code Slot#mayPlace} (checked with javap on 26.3 and 26.2). Since
	 * MOD-784 {@code MachineMenu} refuses that; raising the snapshot stays as the second lock, so an item
	 * that does arrive and is taken back out still registers as a removal.
	 *
	 * @return whether the saved value changed, so the station knows to mark itself dirty
	 */
	boolean refresh(int outputItems, boolean outputFull) {
		int before = blockedAt;
		if (isBlocked()) {
			if (outputItems < blockedAt) {
				blockedAt = NOT_BLOCKED;
			} else if (outputItems > blockedAt) {
				blockedAt = outputItems;
			}
		}
		if (!isBlocked() && outputFull) {
			block(outputItems);
		}
		return blockedAt != before;
	}

	/**
	 * Lifts a block set on an empty output, so the refused tile is tried again; a block over items is
	 * left for {@link #refresh} to lift when one is taken out.
	 *
	 * @return whether the saved value changed
	 */
	boolean retryIfEmpty(int outputItems) {
		if (isBlocked() && outputItems == 0) {
			blockedAt = NOT_BLOCKED;
			return true;
		}
		return false;
	}

	/** The value the station saves. */
	int saved() {
		return blockedAt;
	}

	/** Restores a saved value; anything below {@link #NOT_BLOCKED} reads as open. */
	void restore(int saved) {
		blockedAt = Math.max(NOT_BLOCKED, saved);
	}
}
