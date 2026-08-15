package dev.alaindustrial.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
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
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/**
 * JEI category for machines with no recipe of any kind (MOD-420) — the NeoForge mirror of the
 * machine-info half of {@code AlaInfoCategory}.
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
	private static final int WIDTH = 160;
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int SLOT_X = 2, SLOT_Y = 2;
	private static final int TITLE_X = 24, TITLE_Y = 7;
	private static final int BODY_Y = 26;
	/** Body lines reserved after word-wrap — the same budget the REI side draws to. */
	private static final int MAX_BODY_LINES = 8;
	private static final int HEIGHT = BODY_Y + LINE_HEIGHT * MAX_BODY_LINES + PADDING;

	private static final int TITLE_COLOR = 0xFF303030;
	private static final int BODY_COLOR = 0xFF404040;

	private final IRecipeType<RecipeViewerInfo.Entry> recipeType;
	private final Component title;
	private final IDrawable icon;

	MachineInfoJeiCategory(IRecipeType<RecipeViewerInfo.Entry> recipeType, Block iconBlock,
			Component title, IGuiHelper guiHelper) {
		this.recipeType = recipeType;
		this.title = title;
		this.icon = guiHelper.createDrawableItemLike(iconBlock);
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
		return HEIGHT;
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
		return Industrialization.id("machine_info/"
				+ BuiltInRegistries.ITEM.getKey(owner.asItem()).toString().replace(':', '/'));
	}

	/** The pages this category shows, in the order the shared source declares them. */
	static List<RecipeViewerInfo.Entry> pages() {
		return RecipeViewerInfo.machineInfoEntries();
	}
}
