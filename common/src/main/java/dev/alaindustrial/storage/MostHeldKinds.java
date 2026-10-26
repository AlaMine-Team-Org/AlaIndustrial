package dev.alaindustrial.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Which kinds of things a container holds the most of (MOD-885, epic MOD-824 R157): the warehouse shelf shows the
 * icons of its four biggest kinds on its bin cards.
 *
 * <p>A kind is a group of slots whose stacks are the same thing ({@code sameKind}); its size is the sum of their
 * counts. Kinds are ranked by size, largest first; equal sizes keep the order in which the kinds first appear in the
 * slots, so the cards do not swap places while nothing changes. Each kind is represented by its first slot's stack.
 *
 * <p>Minecraft-free on purpose, so {@code MostHeldKindsTest} reaches it on the L1 lane; the shelf passes item stacks
 * with {@code ItemStack::isSameItemSameComponents} as the kind test.
 */
public final class MostHeldKinds {

	private MostHeldKinds() {
	}

	/**
	 * The representatives of the {@code limit} biggest kinds among {@code slots}, biggest first.
	 *
	 * @param slots    the container's slots in order; empty slots are skipped
	 * @param isEmpty  whether a slot holds nothing
	 * @param count    how many things a slot holds
	 * @param sameKind whether two slots hold the same kind of thing
	 * @param limit    how many kinds to return at most
	 * @return fewer than {@code limit} entries when the slots hold fewer kinds; never {@code null}
	 */
	public static <T> List<T> top(List<T> slots, Predicate<T> isEmpty, ToIntFunction<T> count,
			BiPredicate<T, T> sameKind, int limit) {
		List<T> kinds = new ArrayList<>();
		List<Long> totals = new ArrayList<>();
		for (T slot : slots) {
			if (slot == null || isEmpty.test(slot)) {
				continue;
			}
			int kind = indexOf(kinds, slot, sameKind);
			if (kind < 0) {
				kinds.add(slot);
				totals.add((long) count.applyAsInt(slot));
			} else {
				totals.set(kind, totals.get(kind) + count.applyAsInt(slot));
			}
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < kinds.size(); i++) {
			order.add(i);
		}
		// A stable sort: equal totals keep first-appearance order.
		order.sort((a, b) -> Long.compare(totals.get(b), totals.get(a)));
		List<T> out = new ArrayList<>();
		for (int i = 0; i < order.size() && out.size() < limit; i++) {
			out.add(kinds.get(order.get(i)));
		}
		return out;
	}

	private static <T> int indexOf(List<T> kinds, T slot, BiPredicate<T, T> sameKind) {
		for (int i = 0; i < kinds.size(); i++) {
			if (sameKind.test(kinds.get(i), slot)) {
				return i;
			}
		}
		return -1;
	}
}
