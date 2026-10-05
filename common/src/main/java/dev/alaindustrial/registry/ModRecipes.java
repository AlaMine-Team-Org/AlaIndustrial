package dev.alaindustrial.registry;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.AlloyRecipeInput;
import dev.alaindustrial.recipe.AlloyingRecipe;
import dev.alaindustrial.recipe.ChargedCraftRecipe;
import dev.alaindustrial.recipe.FluidRecipeInput;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
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
import net.minecraft.world.level.block.Block;

/**
 * Central registration for the machine {@link RecipeType}s + {@link RecipeSerializer}s (R-14). Each
 * processing machine has its own {@link Kind} (so JEI/REI and {@code /reload} see four distinct
 * recipe families), all sharing the single {@link AlaProcessingRecipe} class and JSON shape.
 *
 * <p>Recipes are real vanilla recipes under {@code data/<ns>/recipe/<machine>/*.json}; their input is
 * an {@link net.minecraft.world.item.crafting.Ingredient} so it can be an item or a tag (R-15).
 *
 * <p>MOD-022 facade: NeoForge freezes the {@code RECIPE_TYPE}/{@code RECIPE_SERIALIZER} registries before
 * mod construction, so each family's type/serializer are bound lazily per loader — Fabric via the
 * eager {@link #init()} below, NeoForge via a {@code DeferredRegister} (see {@code ModRecipesNeoForge}) —
 * and read through {@code Supplier}s in the accessors.
 *
 * <p><b>One list (MOD-708).</b> Every {@link RecipeFamily} is declared through {@link #family}, which
 * appends it to {@link #families()} in declaration order — the registration order of both loaders. A new
 * family is that one declaration; {@link #kinds()}, {@link #fluidKinds()} and {@link #alloyKinds()} (the
 * lists the recipe viewers replay) are derived from it.
 */
public final class ModRecipes {
	private ModRecipes() {
	}

	/** Every family in declaration order. Declared first: the {@link #family} calls below append to it. */
	private static final List<RecipeFamily<?>> FAMILIES = new ArrayList<>();

	private static <F extends RecipeFamily<?>> F family(F family) {
		FAMILIES.add(family);
		return family;
	}

	/** Every recipe family, in registration order — the list both loaders register and Fabric syncs. */
	public static List<RecipeFamily<?>> families() {
		return Collections.unmodifiableList(FAMILIES);
	}

	/** The families of class {@code type}, in registration order. */
	private static <F> F[] only(Class<?> type, IntFunction<F[]> array) {
		List<Object> matching = new ArrayList<>();
		for (RecipeFamily<?> family : FAMILIES) {
			if (type.isInstance(family)) {
				matching.add(family);
			}
		}
		return matching.toArray(array.apply(0));
	}

	// ── Charge-carrying crafting recipe (MOD-083) ────────────────────────────────────────────────
	//
	// Unlike the machine families below this is a CRAFTING recipe: the type is vanilla's, so only a
	// serializer is registered. Declared first because it is registered first on both loaders.

	/** Registry path of the charge-carrying crafting serializer — also its {@code "type"} in JSON. */
	public static final String CHARGED_CRAFT_ID = "crafting_shaped_charge_transfer";

	public static final ChargedCraft CHARGED_CRAFT = family(new ChargedCraft(CHARGED_CRAFT_ID));

	/**
	 * One item-processing family: the positional {@link ProcessingRecipeInput} and the single
	 * {@link AlaProcessingRecipe} class every such machine shares. Id, energy, station, draw, type and
	 * serializer are {@link MachineRecipeFamily}'s.
	 */
	public static final class Kind extends MachineRecipeFamily<ProcessingRecipeInput, AlaProcessingRecipe, Kind> {
		private Kind(String id, int defaultEnergy, Supplier<Block> station) {
			this(id, defaultEnergy, station, ServerBalance::machineEuPerTick);
		}

		private Kind(String id, int defaultEnergy, Supplier<Block> station, IntSupplier euPerTick) {
			super(id, defaultEnergy, station, euPerTick,
					AlaProcessingRecipe::mapCodec, AlaProcessingRecipe::streamCodec);
		}
	}

	// defaultEnergy is the fallback when a recipe JSON omits `energy`; every shipped
	// maceration JSON sets `energy: 300` (= maceratorDuration × machineEuPerTick), so this default
	// is never active. It is kept aligned with the actual recipe energy on purpose so the
	// recipe_check.py validator does not flag a stale-looking fallback (MOD-134).
	public static final Kind MACERATION = family(new Kind("maceration", 300, () -> ModContent.MACERATOR.get()));
	public static final Kind SMELTING = family(new Kind("smelting", 200, () -> ModContent.ELECTRIC_FURNACE.get()));
	public static final Kind COMPRESSING = family(new Kind("compressing", 260, () -> ModContent.COMPRESSOR.get()));
	public static final Kind EXTRACTING = family(new Kind("extracting", 240, () -> ModContent.EXTRACTOR.get()));
	public static final Kind VULCANIZING = family(new Kind("vulcanizing", 400, () -> ModContent.VULCANIZER.get()));
	// Galvanic Bath (MOD-127): fibre + silver dust → flux thread. Two item inputs like the Vulcanizer;
	// the water the bath also consumes is a fixed config cost, not a recipe field (see the block entity).
	public static final Kind GALVANIC_BATH =
			family(new Kind("galvanic_bath", 1000, () -> ModContent.GALVANIC_BATH.get()));
	// Thermal Centrifuge (MOD-424): the second doubling step on an ore, after the macerator's. Carries its
	// own draw (4 EU/t against the shared 2), so the kind must be told — energy / euPerTick is what the
	// recipe viewers print as the operation's length, and a wrong divisor here shows players a wrong time.
	public static final Kind CENTRIFUGING = family(new Kind("centrifuging", 800,
			() -> ModContent.THERMAL_CENTRIFUGE.get(), ServerBalance::thermalCentrifugeEuPerTick));
	// Sawmill (MOD-150): one Kind per cutting mode (planks/sticks/slabs/stairs). defaultEnergy 160 =
	// sawmillDuration (80) × machineEuPerTick (2); every shipped sawing JSON sets energy: 160 explicitly.
	public static final Kind SAWING_PLANKS = family(new Kind("sawing_planks", 160, () -> ModContent.SAWMILL.get()));
	public static final Kind SAWING_STICKS = family(new Kind("sawing_sticks", 160, () -> ModContent.SAWMILL.get()));
	public static final Kind SAWING_SLABS = family(new Kind("sawing_slabs", 160, () -> ModContent.SAWMILL.get()));
	public static final Kind SAWING_STAIRS = family(new Kind("sawing_stairs", 160, () -> ModContent.SAWMILL.get()));

	// Incubator (MOD-118): one kind per mutation mode, selected by the chip in the machine.
	// Splitting by type (rather than a "kind" field inside one type) matches how the sawmill models
	// its cutting modes, and it comes with per-mode recipe-viewer categories for free.
	// The incubator is the one machine with its own draw (8 EU/t against the shared 2), so these three
	// carry it: energy / euPerTick is what the recipe viewers show as the operation's length.
	public static final Kind MUTATION_TRANSFORM = family(new Kind("mutation_transform", 2400,
			() -> ModContent.INCUBATOR.get(), ServerBalance::incubatorEuPerTick));
	public static final Kind MUTATION_DUPLICATE = family(new Kind("mutation_duplicate", 4000,
			() -> ModContent.INCUBATOR.get(), ServerBalance::incubatorEuPerTick));
	public static final Kind MUTATION_CREATE = family(new Kind("mutation_create", 8000,
			() -> ModContent.INCUBATOR.get(), ServerBalance::incubatorEuPerTick));

	// Fermenter (MOD-146): organic waste → biomass. The water it drinks and the biofuel it brews are
	// fixed config costs rather than recipe fields — the mod has no recipe family that mixes items and
	// fluids on one side, and the Galvanic Bath already set this precedent rather than inventing one.
	// 1200 = fermenterDuration (600) × machineEuPerTick (2); every shipped fermenting JSON says so.
	public static final Kind FERMENTING = family(new Kind("fermenting", 1200, () -> ModContent.FERMENTER.get()));

	private static final Kind[] ALL = only(Kind.class, Kind[]::new);

	/** The item-processing families, in registration order — the list the recipe viewers replay. */
	public static Kind[] kinds() {
		return ALL;
	}

	/**
	 * One fluid-input recipe family. The recipe type is generic because polymerizing produces an item,
	 * while distilling produces fluid stacks; each family owns codec factories for its concrete recipe.
	 */
	public static final class FluidKind<R extends Recipe<FluidRecipeInput>>
			extends MachineRecipeFamily<FluidRecipeInput, R, FluidKind<R>> {
		/** The fluid machines share the processing-machine rate. */
		private FluidKind(String id, int defaultEnergy, Supplier<Block> station,
				Function<FluidKind<R>, MapCodec<R>> mapCodecFactory,
				Function<FluidKind<R>, StreamCodec<RegistryFriendlyByteBuf, R>> streamCodecFactory) {
			super(id, defaultEnergy, station, ServerBalance::machineEuPerTick, mapCodecFactory, streamCodecFactory);
		}
	}

	// defaultEnergy 400 = polymerizerDuration (200) × machineEuPerTick (2); the shipped JSON states it
	// explicitly, so this fallback is never active — it is kept in step with the real cost on purpose so
	// recipe_check.py does not flag a stale-looking default (MOD-134).
	//
	// Codec factories receive their already-created FluidKind when registration runs. They therefore
	// never read a not-yet-assigned ModRecipes static during this class's own initialization.
	public static final FluidKind<PolymerizingRecipe> POLYMERIZING = family(new FluidKind<>(
			"polymerizing", 400, () -> ModContent.POLYMERIZER.get(),
			kind -> PolymerizingRecipe.mapCodec(kind),
			kind -> PolymerizingRecipe.streamCodec(kind)));
	public static final FluidKind<FluidOutputRecipe> DISTILLING = family(new FluidKind<>(
			"distilling", 400, () -> ModContent.DISTILLATION_COLUMN.get(),
			kind -> FluidOutputRecipe.mapCodec(kind),
			kind -> FluidOutputRecipe.streamCodec(kind)));

	private static final FluidKind<?>[] FLUID_ALL = only(FluidKind.class, FluidKind<?>[]::new);

	/** The fluid-input families, in registration order — the list the recipe viewers replay. */
	public static FluidKind<?>[] fluidKinds() {
		return FLUID_ALL;
	}

	/**
	 * One multi-input alloying family (MOD-064): an unordered three-slot {@link AlloyRecipeInput}, with its
	 * own draw. The third {@link MachineRecipeFamily} next to {@link Kind} and {@link FluidKind}.
	 */
	public static final class AlloyKind<R extends Recipe<AlloyRecipeInput>>
			extends MachineRecipeFamily<AlloyRecipeInput, R, AlloyKind<R>> {
		private AlloyKind(String id, int defaultEnergy, Supplier<Block> station, IntSupplier euPerTick,
				Function<AlloyKind<R>, MapCodec<R>> mapCodecFactory,
				Function<AlloyKind<R>, StreamCodec<RegistryFriendlyByteBuf, R>> streamCodecFactory) {
			super(id, defaultEnergy, station, euPerTick, mapCodecFactory, streamCodecFactory);
		}
	}

	// Alloy Smelter (MOD-064). defaultEnergy 1200 = alloySmelterDuration (150) × alloySmelterEuPerTick (8);
	// every shipped alloying JSON states it explicitly, so this fallback is never active — it is kept in
	// step with the real cost on purpose so recipe_check.py does not flag a stale-looking default (MOD-134).
	// The smelter is one of the few machines with its own draw (8 EU/t against the shared 2), so the kind
	// carries it: energy / euPerTick is what the recipe viewers show as the operation's length.
	public static final AlloyKind<AlloyingRecipe> ALLOYING = family(new AlloyKind<>(
			"alloying", 1200, () -> ModContent.ALLOY_SMELTER.get(), ServerBalance::alloySmelterEuPerTick,
			kind -> AlloyingRecipe.mapCodec(kind),
			kind -> AlloyingRecipe.streamCodec(kind)));

	private static final AlloyKind<?>[] ALLOY_ALL = only(AlloyKind.class, AlloyKind<?>[]::new);

	/** The alloying families, in registration order — the list the recipe viewers replay. */
	public static AlloyKind<?>[] alloyKinds() {
		return ALLOY_ALL;
	}

	/** Resolve a {@link Kind} back from its string id, or {@code null} if unknown. Used by the REI
	 *  display serializer to rebuild a display's kind from its synced id (see {@code AlaProcessingDisplay}). */
	public static Kind byId(String id) {
		for (Kind kind : ALL) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		return null;
	}

	/**
	 * Fabric registration: the {@code RECIPE_TYPE}/{@code RECIPE_SERIALIZER} registries stay writable during
	 * init, so every family is registered eagerly, in {@link #families()} order, and bound to constant
	 * suppliers. NeoForge replays the same list through a {@code DeferredRegister} (see
	 * {@code ModRecipesNeoForge}).
	 */
	public static void init() {
		for (RecipeFamily<?> family : FAMILIES) {
			register(family);
		}
	}

	private static <R extends Recipe<?>> void register(RecipeFamily<R> family) {
		Identifier id = Industrialization.id(family.id());
		RecipeType<R> type = family.createType()
				.map(created -> Registry.register(BuiltInRegistries.RECIPE_TYPE, id, created))
				.orElse(null);
		RecipeSerializer<R> serializer =
				Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id, family.createSerializer());
		family.bind(type == null ? RecipeFamily.noType(family.id()) : () -> type, () -> serializer);
	}

	/** The serializer every {@link ChargedCraftRecipe} reports as its own. */
	public static RecipeSerializer<ChargedCraftRecipe> chargedCraftSerializer() {
		return CHARGED_CRAFT.serializer();
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

	/** Resolve an unordered three-slot input against the alloying family (MOD-064). */
	public static AlloyingRecipe lookup(RecipeManager.CachedCheck<AlloyRecipeInput, AlloyingRecipe> check,
			ServerLevel level, AlloyRecipeInput input) {
		if (input.isEmpty()) {
			return null;
		}
		return check.getRecipeFor(input, level).map(RecipeHolder::value).orElse(null);
	}
}
