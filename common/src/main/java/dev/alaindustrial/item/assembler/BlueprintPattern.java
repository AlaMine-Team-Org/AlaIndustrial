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
 * their internals keep churning between 26.x versions. Like it, it compares by stack value, not by
 * identity (MOD-723).
 *
 * <p><b>The stacks are the component's value: never mutate them.</b> {@link #items()} and {@link #cell}
 * hand out the record's own stacks, not copies, and {@link #hashCode} is computed from their content — a
 * stack changed in place would change the hash of a component already sitting on an item. Read them, or
 * {@code copy()} first.
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

	// ItemStack has identity equals, so the generated record equals/hashCode would call two layouts that
	// were built or decoded separately different: ItemStack.matches on two blueprints of one recipe was
	// false, and a component round trip never gave back "the same value" (MOD-723). Compared and hashed
	// by stack value instead, in the shape of PouchContents (whose comment says why not the deprecated
	// ItemStack.listMatches/hashStackList). An empty layout and nine empty cells stay different values,
	// as the codec keeps them apart too.
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BlueprintPattern that) || items.size() != that.items.size()) {
			return false;
		}
		for (int i = 0; i < items.size(); i++) {
			if (!ItemStack.matches(items.get(i), that.items.get(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 1;
		for (ItemStack s : items) {
			hash = 31 * hash + ItemStack.hashItemAndComponents(s);
			hash = 31 * hash + s.getCount();
		}
		return hash;
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

	/** Cell {@code index} of the recorded grid, or empty for a blank one. The record's own stack: do not mutate. */
	public ItemStack cell(int index) {
		return index >= 0 && index < items.size() ? items.get(index) : ItemStack.EMPTY;
	}
}
