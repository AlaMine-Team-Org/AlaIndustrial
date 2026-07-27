package dev.alaindustrial.item.tool.neoforge;

import dev.alaindustrial.item.tool.HammerItem;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge {@link HammerItem}: routes the stack-aware
 * {@code IItemExtension#getCraftingRemainder(ItemInstance)} hook (invoked from the patched
 * {@code CraftingRecipe.defaultCraftingReminder}) to the shared
 * {@link HammerItem#damagedRemainder(ItemStack)} body. The grid item is always an {@link ItemStack}
 * (which implements {@link ItemInstance}); the guard falls back to the vanilla no-remainder default
 * for any other {@code ItemInstance}. See {@code docs/blocks/items/forge_hammer.md}.
 */
public class HammerItemNeoForge extends HammerItem {

	public HammerItemNeoForge(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
		return instance instanceof ItemStack stack ? damagedRemainder(stack) : super.getCraftingRemainder(instance);
	}
}
