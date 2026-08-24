package dev.alaindustrial.compat.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlloyComponent;
import dev.alaindustrial.recipe.AlloyingRecipe;
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
 * REI display for one {@link AlloyingRecipe} — the Alloy Smelter's two-or-three inputs → one alloy
 * card (MOD-064).
 *
 * <p>Separate from {@link AlaProcessingDisplay} because that class carries a
 * {@link ModRecipes.Kind}, which the alloying family is not: it belongs to
 * {@link ModRecipes.AlloyKind}, and its per-component counts are not the positional
 * {@code inputCounts} of a processing recipe.
 *
 * <p>Lives in the Fabric subproject's <em>main</em> source set (REI is a Fabric-side dependency), and
 * holds pure recipe data on REI's common API only, with no client/GUI types. Displays are built server-side by {@link AlaReiCommonPlugin} (where the
 * {@code RecipeManager} is populated) and synced to the client via {@link #SERIALIZER}; the client
 * supplies only the category widgets ({@code AlloyingCategory}). Required on MC 26.2, where the
 * client no longer receives full recipes.
 */
public class AlloyingDisplay extends BasicDisplay {
	/** The one category this display routes to ({@code alaindustrial:alloying}). */
	public static final CategoryIdentifier<AlloyingDisplay> CATEGORY =
			CategoryIdentifier.of(Industrialization.id(ModRecipes.ALLOYING.id()));

	private final int energy;

	/** Build a display from a live recipe (the common case, used by the server-side filler). */
	public AlloyingDisplay(AlloyingRecipe recipe) {
		this(inputsOf(recipe), List.of(EntryIngredients.of(recipe.result())), recipe.energy());
	}

	/**
	 * One REI ingredient per component, each stack carrying the count the operation actually consumes.
	 * REI's {@code ofIngredients} would build one-item stacks and quietly understate bronze as "1 copper
	 * + 1 tin" when the recipe eats three copper.
	 */
	// MOD-498 — Ingredient#items() is soft-deprecated and kept deliberately. Ingredient.values is private,
	// so the only non-deprecated route is display() → SlotDisplay#resolveForStacks, and that needs a
	// ContextMap built from a Level. These displays are filled by AlaReiCommonPlugin — a REICommonPlugin
	// feeding ServerDisplayRegistry — so they are built on the server side, on a dedicated server too,
	// where no client Level exists at all. It also yields count-1 stacks rather than holders this code can
	// restack with the recipe's own counts. Vanilla still calls items(), in RecipeManager#isIngredientEnabled.
	@SuppressWarnings("deprecation")
	private static List<EntryIngredient> inputsOf(AlloyingRecipe recipe) {
		List<EntryIngredient> inputs = new ArrayList<>(recipe.components().size());
		for (AlloyComponent component : recipe.components()) {
			inputs.add(EntryIngredient.of(component.ingredient().items()
					.map(holder -> EntryStacks.of(new ItemStack(holder, component.count())))
					.toList()));
		}
		return inputs;
	}

	private AlloyingDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, int energy) {
		super(inputs, outputs);
		this.energy = energy;
	}

	/** Total EU spent to complete one operation (the recipe's nominal cost). */
	public int energy() {
		return energy;
	}

	/** Base processing time in ticks — from the smelter's own EU rate, not the shared machine one. */
	public int processingTicks() {
		return ModRecipes.ALLOYING.ticksFor(energy);
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return CATEGORY;
	}

	@Override
	public DisplaySerializer<? extends me.shedaniel.rei.api.common.display.Display> getSerializer() {
		return SERIALIZER;
	}

	/** Round-trips a display as {inputs, outputs, energy}. */
	public static final DisplaySerializer<AlloyingDisplay> SERIALIZER = DisplaySerializer.of(
			RecordCodecBuilder.mapCodec(instance -> instance.group(
					EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(BasicDisplay::getInputEntries),
					EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(BasicDisplay::getOutputEntries),
					Codec.INT.fieldOf("energy").forGetter(d -> d.energy)
			).apply(instance, AlloyingDisplay::new)),
			StreamCodec.composite(
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getInputEntries,
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getOutputEntries,
					ByteBufCodecs.INT, d -> d.energy,
					AlloyingDisplay::new));
}
