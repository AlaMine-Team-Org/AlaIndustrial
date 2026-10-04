package dev.alaindustrial.registry;

import dev.alaindustrial.recipe.ChargedCraftRecipe;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The charge-carrying crafting recipe family (MOD-083): a serializer only — its recipes are vanilla
 * {@code crafting} recipes. Declared as {@link ModRecipes#CHARGED_CRAFT}; it goes through the same
 * {@link ModRecipes#families()} list as the machine families because the two loaders register serializers
 * differently and Fabric must opt it into recipe sync (MOD-683).
 */
public final class ChargedCraft implements RecipeFamily<ChargedCraftRecipe> {
	private final String id;
	private Supplier<RecipeSerializer<ChargedCraftRecipe>> serializer = () -> {
		throw new IllegalStateException("ModRecipes charged-craft serializer read before its loader bound it");
	};

	ChargedCraft(String id) {
		this.id = id;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public Optional<RecipeType<ChargedCraftRecipe>> createType() {
		return Optional.empty();
	}

	@Override
	public RecipeSerializer<ChargedCraftRecipe> createSerializer() {
		return new RecipeSerializer<>(ChargedCraftRecipe.MAP_CODEC, ChargedCraftRecipe.STREAM_CODEC);
	}

	@Override
	public void bind(Supplier<RecipeType<ChargedCraftRecipe>> type,
			Supplier<RecipeSerializer<ChargedCraftRecipe>> serializerSupplier) {
		this.serializer = serializerSupplier;
	}

	@Override
	public RecipeSerializer<ChargedCraftRecipe> serializer() {
		return serializer.get();
	}
}
