package dev.alaindustrial.compat.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.recipe.ItemToFluidRecipe;
import dev.alaindustrial.registry.ModRecipes;
import dev.architectury.fluid.FluidStack;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * REI display for one {@link ItemToFluidRecipe} — the Honey Centrifuge's item → fluid card (MOD-924): the item
 * in, the fluid volume as the first output and, when the recipe hands one back, the container as the second.
 *
 * <p>Lives in the main source set on REI's common API only: built server-side by {@link AlaReiCommonPlugin}
 * and synced through {@link #SERIALIZER}, like the other machine displays.
 */
public class ItemToFluidDisplay extends BasicDisplay {
	/** The one category this display routes to ({@code alaindustrial:honey_centrifuging}). */
	public static final CategoryIdentifier<ItemToFluidDisplay> CATEGORY =
			CategoryIdentifier.of(Industrialization.id(ModRecipes.HONEY_CENTRIFUGING.id()));

	private final int energy;
	private final int amountMb;

	/** Build a display from a live recipe (the common case, used by the server-side filler). */
	public ItemToFluidDisplay(ItemToFluidRecipe recipe) {
		this(List.of(EntryIngredients.ofIngredient(recipe.ingredient())), outputsOf(recipe),
				recipe.energy(), recipe.amount());
	}

	private ItemToFluidDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
			int energy, int amountMb) {
		super(inputs, outputs);
		this.energy = energy;
		this.amountMb = amountMb;
	}

	/**
	 * The fluid first, the returned container second. REI measures fluids in architectury's own unit, so the
	 * volume is converted through {@link FluidStack#bucketAmount()} rather than by a hardcoded factor.
	 */
	private static List<EntryIngredient> outputsOf(ItemToFluidRecipe recipe) {
		long amount = FluidStack.bucketAmount() * recipe.amount() / FluidAmounts.BUCKET;
		List<EntryIngredient> outputs = new ArrayList<>(2);
		outputs.add(EntryIngredients.ofFluidHolder(recipe.result().fluid(), amount));
		ItemStack remainder = recipe.remainderStack();
		if (!remainder.isEmpty()) {
			outputs.add(EntryIngredients.of(remainder));
		}
		return outputs;
	}

	/** Total EU spent to complete one operation. */
	public int energy() {
		return energy;
	}

	/** Fluid produced per operation, in mB — printed on the card. */
	public int amountMb() {
		return amountMb;
	}

	/** Base processing time in ticks — see {@link ModRecipes.ItemFluidKind#ticksFor(int)}. */
	public int processingTicks() {
		return ModRecipes.HONEY_CENTRIFUGING.ticksFor(energy);
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
	public static final DisplaySerializer<ItemToFluidDisplay> SERIALIZER = DisplaySerializer.of(
			RecordCodecBuilder.mapCodec(instance -> instance.group(
					EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(BasicDisplay::getInputEntries),
					EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(BasicDisplay::getOutputEntries),
					Codec.INT.fieldOf("energy").forGetter(d -> d.energy),
					Codec.INT.fieldOf("amount").forGetter(d -> d.amountMb)
			).apply(instance, ItemToFluidDisplay::new)),
			StreamCodec.composite(
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getInputEntries,
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getOutputEntries,
					ByteBufCodecs.INT, d -> d.energy,
					ByteBufCodecs.INT, d -> d.amountMb,
					ItemToFluidDisplay::new));
}
