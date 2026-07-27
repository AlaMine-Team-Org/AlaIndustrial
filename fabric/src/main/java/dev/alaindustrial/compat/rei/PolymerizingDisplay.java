package dev.alaindustrial.compat.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import dev.architectury.fluid.FluidStack;
import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * REI display for one {@link PolymerizingRecipe} — the Polymerizer's fluid → item card (MOD-019).
 *
 * <p>Separate from {@link AlaProcessingDisplay} because the input is a fluid entry, not an item one:
 * REI renders it as a real fluid stack (its own texture and its own "1000 mB" tooltip), which is what
 * the machine actually consumes.
 *
 * <p>Lives in the <em>main</em> (common) source set — pure recipe data on REI's common API only, no
 * client/GUI types. Displays are built server-side by {@link AlaReiCommonPlugin} (where the
 * {@code RecipeManager} is populated) and synced to the client via {@link #SERIALIZER}; the client
 * supplies only the category widgets ({@code PolymerizingCategory}). Required on MC 26.2, where the
 * client no longer receives full recipes.
 */
public class PolymerizingDisplay extends BasicDisplay {
	/** The one category this display routes to ({@code alaindustrial:polymerizing}). */
	public static final CategoryIdentifier<PolymerizingDisplay> CATEGORY =
			CategoryIdentifier.of(Industrialization.id(ModRecipes.POLYMERIZING.id()));

	private final int energy;
	private final int amountMb;

	/** Build a display from a live recipe (the common case, used by the server-side filler). */
	public PolymerizingDisplay(PolymerizingRecipe recipe) {
		this(List.of(fluidInput(recipe)), List.of(EntryIngredients.of(recipe.result())),
				recipe.energy(), recipe.amount());
	}

	private PolymerizingDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
			int energy, int amountMb) {
		super(inputs, outputs);
		this.energy = energy;
		this.amountMb = amountMb;
	}

	/**
	 * The recipe's accepted fluids as one REI ingredient, each carrying the consumed volume. A tag-backed
	 * recipe ({@code #c:oil}) therefore cycles through every oil in the pack, exactly as an item tag does
	 * in the other machines' cards.
	 *
	 * <p>REI measures fluids in architectury's own unit, not millibuckets, so the volume is converted
	 * through {@link FluidStack#bucketAmount()} rather than by a hardcoded factor.
	 */
	private static EntryIngredient fluidInput(PolymerizingRecipe recipe) {
		long amount = FluidStack.bucketAmount() * recipe.amount() / FluidAmounts.BUCKET;
		// displayFluids(), not fluid(): a fluid tag lists the flowing variant too, and showing "Flowing Oil"
		// as a second, identical-looking input on the card is noise the player cannot act on.
		return EntryIngredients.from(recipe.displayFluids(), holder -> EntryStacks.of(holder.value(), amount));
	}

	/** Total EU spent to complete one operation (the recipe's nominal cost). */
	public int energy() {
		return energy;
	}

	/** Fluid consumed per operation, in mB — printed on the card, since REI's own label is unit-agnostic. */
	public int amountMb() {
		return amountMb;
	}

	/** Base processing time in ticks — see {@link ModRecipes.FluidKind#ticksFor(int)}. */
	public int processingTicks() {
		return ModRecipes.POLYMERIZING.ticksFor(energy);
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return CATEGORY;
	}

	@Override
	public DisplaySerializer<? extends me.shedaniel.rei.api.common.display.Display> getSerializer() {
		return SERIALIZER;
	}

	/** Round-trips a display as {inputs, outputs, energy, amount}. */
	public static final DisplaySerializer<PolymerizingDisplay> SERIALIZER = DisplaySerializer.of(
			RecordCodecBuilder.mapCodec(instance -> instance.group(
					EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(BasicDisplay::getInputEntries),
					EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(BasicDisplay::getOutputEntries),
					Codec.INT.fieldOf("energy").forGetter(d -> d.energy),
					Codec.INT.fieldOf("amount").forGetter(d -> d.amountMb)
			).apply(instance, PolymerizingDisplay::new)),
			StreamCodec.composite(
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getInputEntries,
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getOutputEntries,
					ByteBufCodecs.INT, d -> d.energy,
					ByteBufCodecs.INT, d -> d.amountMb,
					PolymerizingDisplay::new));
}
