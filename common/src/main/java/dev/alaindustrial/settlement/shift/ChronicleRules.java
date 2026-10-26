package dev.alaindustrial.settlement.shift;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.ToIntFunction;

/**
 * How a line joins a town's chronicle (MOD-886, plan 0.1.220 block B3): the hall's log of every town lives in one list,
 * oldest line first, and each town keeps at most {@link #CAPACITY} lines of its own — its oldest go first. A line that
 * repeats the town's newest line (the same event about the same subject) does not add a line: it is merged into that
 * one, which then counts the repeats and carries the latest time.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks the rules ({@code ChronicleRulesTest}) on plain values; the hall's
 * log ({@link ShiftWorkerBook}) applies them to its lines.
 */
public final class ChronicleRules {

	/** How many lines one town's chronicle keeps. */
	public static final int CAPACITY = 200;

	/** What became of a line given to {@link #append}. */
	public enum Result {
		/** Added as a new line. */
		ADDED,
		/** Merged into the town's newest line. */
		MERGED
	}

	private ChronicleRules() {
	}

	/**
	 * Adds {@code line} to {@code log}: merged into the newest line of its town when {@code same} says it repeats it
	 * ({@code merge} makes the merged line out of the old and the new one), else appended; then the town's oldest lines
	 * are dropped until it holds at most {@code capacity}. Lines of other towns are never touched.
	 *
	 * @param town  the town a line belongs to
	 * @param same  whether the new line (second) repeats the town's newest line (first)
	 * @param merge the line that replaces the newest one: the newest (first) and the repeat (second) made one
	 */
	public static <T> Result append(List<T> log, T line, ToIntFunction<T> town, BiPredicate<T, T> same,
			BinaryOperator<T> merge, int capacity) {
		int id = town.applyAsInt(line);
		for (int i = log.size() - 1; i >= 0; i--) {
			T newest = log.get(i);
			if (town.applyAsInt(newest) != id) {
				continue;
			}
			if (same.test(newest, line)) {
				log.set(i, merge.apply(newest, line));
				return Result.MERGED;
			}
			break;
		}
		log.add(line);
		trim(log, id, town, capacity);
		return Result.ADDED;
	}

	/** Drops town {@code id}'s oldest lines from {@code log} until it holds at most {@code capacity} of them. */
	public static <T> void trim(List<T> log, int id, ToIntFunction<T> town, int capacity) {
		int count = 0;
		for (T line : log) {
			if (town.applyAsInt(line) == id) {
				count++;
			}
		}
		for (int i = 0; i < log.size() && count > Math.max(0, capacity); ) {
			if (town.applyAsInt(log.get(i)) == id) {
				log.remove(i);
				count--;
			} else {
				i++;
			}
		}
	}
}
