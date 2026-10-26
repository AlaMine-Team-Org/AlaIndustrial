package dev.alaindustrial.settlement.works;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * What the storekeeper carries (MOD-888; epic MOD-824, decisions R152, R155, plan 0.1.220 block B6): from the town's
 * storage to the dispatcher's buffer what the current order still lacks, and back from the buffer into storage what no
 * order wants — the clearing's spoil — once the buffer is filling. It carries a batch, never one item a trip: up to
 * {@link #CARRY_SLOTS} stacks.
 *
 * <p><b>The storage</b> is every container of a building the town has restored once (R152): the hall, a repaired
 * house, a restored warehouse — a chest in a ruin never counts ({@link #isStorage}).
 *
 * <p>Items are named by their registry id, as in {@link MaterialSource}, so the rules stay Minecraft-free: the L1 lane
 * checks them ({@code StorekeeperRulesTest}).
 */
public final class StorekeeperRules {

	/** How many stacks the storekeeper carries a trip. */
	public static final int CARRY_SLOTS = 4;

	private StorekeeperRules() {
	}

	/**
	 * Whether a container of a building in this state is part of the town's storage (R152): a building the town has
	 * restored once and not given up. A restored building that lost a block since still counts — its maintenance mends
	 * it and its chests are where they were.
	 */
	public static boolean isStorage(boolean everRestored, boolean withdrawn) {
		return everRestored && !withdrawn;
	}

	/**
	 * The batch to fetch from a container that holds {@code held} (item to count) for {@code wanted} (what the order
	 * lacks, most pressing first): of each wanted item as much as the container has and the order lacks, while the
	 * batch takes at most {@link #CARRY_SLOTS} stacks of {@code maxStack} items each.
	 */
	public static List<BuildStageRules.Need> fetch(List<BuildStageRules.Need> wanted, Map<String, Integer> held,
			ToIntFunction<String> maxStack) {
		List<BuildStageRules.Need> out = new ArrayList<>();
		int slots = CARRY_SLOTS;
		for (BuildStageRules.Need need : wanted) {
			if (slots <= 0) {
				break;
			}
			int there = Math.max(0, held.getOrDefault(need.item(), 0));
			int stack = Math.max(1, maxStack.applyAsInt(need.item()));
			int take = Math.min(Math.min(need.count(), there), slots * stack);
			if (take > 0) {
				out.add(new BuildStageRules.Need(need.item(), take));
				slots -= (take + stack - 1) / stack;
			}
		}
		return out;
	}

	/**
	 * Whether the dispatcher's buffer of {@code size} slots with {@code free} of them empty is filling: a quarter of it
	 * or less is left. Then the storekeeper takes the spoil out.
	 */
	public static boolean filling(int free, int size) {
		return size > 0 && free * 4 <= size;
	}

	/**
	 * The batch of spoil to take out of a buffer holding {@code buffer} (item to count, in the buffer's order): the
	 * items no order wants ({@code wanted}), at most {@link #CARRY_SLOTS} stacks.
	 */
	public static List<BuildStageRules.Need> haul(Map<String, Integer> buffer, Collection<String> wanted,
			ToIntFunction<String> maxStack) {
		List<BuildStageRules.Need> out = new ArrayList<>();
		int slots = CARRY_SLOTS;
		for (Map.Entry<String, Integer> held : buffer.entrySet()) {
			if (slots <= 0) {
				break;
			}
			if (wanted.contains(held.getKey()) || held.getValue() <= 0) {
				continue;
			}
			int stack = Math.max(1, maxStack.applyAsInt(held.getKey()));
			int take = Math.min(held.getValue(), slots * stack);
			out.add(new BuildStageRules.Need(held.getKey(), take));
			slots -= (take + stack - 1) / stack;
		}
		return out;
	}
}
