package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.client.compat.FluidOutputViewerModel;
import dev.alaindustrial.client.compat.RecipeViewerLayout;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Reusable JEI adapter for one fluid input and one or two simultaneous fluid outputs.
 *
 * <p>MOD-251 will register this category with the actual Distillation Column, its catalyst and GUI
 * click target. Keeping that hook unregistered here avoids advertising the Polymerizer as a machine
 * that performs distillation.
 */
final class FluidOutputJeiCategory implements IRecipeCategory<RecipeHolder<FluidOutputRecipe>> {
	private final IRecipeHolderType<FluidOutputRecipe> recipeType;
	private final Component title;
	private final IDrawable icon;
	private final IDrawable arrow;

	FluidOutputJeiCategory(IRecipeHolderType<FluidOutputRecipe> recipeType, Block iconBlock,
			Component title, IGuiHelper guiHelper) {
		this.recipeType = recipeType;
		this.title = title;
		this.icon = guiHelper.createDrawableItemLike(iconBlock);
		this.arrow = guiHelper.getRecipeArrow();
	}

	@Override
	public IRecipeHolderType<FluidOutputRecipe> getRecipeType() {
		return recipeType;
	}

	@Override
	public Component getTitle() {
		return title;
	}

	@Override
	public int getWidth() {
		return RecipeViewerLayout.WIDTH;
	}

	@Override
	public int getHeight() {
		return RecipeViewerLayout.HEIGHT;
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FluidOutputRecipe> holder, IFocusGroup focuses) {
		FluidOutputViewerModel<Holder<Fluid>> model = model(holder.value());
		IRecipeSlotBuilder input = builder.addInputSlot(
				RecipeViewerLayout.inputXs(1).getFirst(), RecipeViewerLayout.SLOT_Y)
				.setStandardSlotBackground();
		for (Holder<Fluid> fluid : model.acceptedInputs()) {
			input.add(fluid.value(), model.inputAmountMb());
		}

		List<Integer> outputXs = RecipeViewerLayout.outputXs(model.outputs().size());
		for (int i = 0; i < outputXs.size(); i++) {
			FluidOutputViewerModel.Output<Holder<Fluid>> output = model.outputs().get(i);
			builder.addOutputSlot(outputXs.get(i), RecipeViewerLayout.SLOT_Y)
					.setOutputSlotBackground()
					.add(output.fluid().value(), output.amountMb());
		}
	}

	@Override
	public void draw(RecipeHolder<FluidOutputRecipe> holder, IRecipeSlotsView recipeSlotsView,
			GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
		FluidOutputViewerModel<Holder<Fluid>> model = model(holder.value());
		arrow.draw(graphics, RecipeViewerLayout.ARROW_X, RecipeViewerLayout.ARROW_Y);
		String cost = RecipeViewerLayout.fluidCostLabel(
				model.energy(), model.processingTicks(), model.inputAmountMb());
		int x = (RecipeViewerLayout.WIDTH - Minecraft.getInstance().font.width(cost)) / 2;
		graphics.text(Minecraft.getInstance().font, Component.literal(cost), x,
				RecipeViewerLayout.LABEL_Y, 0xFF404040, false);
	}

	@Override
	public Identifier getIdentifier(RecipeHolder<FluidOutputRecipe> holder) {
		return holder.id().identifier();
	}

	static FluidOutputViewerModel<Holder<Fluid>> model(FluidOutputRecipe recipe) {
		List<FluidOutputViewerModel.Output<Holder<Fluid>>> outputs = recipe.fluidResults().stream()
				.map(result -> new FluidOutputViewerModel.Output<>(result.fluid(), result.amount()))
				.toList();
		return new FluidOutputViewerModel<>(
				recipe.displayFluids(), recipe.amount(), outputs,
				recipe.energy(), recipe.kind().ticksFor(recipe.energy()));
	}
}
