package dev.alaindustrial.block.entity;

import java.util.AbstractList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/**
 * The first nine slots of a container seen as a 3x3 {@link CraftingContainer} (MOD-810).
 *
 * <p>Vanilla's {@code ResultSlot} and recipe placement want a crafting container, whose stock implementation
 * keeps its own private items. The Advanced Industrial Workbench's grid lives in its block entity and is shared
 * by every viewer, so this view reads and writes THROUGH to that container instead of holding a copy: no
 * second list to keep in step, nothing to lose when a menu closes.
 */
public final class AdvancedWorkbenchGrid implements CraftingContainer {

	public static final int SIZE = 9;

	private final Container backing;

	public AdvancedWorkbenchGrid(Container backing) {
		this.backing = backing;
	}

	@Override
	public int getWidth() {
		return 3;
	}

	@Override
	public int getHeight() {
		return 3;
	}

	@Override
	public List<ItemStack> getItems() {
		return new AbstractList<>() {
			@Override
			public ItemStack get(int index) {
				return backing.getItem(index);
			}

			@Override
			public int size() {
				return SIZE;
			}
		};
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < SIZE; i++) {
			if (!backing.getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return backing.getItem(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return backing.removeItem(slot, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return backing.removeItemNoUpdate(slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		backing.setItem(slot, stack);
	}

	@Override
	public void setChanged() {
		backing.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return backing.stillValid(player);
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < SIZE; i++) {
			backing.setItem(i, ItemStack.EMPTY);
		}
	}

	@Override
	public void fillStackedContents(StackedItemContents contents) {
		for (int i = 0; i < SIZE; i++) {
			contents.accountSimpleStack(backing.getItem(i));
		}
	}
}
