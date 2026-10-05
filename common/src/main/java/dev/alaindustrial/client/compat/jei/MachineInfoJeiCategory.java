package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.InfoPageLayout;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
import java.util.List;
import java.util.function.Supplier;
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
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * JEI category for machines with no recipe of any kind (MOD-420) — the JEI counterpart of the
 * machine-info half of the REI {@code AlaInfoCategory}.
 *
 * <p>Since MOD-695 the same class also serves the evolution and plant pages, which used to go through
 * JEI's built-in {@code addIngredientInfo}. That call takes FIXED components at registration, which
 * happens at login before the server's balance arrives ({@code ServerBalance}); a page drawn here
 * builds its lines on every draw, so it shows the server's numbers and follows a later reload.
 *
 * <p><b>Why this exists instead of {@code addIngredientInfo}.</b> The solar evolution pages use JEI's
 * built-in ingredient info, and that is fine for pages reached from the item. It cannot serve a GUI
 * click area: a click area opens its category <i>whole and unfocused</i>, and the built-in info
 * category holds the info pages of every installed mod — clicking the geothermal generator's arrow
 * would show the player a stack of unrelated pages from other mods. A category of our own holds only
 * our two pages.
 *
 * <p>Like {@code CanningJeiCategory}, the "recipe" here is not a {@code RecipeHolder} but a plain
 * record, so {@link #getIdentifier} must return something unique per page rather than the {@code null}
 * JEI gives a POJO by default.
 */
final class MachineInfoJeiCategory implements IRecipeCategory<RecipeViewerInfo.Entry> {
	/** Card width, line height, line bounds and colours are the shared page card's (MOD-716, 12e). */
	private static final int WIDTH = InfoPageLayout.WIDTH;
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = InfoPageLayout.LINE_HEIGHT;
	private static final int SLOT_X = 2, SLOT_Y = 2;
	private static final int TITLE_X = 24, TITLE_Y = 7;
	private static final int BODY_Y = 26;
	private static final int TITLE_COLOR = InfoPageLayout.TITLE_COLOR;
	private static final int BODY_COLOR = InfoPageLayout.BODY_COLOR;

	private final IRecipeType<RecipeViewerInfo.Entry> recipeType;
	private final Component title;
	private final IDrawable icon;
	private final Supplier<List<RecipeViewerInfo.Entry>> pages;
	private final String idPrefix;

	/** A text-page category over {@code pages}; {@code idPrefix} keeps page ids unique per category. */
	MachineInfoJeiCategory(IRecipeType<RecipeViewerInfo.Entry> recipeType, ItemLike iconItem,
			Component title, IGuiHelper guiHelper, Supplier<List<RecipeViewerInfo.Entry>> pages, String idPrefix) {
		this.recipeType = recipeType;
		this.title = title;
		this.icon = guiHelper.createDrawableItemLike(iconItem);
		this.pages = pages;
		this.idPrefix = idPrefix;
	}

	@Override
	public IRecipeType<RecipeViewerInfo.Entry> getRecipeType() {
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
		return BODY_Y + LINE_HEIGHT * bodyLines() + PADDING;
	}

	/** Body lines to reserve: the tallest page, wrapped at the width {@link #draw} wraps with (MOD-422). */
	private int bodyLines() {
		return InfoPageLayout.bodyLines(pages.get(), WIDTH - PADDING * 2);
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeViewerInfo.Entry entry, IFocusGroup focuses) {
		// The machine itself, published as a slot so JEI's "uses/recipes" lookup on the block finds this
		// page. There is nothing being crafted — the slot is the subject of the page, not its output.
		builder.addOutputSlot(SLOT_X, SLOT_Y)
				.setStandardSlotBackground()
				.add(new ItemStack(entry.owner().get()));
	}

	@Override
	public void draw(RecipeViewerInfo.Entry entry, IRecipeSlotsView recipeSlotsView,
			GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
		var font = Minecraft.getInstance().font;
		graphics.text(font, RecipeViewerInfo.title(entry), TITLE_X, TITLE_Y, TITLE_COLOR, false);

		// Word-wrap each source line against the panel width. The splitter yields FormattedText; the
		// body lines carry no styling, so flattening to a plain literal keeps the draw call to the one
		// Component overload without losing anything on screen.
		int y = BODY_Y;
		int maxTextWidth = WIDTH - PADDING * 2;
		for (Component line : RecipeViewerInfo.buildLines(entry)) {
			for (FormattedText wrapped : font.getSplitter().splitLines(line, maxTextWidth, Style.EMPTY)) {
				graphics.text(font, Component.literal(wrapped.getString()), PADDING, y, BODY_COLOR, false);
				y += LINE_HEIGHT;
			}
		}
	}

	@Override
	public Identifier getIdentifier(RecipeViewerInfo.Entry entry) {
		// One id per page, keyed on the machine it describes. ':' is not legal in a path, hence the slash.
		ItemLike owner = entry.owner().get();
		return Industrialization.id(idPrefix
				+ BuiltInRegistries.ITEM.getKey(owner.asItem()).toString().replace(':', '/'));
	}
}
