package dev.alaindustrial.core.machine;

/**
 * The handful of stack operations {@link ScratchInventory} plans with, so the planning itself names no
 * Minecraft type and runs under L1 (MOD-714).
 *
 * <p>The game implements it over {@code ItemStack} and {@code Item}
 * ({@code dev.alaindustrial.item.assembler.ItemStackOps}); the L1 suite implements it over a small
 * fake record. Every method mirrors one {@code ItemStack} call one to one — nothing here is a new rule,
 * and a stack value is never mutated through this interface, only replaced.
 *
 * @param <S> the stack type
 * @param <I> the item type; items compare by identity, as {@code ItemStack.is(Item)} does
 */
public interface StackOps<S, I> {

	/** The canonical empty stack ({@code ItemStack.EMPTY}). */
	S empty();

	/** Whether {@code stack} holds nothing ({@code ItemStack.isEmpty}). */
	boolean isEmpty(S stack);

	/** How many items {@code stack} holds ({@code ItemStack.getCount}). */
	int count(S stack);

	/** The most {@code stack} may hold in one slot ({@code ItemStack.getMaxStackSize}). */
	int maxStackSize(S stack);

	/** The item of a non-empty {@code stack} ({@code ItemStack.getItem}). */
	I item(S stack);

	/** Same item, components ignored ({@code ItemStack.isSameItem}). */
	boolean sameItem(S a, S b);

	/** Same item and the same components ({@code ItemStack.isSameItemSameComponents}). */
	boolean sameItemSameComponents(S a, S b);

	/** A copy of {@code stack} with {@code count} items, components kept ({@code ItemStack.copyWithCount}). */
	S copyWithCount(S stack, int count);
}
