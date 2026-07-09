package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Texture-backed screen for the high-altitude wind mill (T2, LV). */
public class HighAltitudeWindMillScreen extends AbstractContainerScreen<HighAltitudeWindMillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/high_altitude_wind_mill.png");
	private static final int TEX_SIZE = 256;
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 178;

	private static final int EBAR_X = 17;
	private static final int EBAR_BOTTOM = 76;
	private static final int EBAR_W = 10;
	private static final int EBAR_H = 44;
	private static final float EBAR_UV_X = 176.0F;
	private static final float EBAR_UV_TOP = 48.0F;

	private static final int STATUS_FRAME_X = 150;
	private static final int STATUS_FRAME_Y = 23;
	private static final int STATUS_FRAME_W = 13;
	private static final int STATUS_FRAME_H = 13;
	private static final float STATUS_UV_X = 177.0F;
	private static final float STATUS_UV_Y = 0.0F;

	/** Centred idle-status label row (between the rotor slot and the inventory label). */
	private static final int STATUS_TEXT_Y = 50;

	public HighAltitudeWindMillScreen(HighAltitudeWindMillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 84;
	}

	@Override
	public void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;

		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

		int capacity = this.menu.getCapacity();
		int eFill = capacity > 0 ? (int) ((long) this.menu.getEnergy() * EBAR_H / capacity) : 0;
		if (eFill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + EBAR_X, y + EBAR_BOTTOM - eFill,
					EBAR_UV_X, EBAR_UV_TOP + (EBAR_H - eFill),
					EBAR_W, eFill,
					TEX_SIZE, TEX_SIZE);
		}

		int mode = this.menu.getMode();
		boolean generating = mode == WindMillBlockEntity.MODE_BREEZE
				|| mode == WindMillBlockEntity.MODE_GALE
				|| mode == WindMillBlockEntity.MODE_STORM;
		if (generating) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + STATUS_FRAME_X, y + STATUS_FRAME_Y,
					STATUS_UV_X, STATUS_UV_Y,
					STATUS_FRAME_W, STATUS_FRAME_H,
					TEX_SIZE, TEX_SIZE);
		}

		// While idle, explain why in a centred translated label so the player can fix it.
		drawStatusText(graphics, mode, x, y);
	}

	/** Centred status label for idle modes; nothing is drawn while the mill is generating. */
	private void drawStatusText(GuiGraphicsExtractor graphics, int mode, int x, int y) {
		Component label = modeLabel(mode);
		if (label == null) {
			return;
		}
		int tx = x + (this.imageWidth - this.font.width(label)) / 2;
		graphics.text(this.font, label, tx, y + STATUS_TEXT_Y, GuiStyle.TEXT_DIM, false);
	}

	/** Map a wind-mill mode code to its translated status label, or {@code null} while generating. */
	private static Component modeLabel(int mode) {
		String key = switch (mode) {
			case WindMillBlockEntity.MODE_NO_ROTOR -> "gui.alaindustrial.wind_mill.mode.no_rotor";
			case WindMillBlockEntity.MODE_ROOFED -> "gui.alaindustrial.wind_mill.mode.roofed";
			case WindMillBlockEntity.MODE_OBSTRUCTED -> "gui.alaindustrial.wind_mill.mode.obstructed";
			case WindMillBlockEntity.MODE_CALM -> "gui.alaindustrial.wind_mill.mode.calm";
			default -> null;
		};
		return key == null ? null : Component.translatable(key);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (this.isHovering(EBAR_X, EBAR_BOTTOM - EBAR_H, EBAR_W, EBAR_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
					mouseX, mouseY);
		}
	}
}
