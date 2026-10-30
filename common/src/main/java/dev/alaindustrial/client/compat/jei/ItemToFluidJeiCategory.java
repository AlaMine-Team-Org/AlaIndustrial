package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.client.compat.RecipeViewerLayout;
import dev.alaindustrial.recipe.ItemToFluidRecipe;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;

/**
 * JEI category for an item → fluid family (MOD-924, the Honey Centrifuge): item in, the fluid volume out, and
 * the returned container in a second output slot when the recipe hands one back. The JEI counterpart of the
 * REI {@code ItemToFluidCategory}; same single-row layout as the other machine cards.
 */
final class ItemToFluidJeiCategory implements IRecipeCategory<RecipeHolder<ItemToFluidRecipe>> {
	private final IRecipeHolderType<ItemToFluidRecipe> recipeType;
	private final Component title;
	private final IDrawable icon;
	private final IDrawable arrow;

	ItemToFluidJeiCategory(IRecipeHolderType<ItemToFluidRecipe> recipeType, ItemLike iconItem,
			Component title, IGuiHelper guiHelper) {
		this.recipeType = recipeType;
		this.title = title;
		this.icon = guiHelper.createDrawableItemLike(iconItem);
		this.arrow = guiHelper.getRecipeArrow();
	}

	@Override
	public IRecipeHolderType<ItemToFluidRecipe> getRecipeType() {
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
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ItemToFluidRecipe> holder, IFocusGroup focuses) {
		ItemToFluidRecipe recipe = holder.value();
		builder.addInputSlot(RecipeViewerLayout.inputXs(1).getFirst(), RecipeViewerLayout.SLOT_Y)
				.setStandardSlotBackground()
				.add(recipe.ingredient());
		ItemStack remainder = recipe.remainderStack();
		int outputs = remainder.isEmpty() ? 1 : 2;
		// JEI's fluid overload takes the platform's own unit — millibuckets on NeoForge, the recipe's unit.
		builder.addOutputSlot(RecipeViewerLayout.outputXs(outputs).getFirst(), RecipeViewerLayout.SLOT_Y)
				.setOutputSlotBackground()
				.add(recipe.result().fluid().value(), recipe.amount());
		if (!remainder.isEmpty()) {
			builder.addOutputSlot(RecipeViewerLayout.outputXs(2).get(1), RecipeViewerLayout.SLOT_Y)
					.setOutputSlotBackground()
					.add(remainder);
		}
	}

	@Override
	public void draw(RecipeHolder<ItemToFluidRecipe> holder, IRecipeSlotsView recipeSlotsView,
			GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
		ItemToFluidRecipe recipe = holder.value();
		arrow.draw(graphics, RecipeViewerLayout.ARROW_X, RecipeViewerLayout.ARROW_Y);
		String cost = RecipeViewerLayout.fluidCostLabel(
				recipe.energy(), recipe.kind().ticksFor(recipe.energy()), recipe.amount());
		int x = (RecipeViewerLayout.WIDTH - Minecraft.getInstance().font.width(cost)) / 2;
		graphics.text(Minecraft.getInstance().font, Component.literal(cost), x,
				RecipeViewerLayout.LABEL_Y, 0xFF404040, false);
	}

	@Override
	public Identifier getIdentifier(RecipeHolder<ItemToFluidRecipe> holder) {
		return holder.id().identifier();
	}
}
