package dev.alaindustrial.item.assembler;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The 3×3 layout recorded on an Assembly Blueprint (MOD-275), carried as the
 * {@code alaindustrial:blueprint_pattern} data component.
 *
 * <p><b>It stores the ingredients, not the recipe.</b> The recipe is resolved again from
 * {@code RecipeType.CRAFTING} on every operation, so a blueprint keeps working when another mod or a
 * datapack changes the recipe, and stops working — visibly, without a crash — when the recipe is
 * removed. Recording a recipe id instead would leave the blueprint pointing at something that no
 * longer exists.
 *
 * <p>Always nine entries in row-major order, empty slots included, so index arithmetic against the
 * grid never has to account for trimming. That matters because {@code CraftingInput.of} <em>does</em>
 * trim empty edge rows and columns, and the two representations must not be confused.
 *
 * <p>Modelled on {@code PouchContents}: a record over {@link ItemStack} with a codec for disk and a
 * stream codec for the client. The vanilla container components are avoided for the same reason —
 * their internals keep churning between 26.x versions.
 */
public record BlueprintPattern(List<ItemStack> items) {
	/** Slots in the recorded grid — always nine, empty cells included. */
	public static final int GRID_SIZE = 9;
	public static final int GRID_WIDTH = 3;

	/** A blank blueprint: no layout recorded yet. */
	public static final BlueprintPattern EMPTY = new BlueprintPattern(List.of());

	public static final Codec<BlueprintPattern> CODEC =
			ItemStack.OPTIONAL_CODEC.listOf().xmap(BlueprintPattern::new, BlueprintPattern::items);

	public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintPattern> STREAM_CODEC =
			ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list())
					.map(BlueprintPattern::new, BlueprintPattern::items);

	public BlueprintPattern {
		items = List.copyOf(items);
	}

	/** Record a layout: exactly nine cells, copied so later edits to the grid cannot reach in. */
	public static BlueprintPattern of(List<ItemStack> grid) {
		if (grid.size() != GRID_SIZE) {
			throw new IllegalArgumentException("blueprint grid must be " + GRID_SIZE + " cells, got " + grid.size());
		}
		ItemStack[] copy = new ItemStack[GRID_SIZE];
		for (int i = 0; i < GRID_SIZE; i++) {
			ItemStack cell = grid.get(i);
			// One of each: a blueprint records what goes where, never how many.
			copy[i] = cell.isEmpty() ? ItemStack.EMPTY : cell.copyWithCount(1);
		}
		return new BlueprintPattern(List.of(copy));
	}

	/** Nothing recorded yet — the item is still a blank. */
	public boolean isBlank() {
		if (items.size() != GRID_SIZE) {
			return true;
		}
		for (ItemStack cell : items) {
			if (!cell.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Cell {@code index} of the recorded grid, or empty for a blank blueprint. */
	public ItemStack cell(int index) {
		return index >= 0 && index < items.size() ? items.get(index) : ItemStack.EMPTY;
	}
}
