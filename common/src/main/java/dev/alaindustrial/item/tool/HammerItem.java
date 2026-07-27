package dev.alaindustrial.item.tool;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Forge Hammer (MOD-078) — a pre-machine hand tool that turns an ingot into a metal plate on the
 * crafting grid. Unlike a normal ingredient the hammer is <b>not</b> consumed: it stays in the grid
 * and loses exactly 1 durability per plate, so it is the "expensive by hand" path to plates before
 * the Compressor automates them.
 *
 * <p>The staying-with-damage behaviour is a stack-aware craft-remainder, not a custom recipe
 * serializer: the plate recipes are ordinary {@code minecraft:crafting_shapeless} (so they show in
 * the recipe book, JEI and REI), and each loader routes its own craft-remainder hook to
 * {@link #damagedRemainder(ItemStack)}. The hook fires inside
 * {@code CraftingRecipe.defaultCraftingReminder}, which runs on both the client (grid prediction)
 * and the server, so the damaged hammer never flickers.
 *
 * <p>In 26.2 {@code Item#getCraftingRemainder()} is {@code final} and returns
 * {@code @Nullable ItemStackTemplate}, so it cannot be overridden here. The stack-aware overloads live
 * on the loader interfaces with different signatures (Fabric {@code FabricItem#getCraftingRemainder
 * (ItemStack)}, NeoForge {@code IItemExtension#getCraftingRemainder(ItemInstance)}), which is why the
 * base logic sits here and each loader subclass ({@code HammerItemFabric}, {@code HammerItemNeoForge})
 * only wires its own hook. See {@code docs/blocks/items/forge_hammer.md}.
 */
public class HammerItem extends Item {

	public HammerItem(Properties properties) {
		super(properties);
	}

	/**
	 * The craft-remainder body shared by both loader subclasses: a single-count copy of the hammer with
	 * one more point of damage, or {@code null} when this use breaks it (no remainder → the grid slot
	 * stays empty, and a shift-craft loop stops once the hammer is gone).
	 *
	 * <p>The hammer always has {@code stack.count == 1}, so after {@code ResultSlot.onTake} removes the
	 * ingredient the slot is empty and the damaged copy drops back into that same grid slot.
	 *
	 * @param stack the hammer in the crafting grid
	 * @return the damaged hammer as an {@link ItemStackTemplate}, or {@code null} if it just broke
	 */
	@Nullable
	protected static ItemStackTemplate damagedRemainder(ItemStack stack) {
		ItemStack copy = stack.copyWithCount(1);
		if (copy.nextDamageWillBreak()) {
			return null;
		}
		copy.setDamageValue(copy.getDamageValue() + 1);
		return ItemStackTemplate.fromNonEmptyStack(copy);
	}
}
