package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.block.HorizontalMachineBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A machine's item container: what taking, placing and clearing a stack does, the shared merge rule of
 * a result slot (MOD-440), which slots automation may reach through a face (R-GUI-05/R-GUI-07), and the
 * {@code Items} save key.
 *
 * <p>One component of the machine base (MOD-712, BE-1). The stack list itself stays the base's
 * {@code items} — every machine reads its slots there — and the base still implements
 * {@code WorldlyContainer}, each method a one-line call here; the progress reset on a changed input stays
 * in the base too, because the progress is the machine's. Every change reports back through the two hooks
 * the base hands in: {@code changed} (mark dirty and sync the block entity to the client) and {@code wake}
 * (re-evaluate on the next tick, R-29), in the order the base has always called them.
 */
public final class MachineInventory {

	/**
	 * Output stack cap. A result slot never grows past this even for an item whose own max stack size is
	 * larger; for the ordinary 64-stack item the two limits coincide.
	 *
	 * <p>One declaration for the whole machine family. Before MOD-440 nine block entities each carried a
	 * private {@code OUTPUT_MAX = 64} and five of them a private copy of {@link #canOutput}/
	 * {@link #addOutput} — identical in behaviour, free to drift in text.
	 */
	public static final int OUTPUT_MAX = 64;

	/** Shared empty answer for faces automation must not touch (the FACING face, MOD-179). */
	private static final int[] NO_AUTOMATION_SLOTS = new int[0];

	private final NonNullList<ItemStack> items;
	private final int baseSlots;
	private final Runnable changed;
	private final Runnable wake;

	/**
	 * @param items the machine's whole inventory: its own slots, then the upgrade block and the drawer
	 * @param baseSlots count of the machine's own slots — the only ones automation sees
	 * @param changed marks the block entity dirty and syncs it to the client
	 * @param wake wakes the block entity for the next tick
	 */
	public MachineInventory(NonNullList<ItemStack> items, int baseSlots, Runnable changed, Runnable wake) {
		this.items = items;
		this.baseSlots = baseSlots;
		this.changed = changed;
		this.wake = wake;
	}

	/** True when no slot — upgrade and drawer slots included — holds anything. */
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Split up to {@code amount} off {@code slot}; anything taken marks, syncs and wakes the machine. */
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
		if (!removed.isEmpty()) {
			changed.run();
			wake.run(); // output pulled / input taken — re-evaluate next tick (R-29)
		}
		return removed;
	}

	/** Take the whole stack out of {@code slot}: wakes the machine first, marks and syncs it if anything was there. */
	public ItemStack removeItemNoUpdate(int slot) {
		wake.run();
		ItemStack removed = ContainerHelper.takeItem(items, slot);
		if (!removed.isEmpty()) {
			changed.run();
		}
		return removed;
	}

	/** Put {@code stack} into {@code slot}, then mark, sync and wake the machine. */
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		changed.run();
		wake.run(); // new input / output change — re-evaluate next tick (R-29)
	}

	/** Empty every slot, then mark, sync and wake the machine. */
	public void clear() {
		items.clear();
		changed.run();
		wake.run();
	}

	/**
	 * Whether {@code slot} can take one more {@code result} stack: empty, or the same item with room for
	 * the whole result under {@code min(OUTPUT_MAX, maxStackSize)} (MOD-440).
	 *
	 * <p>Item identity only, deliberately — this is the merge rule every processing machine has always
	 * used, and a recipe result carries no components that could tell two stacks of it apart. A machine
	 * whose output CAN differ by components (the incubator's graded results) keeps its own component-aware
	 * test instead of using this one. An empty result never "fits": there is nothing to place, and a
	 * caller asking is a machine with no recipe.
	 */
	public boolean canOutput(int slot, ItemStack result) {
		if (result.isEmpty()) {
			return false;
		}
		ItemStack out = items.get(slot);
		return out.isEmpty()
				|| (out.getItem() == result.getItem()
						&& out.getCount() + result.getCount() <= Math.min(OUTPUT_MAX, out.getMaxStackSize()));
	}

	/**
	 * Place one {@code result} stack into {@code slot}, growing the matching stack already there. The
	 * caller has checked {@link #canOutput} first — this method does not re-check, and unlike
	 * {@link #setItem} it does not run the change hooks.
	 */
	public void addOutput(int slot, ItemStack result) {
		ItemStack out = items.get(slot);
		if (out.isEmpty()) {
			items.set(slot, result.copy());
		} else {
			out.grow(result.getCount());
		}
	}

	/**
	 * The slots automation reaches through {@code side}: the machine's own slots, never the upgrade block or
	 * the drawer, and none at all through the front.
	 */
	public int[] slotsForFace(BlockState state, Direction side) {
		// The front (FACING) face is the machine's working face and is inert for automation, matching
		// the energy side (facingAwareRole) and the block specs ("hoppers do not work through it").
		// Before MOD-179 this method ignored `side`, so a hopper aimed at the front face could insert.
		if (state.hasProperty(HorizontalMachineBlock.FACING)
				&& side == state.getValue(HorizontalMachineBlock.FACING)) {
			return NO_AUTOMATION_SLOTS;
		}
		// Automation sees machine slots only; upgrade slots (MOD-080) are GUI-only and excluded here
		// so hoppers/pipes can neither fill nor drain them, on either loader.
		int[] slots = new int[baseSlots];
		for (int i = 0; i < baseSlots; i++) {
			slots[i] = i;
		}
		return slots;
	}

	/** Write every slot under {@code Items}. */
	public void save(ValueOutput output) {
		ContainerHelper.saveAllItems(output, items);
	}

	/** Read what {@link #save} wrote, over an emptied inventory. */
	public void load(ValueInput input) {
		items.clear();
		ContainerHelper.loadAllItems(input, items);
	}
}
