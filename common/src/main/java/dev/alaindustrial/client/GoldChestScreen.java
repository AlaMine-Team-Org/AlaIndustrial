package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.GoldChestMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Gold Chest. The whole 176×220 panel — frame, recessed chest slots
 * (6 rows), player inventory + hotbar — comes from a single GUI atlas PNG ({@code gold_chest.png});
 * there is no dynamic overlay (no energy bar, no progress arrow) because the chest is pure storage.
 * Slot coordinates are baked into {@link GoldChestMenu} and line up with this texture.
 *
 * <p>The atlas is 18px taller than the silver chest's 176×202 (one extra chest row). Both labels are
 * left-aligned, matching the iron/silver chest screens.
 */
public class GoldChestScreen extends AbstractContainerScreen<GoldChestMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/gold_chest.png");
	private static final int TEX_SIZE = 256;

	/** The atlas is taller than the silver chest (220 vs 202): one extra chest row. */
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 220;
	/** Player-inventory "Inventory" label Y — 11px above the player grid (matches GoldChestMenu.PLAYER_INV_TOP_Y = 139). */
	private static final int PLAYER_INV_LABEL_Y = 128;

	public GoldChestScreen(GoldChestMenu menu, Inventory playerInventory, Component title) {
		// 4-arg constructor pins the (final) imageWidth/imageHeight from the atlas dimensions.
		super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		// Player-inventory label ("Inventory") sits in the gap between the chest grid and the player
		// inventory — default position is PLAYER_INV_TOP_Y - 11 = 139 - 11 = 128, which lands on the
		// recessed grey strip there. Left-aligned to match the chest title.
		this.inventoryLabelX = 8;
		this.inventoryLabelY = PLAYER_INV_LABEL_Y;
	}

	@Override
	public void init() {
		super.init();
		// Left-align the title (default titleLabelX == 8) instead of centring it, so the chest name
		// sits flush with the left edge of the slot grid like the other Ala Industrial screens.
		this.titleLabelX = 8;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		// Static panel: the visible 176×220 region at the top-left of the 256×256 texture.
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);
	}
}
