package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.client.compat.RecipeViewerLayout;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
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

	// MOD-498 — Ingredient#items() is a VANILLA soft deprecation (not a NeoForge one), kept deliberately.
	// NeoForge does add a non-deprecated `HolderSet<Item> getValues()`, but it throws
	// IllegalStateException for an ICustomIngredient, where items() resolves one through
	// updateCustomIngredientValues(); a recipe viewer walks EVERY recipe in the pack, so that swap turns
	// one modpack ingredient into a crash. Vanilla's display() route is no better here: it yields count-1
	// stacks, and the slot below carries the recipe's own count.
	@SuppressWarnings("deprecation")
	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AlaProcessingRecipe> holder, IFocusGroup focuses) {
		AlaProcessingRecipe recipe = holder.value();
		List<Integer> inputXs = RecipeViewerLayout.inputXs(recipe.ingredients().size());
		for (int i = 0; i < inputXs.size(); i++) {
			IRecipeSlotBuilder slot = builder.addInputSlot(inputXs.get(i), RecipeViewerLayout.SLOT_Y)
					.setStandardSlotBackground();
			int count = recipe.inputCount(i);
			if (count == 1) {
				slot.add(recipe.ingredients().get(i));
			} else {
				// MOD-271: an ingredient carries no count, so a four-dust recipe would read as one dust.
				// Feed the slot explicit stacks instead — the viewer must quote the machine's real price.
				slot.addItemStacks(recipe.ingredients().get(i).items()
						.map(item -> new ItemStack(item, count))
						.toList());
			}
		}
		builder.addOutputSlot(RecipeViewerLayout.outputXs(1).getFirst(), RecipeViewerLayout.SLOT_Y)
				.setOutputSlotBackground()
				.add(recipe.result());
		// MOD-537: the optional bonus output gets its own slot; the two-slot row already exists for
		// the alloy smelter, so the geometry needs no widening.
		recipe.secondaryResult().ifPresent(secondary -> builder
				.addOutputSlot(RecipeViewerLayout.outputXs(2).get(1), RecipeViewerLayout.SLOT_Y)
				.setOutputSlotBackground()
				.add(secondary.stack()));
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
