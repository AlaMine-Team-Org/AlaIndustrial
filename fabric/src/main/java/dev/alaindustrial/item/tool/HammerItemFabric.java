package dev.alaindustrial.item.tool;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric {@link HammerItem}: routes the stack-aware {@code FabricItem#getCraftingRemainder(ItemStack)}
 * hook (injected onto {@code Item} by fabric-item-api-v1, invoked from the {@code CraftingRecipeMixin}
 * redirect inside {@code CraftingRecipe.defaultCraftingReminder}) to the shared
 * {@link HammerItem#damagedRemainder(ItemStack)} body. See {@code docs/blocks/items/forge_hammer.md}.
 */
public class HammerItemFabric extends HammerItem {

	public HammerItemFabric(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		return damagedRemainder(stack);
	}
}
