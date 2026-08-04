package dev.alaindustrial.item.energy;

import dev.alaindustrial.registry.ModTags;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Hands EU to the powered items a player is carrying — the shared rules for "charge everything on this
 * person", used by the worn Energy Pack (MOD-065) and the Charging Station (MOD-274).
 *
 * <p>Extracted from {@code EnergyPackItem} when the station arrived, because the interesting part of
 * this job is not the arithmetic but the <b>list of places a powered item can hide on a player</b>, and
 * that list is easy to get subtly wrong. It grew one entry at a time from real bugs: the offhand is not
 * part of the inventory list in 26.2 (MOD-065), the cursor and the 2×2 crafting grid are not either
 * (MOD-082), a foreign mod's item needs a slot index or it cannot be reached at all (MOD-084). Two
 * copies of that list would drift, and the drift would be silent — an item that simply never charges.
 *
 * <p>This helper never touches the caller's own energy: it fills targets and reports the total, and the
 * caller debits itself for exactly that. So a pack pays from its buffer and the station from its own,
 * with one set of rules about where the energy lands.
 *
 * <p><b>Scan order</b> (a target is served until the budget runs out, then the rest wait for the next
 * call): main inventory including the hotbar → offhand → cursor and crafting grid → worn equipment.
 * The hotbar comes first because index 0..8 <em>is</em> the front of the inventory list, so the tool in
 * the player's hand is served before the spares in their backpack.
 */
public final class PlayerEuDistributor {
	private PlayerEuDistributor() {
	}

	/**
	 * Slot index for a charge target that has no vanilla {@link Inventory} index: the stack on the cursor,
	 * the 2×2 crafting grid (MOD-082), and worn equipment (MOD-274).
	 *
	 * <p>Consequence for foreign items (MOD-084): the mod's own items charge there as usual (they go
	 * through {@link ItemEnergy}, which needs no slot), but <b>another mod's</b> item does not — both
	 * loaders' item energy capabilities are looked up per inventory slot
	 * ({@code ContainerItemContext.ofPlayerSlot} / {@code ItemAccess.forPlayerSlot}), and there is no
	 * index to look up here. A foreign tool held on the cursor waits until it is put back in a slot; a
	 * foreign chestplate is not reachable at all while worn. Both are documented in the station's spec
	 * so they read as a known limit rather than a bug.
	 */
	public static final int NO_SLOT = -1;

	/**
	 * The four slots {@link #chargeEquipped} walks. Deliberately not {@code EquipmentSlot.values()}:
	 * that also yields MAINHAND and OFFHAND, both of which the inventory and offhand passes have already
	 * served, and charging a stack twice in one call would let it exceed the caller's budget.
	 */
	private static final EquipmentSlot[] WORN_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	/**
	 * How a particular charger behaves, in the three ways the pack and the station genuinely differ.
	 * A record rather than three booleans at the call site so each caller's answer is written down once,
	 * next to the reason for it.
	 *
	 * @param skipNoAutoCharge honour {@link ModTags.Items#NO_AUTO_CHARGE}, the "chargers do not charge
	 *     chargers" rule
	 * @param respectInputRate clamp each target to its own {@link ItemEnergy#inputRate}
	 * @param includeEquipped also fill worn armour and equipment
	 * @param spreadEvenly split the budget across every target at once instead of serving them in scan
	 *     order until it runs out
	 */
	public record Policy(boolean skipNoAutoCharge, boolean respectInputRate, boolean includeEquipped,
			boolean spreadEvenly) {

		/**
		 * What a worn Energy Pack does — the behaviour that shipped in MOD-065/082/084, preserved
		 * exactly.
		 *
		 * <p>It skips {@code NO_AUTO_CHARGE} so two packs cannot ping-pong energy between each other and
		 * drain the wearer for nothing. It ignores per-item input rates because it pays in one batch per
		 * second ({@code energyPackOutputRate × 20}) rather than per tick, so a per-tick clamp would
		 * simply throttle the batch to 1/20 of its intended size. And it leaves worn gear alone: the pack
		 * occupies the chest slot itself, and its whole model is "the bag tops up what you are carrying".
		 */
		public static final Policy WORN_PACK = new Policy(true, false, false, false);

		/**
		 * What the Charging Station does (MOD-274) — the deliberate opposite on all three counts.
		 *
		 * <p>It does <b>not</b> skip {@code NO_AUTO_CHARGE}: that tag exists to stop a charger charging
		 * another charger, and its only member is the Energy Pack — but topping up the pack is the single
		 * most likely reason a player steps onto the station at all. A stationary charger cannot
		 * ping-pong with anything, so the rule the tag encodes does not apply to it. (The Battery Box
		 * charge slot has always taken a pack for the same reason.)
		 *
		 * <p>It respects each item's input rate, because it pays every tick and that is exactly what the
		 * rate is specified against — the same clamp the Battery Box charge slot applies.
		 *
		 * <p>And it charges worn gear, which is the point of standing on it: the jetpack on your back is
		 * the thing most likely to be empty when you land.
		 *
		 * <p>It also splits the budget evenly (MOD-334): it pays every tick, so scan order would be
		 * visible as gear filling one group at a time — a player in full Fluxweave saw the helmet and
		 * chestplate charge, then the leggings and boots.
		 */
		public static final Policy STATION = new Policy(false, true, true, true);
	}

	/**
	 * Spreads up to {@code budget} EU across everything powered the player is carrying and returns how
	 * much actually landed. Writes nothing when it returns 0, so the caller can skip its own bookkeeping
	 * entirely on an idle call.
	 */
	public static long distribute(Player player, long budget, Policy policy) {
		if (budget <= 0) {
			return 0L;
		}
		return policy.spreadEvenly()
				? distributeEvenly(player, budget, policy)
				: distributeInScanOrder(player, budget, policy);
	}

	/**
	 * Serves targets in scan order until the budget runs out — the original behaviour, kept for the
	 * worn Energy Pack.
	 *
	 * <p>Right for a charger that pays in one-second batches: a full second's worth of EU fills every
	 * consumer within a few steps anyway, so the order stops mattering after a moment, and no cursor
	 * has to be persisted on the stack to remember who was next.
	 */
	private static long distributeInScanOrder(Player player, long budget, Policy policy) {
		long moved = 0L;
		// The list index doubles as the vanilla Inventory slot index, which is what the cross-mod bridge
		// needs to address the slot (both loaders' item capabilities are slot-scoped, not stack-scoped).
		NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
		for (int i = 0; i < items.size() && budget > 0; i++) {
			long sent = give(player, i, items.get(i), budget, policy);
			budget -= sent;
			moved += sent;
		}
		if (budget > 0) {
			long sent = give(player, Inventory.SLOT_OFFHAND,
					player.getItemBySlot(EquipmentSlot.OFFHAND), budget, policy);
			budget -= sent;
			moved += sent;
		}
		if (budget > 0) {
			long sent = chargeOpenScreen(player, budget, policy);
			budget -= sent;
			moved += sent;
		}
		if (policy.includeEquipped() && budget > 0) {
			moved += chargeEquipped(player, budget, policy);
		}
		return moved;
	}

	/**
	 * One place a powered item can sit on the player, plus how much it may still take this tick.
	 *
	 * <p>{@code headroom} is what keeps the multi-pass split honest: a per-item cap has to hold across
	 * the whole call, not per pass, or an item served four times would end up with four times its input
	 * rate. Unknown for a foreign mod's item — its own handler is the only thing that knows — so those
	 * start unbounded and are dropped as soon as a pass shows they take nothing.
	 */
	private static final class Target {
		private final int slot;
		private final ItemStack stack;
		private long headroom;

		Target(int slot, ItemStack stack, long headroom) {
			this.slot = slot;
			this.stack = stack;
			this.headroom = headroom;
		}
	}

	/**
	 * Serves every target at once, splitting the budget between them (MOD-334).
	 *
	 * <p>Scan order is fine for a charger that pays once a second, but the Charging Station pays every
	 * tick, and there the order is the whole experience: a 128 EU/t budget covers exactly four items at
	 * 32 EU/t each, so a player in a full set of Fluxweave watched the helmet and chestplate fill, then
	 * the leggings and boots — worn gear being last in the scan order made it worse. Splitting the
	 * budget makes everything rise together, which is what "stand on it and your kit charges" is
	 * supposed to look like. The total per tick is unchanged; only its distribution is.
	 *
	 * <p>Passes repeat because shares do not divide evenly: an item that needs less than its share
	 * takes what it needs and drops out, and the remainder goes round again to whoever is still hungry.
	 * The loop ends when the budget is spent, nobody wants more, or a pass moves nothing (which is what
	 * a share rounding down to zero looks like — more targets than EU).
	 */
	private static long distributeEvenly(Player player, long budget, Policy policy) {
		List<Target> targets = collectTargets(player, policy);
		if (targets.isEmpty()) {
			return 0L;
		}
		long moved = 0L;
		while (budget > 0 && !targets.isEmpty()) {
			long share = budget / targets.size();
			if (share <= 0) {
				// Fewer EU than targets: hand the remainder out one item at a time rather than stalling.
				share = budget;
			}
			long movedThisPass = 0L;
			Iterator<Target> it = targets.iterator();
			while (it.hasNext() && budget > 0) {
				Target target = it.next();
				long offer = Math.min(Math.min(share, budget), target.headroom);
				long sent = offer > 0 ? give(player, target.slot, target.stack, offer, policy) : 0L;
				if (sent <= 0) {
					// Full, capped out for this tick, or not chargeable after all.
					it.remove();
					continue;
				}
				target.headroom -= sent;
				if (target.headroom <= 0) {
					it.remove();
				}
				budget -= sent;
				moved += sent;
				movedThisPass += sent;
			}
			if (movedThisPass <= 0) {
				break;
			}
		}
		return moved;
	}

	/**
	 * Every spot the policy says to serve, in the same order the sequential path walks them — the order
	 * still decides who gets the odd EU left over by an uneven split, and keeping it identical means the
	 * two modes cannot disagree about <em>where</em> a powered item may hide, only about pacing.
	 */
	private static List<Target> collectTargets(Player player, Policy policy) {
		List<Target> targets = new ArrayList<>();
		NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
		for (int i = 0; i < items.size(); i++) {
			addTarget(targets, i, items.get(i), policy);
		}
		addTarget(targets, Inventory.SLOT_OFFHAND, player.getItemBySlot(EquipmentSlot.OFFHAND), policy);
		addTarget(targets, NO_SLOT, player.containerMenu.getCarried(), policy);
		CraftingContainer grid = player.inventoryMenu.getCraftSlots();
		for (int i = 0; i < grid.getContainerSize(); i++) {
			addTarget(targets, NO_SLOT, grid.getItem(i), policy);
		}
		if (policy.includeEquipped()) {
			for (EquipmentSlot slot : WORN_SLOTS) {
				addTarget(targets, NO_SLOT, player.getItemBySlot(slot), policy);
			}
		}
		return targets;
	}

	/**
	 * Adds a stack to the round if it could take charge at all. Only the cheap, certain exclusions are
	 * applied here — empty stacks, the anti-loop tag, and the mod's own items that are already full.
	 * A foreign item is always kept: asking whether it has room means opening a capability lookup and a
	 * transaction, which is exactly what {@link #give} does anyway.
	 */
	private static void addTarget(List<Target> targets, int slot, ItemStack stack, Policy policy) {
		if (stack.isEmpty() || (policy.skipNoAutoCharge() && stack.is(ModTags.Items.NO_AUTO_CHARGE))) {
			return;
		}
		long headroom;
		if (ItemEnergy.capacity(stack) > 0) {
			headroom = ItemEnergy.room(stack);
			if (policy.respectInputRate()) {
				headroom = Math.min(headroom, ItemEnergy.inputRate(stack));
			}
			if (headroom <= 0) {
				return;
			}
		} else if (slot == NO_SLOT) {
			// Not ours and unreachable without a slot index — give() would return 0 every pass.
			return;
		} else {
			// Foreign item: its own handler owns the per-tick limit, so nothing to cap it with here.
			headroom = Long.MAX_VALUE;
		}
		targets.add(new Target(slot, stack, headroom));
	}

	/**
	 * The two spots a powered item can occupy while the player has a screen open, neither of which is
	 * part of the inventory list above (MOD-082): the stack held on the cursor, and the inventory's own
	 * 2×2 crafting grid. Both are physically on the player, so a charger tops them up like any other
	 * carried item; without this a pouch would visibly stop charging the moment it was picked up.
	 *
	 * <p>Only these two — <b>not</b> the slots of whatever container is open. Those belong to a chest or
	 * a machine in the world, and a charger must not reach into them.
	 *
	 * <p>Both write through to the client on their own: the cursor is resynced by
	 * {@code AbstractContainerMenu.broadcastChanges → synchronizeCarriedToRemote} (a component write
	 * makes {@code ItemStack.matches} fail, which is exactly what triggers the resend), and the crafting
	 * grid can only hold items while the inventory screen itself is open — closing it runs
	 * {@code InventoryMenu.removed → clearContainer}, which hands the items back — so its slots are
	 * being broadcast whenever there is anything in them to charge.
	 */
	private static long chargeOpenScreen(Player player, long budget, Policy policy) {
		long moved = give(player, NO_SLOT, player.containerMenu.getCarried(), budget, policy);
		budget -= moved;
		CraftingContainer grid = player.inventoryMenu.getCraftSlots();
		for (int i = 0; i < grid.getContainerSize() && budget > 0; i++) {
			long sent = give(player, NO_SLOT, grid.getItem(i), budget, policy);
			budget -= sent;
			moved += sent;
		}
		return moved;
	}

	/**
	 * Fills worn equipment — the jetpack, the energy pack, a set of Fluxweave armour (MOD-274). New
	 * behaviour, not moved code: nothing charged worn gear before the station, because the pack (which
	 * is itself worn) only ever served what the player was <em>carrying</em>.
	 *
	 * <p>Worn stacks have no {@link Inventory} index, so they pass {@link #NO_SLOT} and are reachable
	 * only through {@link ItemEnergy} — the mod's own powered equipment. Writing straight into the stack
	 * returned by {@code getItemBySlot} is correct: it is the live stack held by the entity, and vanilla
	 * broadcasts equipment changes to nearby clients on its own.
	 */
	private static long chargeEquipped(Player player, long budget, Policy policy) {
		long moved = 0L;
		for (EquipmentSlot slot : WORN_SLOTS) {
			if (budget <= 0) {
				break;
			}
			long sent = give(player, NO_SLOT, player.getItemBySlot(slot), budget, policy);
			budget -= sent;
			moved += sent;
		}
		return moved;
	}

	/**
	 * Fill the stack in one slot with up to {@code budget} EU and return how much it took (the caller is
	 * debited for the total). Nothing is written when the transfer would be 0 — a full consumer, or a
	 * non-powered item, must not cost a component write and a stack resync.
	 *
	 * <p>The mod's own powered items go through {@link ItemEnergy} directly, and only everything else
	 * falls through to {@link ItemEnergyBridge} — a mod item is never worth the cost of opening a
	 * capability lookup and a transaction.
	 *
	 * <p>{@code slot} is the vanilla {@link Inventory} index of the target, or {@link #NO_SLOT} when it
	 * has none.
	 */
	private static long give(Player player, int slot, ItemStack target, long budget, Policy policy) {
		if (target.isEmpty() || (policy.skipNoAutoCharge() && target.is(ModTags.Items.NO_AUTO_CHARGE))) {
			return 0L;
		}
		if (ItemEnergy.capacity(target) > 0) {
			long move = Math.min(ItemEnergy.room(target), budget);
			if (policy.respectInputRate()) {
				move = Math.min(move, ItemEnergy.inputRate(target));
			}
			if (move <= 0) {
				return 0L;
			}
			ItemEnergy.add(target, move);
			return move;
		}
		if (slot == NO_SLOT) {
			return 0L;
		}
		// Not one of ours — offer it to the loader's item energy API (a foreign FE tool, battery, ...).
		// The foreign handler applies its own per-tick limit, so respectInputRate has nothing to clamp
		// here: ItemEnergy.inputRate only knows about this mod's items and reports 0 for everything else.
		return ItemEnergyBridge.get().chargeSlot(player, slot, budget);
	}
}
