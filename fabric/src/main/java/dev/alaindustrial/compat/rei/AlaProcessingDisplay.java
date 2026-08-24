package dev.alaindustrial.compat.rei;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * REI display for one {@link AlaProcessingRecipe} (macerator / electric furnace / compressor /
 * extractor). One class serves all four machines: the recipe's {@link ModRecipes.Kind} picks the
 * category, and the displayed EU cost / time come from the recipe.
 *
 * <p>Lives in the <em>main</em> (common) source set — it is pure recipe data (inputs, outputs, kind,
 * energy) built on REI's common API only, with no client/GUI types. Displays are built server-side by
 * {@link AlaReiCommonPlugin} (where the {@code RecipeManager} is populated) and synced to the client
 * via {@link #SERIALIZER}; the client only supplies the category widgets ({@code AlaProcessingCategory}).
 * This split is required on MC 26.2, where the client no longer receives full recipes.
 */
public class AlaProcessingDisplay extends BasicDisplay {
	private final ModRecipes.Kind kind;
	private final int energy;
	private final double chance;

	/** Build a display from a live recipe (the common case, used by the server-side filler). */
	public AlaProcessingDisplay(AlaProcessingRecipe recipe) {
		this(inputsOf(recipe),
				List.of(EntryIngredients.of(recipe.result())),
				recipe.kind(), recipe.energy(), recipe.chance());
	}

	/**
	 * Inputs with their per-operation counts (MOD-271). REI's {@code ofIngredients} builds one-item
	 * stacks, which would understate a recipe that eats four of something — the viewer must show the
	 * price the machine actually charges. Recipes consuming one of each keep the plain REI path.
	 */
	// MOD-498 — Ingredient#items() is soft-deprecated by Mojang and kept deliberately. Ingredient.values is
	// private, so the only non-deprecated route is display() → SlotDisplay#resolveForStacks, and that needs
	// a ContextMap built from a Level. These displays are filled by AlaReiCommonPlugin — a REICommonPlugin
	// feeding ServerDisplayRegistry — so they are built on the server side, on a dedicated server too,
	// where no client Level exists at all. It also yields count-1 stacks where the code below applies the
	// recipe's own counts. Vanilla itself still calls items(), in RecipeManager#isIngredientEnabled.
	@SuppressWarnings("deprecation")
	private static List<EntryIngredient> inputsOf(AlaProcessingRecipe recipe) {
		if (recipe.consumesOneEach()) {
			return EntryIngredients.ofIngredients(recipe.ingredients());
		}
		List<EntryIngredient> inputs = new ArrayList<>(recipe.ingredients().size());
		for (int i = 0; i < recipe.ingredients().size(); i++) {
			int count = recipe.inputCount(i);
			inputs.add(EntryIngredient.of(recipe.ingredients().get(i).items()
					.map(holder -> EntryStacks.of(new ItemStack(holder, count)))
					.toList()));
		}
		return inputs;
	}

	private AlaProcessingDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
			ModRecipes.Kind kind, int energy, double chance) {
		super(inputs, outputs);
		this.kind = kind;
		this.energy = energy;
		this.chance = chance;
	}

	/**
	 * Success chance of one attempt, or 0 for the machines that always deliver.
	 *
	 * <p>What travels in the display is the recipe's own {@code chance} — unset stays unset — and the
	 * mode default is applied here, on the client. Resolving it server-side would have been more
	 * correct in isolation, but JEI has no server-side display pass and reads Config locally, so the
	 * two loaders printed different numbers against a server with a non-default config. Same source on
	 * both is worth more than being right on one of them.
	 */
	public double chance() {
		return IncubatorMode.chanceOf(kind, chance);
	}

	/** Total EU spent to complete one operation (the recipe's nominal cost). */
	public int energy() {
		return energy;
	}

	/**
	 * Base processing time in ticks, from the recipe family's own EU rate — see
	 * {@link ModRecipes.Kind#ticksFor(int)}. Dividing by the shared machine rate here used to print the
	 * incubator's operations four times too long.
	 */
	public int processingTicks() {
		return kind.ticksFor(energy);
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		// Each machine kind maps 1:1 to a REI category id (alaindustrial:<kind>). Computed directly so
		// this common-side class stays independent of the client plugin.
		return CategoryIdentifier.of(Industrialization.id(kind.id()));
	}

	@Override
	public DisplaySerializer<? extends me.shedaniel.rei.api.common.display.Display> getSerializer() {
		return SERIALIZER;
	}

	/** Round-trips a display as {inputs, outputs, kind id, energy, chance}; kind resolves back via id. */
	public static final DisplaySerializer<AlaProcessingDisplay> SERIALIZER = DisplaySerializer.of(
			RecordCodecBuilder.mapCodec(instance -> instance.group(
					EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(BasicDisplay::getInputEntries),
					EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(BasicDisplay::getOutputEntries),
					com.mojang.serialization.Codec.STRING.fieldOf("kind").forGetter(d -> d.kind.id()),
					com.mojang.serialization.Codec.INT.fieldOf("energy").forGetter(d -> d.energy),
					// Optional with the recipe's own signal value: a display serialised before this field
					// existed must still load, and "unset" has to survive the round trip so the mode
					// default is applied to it rather than a hard zero.
					com.mojang.serialization.Codec.DOUBLE
							.optionalFieldOf("chance", AlaProcessingRecipe.CHANCE_UNSET)
							.forGetter(d -> d.chance)
			).apply(instance, AlaProcessingDisplay::fromParts)),
			StreamCodec.composite(
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getInputEntries,
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getOutputEntries,
					ByteBufCodecs.STRING_UTF8, d -> d.kind.id(),
					ByteBufCodecs.INT, d -> d.energy,
					ByteBufCodecs.DOUBLE, d -> d.chance,
					AlaProcessingDisplay::fromParts));

	private static AlaProcessingDisplay fromParts(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
			String kindId, int energy, double chance) {
		return new AlaProcessingDisplay(inputs, outputs, ModRecipes.byId(kindId), energy, chance);
	}
}
