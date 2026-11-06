package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BlockBreakerBlockEntity.RedstoneMode;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.quarry.QuarryWorkStatus;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.menu.QuarryControllerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Screen of the quarry controller (MOD-951), in the mod's machine style ({@code tools/gen_quarry_controller_gui.py},
 * built from the Macerator's atlas): the energy bar on the left, the drill bit's slot next to it, the work right of
 * the slot — the layer, how much of it is dug, the cycle's state and the Macerator's drill-bit sprite filling as the
 * running operation goes — the status row under it, and the 27-slot buffer above the player's inventory.
 *
 * <p>MOD-952: two narrow bars at the right edge, the drill's heat and the water that cools it, and the redstone mode
 * button above the bit slot (its icons and captions are the block breaker's: the same three modes).
 *
 * <p>The status row names why the quarry stands. While the frame does not stand it names the frame's first broken
 * rule instead, in a short form: the coordinates of the block to fix are not synced to the screen, the chat message
 * a right click on an unformed frame prints carries them.
 */
public class QuarryControllerScreen extends MachineScreen<QuarryControllerMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/quarry_controller.png");

	/** The info block right of the bit slot: the left edge of its text, its right edge and its rows. */
	public static final int INFO_X = 56;
	public static final int INFO_RIGHT = 146;
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

	/** The heat and water bars at the right edge (MOD-952): outer left edge, top, outer width and height. */
	public static final int HEAT_X = 150;
	public static final int WATER_X = 160;
	public static final int BAR_Y = 17;
	public static final int BAR_W = 8;
	public static final int BAR_H = 42;
	/** The redstone mode button, above the bit slot. */
	public static final int BUTTON_X = 31;
	public static final int BUTTON_Y = 18;
	public static final int BUTTON_SIZE = 20;

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
		bar(graphics, HEAT_X, this.menu.heatPermille(), GuiStyle.HEAT_FILL, GuiStyle.HEAT_FILL_HI);
		bar(graphics, WATER_X, this.menu.waterPermille(), GuiStyle.LEVEL_FILL, GuiStyle.LEVEL_FILL_HI);
		int bx = this.leftPos + BUTTON_X;
		int by = this.topPos + BUTTON_Y;
		boolean over = overButton(mouseX, mouseY);
		graphics.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE,
				over ? GuiStyle.BUTTON_ACTIVE_EDGE : GuiStyle.SLOT_EDGE);
		graphics.fill(bx + 1, by + 1, bx + BUTTON_SIZE - 1, by + BUTTON_SIZE - 1,
				over ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
		graphics.item(modeIcon(this.menu.redstoneMode()), bx + 2, by + 2);
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

	/** A vertical bar filled from the bottom by {@code permille}, in a slot-edged well. */
	private void bar(GuiGraphicsExtractor graphics, int x, int permille, int fill, int edge) {
		int left = this.leftPos + x;
		int top = this.topPos + BAR_Y;
		graphics.fill(left, top, left + BAR_W, top + BAR_H, GuiStyle.SLOT_EDGE);
		graphics.fill(left + 1, top + 1, left + BAR_W - 1, top + BAR_H - 1, GuiStyle.LEVEL_TRACK);
		int inner = BAR_H - 2;
		int height = Math.max(0, Math.min(inner, permille * inner / 1000));
		if (permille > 0 && height == 0) {
			height = 1;
		}
		if (height > 0) {
			int bottom = top + BAR_H - 1;
			graphics.fill(left + 1, bottom - height, left + BAR_W - 1, bottom, fill);
			graphics.fill(left + 1, bottom - height, left + BAR_W - 1, bottom - height + 1, edge);
		}
	}

	private boolean overButton(double mx, double my) {
		return over(mx, my, BUTTON_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE);
	}

	private boolean over(double mx, double my, int x, int y, int w, int h) {
		int left = this.leftPos + x;
		int top = this.topPos + y;
		return mx >= left && mx < left + w && my >= top && my < top + h;
	}

	private static ItemStack modeIcon(RedstoneMode mode) {
		return new ItemStack(switch (mode) {
			case IGNORE -> Items.GUNPOWDER;
			case WITH_SIGNAL -> Items.REDSTONE_TORCH;
			case WITHOUT_SIGNAL -> Items.LEVER;
		});
	}

	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component tip = null;
		if (overButton(mouseX, mouseY)) {
			tip = Component.translatable("gui.alaindustrial.block_breaker.redstone."
					+ this.menu.redstoneMode().name().toLowerCase(Locale.ROOT));
		} else if (over(mouseX, mouseY, HEAT_X, BAR_Y, BAR_W, BAR_H)) {
			tip = Component.translatable("gui.alaindustrial.quarry.heat", this.menu.heatPermille() / 10);
		} else if (over(mouseX, mouseY, WATER_X, BAR_Y, BAR_W, BAR_H)) {
			tip = Component.translatable("gui.alaindustrial.quarry.water", this.menu.waterPermille() / 10);
		}
		if (tip != null) {
			graphics.setTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overButton(event.x(), event.y())
				&& this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
					QuarryControllerMenu.BUTTON_REDSTONE_MODE);
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.controlClicked(event, doubleClick);
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
