package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.settlement.works.MaterialSource;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The material buffer of a town's dispatcher (MOD-858, epic decision R84) as the builder drones see it: a run of the
 * dispatcher's slots, the only place a drone takes blocks from. A view, not a copy — every call reads and writes the
 * dispatcher's own slots, so what the drone took is gone from the screen at once and goes into the save with the
 * dispatcher.
 *
 * <p><b>The item is taken when the block is placed</b> ({@link #consume}, through
 * {@code BuildStageRules.consumeOnPlace}), never at take-off: a drone that is saved in the air, or whose cell turns
 * out right on arrival, has taken nothing.
 *
 * <p>The town's {@link MaterialSource} for the material stages of MOD-860 ({@code BuildMaterials.installSourceLookup}).
 */
public final class DispatcherBuffer implements MaterialSource {

	private final Container container;
	private final int first;
	private final int size;
	private final Runnable changed;

	/**
	 * A view of {@code size} slots of {@code container} from {@code first}; {@code changed} runs after every change
	 * (the block entity's {@code setChanged}).
	 */
	public DispatcherBuffer(Container container, int first, int size, Runnable changed) {
		this.container = container;
		this.first = first;
		this.size = size;
		this.changed = changed;
	}

	/** How many slots the buffer has. */
	public int size() {
		return size;
	}

	/** The stack in buffer slot {@code index} (0-based within the buffer). */
	public ItemStack get(int index) {
		return container.getItem(first + index);
	}

	/** How many of {@code item} the buffer holds, over all its slots. */
	public int count(Item item) {
		int total = 0;
		for (int i = 0; i < size; i++) {
			ItemStack stack = container.getItem(first + i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** How many of the item with registry id {@code item} the buffer holds ({@link MaterialSource}). */
	@Override
	public int count(String item) {
		return byId(item).map(this::count).orElse(0);
	}

	/**
	 * Takes {@code count} of the item with registry id {@code item} out of the buffer: all of them, or nothing
	 * ({@link MaterialSource}). The caller does it in the tick it places the block.
	 */
	@Override
	public boolean consume(String item, int count) {
		if (count <= 0) {
			return true;
		}
		Optional<Item> found = byId(item);
		if (found.isEmpty() || count(found.get()) < count) {
			return false;
		}
		int left = count;
		for (int i = size - 1; i >= 0 && left > 0; i--) {
			ItemStack stack = container.getItem(first + i);
			if (stack.is(found.get())) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				if (stack.isEmpty()) {
					container.setItem(first + i, ItemStack.EMPTY);
				}
				left -= take;
			}
		}
		changed.run();
		return true;
	}

	private static Optional<Item> byId(String item) {
		Identifier id = Identifier.tryParse(item);
		return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
	}

	/** Puts {@code stack} into the buffer, topping up stacks of the same item first; returns what did not fit. */
	public ItemStack insert(ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int i = 0; i < size && !rest.isEmpty(); i++) {
			ItemStack slot = container.getItem(first + i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest)) {
				int room = Math.min(slot.getMaxStackSize(), container.getMaxStackSize()) - slot.getCount();
				int moved = Math.min(room, rest.getCount());
				if (moved > 0) {
					slot.grow(moved);
					rest.shrink(moved);
				}
			}
		}
		for (int i = 0; i < size && !rest.isEmpty(); i++) {
			if (container.getItem(first + i).isEmpty()) {
				int moved = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), container.getMaxStackSize()));
				container.setItem(first + i, rest.split(moved));
			}
		}
		if (rest.getCount() != stack.getCount()) {
			changed.run();
		}
		return rest;
	}
}
