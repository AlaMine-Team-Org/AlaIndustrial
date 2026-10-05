package dev.alaindustrial.core.machine;

import java.util.List;
import java.util.Optional;

/**
 * The assembler's planning over scratch copies of its warehouse and output area (MOD-275, MOD-287),
 * moved out of {@code AssemblerBlockEntity} so it runs under L1 (MOD-714).
 *
 * <p>Every method works on a {@code List} the caller snapshotted from a real container and reads or
 * replaces its entries; none touches the world. The caller plans an operation on the scratch lists and,
 * only if every step succeeded, writes them back — so a step that fails must leave its list exactly as
 * it found it, and the ones that can fail ({@link #insertInto}, {@link #returnRemainder}) do.
 *
 * <p>Generic over the stack type through {@link StackOps}: the game passes {@code ItemStack}, the L1
 * suite a fake record. That is what keeps this class free of Minecraft types.
 */
public final class ScratchInventory {

	private ScratchInventory() {
	}

	/** One reserved ingredient: the warehouse slot it came out of, and the actual single item taken. */
	public record Reserved<S>(int slot, S stack) {
	}

	/**
	 * Take one {@code want} out of the warehouse scratch, or nothing when there is none.
	 *
	 * <p>Matching is by item, ignoring components, exactly as a crafting {@code Ingredient} does — that
	 * is what lets a worn tool satisfy a recipe written against a fresh one. The lowest slot that holds
	 * the item is used. The stack handed back is the real one (components and damage intact), because the
	 * craft remainder is computed from it.
	 */
	public static <S> Optional<Reserved<S>> findAndTake(StackOps<S, ?> ops, List<S> storeScratch, S want) {
		for (int i = 0; i < storeScratch.size(); i++) {
			S held = storeScratch.get(i);
			if (!ops.isEmpty(held) && ops.sameItem(held, want)) {
				return Optional.of(takeOne(ops, storeScratch, i, held));
			}
		}
		return Optional.empty();
	}

	/**
	 * Take one item of any {@code candidate} out of the warehouse scratch, skipping {@code recorded} —
	 * the item the blueprint named, which the caller has already tried and failed to find.
	 *
	 * <p>Candidate order is the recipe's own ingredient order, and the first one the warehouse can supply
	 * wins; within a candidate the lowest slot is used. Deterministic, so two assemblers sharing a
	 * warehouse make the same choice.
	 */
	public static <S, I> Optional<Reserved<S>> findAndTakeAny(StackOps<S, I> ops, List<S> storeScratch,
			List<I> candidates, I recorded) {
		for (I candidate : candidates) {
			if (candidate == recorded) {
				continue;
			}
			for (int i = 0; i < storeScratch.size(); i++) {
				S held = storeScratch.get(i);
				if (ops.isEmpty(held) || ops.item(held) != candidate) {
					continue;
				}
				return Optional.of(takeOne(ops, storeScratch, i, held));
			}
		}
		return Optional.empty();
	}

	/** Remove one item from scratch slot {@code slot} (holding {@code held}) and hand it back. */
	private static <S> Reserved<S> takeOne(StackOps<S, ?> ops, List<S> storeScratch, int slot, S held) {
		S unit = ops.copyWithCount(held, 1);
		S left = ops.copyWithCount(held, ops.count(held) - 1);
		storeScratch.set(slot, ops.isEmpty(left) ? ops.empty() : left);
		return new Reserved<>(slot, unit);
	}

	/**
	 * Put a craft remainder back: into the very warehouse slot its ingredient came from ({@code home},
	 * {@code -1} for none) when that slot can take it, otherwise anywhere in the warehouse, otherwise the
	 * output area.
	 *
	 * <p>Preferring the original slot is what makes the Forge Hammer behave: it stays put and simply
	 * loses a point of durability, instead of migrating across the warehouse one craft at a time.
	 *
	 * @return whether the remainder found a place; when not, neither list was changed
	 */
	public static <S> boolean returnRemainder(StackOps<S, ?> ops, List<S> storeScratch, List<S> outputScratch,
			int home, S left) {
		if (home >= 0 && home < storeScratch.size()) {
			S there = storeScratch.get(home);
			if (ops.isEmpty(there)) {
				storeScratch.set(home, left);
				return true;
			}
			if (ops.sameItemSameComponents(there, left)
					&& ops.count(there) + ops.count(left) <= ops.maxStackSize(there)) {
				storeScratch.set(home, ops.copyWithCount(there, ops.count(there) + ops.count(left)));
				return true;
			}
		}
		return insertInto(ops, storeScratch, left) || insertInto(ops, outputScratch, left);
	}

	/**
	 * Insert {@code stack} into a scratch container, merging into matching stacks before using empty
	 * slots, lowest slot first in both passes. All-or-nothing: a stack that does not fit entirely leaves
	 * the scratch untouched, so a failed plan cannot half-place anything.
	 *
	 * @return whether the whole stack fitted
	 */
	public static <S> boolean insertInto(StackOps<S, ?> ops, List<S> scratch, S stack) {
		int remaining = ops.count(stack);
		int max = ops.maxStackSize(stack);
		int[] add = new int[scratch.size()];
		for (int i = 0; i < scratch.size() && remaining > 0; i++) {
			S there = scratch.get(i);
			if (!ops.isEmpty(there) && ops.sameItemSameComponents(there, stack)) {
				int room = Math.min(max, ops.maxStackSize(there)) - ops.count(there);
				if (room > 0) {
					add[i] = Math.min(room, remaining);
					remaining -= add[i];
				}
			}
		}
		for (int i = 0; i < scratch.size() && remaining > 0; i++) {
			if (ops.isEmpty(scratch.get(i)) && add[i] == 0) {
				add[i] = Math.min(max, remaining);
				remaining -= add[i];
			}
		}
		if (remaining > 0) {
			return false;
		}
		for (int i = 0; i < scratch.size(); i++) {
			if (add[i] == 0) {
				continue;
			}
			S there = scratch.get(i);
			scratch.set(i, ops.isEmpty(there)
					? ops.copyWithCount(stack, add[i])
					: ops.copyWithCount(there, ops.count(there) + add[i]));
		}
		return true;
	}
}
