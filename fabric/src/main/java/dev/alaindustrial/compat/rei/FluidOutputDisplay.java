package dev.alaindustrial.compat.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.FluidOutputViewerModel;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import dev.architectury.fluid.FluidStack;
import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;

/**
 * Reusable REI payload for fluid-input recipes with one or two simultaneous fluid outputs.
 *
 * <p>MOD-257 supplies the display contract only. MOD-251 will register this serializer, its server
 * recipe filler, a {@link FluidOutputCategory}, and the real Distillation Column workstation/screen
 * together; registering a Polymerizer-shaped placeholder here would expose a false workstation.
 */
public final class FluidOutputDisplay extends BasicDisplay {
	private final String kindId;
	private final int energy;
	private final int processingTicks;
	private final int inputAmountMb;

	public FluidOutputDisplay(FluidOutputRecipe recipe) {
		this(inputEntries(model(recipe)), outputEntries(model(recipe)), recipe.kind().id(),
				recipe.energy(), recipe.kind().ticksFor(recipe.energy()), recipe.amount());
	}

	private FluidOutputDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
			String kindId, int energy, int processingTicks, int inputAmountMb) {
		super(inputs, outputs);
		this.kindId = kindId;
		this.energy = energy;
		this.processingTicks = processingTicks;
		this.inputAmountMb = inputAmountMb;
	}

	static FluidOutputViewerModel<Holder<Fluid>> model(FluidOutputRecipe recipe) {
		List<FluidOutputViewerModel.Output<Holder<Fluid>>> outputs = recipe.fluidResults().stream()
				.map(result -> new FluidOutputViewerModel.Output<>(result.fluid(), result.amount()))
				.toList();
		return new FluidOutputViewerModel<>(
				recipe.displayFluids(), recipe.amount(), outputs,
				recipe.energy(), recipe.kind().ticksFor(recipe.energy()));
	}

	private static List<EntryIngredient> inputEntries(FluidOutputViewerModel<Holder<Fluid>> model) {
		long amount = reiAmount(model.inputAmountMb());
		return List.of(EntryIngredients.from(
				model.acceptedInputs(), holder -> EntryStacks.ofFluidHolder(holder, amount)));
	}

	private static List<EntryIngredient> outputEntries(FluidOutputViewerModel<Holder<Fluid>> model) {
		return model.outputs().stream()
				.map(output -> EntryIngredients.ofFluidHolder(output.fluid(), reiAmount(output.amountMb())))
				.toList();
	}

	static long reiAmount(int amountMb) {
		return FluidStack.bucketAmount() * amountMb / FluidAmounts.BUCKET;
	}

	public int energy() {
		return energy;
	}

	public int processingTicks() {
		return processingTicks;
	}

	public int inputAmountMb() {
		return inputAmountMb;
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return CategoryIdentifier.of(Industrialization.id(kindId));
	}

	@Override
	public DisplaySerializer<? extends me.shedaniel.rei.api.common.display.Display> getSerializer() {
		return SERIALIZER;
	}

	public static final DisplaySerializer<FluidOutputDisplay> SERIALIZER = DisplaySerializer.of(
			RecordCodecBuilder.mapCodec(instance -> instance.group(
					EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(BasicDisplay::getInputEntries),
					EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(BasicDisplay::getOutputEntries),
					Codec.STRING.fieldOf("kind").forGetter(display -> display.kindId),
					Codec.INT.fieldOf("energy").forGetter(display -> display.energy),
					Codec.INT.fieldOf("processing_ticks").forGetter(display -> display.processingTicks),
					Codec.INT.fieldOf("input_amount").forGetter(display -> display.inputAmountMb)
			).apply(instance, FluidOutputDisplay::new)),
			StreamCodec.composite(
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getInputEntries,
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getOutputEntries,
					ByteBufCodecs.STRING_UTF8, display -> display.kindId,
					ByteBufCodecs.INT, display -> display.energy,
					ByteBufCodecs.INT, display -> display.processingTicks,
					ByteBufCodecs.INT, display -> display.inputAmountMb,
					FluidOutputDisplay::new));
}
