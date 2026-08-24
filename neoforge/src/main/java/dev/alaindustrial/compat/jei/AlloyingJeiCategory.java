package dev.alaindustrial.compat.jei;

import dev.alaindustrial.client.compat.RecipeViewerLayout;
import dev.alaindustrial.recipe.AlloyComponent;
import dev.alaindustrial.recipe.AlloyingRecipe;
import dev.alaindustrial.registry.ModRecipes;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

/**
 * JEI category for the Alloy Smelter (MOD-064) — the NeoForge mirror of {@code AlloyingCategory}.
 *
 * <p>Same single-row layout as {@link AlaProcessingJeiCategory}, but the number of input slots varies
 * with the recipe (an alloy takes two components or three) and each slot shows the count the operation
 * actually consumes rather than a single item.
 */
final class AlloyingJeiCategory implements IRecipeCategory<RecipeHolder<AlloyingRecipe>> {
	private final IRecipeHolderType<AlloyingRecipe> recipeType;
	private final Component title;
	private final IDrawable icon;
	private final IDrawable arrow;

	AlloyingJeiCategory(IRecipeHolderType<AlloyingRecipe> recipeType, Block iconBlock,
			Component title, IGuiHelper guiHelper) {
		this.recipeType = recipeType;
		this.title = title;
		this.icon = guiHelper.createDrawableItemLike(iconBlock);
		this.arrow = guiHelper.getRecipeArrow();
	}

	@Override
	public IRecipeHolderType<AlloyingRecipe> getRecipeType() {
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

	// MOD-498 — Ingredient#items() is a vanilla soft deprecation, kept deliberately. NeoForge does add a
	// non-deprecated accessor, `HolderSet<Item> getValues()` — but it throws IllegalStateException
	// ("Cannot retrieve values from custom ingredient!") for an ICustomIngredient, while items() resolves
	// one through updateCustomIngredientValues(). A recipe viewer enumerates EVERY recipe in the pack, so
	// swapping it in would turn one modpack ingredient into a crash. Vanilla's own display() route is no
	// better here: it yields count-1 stacks, and the slot below carries the recipe's own count.
	@SuppressWarnings("deprecation")
	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AlloyingRecipe> holder, IFocusGroup focuses) {
		AlloyingRecipe recipe = holder.value();
		List<AlloyComponent> components = recipe.components();
		List<Integer> inputXs = RecipeViewerLayout.inputXs(components.size());
		for (int i = 0; i < components.size(); i++) {
			AlloyComponent component = components.get(i);
			IRecipeSlotBuilder slot = builder
					.addInputSlot(inputXs.get(i), RecipeViewerLayout.SLOT_Y)
					.setStandardSlotBackground();
			// One entry per accepted item so a tag-backed component (#c:ingots/copper) cycles through
			// every copper in the pack, each stack carrying the per-operation count.
			for (Holder<Item> item : component.ingredient().items().toList()) {
				slot.add(new ItemStack(item, component.count()));
			}
		}
		builder.addOutputSlot(RecipeViewerLayout.outputXs(1).getFirst(), RecipeViewerLayout.SLOT_Y)
				.setOutputSlotBackground()
				.add(recipe.resultStack());
	}

	@Override
	public void draw(RecipeHolder<AlloyingRecipe> holder, IRecipeSlotsView recipeSlotsView,
			GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
		AlloyingRecipe recipe = holder.value();
		arrow.draw(graphics, RecipeViewerLayout.ARROW_X, RecipeViewerLayout.ARROW_Y);
		String cost = RecipeViewerLayout.costLabel(
				recipe.energy(), ModRecipes.ALLOYING.ticksFor(recipe.energy()));
		int x = (RecipeViewerLayout.WIDTH - Minecraft.getInstance().font.width(cost)) / 2;
		graphics.text(Minecraft.getInstance().font, Component.literal(cost), x,
				RecipeViewerLayout.LABEL_Y, 0xFF404040, false);
	}

	@Override
	public Identifier getIdentifier(RecipeHolder<AlloyingRecipe> holder) {
		return holder.id().identifier();
	}
}
