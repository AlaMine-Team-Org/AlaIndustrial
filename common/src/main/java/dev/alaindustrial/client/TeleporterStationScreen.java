package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.TeleporterStationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * The station's screen (MOD-093): the jump fund, whose station it is, and the privacy switch.
 *
 * <p>All coordinates below were read off the texture itself rather than guessed — the energy track's
 * interior is exactly 158×12 at (9,27), which is exactly the size of the fill sprite, and the slot
 * grid sits on the standard 18px step. If the art changes, re-scan; do not nudge these by eye.
 *
 * <p>The privacy control is drawn from the texture's own two sprites (private / public) and clicked
 * by hitbox, instead of a vanilla {@code Button} widget — that is what keeps it looking like the art
 * rather than like a grey vanilla box on top of it.
 */
public class TeleporterStationScreen extends AbstractContainerScreen<TeleporterStationMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/teleporter_station.png");
	private static final int TEX_SIZE = 256;

	/** Panel, measured from the texture: opaque 176×187 from the top-left corner. */
	private static final int PANEL_W = 176, PANEL_H = 187;

	/** Energy track interior — the fill sprite lands here, clipped by charge. */
	private static final int TRACK_X = 9, TRACK_Y = 27, TRACK_W = 158, TRACK_H = 12;
	/** Fill sprite in the atlas, below the panel. */
	private static final int FILL_U = 0, FILL_V = 196;

	/** Privacy control: the two state sprites in the atlas, and where it sits on the panel. */
	private static final int BTN_W = 94, BTN_H = 13;
	private static final int BTN_PRIVATE_V = 227;
	private static final int BTN_PUBLIC_V = 243;
	private static final int BTN_X = (PANEL_W - BTN_W) / 2, BTN_Y = 60;

	/**
	 * The padlock guarding the privacy switch. Both sprites were scanned out of the atlas, and they
	 * are different heights (the open shackle stands taller), so they are bottom-aligned to the
	 * button's baseline rather than sharing a top edge.
	 */
	private static final int LOCK_W = 10;
	private static final int LOCK_CLOSED_U = 96, LOCK_CLOSED_V = 243, LOCK_CLOSED_H = 13;
	private static final int LOCK_OPEN_U = 112, LOCK_OPEN_V = 241, LOCK_OPEN_H = 15;
	private static final int LOCK_X = BTN_X + BTN_W + 5;
	private static final int LOCK_BASELINE = BTN_Y + BTN_H;

	/**
	 * Whether the switch is armed. Client-side only, and deliberately so: this guards against the
	 * player's own stray click, not against a hostile client. Ownership is what actually protects the
	 * station, and the server re-checks that on every press regardless of what this says.
	 *
	 * <p>Starts closed on every open, and re-closes itself after a change goes through, so flipping
	 * privacy always costs two deliberate clicks rather than one accident.
	 */
	private boolean unlocked;

	public TeleporterStationScreen(TeleporterStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_W, PANEL_H);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(header())) / 2;
		// The slot grid starts at y=105; put the inventory label just above it.
		this.inventoryLabelY = 94;
	}

	/**
	 * The header: whose station it is, plus "(public)" when it is open to everyone.
	 *
	 * <p>The owner's name arrives baked into the menu title (that is the one string vanilla syncs when
	 * a menu opens), while the privacy flag is a live data slot — so the suffix is appended here
	 * rather than folded into the title, and it follows the switch the moment it is flipped.
	 */
	private Component header() {
		return this.menu.isPrivate()
				? this.title
				: Component.translatable("gui.alaindustrial.teleporter.title_public", this.title);
	}

	/**
	 * Draw {@link #header()} rather than the raw title, and re-centre it every frame — the "(public)"
	 * suffix appears and disappears with the switch, so its width is not something init() can know.
	 */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component header = header();
		graphics.text(this.font, header, (this.imageWidth - this.font.width(header)) / 2,
				this.titleLabelY, GuiStyle.TEXT, false);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
				GuiStyle.TEXT, false);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, PANEL_W, PANEL_H, TEX_SIZE, TEX_SIZE);

		// Jump fund. The fraction arrives as permille — raw EU (500 000) would not survive a 16-bit
		// DataSlot, which is the bug the solar panel's evolution bar already found once.
		int fill = TRACK_W * this.menu.getEnergyPermille() / 1000;
		if (fill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + TRACK_X, y + TRACK_Y,
					(float) FILL_U, (float) FILL_V, fill, TRACK_H, TEX_SIZE, TEX_SIZE);
		}

		Component charge = Component.translatable("gui.alaindustrial.teleporter.charge",
				this.menu.getEnergyPermille() / 10);
		drawCentered(graphics, charge, x, y + TRACK_Y + TRACK_H + 3, GuiStyle.TEXT);

		// Privacy control, drawn from the art's own two states.
		int v = this.menu.isPrivate() ? BTN_PRIVATE_V : BTN_PUBLIC_V;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + BTN_X, y + BTN_Y,
				0.0F, (float) v, BTN_W, BTN_H, TEX_SIZE, TEX_SIZE);
		Component label = this.menu.isPrivate()
				? Component.translatable("gui.alaindustrial.teleporter.private")
				: Component.translatable("gui.alaindustrial.teleporter.public");
		// Dimmed unless the switch can actually be worked right now — greyed reads as "not yet",
		// which is the whole point of the padlock next to it.
		int labelColor = canToggle() ? 0xFFFFFFFF : 0xFF9A9A9A;
		graphics.text(this.font, label, x + BTN_X + (BTN_W - this.font.width(label)) / 2,
				y + BTN_Y + 3, labelColor, false);

		if (this.menu.isOwner()) {
			int lockH = unlocked ? LOCK_OPEN_H : LOCK_CLOSED_H;
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + LOCK_X, y + LOCK_BASELINE - lockH,
					(float) (unlocked ? LOCK_OPEN_U : LOCK_CLOSED_U),
					(float) (unlocked ? LOCK_OPEN_V : LOCK_CLOSED_V),
					LOCK_W, lockH, TEX_SIZE, TEX_SIZE);
		}

		// Kept short on purpose: the panel is 176 wide, and a longer line runs off the art.
		if (!this.menu.isOwner()) {
			drawCentered(graphics, Component.translatable("gui.alaindustrial.teleporter.not_owner"),
					x, y + BTN_Y + BTN_H + 4, GuiStyle.TEXT_DIM);
		}
	}

	/**
	 * Click the privacy control by hitbox.
	 *
	 * <p>Fire-and-forget over vanilla's container-button packet: the server flips the flag and
	 * re-checks ownership itself, then the synced data relabels the sprite. Nothing is predicted
	 * client-side, and a client that clicks a control it is not allowed to touch gets nowhere.
	 */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && this.menu.isOwner() && isOverLock(event.x(), event.y())) {
			unlocked = !unlocked;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
					unlocked ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, 1.0F));
			return true;
		}
		if (event.button() == 0 && canToggle() && isOverPrivacyButton(event.x(), event.y())) {
			this.minecraft.gameMode.handleInventoryButtonClick(
					this.menu.containerId, TeleporterStationMenu.BUTTON_TOGGLE_PRIVACY);
			this.minecraft.getSoundManager().play(
					SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			// Snap shut behind the change: the next flip has to be asked for again.
			unlocked = false;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** The switch answers only to its owner, and only while the padlock is open. */
	private boolean canToggle() {
		return this.menu.isOwner() && unlocked;
	}

	private boolean isOverPrivacyButton(double mouseX, double mouseY) {
		double rx = mouseX - this.leftPos;
		double ry = mouseY - this.topPos;
		return rx >= BTN_X && rx < BTN_X + BTN_W && ry >= BTN_Y && ry < BTN_Y + BTN_H;
	}

	private boolean isOverLock(double mouseX, double mouseY) {
		double rx = mouseX - this.leftPos;
		double ry = mouseY - this.topPos;
		int lockH = unlocked ? LOCK_OPEN_H : LOCK_CLOSED_H;
		return rx >= LOCK_X && rx < LOCK_X + LOCK_W
				&& ry >= LOCK_BASELINE - lockH && ry < LOCK_BASELINE;
	}

	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
		graphics.text(this.font, text, x + (PANEL_W - this.font.width(text)) / 2, y, color, false);
	}
}
