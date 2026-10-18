package dev.alaindustrial.item.energy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who was warned about what, and when (MOD-652) — the memory {@link LowChargeRule} needs for its repeat. One clock
 * per player and per kind of item, so a warning about the drill does not silence the saw and one player's beep
 * does not hold back another's.
 *
 * <p>A server-side map and not a data component on the stack: an extra component makes stacks that look alike
 * compare unequal, stops them merging and re-syncs the stack to the client with a swap animation. It does not
 * survive a relog or a restart, which for a beep is the right lifetime. Minecraft-free, so the whole memory is
 * covered by a plain JUnit test ({@code LowChargeLedgerTest}).
 *
 * <p>Server thread only (every {@code ItemEnergy.spend} runs there), so there is no locking.
 */
public final class LowChargeLedger {

	private final Map<UUID, Map<Object, Long>> lastWarned = new HashMap<>();

	/**
	 * Decides what the debit should tell {@code player} and, when it tells them anything, starts that kind of
	 * item's clock at {@code now}. {@code kind} is what a warning is remembered per — the item in play.
	 * See {@link LowChargeRule#decide} for the arguments.
	 */
	public int decide(UUID player, Object kind, long before, long after, long capacity, int percent,
			long depletedBelow, long now, long repeatTicks) {
		Map<Object, Long> byKind = lastWarned.get(player);
		long last = byKind == null ? LowChargeRule.NEVER : byKind.getOrDefault(kind, LowChargeRule.NEVER);
		int signal = LowChargeRule.decide(before, after, capacity, percent, depletedBelow, now, last, repeatTicks);
		if (signal != LowChargeRule.NONE) {
			lastWarned.computeIfAbsent(player, id -> new HashMap<>()).put(kind, now);
		}
		return signal;
	}

	/** A player left: drop their clocks. */
	public void forget(UUID player) {
		lastWarned.remove(player);
	}

	/** Drop every clock — used when the server stops, so nothing crosses into the next world. */
	public void clear() {
		lastWarned.clear();
	}
}
