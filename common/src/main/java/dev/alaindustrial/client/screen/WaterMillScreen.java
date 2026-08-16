package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.menu.WaterMillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Water Mill. It mirrors the wind-mill layout: the wheel slot is
 * centered, the left bar shows stored EU, and the status wheel turns blue while water is producing.
 */
public class WaterMillScreen extends MachineScreen<WaterMillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/water_mill.png");
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 178;

	private static final int STATUS_FRAME_X = 148;
	private static final int STATUS_FRAME_Y = 22;
	private static final int STATUS_FRAME_W = 16;
	private static final int STATUS_FRAME_H = 16;
	private static final float STATUS_UV_X = 176.0F;
	private static final float STATUS_UV_Y = 0.0F;

	/**
	 * Baseline (relative to topPos) of the centered status label: current output, or the idle reason.
	 *
	 * <p>Public because the L3 status-row gate (MOD-354) crops its pixel comparison to exactly this row.
	 * A copy of the number in the test would drift the moment the layout moves, and the gate would then
	 * measure an empty band and pass while the row itself was broken.
	 */
	public static final int STATUS_TEXT_Y = 44;

	public WaterMillScreen(WaterMillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 84;
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);

		// The supplied atlas uses the same left-side energy track as the wind-mill family.
		renderEnergyBar(graphics, EnergyBarSpec.LEFT_WINDMILL);

		// Progress carries the number of horizontal faces touching water (0..4). The slot check keeps
		// the indicator inactive when water is present but the required wheel has not been installed.
		if (this.menu.getSlot(0).hasItem() && this.menu.getProgress() > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					this.leftPos + STATUS_FRAME_X, this.topPos + STATUS_FRAME_Y,
					STATUS_UV_X, STATUS_UV_Y,
					STATUS_FRAME_W, STATUS_FRAME_H,
					TEX_SIZE, TEX_SIZE);
		}

		// One centred row carries both messages, because only one of them is ever meaningful: a running
		// mill answers "how much am I making right now" (MOD-348), a stopped one answers "why am I
		// stopped" (MOD-175/MOD-179 — wheel clash, wheel blocked by a solid block, or no water at all).
		drawStatusText(graphics);
	}

	/** Centered status row: production while the wheel turns, the idle reason otherwise. */
	private void drawStatusText(GuiGraphicsExtractor graphics) {
		// The mode is decided on the server (MOD-430): the screen no longer second-guesses it by looking
		// at the slot. A bare mill used to arrive here as MODE_OK, and the row was left blank on the
		// reasoning that the empty slot speaks for itself — it did not. A player who has not yet learned
		// that the mill needs a wheel saw a working-looking screen making nothing, with no clue why.
		String modeKey = switch (this.menu.getMode()) {
			case WaterMillBlockEntity.MODE_INTERFERENCE -> "gui.alaindustrial.water_mill.mode.interference";
			case WaterMillBlockEntity.MODE_OBSTRUCTED -> "gui.alaindustrial.water_mill.mode.obstructed";
			case WaterMillBlockEntity.MODE_NO_WATER -> "gui.alaindustrial.water_mill.mode.no_water";
			case WaterMillBlockEntity.MODE_NO_WHEEL -> "gui.alaindustrial.water_mill.mode.no_wheel";
			default -> null;
		};
		boolean idle = modeKey != null;
		// MODE_OK now means exactly one thing — a wheel is in, unobstructed and wet — so the row shows
		// what the mill makes (MOD-348). The "empty slot" case can no longer reach here: it arrives as
		// MODE_NO_WHEEL and is handled above.
		Component label = idle ? Component.translatable(modeKey) : outputLine(this.menu.getProductionRate());
		int tx = this.leftPos + (this.imageWidth - this.font.width(label)) / 2;
		graphics.text(this.font, label, tx, this.topPos + STATUS_TEXT_Y,
				idle ? GuiStyle.TEXT_DIM : GuiStyle.TEXT, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT_WINDMILL);
	}
}
