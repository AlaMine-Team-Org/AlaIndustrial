package dev.alaindustrial.registry;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.FluidRecipeInput;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import java.util.function.IntSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

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
		/** Read lazily: Config values are reloadable, so a captured int would go stale. */
		private final IntSupplier euPerTick;
		private Supplier<RecipeType<AlaProcessingRecipe>> type = () -> {
			throw new IllegalStateException("ModRecipes.Kind type read before its loader bound it");
		};
		private Supplier<RecipeSerializer<AlaProcessingRecipe>> serializer = () -> {
			throw new IllegalStateException("ModRecipes.Kind serializer read before its loader bound it");
		};

		private Kind(String id, int defaultEnergy) {
			this(id, defaultEnergy, () -> Config.machineEuPerTick);
		}

		private Kind(String id, int defaultEnergy, IntSupplier euPerTick) {
			this.id = id;
			this.defaultEnergy = defaultEnergy;
			this.euPerTick = euPerTick;
		}

		public String id() {
			return id;
		}

		public int defaultEnergy() {
			return defaultEnergy;
		}

		/** What the machine working this family draws per tick. Almost every machine shares one rate. */
		public int euPerTick() {
			return Math.max(1, euPerTick.getAsInt());
		}

		/**
		 * Base processing time of a recipe of this family costing {@code energy} EU — the number the
		 * recipe viewers print. It has to divide by <em>this family's</em> rate: the incubator draws
		 * four times what the other machines do, and the shared rate made its 15 seconds read as 60.
		 * The global speed multiplier is a runtime balance knob and deliberately not applied — the
		 * viewer shows the recipe's intrinsic time.
		 */
		public int ticksFor(int energy) {
			return Math.max(1, energy / euPerTick());
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
		public RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> newCheck() {
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
	public static final Kind VULCANIZING = new Kind("vulcanizing", 400);
	// Sawmill (MOD-150): one Kind per cutting mode (planks/sticks/slabs/stairs). defaultEnergy 160 =
	// sawmillDuration (80) × machineEuPerTick (2); every shipped sawing JSON sets energy: 160 explicitly.
	public static final Kind SAWING_PLANKS = new Kind("sawing_planks", 160);
	public static final Kind SAWING_STICKS = new Kind("sawing_sticks", 160);
	public static final Kind SAWING_SLABS = new Kind("sawing_slabs", 160);
	public static final Kind SAWING_STAIRS = new Kind("sawing_stairs", 160);

	// Incubator (MOD-118): one kind per mutation mode, selected by the chip in the machine.
	// Splitting by type (rather than a "kind" field inside one type) matches how the sawmill models
	// its cutting modes, and it comes with per-mode recipe-viewer categories for free.
	// The incubator is the one machine with its own draw (8 EU/t against the shared 2), so these three
	// carry it: energy / euPerTick is what the recipe viewers show as the operation's length.
	public static final Kind MUTATION_TRANSFORM =
			new Kind("mutation_transform", 2400, () -> Config.incubatorEuPerTick);
	public static final Kind MUTATION_DUPLICATE =
			new Kind("mutation_duplicate", 4000, () -> Config.incubatorEuPerTick);
	public static final Kind MUTATION_CREATE =
			new Kind("mutation_create", 8000, () -> Config.incubatorEuPerTick);

	private static final Kind[] ALL = {MACERATION, SMELTING, COMPRESSING, EXTRACTING, VULCANIZING,
			SAWING_PLANKS, SAWING_STICKS, SAWING_SLABS, SAWING_STAIRS,
			MUTATION_TRANSFORM, MUTATION_DUPLICATE, MUTATION_CREATE};

	/** All recipe families, in registration order (used by both loaders' registration). */
	public static Kind[] kinds() {
		return ALL;
	}

	/**
	 * One fluid-input recipe family. The recipe type is generic because polymerizing produces an item,
	 * while distilling produces fluid stacks; each family owns codec factories for its concrete recipe.
	 */
	public static final class FluidKind<R extends Recipe<FluidRecipeInput>> {
		private final String id;
		private final int defaultEnergy;
		private final Function<FluidKind<R>, MapCodec<R>> mapCodecFactory;
		private final Function<FluidKind<R>, StreamCodec<RegistryFriendlyByteBuf, R>> streamCodecFactory;
		private Supplier<RecipeType<R>> type = () -> {
			throw new IllegalStateException("ModRecipes.FluidKind type read before its loader bound it");
		};
		private Supplier<RecipeSerializer<R>> serializer = () -> {
			throw new IllegalStateException("ModRecipes.FluidKind serializer read before its loader bound it");
		};

		private FluidKind(String id, int defaultEnergy,
				Function<FluidKind<R>, MapCodec<R>> mapCodecFactory,
				Function<FluidKind<R>, StreamCodec<RegistryFriendlyByteBuf, R>> streamCodecFactory) {
			this.id = id;
			this.defaultEnergy = defaultEnergy;
			this.mapCodecFactory = mapCodecFactory;
			this.streamCodecFactory = streamCodecFactory;
		}

		public String id() {
			return id;
		}

		public int defaultEnergy() {
			return defaultEnergy;
		}

		/** What the machine working this family draws per tick — the shared processing-machine rate. */
		public int euPerTick() {
			return Math.max(1, Config.machineEuPerTick);
		}

		/** Base processing time of a recipe costing {@code energy} EU — the number the recipe viewers print. */
		public int ticksFor(int energy) {
			return Math.max(1, energy / euPerTick());
		}

		public RecipeType<R> type() {
			return type.get();
		}

		public RecipeSerializer<R> serializer() {
			return serializer.get();
		}

		/** Bind this family's type + serializer suppliers. Called once per loader during its registration. */
		public void bind(Supplier<RecipeType<R>> typeSupplier,
				Supplier<RecipeSerializer<R>> serializerSupplier) {
			this.type = typeSupplier;
			this.serializer = serializerSupplier;
		}

		/** A cached lookup for the machine (mirrors {@link Kind#newCheck()}). */
		public RecipeManager.CachedCheck<FluidRecipeInput, R> newCheck() {
			return RecipeManager.createCheck(type.get());
		}

		private RecipeSerializer<R> createSerializer() {
			return new RecipeSerializer<>(mapCodecFactory.apply(this), streamCodecFactory.apply(this));
		}
	}

	// defaultEnergy 400 = polymerizerDuration (200) × machineEuPerTick (2); the shipped JSON states it
	// explicitly, so this fallback is never active — it is kept in step with the real cost on purpose so
	// recipe_check.py does not flag a stale-looking default (MOD-134).
	//
	// Codec factories receive their already-created FluidKind when registration runs. They therefore
	// never read a not-yet-assigned ModRecipes static during this class's own initialization.
	public static final FluidKind<PolymerizingRecipe> POLYMERIZING = new FluidKind<>(
			"polymerizing", 400,
			kind -> PolymerizingRecipe.mapCodec(kind),
			kind -> PolymerizingRecipe.streamCodec(kind));
	public static final FluidKind<FluidOutputRecipe> DISTILLING = new FluidKind<>(
			"distilling", 400,
			kind -> FluidOutputRecipe.mapCodec(kind),
			kind -> FluidOutputRecipe.streamCodec(kind));

	private static final FluidKind<?>[] FLUID_ALL = {POLYMERIZING, DISTILLING};

	/** All fluid-input recipe families, in registration order (used by both loaders' registration). */
	public static FluidKind<?>[] fluidKinds() {
		return FLUID_ALL;
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

	/** Build the {@link RecipeType} instance both loaders register for {@code kind} (MOD-019). */
	public static <R extends Recipe<FluidRecipeInput>> RecipeType<R> createType(FluidKind<R> kind) {
		Identifier id = Industrialization.id(kind.id);
		return new RecipeType<R>() {
			@Override
			public String toString() {
				return id.toString();
			}
		};
	}

	/** Build the family-specific serializer from the codec factories owned by {@code kind}. */
	public static <R extends Recipe<FluidRecipeInput>> RecipeSerializer<R> createSerializer(FluidKind<R> kind) {
		return kind.createSerializer();
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
		for (FluidKind<?> kind : FLUID_ALL) {
			registerFluid(kind);
		}
	}

	private static <R extends Recipe<FluidRecipeInput>> void registerFluid(FluidKind<R> kind) {
		Identifier id = Industrialization.id(kind.id);
		RecipeType<R> type = Registry.register(BuiltInRegistries.RECIPE_TYPE, id, createType(kind));
		RecipeSerializer<R> serializer =
				Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id, createSerializer(kind));
		kind.bind(() -> type, () -> serializer);
	}

	/**
	 * Resolve the recipe matching {@code input} for a machine's cached check, or {@code null} if the
	 * input is empty or no recipe (item or tag) accepts it.
	 */
	public static AlaProcessingRecipe lookup(RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> check,
			ServerLevel level, ItemStack input) {
		if (input.isEmpty()) {
			return null;
		}
		return lookup(check, level, new ProcessingRecipeInput(input));
	}

	/** Resolve an ordered one- or two-slot input against an item-processing recipe family. */
	public static AlaProcessingRecipe lookup(RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> check,
			ServerLevel level, ProcessingRecipeInput input) {
		if (input.isEmpty()) {
			return null;
		}
		return check.getRecipeFor(input, level).map(RecipeHolder::value).orElse(null);
	}
}
