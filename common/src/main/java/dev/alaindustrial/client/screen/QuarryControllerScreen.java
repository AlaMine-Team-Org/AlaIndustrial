package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.quarry.QuarryWorkStatus;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.menu.QuarryControllerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Screen of the quarry controller (MOD-951), in the mod's machine style ({@code tools/gen_quarry_controller_gui.py},
 * built from the Macerator's atlas): the energy bar on the left, the drill bit's slot next to it, the work right of
 * the slot — the layer, how much of it is dug, the cycle's state and the Macerator's drill-bit sprite filling as the
 * running operation goes — the status row under it, and the 27-slot buffer above the player's inventory.
 *
 * <p>The status row names why the quarry stands. While the frame does not stand it names the frame's first broken
 * rule instead, in a short form: the coordinates of the block to fix are not synced to the screen, the chat message
 * a right click on an unformed frame prints carries them.
 */
public class QuarryControllerScreen extends MachineScreen<QuarryControllerMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/quarry_controller.png");

	/** The info block right of the bit slot: the left edge of its text, its right edge and its rows. */
	public static final int INFO_X = 56;
	public static final int INFO_RIGHT = 168;
	public static final int LAYER_Y = 18;
	public static final int LAYER_DONE_Y = 28;
	public static final int CYCLE_Y = 38;

	/** The Macerator's drill-bit progress sprite: atlas u/v/w/h, then where its track lies in the frame. */
	private static final int DRILL_U = 176;
	private static final int DRILL_V = 44;
	private static final int DRILL_W = 25;
	private static final int DRILL_H = 9;
	public static final int DRILL_X = 56;
	public static final int DRILL_Y = 50;

	/** The status row: under the drill track and above the buffer, clear of the energy bar on its left. */
	public static final MachineLayout.StatusBand STATUS_BAND = new MachineLayout.StatusBand(61,
			MachineScreen.STATUS_ROW_LEFT, MachineScreen.STATUS_ROW_RIGHT);

	public QuarryControllerScreen(QuarryControllerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, QuarryControllerMenu.IMAGE_HEIGHT);
		// The frame is taller than the vanilla default: the inventory caption follows the inventory down.
		this.inventoryLabelY = QuarryControllerMenu.INVENTORY_Y - 12;
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected EnergyBarSpec energyBar() {
		return EnergyBarSpec.LEFT;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		int max = this.menu.getMaxProgress();
		int progress = this.menu.getProgress();
		int filled = max > 0 ? Math.min(DRILL_W, progress * DRILL_W / max) : 0;
		if (progress > 0 && filled == 0) {
			filled = 1; // a started operation shows at once, not when the rounded fill reaches a pixel
		}
		if (filled > 0 && this.menu.workStatus().working()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + DRILL_X, this.topPos + DRILL_Y,
					(float) DRILL_U, (float) DRILL_V, filled, DRILL_H, TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, QuarryControllerBlockEntity.BIT_SLOT, new ItemStack(ModContent.QUARRY_DRILL_BIT.get()));
	}

	@Override
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		QuarryWorkStatus status = this.menu.workStatus();
		if (this.menu.layers() > 0) {
			infoLine(graphics, Component.translatable("gui.alaindustrial.quarry.layer",
					this.menu.layer(), this.menu.layers()), LAYER_Y);
			infoLine(graphics, Component.translatable("gui.alaindustrial.quarry.layer_done",
					this.menu.layerPermille() / 10), LAYER_DONE_Y);
		}
		if (!status.isBlocking()) {
			infoLine(graphics, Component.translatable(status.translationKey()), CYCLE_Y);
		}
	}

	/** One left-aligned row of the info block, shrunk like a status row if a translation runs past the frame. */
	private void infoLine(GuiGraphicsExtractor graphics, Component text, int y) {
		int band = INFO_RIGHT - INFO_X;
		int width = this.font.width(text);
		int left = this.leftPos + INFO_X;
		int top = this.topPos + y;
		if (width <= band) {
			graphics.text(this.font, text, left, top, GuiStyle.LABEL, false);
			return;
		}
		float scale = Math.max(MIN_STATUS_SCALE, (float) band / width);
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, text, 0, 0, GuiStyle.LABEL, false);
		graphics.pose().popMatrix();
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		Component reason = blockingReason();
		if (reason != null) {
			drawFittedStatus(graphics, reason, STATUS_BAND.y(), STATUS_BAND.left(), STATUS_BAND.right(),
					GuiStyle.STATUS_BLOCKING);
		}
	}

	/** Why the quarry stands, or null while it works or is done: the frame's broken rule while it is not formed. */
	private Component blockingReason() {
		QuarryWorkStatus status = this.menu.workStatus();
		if (!status.isBlocking()) {
			return null;
		}
		QuarryFrameScan.Status frame = this.menu.frameStatus();
		if (status == QuarryWorkStatus.NOT_FORMED && frame != QuarryFrameScan.Status.FORMED) {
			return Component.translatable(frameKey(frame));
		}
		return Component.translatable(status.translationKey());
	}

	/** The short caption of a frame status, without the coordinates the chat message carries. */
	public static String frameKey(QuarryFrameScan.Status frame) {
		return "gui.alaindustrial.quarry.frame." + frame.name().toLowerCase(Locale.ROOT);
	}
}
