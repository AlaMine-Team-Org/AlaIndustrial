package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Central registration for the machine {@link RecipeType}s + {@link RecipeSerializer}s (R-14). Each
 * processing machine has its own {@link Kind} (so JEI/REI and {@code /reload} see four distinct
 * recipe families), all sharing the single {@link AlaProcessingRecipe} class and JSON shape.
 *
 * <p>Recipes are real vanilla recipes under {@code data/<ns>/recipe/<machine>/*.json}; their input is
 * an {@link net.minecraft.world.item.crafting.Ingredient} so it can be an item or a tag (R-15).
 */
public final class ModRecipes {
	private ModRecipes() {
	}

	/** One machine recipe family: its {@link RecipeType}, {@link RecipeSerializer} and default EU cost. */
	public static final class Kind {
		private final String id;
		private final int defaultEnergy;
		private RecipeType<AlaProcessingRecipe> type;
		private RecipeSerializer<AlaProcessingRecipe> serializer;

		private Kind(String id, int defaultEnergy) {
			this.id = id;
			this.defaultEnergy = defaultEnergy;
		}

		public String id() {
			return id;
		}

		public int defaultEnergy() {
			return defaultEnergy;
		}

		public RecipeType<AlaProcessingRecipe> type() {
			return type;
		}

		public RecipeSerializer<AlaProcessingRecipe> serializer() {
			return serializer;
		}

		/** A per-machine cached lookup (mirrors vanilla {@code AbstractFurnaceBlockEntity.quickCheck}). */
		public RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> newCheck() {
			return RecipeManager.createCheck(type);
		}
	}

	public static final Kind MACERATION = new Kind("maceration", 400);
	public static final Kind SMELTING = new Kind("smelting", 200);
	public static final Kind COMPRESSING = new Kind("compressing", 260);
	public static final Kind EXTRACTING = new Kind("extracting", 240);

	private static final Kind[] ALL = {MACERATION, SMELTING, COMPRESSING, EXTRACTING};

	/** Resolve a {@link Kind} back from its string id, or {@code null} if unknown. Used by the REI
	 *  display serializer to rebuild a display's kind from its synced id (see {@code AlaProcessingDisplay}). */
	public static Kind byId(String id) {
		for (Kind kind : ALL) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		return null;
	}

	public static void init() {
		for (Kind kind : ALL) {
			Identifier id = Industrialization.id(kind.id);
			kind.type = Registry.register(BuiltInRegistries.RECIPE_TYPE, id, new RecipeType<AlaProcessingRecipe>() {
				@Override
				public String toString() {
					return id.toString();
				}
			});
			kind.serializer = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id,
					new RecipeSerializer<>(AlaProcessingRecipe.mapCodec(kind), AlaProcessingRecipe.streamCodec(kind)));
		}
	}

	/**
	 * Resolve the recipe matching {@code input} for a machine's cached check, or {@code null} if the
	 * input is empty or no recipe (item or tag) accepts it.
	 */
	public static AlaProcessingRecipe lookup(RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> check,
			ServerLevel level, ItemStack input) {
		if (input.isEmpty()) {
			return null;
		}
		return check.getRecipeFor(new SingleRecipeInput(input), level).map(RecipeHolder::value).orElse(null);
	}
}
