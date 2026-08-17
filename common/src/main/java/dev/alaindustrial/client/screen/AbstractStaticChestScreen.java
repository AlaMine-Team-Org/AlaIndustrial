package dev.alaindustrial.client.screen;

import dev.alaindustrial.menu.AbstractChestMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen shared by the fixed-size chests (iron, silver, gold). The whole panel — frame,
 * recessed chest slots, player inventory + hotbar — comes from a single GUI atlas PNG per chest; there
 * is no dynamic overlay (no energy bar, no progress arrow) because a chest is pure storage. Slot
 * coordinates are baked into the chest's {@link AbstractChestMenu} and line up with its texture.
 *
 * <p>The three chests differ only in atlas, panel height (one extra 18 px row per tier) and the Y of the
 * player "Inventory" label, so the body lives here once and each concrete screen passes those three
 * (MOD-439). Both labels are left-aligned: the chest title sits above the chest grid, and the player
 * "Inventory" label sits in the gap between the chest grid and the player inventory (the recessed grey
 * strip 11 px above the player grid), matching how the vanilla chest/shulker screens label each section.
 *
 * <p>Not to be confused with {@link AbstractStorageScreen} — that is the scrolling family over
 * {@code AbstractScrollingChestMenu} (electrum chest, double chest, warehouse windows).
 *
 * @param <M> the concrete chest menu; distinct per chest, as the menu&#8594;screen manifest requires
 */
public abstract class AbstractStaticChestScreen<M extends AbstractChestMenu> extends AbstractContainerScreen<M> {
	private static final int TEX_SIZE = 256;
	private static final int IMAGE_WIDTH = 176;

	private final Identifier texture;

	/**
	 * @param texture          the chest's 256×256 GUI atlas; the visible panel is its top-left
	 *                         {@value #IMAGE_WIDTH}×{@code imageHeight} region
	 * @param imageHeight      panel height — 166 for the vanilla 3-row chest plus 18 per extra row
	 * @param playerInvLabelY  the "Inventory" label Y: 11 px above the chest menu's player grid
	 */
	protected AbstractStaticChestScreen(M menu, Inventory playerInventory, Component title, Identifier texture,
			int imageHeight, int playerInvLabelY) {
		// 4-arg constructor pins the (final) imageWidth/imageHeight from the atlas dimensions.
		super(menu, playerInventory, title, IMAGE_WIDTH, imageHeight);
		this.texture = texture;
		// Player-inventory label ("Inventory") sits in the gap between the chest grid and the player
		// inventory — the recessed grey strip there. Left-aligned to match the chest title.
		this.inventoryLabelX = 8;
		this.inventoryLabelY = playerInvLabelY;
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
		// Static panel: the visible imageWidth×imageHeight region at the top-left of the 256×256 texture.
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);
	}
}
