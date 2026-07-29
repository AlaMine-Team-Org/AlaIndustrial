package dev.alaindustrial.compat.jei;

import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.client.compat.RecipeViewerLayout;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

final class AlaProcessingJeiCategory implements IRecipeCategory<RecipeHolder<AlaProcessingRecipe>> {
	private final IRecipeHolderType<AlaProcessingRecipe> recipeType;
	private final Component title;
	private final IDrawable icon;
	private final IDrawable arrow;

	AlaProcessingJeiCategory(IRecipeHolderType<AlaProcessingRecipe> recipeType, Block iconBlock,
			Component title, IGuiHelper guiHelper) {
		this.recipeType = recipeType;
		// Composed by the caller from already-localised parts (block name + mode) — no new lang keys.
		this.title = title;
		this.icon = guiHelper.createDrawableItemLike(iconBlock);
		this.arrow = guiHelper.getRecipeArrow();
	}

	@Override
	public IRecipeHolderType<AlaProcessingRecipe> getRecipeType() {
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
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AlaProcessingRecipe> holder, IFocusGroup focuses) {
		AlaProcessingRecipe recipe = holder.value();
		List<Integer> inputXs = RecipeViewerLayout.inputXs(recipe.ingredients().size());
		for (int i = 0; i < inputXs.size(); i++) {
			builder.addInputSlot(inputXs.get(i), RecipeViewerLayout.SLOT_Y)
					.setStandardSlotBackground()
					.add(recipe.ingredients().get(i));
		}
		builder.addOutputSlot(RecipeViewerLayout.outputXs(1).getFirst(), RecipeViewerLayout.SLOT_Y)
				.setOutputSlotBackground()
				.add(recipe.result());
	}

	@Override
	public void draw(RecipeHolder<AlaProcessingRecipe> holder, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor graphics,
			double mouseX, double mouseY) {
		AlaProcessingRecipe recipe = holder.value();
		arrow.draw(graphics, RecipeViewerLayout.ARROW_X, RecipeViewerLayout.ARROW_Y);
		// The chance suffix only appears where the operation is a gamble (the incubator); without it a
		// duplicate recipe reads as a guaranteed doubling. Symbols, so no lang key — as with EU and s.
		String cost = RecipeViewerLayout.withChance(
				RecipeViewerLayout.costLabel(recipe.energy(), processingTicks(recipe)),
				IncubatorMode.chanceOf(recipe.kind(), recipe.chance()));
		int x = (RecipeViewerLayout.WIDTH - Minecraft.getInstance().font.width(cost)) / 2;
		graphics.text(Minecraft.getInstance().font, Component.literal(cost), x,
				RecipeViewerLayout.LABEL_Y, 0xFF404040, false);
	}

	@Override
	public Identifier getIdentifier(RecipeHolder<AlaProcessingRecipe> holder) {
		return holder.id().identifier();
	}

	/**
	 * From the recipe family's own EU rate — see {@link dev.alaindustrial.registry.ModRecipes.Kind#ticksFor(int)}.
	 * Dividing by the shared machine rate here used to print the incubator's operations four times too long.
	 */
	private static int processingTicks(AlaProcessingRecipe recipe) {
		return recipe.kind().ticksFor(recipe.energy());
	}

}
