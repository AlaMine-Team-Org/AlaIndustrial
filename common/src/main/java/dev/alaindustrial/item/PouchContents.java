package dev.alaindustrial.item;

import com.mojang.serialization.Codec;
import dev.alaindustrial.Config;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable contents of an Battery Pouch (MOD-052), carried on the item as the
 * {@code alaindustrial:pouch_contents} data component. Not the vanilla {@code bundle_contents}:
 * the pouch needs its own capacity ({@link Config#lvPouchCapacity}, default 128 weight — twice a
 * bundle) and its own insert rules (merge-first, no pouch-in-pouch), and the vanilla component's
 * internals keep churning between versions (26.x moved it to {@code ItemStackTemplate}).
 *
 * <p>Weight model matches the vanilla bundle so player expectations carry over: one item weighs
 * {@code 64 / maxStackSize} (min 1) — a full 64-stack weighs 64, a pearl weighs 4, a tool weighs 64.
 *
 * <p>All operations are copy-on-write: they never mutate {@code items} or the stacks inside it and
 * return a fresh {@link PouchContents}. Equality is by stack contents (item + count + components),
 * not identity — component change detection and menu sync rely on a value-based {@code equals}.
 */
public record PouchContents(List<ItemStack> items) {

	public static final PouchContents EMPTY = new PouchContents(List.of());

	public static final Codec<PouchContents> CODEC =
			ItemStack.CODEC.listOf().xmap(PouchContents::new, PouchContents::items);

	public static final StreamCodec<RegistryFriendlyByteBuf, PouchContents> STREAM_CODEC =
			ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).map(PouchContents::new, PouchContents::items);

	public PouchContents {
		items = List.copyOf(items);
	}

	/** Weight of a single item of this stack's type: {@code 64 / maxStackSize}, never below 1. */
	public static int weightOf(ItemStack stack) {
		return Math.max(1, 64 / stack.getMaxStackSize());
	}

	/** Total stored weight: sum of {@code weightOf × count} over all stacks. */
	public int weight() {
		int total = 0;
		for (ItemStack s : items) {
			total += weightOf(s) * s.getCount();
		}
		return total;
	}

	/** Remaining weight before the pouch is full ({@link Config#lvPouchCapacity}). */
	public int room() {
		return Math.max(0, Config.lvPouchCapacity - weight());
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	public boolean isFull() {
		return room() <= 0;
	}

	/** Result of {@link #insert}: the new contents, what did not fit, and how many items went in. */
	public record InsertResult(PouchContents contents, ItemStack leftover, int inserted) {
	}

	/** Result of {@link #removeTop}: the new contents and the whole removed top stack (LIFO). */
	public record RemoveResult(PouchContents contents, ItemStack removed) {
	}

	/**
	 * Insert as much of {@code stack} as the remaining weight allows. Merge-first (design §3.3):
	 * tops up existing partial stacks of the same item+components, then appends the remainder as a
	 * new stack at the end (keeping LIFO order = insertion order). Pouches themselves are rejected
	 * (no pouch-in-pouch). The input stack is not mutated.
	 */
	public InsertResult insert(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof PouchItem) {
			return new InsertResult(this, stack.copy(), 0);
		}
		int perItem = weightOf(stack);
		int accepted = Math.min(stack.getCount(), room() / perItem);
		if (accepted <= 0) {
			return new InsertResult(this, stack.copy(), 0);
		}
		List<ItemStack> out = new ArrayList<>(items.size() + 1);
		int remaining = accepted;
		for (ItemStack s : items) {
			if (remaining > 0 && ItemStack.isSameItemSameComponents(s, stack)
					&& s.getCount() < s.getMaxStackSize()) {
				int add = Math.min(remaining, s.getMaxStackSize() - s.getCount());
				out.add(s.copyWithCount(s.getCount() + add));
				remaining -= add;
			} else {
				out.add(s.copy());
			}
		}
		if (remaining > 0) {
			out.add(stack.copyWithCount(remaining));
		}
		ItemStack leftover = stack.getCount() > accepted
				? stack.copyWithCount(stack.getCount() - accepted)
				: ItemStack.EMPTY;
		return new InsertResult(new PouchContents(out), leftover, accepted);
	}

	/** Remove the whole last-inserted stack (LIFO, Q-EXT-1: full top stack per action). */
	public RemoveResult removeTop() {
		if (items.isEmpty()) {
			return new RemoveResult(this, ItemStack.EMPTY);
		}
		List<ItemStack> out = new ArrayList<>(items.subList(0, items.size() - 1));
		ItemStack removed = items.get(items.size() - 1).copy();
		return new RemoveResult(new PouchContents(out), removed);
	}

	// ItemStack has identity equals; a record's generated equals/hashCode would make two logically
	// identical contents "different", causing spurious component diffs (menu resync every tick,
	// broken ItemStack.matches). Compare/hash by stack value instead. Hand-rolled rather than
	// ItemStack.listMatches/hashStackList — both are @Deprecated in 26.2 (vanilla is phasing out
	// stack-list helpers along with the BundleContents → ItemStackTemplate move).
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PouchContents that) || this.items.size() != that.items.size()) {
			return false;
		}
		for (int i = 0; i < items.size(); i++) {
			if (!ItemStack.matches(items.get(i), that.items.get(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 1;
		for (ItemStack s : items) {
			hash = 31 * hash + ItemStack.hashItemAndComponents(s);
			hash = 31 * hash + s.getCount();
		}
		return hash;
	}
}
