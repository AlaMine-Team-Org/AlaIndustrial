package dev.alaindustrial.registry;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.FluidRecipeInput;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import java.util.function.IntSupplier;
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

	private static final Kind[] ALL = {MACERATION, SMELTING, COMPRESSING, EXTRACTING,
			SAWING_PLANKS, SAWING_STICKS, SAWING_SLABS, SAWING_STAIRS,
			MUTATION_TRANSFORM, MUTATION_DUPLICATE, MUTATION_CREATE};

	/** All recipe families, in registration order (used by both loaders' registration). */
	public static Kind[] kinds() {
		return ALL;
	}

	/**
	 * The Polymerizer's recipe family (MOD-019). Separate from {@link Kind} because its recipes are
	 * {@link PolymerizingRecipe}s — a fluid volume in, an item out — not {@link AlaProcessingRecipe}s, so
	 * the two cannot share a typed {@code RecipeType}/{@code RecipeSerializer} pair. Everything else about
	 * it (lazy per-loader binding, an EU rate, "how long does this cost print as") is the same, and the
	 * two are registered side by side in {@link #init()} and its NeoForge counterpart.
	 *
	 * <p>Not generified over the recipe class: with exactly one fluid family, one more type parameter on
	 * every accessor buys nothing a second copy of six short methods does not.
	 */
	public static final class FluidKind {
		private final String id;
		private final int defaultEnergy;
		private Supplier<RecipeType<PolymerizingRecipe>> type = () -> {
			throw new IllegalStateException("ModRecipes.FluidKind type read before its loader bound it");
		};
		private Supplier<RecipeSerializer<PolymerizingRecipe>> serializer = () -> {
			throw new IllegalStateException("ModRecipes.FluidKind serializer read before its loader bound it");
		};

		private FluidKind(String id, int defaultEnergy) {
			this.id = id;
			this.defaultEnergy = defaultEnergy;
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

		public RecipeType<PolymerizingRecipe> type() {
			return type.get();
		}

		public RecipeSerializer<PolymerizingRecipe> serializer() {
			return serializer.get();
		}

		/** Bind this family's type + serializer suppliers. Called once per loader during its registration. */
		public void bind(Supplier<RecipeType<PolymerizingRecipe>> typeSupplier,
				Supplier<RecipeSerializer<PolymerizingRecipe>> serializerSupplier) {
			this.type = typeSupplier;
			this.serializer = serializerSupplier;
		}

		/** A cached lookup for the machine (mirrors {@link Kind#newCheck()}). */
		public RecipeManager.CachedCheck<FluidRecipeInput, PolymerizingRecipe> newCheck() {
			return RecipeManager.createCheck(type.get());
		}
	}

	// defaultEnergy 400 = polymerizerDuration (200) × machineEuPerTick (2); the shipped JSON states it
	// explicitly, so this fallback is never active — it is kept in step with the real cost on purpose so
	// recipe_check.py does not flag a stale-looking default (MOD-134).
	//
	// NOTE: PolymerizingRecipe's static codecs read this field's defaultEnergy(), so this class's own
	// static init must never touch PolymerizingRecipe's statics — that would be a re-entrant clinit and
	// the codec would see a null field. Today it does not (only Kind/FluidKind objects and arrays are
	// built here); keep it that way when adding statics above this line.
	public static final FluidKind POLYMERIZING = new FluidKind("polymerizing", 400);

	private static final FluidKind[] FLUID_ALL = {POLYMERIZING};

	/** All fluid-input recipe families, in registration order (used by both loaders' registration). */
	public static FluidKind[] fluidKinds() {
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
	public static RecipeType<PolymerizingRecipe> createType(FluidKind kind) {
		Identifier id = Industrialization.id(kind.id);
		return new RecipeType<PolymerizingRecipe>() {
			@Override
			public String toString() {
				return id.toString();
			}
		};
	}

	/**
	 * Build the {@link RecipeSerializer} instance both loaders register for {@code kind} (MOD-019).
	 *
	 * <p>Unlike its {@link Kind} counterpart the codec is <b>not</b> per-family: {@link PolymerizingRecipe}
	 * is one concrete class whose {@code getSerializer()} reads {@link #POLYMERIZING} directly, so a second
	 * fluid family sharing this factory would serialise as a polymerizing recipe. That is a real trap and
	 * not a theoretical one — {@code FUTURE_CONTENT.md} lists a fermenter, an electrolyzer and a bottling
	 * machine, all fluid-fed. Whoever adds the second family must give it its own recipe class and codec;
	 * until then this fails loudly rather than mis-serialising quietly.
	 */
	public static RecipeSerializer<PolymerizingRecipe> createSerializer(FluidKind kind) {
		if (kind != POLYMERIZING) {
			throw new IllegalArgumentException("FluidKind '" + kind.id() + "' has no recipe class of its own; "
					+ "PolymerizingRecipe is hard-bound to ModRecipes.POLYMERIZING (see this method's doc)");
		}
		return new RecipeSerializer<>(PolymerizingRecipe.MAP_CODEC, PolymerizingRecipe.STREAM_CODEC);
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
		for (FluidKind kind : FLUID_ALL) {
			Identifier id = Industrialization.id(kind.id);
			RecipeType<PolymerizingRecipe> type = Registry.register(BuiltInRegistries.RECIPE_TYPE, id, createType(kind));
			RecipeSerializer<PolymerizingRecipe> serializer =
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
