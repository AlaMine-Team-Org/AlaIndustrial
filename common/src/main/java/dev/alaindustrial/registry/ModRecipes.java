package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import java.util.function.Supplier;
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
 *
 * <p>MOD-022 facade: NeoForge freezes the {@code RECIPE_TYPE}/{@code RECIPE_SERIALIZER} registries before
 * mod construction, so each {@link Kind}'s type/serializer are bound lazily per loader — Fabric via the
 * eager {@link #init()} below, NeoForge via a {@code DeferredRegister} (see {@code ModRecipesNeoForge}) —
 * and read through {@code Supplier}s in the accessors.
 */
public final class ModRecipes {
	private ModRecipes() {
	}

	/** One machine recipe family: its {@link RecipeType}, {@link RecipeSerializer} and default EU cost. */
	public static final class Kind {
		private final String id;
		private final int defaultEnergy;
		private Supplier<RecipeType<AlaProcessingRecipe>> type = () -> {
			throw new IllegalStateException("ModRecipes.Kind type read before its loader bound it");
		};
		private Supplier<RecipeSerializer<AlaProcessingRecipe>> serializer = () -> {
			throw new IllegalStateException("ModRecipes.Kind serializer read before its loader bound it");
		};

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
			return type.get();
		}

		public RecipeSerializer<AlaProcessingRecipe> serializer() {
			return serializer.get();
		}

		/** Bind this kind's type + serializer suppliers. Called once per loader during its registration. */
		public void bind(Supplier<RecipeType<AlaProcessingRecipe>> typeSupplier,
				Supplier<RecipeSerializer<AlaProcessingRecipe>> serializerSupplier) {
			this.type = typeSupplier;
			this.serializer = serializerSupplier;
		}

		/** A per-machine cached lookup (mirrors vanilla {@code AbstractFurnaceBlockEntity.quickCheck}). */
		public RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> newCheck() {
			return RecipeManager.createCheck(type.get());
		}
	}

	// defaultEnergy is the fallback when a recipe JSON omits `energy`; every shipped
	// maceration JSON sets `energy: 300` (= maceratorDuration × machineEuPerTick), so this default
	// is never active. It is kept aligned with the actual recipe energy on purpose so the
	// recipe_check.py validator does not flag a stale-looking fallback (MOD-134).
	public static final Kind MACERATION = new Kind("maceration", 300);
	public static final Kind SMELTING = new Kind("smelting", 200);
	public static final Kind COMPRESSING = new Kind("compressing", 260);
	public static final Kind EXTRACTING = new Kind("extracting", 240);
	// Sawmill (MOD-150): one Kind per cutting mode (planks/sticks/slabs/stairs). defaultEnergy 160 =
	// sawmillDuration (80) × machineEuPerTick (2); every shipped sawing JSON sets energy: 160 explicitly.
	public static final Kind SAWING_PLANKS = new Kind("sawing_planks", 160);
	public static final Kind SAWING_STICKS = new Kind("sawing_sticks", 160);
	public static final Kind SAWING_SLABS = new Kind("sawing_slabs", 160);
	public static final Kind SAWING_STAIRS = new Kind("sawing_stairs", 160);

	private static final Kind[] ALL = {MACERATION, SMELTING, COMPRESSING, EXTRACTING,
			SAWING_PLANKS, SAWING_STICKS, SAWING_SLABS, SAWING_STAIRS};

	/** All recipe families, in registration order (used by both loaders' registration). */
	public static Kind[] kinds() {
		return ALL;
	}

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

	/** Build the {@link RecipeType} instance both loaders register for {@code kind}. */
	public static RecipeType<AlaProcessingRecipe> createType(Kind kind) {
		Identifier id = Industrialization.id(kind.id);
		return new RecipeType<AlaProcessingRecipe>() {
			@Override
			public String toString() {
				return id.toString();
			}
		};
	}

	/** Build the {@link RecipeSerializer} instance both loaders register for {@code kind}. */
	public static RecipeSerializer<AlaProcessingRecipe> createSerializer(Kind kind) {
		return new RecipeSerializer<>(AlaProcessingRecipe.mapCodec(kind), AlaProcessingRecipe.streamCodec(kind));
	}

	/**
	 * Fabric registration: the {@code RECIPE_TYPE}/{@code RECIPE_SERIALIZER} registries stay writable during
	 * init, so register each kind eagerly and bind it to constant suppliers. NeoForge instead uses a
	 * {@code DeferredRegister} (see {@code ModRecipesNeoForge}).
	 */
	public static void init() {
		for (Kind kind : ALL) {
			Identifier id = Industrialization.id(kind.id);
			RecipeType<AlaProcessingRecipe> type = Registry.register(BuiltInRegistries.RECIPE_TYPE, id, createType(kind));
			RecipeSerializer<AlaProcessingRecipe> serializer =
					Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id, createSerializer(kind));
			kind.bind(() -> type, () -> serializer);
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
