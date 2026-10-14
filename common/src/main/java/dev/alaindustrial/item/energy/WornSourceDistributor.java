package dev.alaindustrial.item.energy;

import dev.alaindustrial.item.energy.PlayerEuDistributor.Policy;
import dev.alaindustrial.item.energy.PlayerEuDistributor.Result;
import dev.alaindustrial.item.energy.PlayerEuDistributor.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * The order in which a source that lives on the wearer hands out its EU (MOD-664, the Solar Fluxweave
 * Helmet): the other worn armour first, then everything else carried. Split from
 * {@link PlayerEuDistributor}, whose roster and rounds it reuses, to keep that class under its size limit.
 */
public final class WornSourceDistributor {
	private WornSourceDistributor() {
	}

	/**
	 * Armour first, then everything else: the worn pieces other than {@code skip} share the budget evenly,
	 * and what they cannot take goes to the rest of the carried gear, split evenly too.
	 *
	 * <p>{@code rotation} shifts where each group's list starts. An even split leaves the odd EU, and, when
	 * the budget is smaller than the number of targets, the whole budget, to whoever comes first, so a caller
	 * passing a slowly changing number (the helmet passes seconds of game time) walks that favour round the
	 * armour and the inventory instead of pinning it to the first scanned slot. No state is stored.
	 *
	 * <p>Uses the Charging Station's rules (input rates respected, nothing excluded, worn gear included);
	 * {@code ticks} scales the input-rate ceiling exactly as in
	 * {@link PlayerEuDistributor#distribute(Player, long, Policy, int)}. Never delivers more than
	 * {@code budget}, and writes no stack that takes nothing.
	 */
	public static Result distributeArmorFirst(Player player, long budget, int ticks, EquipmentSlot skip,
			int rotation) {
		if (budget <= 0) {
			return Result.NOTHING;
		}
		Policy policy = Policy.STATION;
		int batch = Math.max(1, ticks);
		List<Target> armour = new ArrayList<>();
		for (EquipmentSlot slot : PlayerEuDistributor.WORN_SLOTS) {
			if (slot != skip) {
				PlayerEuDistributor.addTarget(armour, PlayerEuDistributor.NO_SLOT, player.getItemBySlot(slot),
						policy, batch, player);
			}
		}
		rotate(armour, rotation);
		Result first = PlayerEuDistributor.spread(player, armour, budget, policy);
		long left = budget - first.movedEu();
		Result second = Result.NOTHING;
		if (left > 0) {
			List<Target> rest = PlayerEuDistributor.collectTargets(player, policy, batch, false);
			rotate(rest, rotation);
			second = PlayerEuDistributor.spread(player, rest, left, policy);
		}
		return new Result(first.movedEu() + second.movedEu(),
				Math.max(0, first.itemsServed()) + Math.max(0, second.itemsServed()),
				Math.max(0L, first.remainingRoom()) + Math.max(0L, second.remainingRoom()));
	}

	private static void rotate(List<Target> targets, int rotation) {
		if (!targets.isEmpty()) {
			Collections.rotate(targets, -Math.floorMod(rotation, targets.size()));
		}
	}
}
