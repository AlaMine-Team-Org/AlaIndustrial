package dev.alaindustrial.item.assembler;

import dev.alaindustrial.core.machine.StackOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * {@link StackOps} over {@link ItemStack} — the bridge that lets the Minecraft-free
 * {@link dev.alaindustrial.core.machine.ScratchInventory} plan the assembler's real stacks (MOD-714).
 *
 * <p>Each method is the one {@code ItemStack} call it names, nothing more: the planner's rules live in
 * {@code ScratchInventory}, and this class only says what "same item" or "a copy with this count" means
 * for a real stack. Stateless, so one shared instance serves every machine.
 */
public final class ItemStackOps implements StackOps<ItemStack, Item> {

	/** The one instance; the class holds no state. */
	public static final ItemStackOps INSTANCE = new ItemStackOps();

	private ItemStackOps() {
	}

	@Override
	public ItemStack empty() {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean isEmpty(ItemStack stack) {
		return stack.isEmpty();
	}

	@Override
	public int count(ItemStack stack) {
		return stack.getCount();
	}

	@Override
	public int maxStackSize(ItemStack stack) {
		return stack.getMaxStackSize();
	}

	@Override
	public Item item(ItemStack stack) {
		return stack.getItem();
	}

	@Override
	public boolean sameItem(ItemStack a, ItemStack b) {
		return ItemStack.isSameItem(a, b);
	}

	@Override
	public boolean sameItemSameComponents(ItemStack a, ItemStack b) {
		return ItemStack.isSameItemSameComponents(a, b);
	}

	@Override
	public ItemStack copyWithCount(ItemStack stack, int count) {
		return stack.copyWithCount(count);
	}
}
