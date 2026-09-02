package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.CanningExchange;
import dev.alaindustrial.client.compat.RecipeViewerLayout;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * JEI category for the Canning Machine (MOD-383) — the JEI counterpart of the REI {@code CanningCategory}.
 *
 * <p>The only category here whose "recipe" is not a {@code RecipeHolder}: the machine matches no JSON
 * recipe, so the cards are {@link CanningExchange.Card} records computed from the item registry.
 * {@link IRecipeType#create(Identifier, Class)} takes any class, and JEI falls back to its slow
 * by-name codec for non-recipe types — which is why {@link #getIdentifier} must return something
 * unique per card rather than the {@code null} the default gives a plain POJO.
 */
final class CanningJeiCategory implements IRecipeCategory<CanningExchange.Card> {
	private final IRecipeType<CanningExchange.Card> recipeType;
	private final Component title;
	private final IDrawable icon;
	private final IDrawable arrow;

	CanningJeiCategory(IRecipeType<CanningExchange.Card> recipeType, Block iconBlock,
			Component title, IGuiHelper guiHelper) {
		this.recipeType = recipeType;
		this.title = title;
		this.icon = guiHelper.createDrawableItemLike(iconBlock);
		this.arrow = guiHelper.getRecipeArrow();
	}

	@Override
	public IRecipeType<CanningExchange.Card> getRecipeType() {
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
	public void setRecipe(IRecipeLayoutBuilder builder, CanningExchange.Card card, IFocusGroup focuses) {
		List<Integer> inputXs = RecipeViewerLayout.inputXs(2);
		// The food slot carries the whole per-ration count — the rate is the only thing this card teaches.
		builder.addInputSlot(inputXs.get(0), RecipeViewerLayout.SLOT_Y)
				.setStandardSlotBackground()
				.add(new ItemStack(card.food(), card.itemsPerRation()));
		builder.addInputSlot(inputXs.get(1), RecipeViewerLayout.SLOT_Y)
				.setStandardSlotBackground()
				.add(new ItemStack(ModContent.EMPTY_CAN.get()));
		builder.addOutputSlot(RecipeViewerLayout.outputXs(1).getFirst(), RecipeViewerLayout.SLOT_Y)
				.setOutputSlotBackground()
				.add(new ItemStack(ModContent.CANNED_RATION.get()));
	}

	@Override
	public void draw(CanningExchange.Card card, IRecipeSlotsView recipeSlotsView,
			GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
		arrow.draw(graphics, RecipeViewerLayout.ARROW_X, RecipeViewerLayout.ARROW_Y);
		String cost = RecipeViewerLayout.costLabel(
				CanningExchange.energyPerRation(), CanningExchange.ticksPerRation());
		int x = (RecipeViewerLayout.WIDTH - Minecraft.getInstance().font.width(cost)) / 2;
		graphics.text(Minecraft.getInstance().font, Component.literal(cost), x,
				RecipeViewerLayout.LABEL_Y, 0xFF404040, false);
	}

	@Override
	public Identifier getIdentifier(CanningExchange.Card card) {
		// One id per accepted food. The item's own id supplies both halves, so two mods shipping a
		// "carrot" cannot collide; ':' is not legal in a path, hence the slash.
		return Industrialization.id(
				"canning/" + BuiltInRegistries.ITEM.getKey(card.food()).toString().replace(':', '/'));
	}
}
