package dev.alaindustrial.compat.jei;

import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import java.util.Locale;
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
 * JEI category for the Polymerizer (MOD-019) — the NeoForge mirror of {@code PolymerizingCategory}.
 * Same single-row layout as {@link AlaProcessingJeiCategory}, but the input slot holds a fluid stack
 * (JEI renders it with the fluid's own texture and a "1000 mB" tooltip) instead of an item ingredient.
 */
final class PolymerizingJeiCategory implements IRecipeCategory<RecipeHolder<PolymerizingRecipe>> {
	private static final int WIDTH = 116;
	private static final int HEIGHT = 48;

	private final IRecipeHolderType<PolymerizingRecipe> recipeType;
	private final Component title;
	private final IDrawable icon;
	private final IDrawable arrow;

	PolymerizingJeiCategory(IRecipeHolderType<PolymerizingRecipe> recipeType, Block iconBlock,
			Component title, IGuiHelper guiHelper) {
		this.recipeType = recipeType;
		this.title = title;
		this.icon = guiHelper.createDrawableItemLike(iconBlock);
		this.arrow = guiHelper.getRecipeArrow();
	}

	@Override
	public IRecipeHolderType<PolymerizingRecipe> getRecipeType() {
		return recipeType;
	}

	@Override
	public Component getTitle() {
		return title;
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<PolymerizingRecipe> holder, IFocusGroup focuses) {
		PolymerizingRecipe recipe = holder.value();
		IRecipeSlotBuilder input = builder.addInputSlot(21, 6).setStandardSlotBackground();
		// One entry per accepted SOURCE fluid, so a tag-backed recipe (#c:oil) cycles through every oil in
		// the pack without also listing each one's flowing variant. JEI's addFluidStack takes the platform's
		// own unit, which on NeoForge is millibuckets — the very number the recipe states.
		for (Holder<Fluid> fluid : recipe.displayFluids()) {
			input.addFluidStack(fluid.value(), recipe.amount());
		}
		builder.addOutputSlot(78, 6).setOutputSlotBackground().add(recipe.result());
	}

	@Override
	public void draw(RecipeHolder<PolymerizingRecipe> holder, IRecipeSlotsView recipeSlotsView,
			GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
		PolymerizingRecipe recipe = holder.value();
		arrow.draw(graphics, 47, 7);
		String cost = recipe.energy() + " EU / " + formatSeconds(ModRecipes.POLYMERIZING.ticksFor(recipe.energy()))
				+ " / " + recipe.amount() + " mB";
		int x = (WIDTH - Minecraft.getInstance().font.width(cost)) / 2;
		graphics.text(Minecraft.getInstance().font, Component.literal(cost), x, 34, 0xFF404040, false);
	}

	@Override
	public Identifier getIdentifier(RecipeHolder<PolymerizingRecipe> holder) {
		return holder.id().identifier();
	}

	private static String formatSeconds(int ticks) {
		String seconds = String.format(Locale.ROOT, "%.1f", ticks / 20.0);
		if (seconds.endsWith(".0")) {
			seconds = seconds.substring(0, seconds.length() - 2);
		}
		return seconds + " s";
	}
}
