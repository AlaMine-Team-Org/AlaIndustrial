package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.AdvancedIndustrialWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen of the Advanced Industrial Workbench (MOD-810): the crafting table's window, laid out the same way
 * (grid at 30,17, result at 124,35, inventory at 8,84), in the classic grey frame of the mod's other machine
 * screens - plus the recipe book of a crafting table and one small button, "Clear grid", that sends the whole
 * grid to the inventory.
 *
 * <p>The button sits in the free band under the arrow, where the background art leaves room. It is drawn here
 * in the mod's button style rather than painted into the texture, so the same art works whatever the language.
 */
public class AdvancedIndustrialWorkbenchScreen extends AbstractRecipeBookScreen<AdvancedIndustrialWorkbenchMenu> {

	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/advanced_industrial_workbench.png");

	/** The title never gets closer to the frame's right edge than this, whatever the language. */
	private static final int TITLE_RIGHT_MARGIN = 8;
	/** A title is never shrunk below this scale: past it the text stops being readable. */
	private static final float MIN_TITLE_SCALE = 0.6F;
	/** Recipe book button, left of the grid (vanilla's crafting table sits at 5; one step further from the edge). */
	private static final int RECIPE_BUTTON_X = 7;

	private static final int BUTTON_X = 86;
	private static final int BUTTON_Y = 60;
	/** Odd sizes on purpose: the 5x5 cross then sits exactly in the middle of the 13x9 face. */
	private static final int BUTTON_W = 15;
	private static final int BUTTON_H = 11;
	/** The idle button's rim, the same as the other machine screens'. */
	private static final int BUTTON_EDGE = 0xFF565B63;

	private final CraftingRecipeBookComponent recipeBook;
	/** Whether the grid held an item on the previous tick; the ghost recipe is cleared when it turns true. */
	private boolean gridHadItems;

	public AdvancedIndustrialWorkbenchScreen(AdvancedIndustrialWorkbenchMenu menu, Inventory inventory,
			Component title) {
		this(menu, new CraftingRecipeBookComponent(menu), inventory, title);
	}

	private AdvancedIndustrialWorkbenchScreen(AdvancedIndustrialWorkbenchMenu menu,
			CraftingRecipeBookComponent recipeBook, Inventory inventory, Component title) {
		super(menu, recipeBook, inventory, title);
		this.recipeBook = recipeBook;
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = 29;
	}

	@Override
	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(this.leftPos + RECIPE_BUTTON_X, this.height / 2 - 49);
	}

	/**
	 * The ghost recipe is only a hint for an EMPTY grid. The server sends it when the window opens on an empty
	 * grid, but nothing tells the client when the grid then fills - by a shift-click, a drag, a pipe or the other
	 * player - so the hint stayed over real items, tinted red. The moment any grid slot holds an item the hint is
	 * cleared, through the recipe book's own "a crafting slot was touched" path (public on 26.2 and 26.3).
	 */
	@Override
	public void containerTick() {
		super.containerTick();
		Slot filled = null;
		for (Slot slot : this.menu.getInputGridSlots()) {
			if (slot.hasItem()) {
				filled = slot;
				break;
			}
		}
		boolean hasItems = filled != null;
		if (hasItems && !gridHadItems) {
			this.recipeBook.slotClicked(filled);
		}
		gridHadItems = hasItems;
	}

	/**
	 * The title and the inventory label. The title is fitted to the room between its origin and the right margin:
	 * a long name in a long language shrinks instead of running out of the frame.
	 */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int room = this.imageWidth - TITLE_RIGHT_MARGIN - this.titleLabelX;
		int width = this.font.width(this.title);
		if (width <= room) {
			graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiStyle.LABEL, false);
		} else {
			float scale = Math.max(MIN_TITLE_SCALE, (float) room / width);
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.titleLabelX, this.titleLabelY);
			graphics.pose().scale(scale, scale);
			graphics.text(this.font, this.title, 0, 0, GuiStyle.LABEL, false);
			graphics.pose().popMatrix();
		}
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
				GuiStyle.LABEL, false);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, (this.height - this.imageHeight) / 2,
				0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int bx = this.leftPos + BUTTON_X;
		int by = this.topPos + BUTTON_Y;
		boolean over = overButton(mouseX, mouseY);
		graphics.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, over ? GuiStyle.BUTTON_ACTIVE_EDGE : BUTTON_EDGE);
		graphics.fill(bx + 1, by + 1, bx + BUTTON_W - 1, by + BUTTON_H - 1,
				over ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
		// A small cross made of two diagonals of pixels: needs no glyph, reads in every language.
		int color = over ? GuiStyle.BUTTON_ACTIVE_EDGE : 0xFFC6C6C6;
		int cx = bx + (BUTTON_W - 5) / 2;
		int cy = by + (BUTTON_H - 5) / 2;
		for (int i = 0; i < 5; i++) {
			graphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
			graphics.fill(cx + 4 - i, cy + i, cx + 5 - i, cy + i + 1, color);
		}
		if (over) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.advanced_workbench.clear"), mouseX, mouseY);
		}
	}

	private boolean overButton(double mx, double my) {
		int bx = this.leftPos + BUTTON_X;
		int by = this.topPos + BUTTON_Y;
		return mx >= bx && mx < bx + BUTTON_W && my >= by && my < by + BUTTON_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overButton(event.x(), event.y())
				&& this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
					AdvancedIndustrialWorkbenchMenu.BUTTON_CLEAR);
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
}
