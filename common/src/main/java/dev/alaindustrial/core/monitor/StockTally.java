package dev.alaindustrial.core.monitor;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * The running answer to "how much of each watched type is out there" (MOD-480).
 *
 * <p>The shape is deliberate: the monitor core asks its panels what they are watching FIRST, and
 * only then walks the containers, adding into the slots of this tally. A scan therefore costs one
 * pass over the wired containers regardless of how varied the warehouse is — the alternative, a
 * full {@code Item -> count} map, grows with the player's storage and is then thrown away for the
 * handful of numbers the panels actually display.
 *
 * <p>Samples are matched with {@link ItemStack#isSameItemSameComponents}: by plain item type an
 * enchanted book would collapse into one line with every other enchanted book.
 */
public final class StockTally {

	private final List<ItemStack> samples;
	private final long[] totals;

	/** @param samples one filter stack per watched type; order is the caller's and is preserved */
	public StockTally(List<ItemStack> samples) {
		this.samples = List.copyOf(samples);
		this.totals = new long[this.samples.size()];
	}

	public int size() {
		return samples.size();
	}

	public ItemStack sample(int index) {
		return samples.get(index);
	}

	/** Add {@code amount} to the watched type at {@code index}. */
	public void add(int index, long amount) {
		if (index >= 0 && index < totals.length && amount > 0L) {
			totals[index] += amount;
		}
	}

	/** Total found for the watched type at {@code index}. */
	public long total(int index) {
		return index >= 0 && index < totals.length ? totals[index] : 0L;
	}

	/**
	 * Index of the watched type matching {@code stack}, or {@code -1} when nothing here watches it.
	 * Callers walking a foreign container use this to skip uninteresting stacks without allocating.
	 */
	public int indexOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < samples.size(); i++) {
			if (ItemStack.isSameItemSameComponents(stack, samples.get(i))) {
				return i;
			}
		}
		return -1;
	}

	/** Whether nothing is being watched — the scan can be skipped entirely. */
	public boolean isEmpty() {
		return samples.isEmpty();
	}
}
