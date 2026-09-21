package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.menu.MonitorCoreMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Monitor Core screen (MOD-480): the card rack, and the four numbers that answer "why is my wall
 * dark" — allowance, seated cards, stored energy and upkeep.
 *
 * <p><b>Not a {@link MachineScreen}.</b> That base draws the upgrade panel and the stats tab of a
 * {@code MachineBlockEntity}; the core has neither. What it does share is the look: every piece of
 * the frame is blitted from the mod's own atlas, and the energy fill uses the same 10×44 sprite as
 * the machines.
 */
public class MonitorCoreScreen extends AbstractContainerScreen<MonitorCoreMenu> {

	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/monitor_core.png");
	private static final int TEX_SIZE = 256;

	/** Energy bar: frame at (8,22), the fill window inside it. Same sprite as every machine. */
	private static final int BAR_X = 10;
	private static final int BAR_Y = 24;
	private static final int BAR_W = 10;
	private static final int BAR_H = 44;
	// The fill sprite starts at x=200 in the atlas — measured, not assumed: reading it from 202
	// takes eight columns of copper and two transparent ones, which is how the first build drew
	// an orange stripe two pixels narrow with the track still showing on the right.
	private static final int BAR_UV_X = 200;
	private static final int BAR_UV_Y = 0;

	/** Two meters: capacity and cards. Track drawn into the atlas, fill tiled from an 8×4 sprite. */
	private static final int METER_X = 75;
	private static final int METER_W = 110;
	private static final int METER_H = 4;
	private static final int CAPACITY_METER_Y = 34;
	private static final int CARDS_METER_Y = 68;
	private static final int METER_UV_X = 202;
	private static final int METER_UV_Y = 46;
	private static final int METER_SPRITE_W = 8;

	/** Status lamp: three 6×6 sprites side by side in the atlas. */
	private static final int LAMP_X = 74;
	private static final int LAMP_Y = 126;
	private static final int LAMP_SIZE = 6;
	private static final int LAMP_UV_X = 202;
	private static final int LAMP_UV_Y = 52;

	// ARGB, and the alpha byte is not optional: 0x3F3F3F is fully transparent, so a colour written
	// without it draws nothing at all — which is exactly how the first build shipped a blank panel.
	private static final int TEXT = 0xFF3F3F3F;
	private static final int TEXT_DIM = 0xFF6B6B6B;

	/** Window size: the rack of five rows is what makes this taller than a machine screen. */
	private static final int WIDTH = 200;
	private static final int HEIGHT = 224;

	public MonitorCoreScreen(MonitorCoreMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
		this.inventoryLabelX = 19;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

		int capacity = this.menu.getCapacity();
		int energy = this.menu.getEnergy();
		int fill = capacity > 0 ? (int) ((long) energy * BAR_H / capacity) : 0;
		if (fill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + BAR_X, y + BAR_Y + (BAR_H - fill),
					BAR_UV_X, BAR_UV_Y + (BAR_H - fill), BAR_W, fill, TEX_SIZE, TEX_SIZE);
		}

		int allowance = this.menu.getAllowance();
		int watched = this.menu.getWatchedTypes();
		// The bar shows how much of the ALLOWANCE is spoken for; with no card fitted it stays empty
		// rather than dividing by zero and drawing a full bar out of nowhere.
		meter(graphics, x, y, CAPACITY_METER_Y, allowance > 0 ? Math.min(1f, (float) watched / allowance) : 0f);
		meter(graphics, x, y, CARDS_METER_Y,
				(float) this.menu.getSeatedCards() / MonitorCoreBlockEntity.CARD_SLOTS);

		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + LAMP_X, y + LAMP_Y,
				LAMP_UV_X + lampIndex() * 8, LAMP_UV_Y, LAMP_SIZE, LAMP_SIZE, TEX_SIZE, TEX_SIZE);
	}

	/** Tile the 8×4 fill sprite across the meter — one sprite, any length, no stretching. */
	private void meter(GuiGraphicsExtractor graphics, int x, int y, int meterY, float frac) {
		int filled = Math.max(0, Math.min(METER_W, Math.round(METER_W * frac)));
		int drawn = 0;
		while (drawn < filled) {
			int step = Math.min(METER_SPRITE_W, filled - drawn);
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + METER_X + drawn, y + meterY, METER_UV_X, METER_UV_Y,
					step, METER_H, TEX_SIZE, TEX_SIZE);
			drawn += step;
		}
	}

	/** 0 running · 1 waiting for a card · 2 out of power — the order the lamps sit in the atlas. */
	private int lampIndex() {
		if (this.menu.getAllowance() <= 0) {
			return 1;
		}
		return this.menu.isPowered() ? 0 : 2;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		int allowance = this.menu.getAllowance();
		graphics.text(this.font, Component.translatable("gui.alaindustrial.monitor_core.capacity"),
				74, 22, TEXT, false);
		graphics.text(this.font,
				Component.translatable("gui.alaindustrial.monitor_core.capacity_value",
						this.menu.getWatchedTypes(), allowance),
				74, 41, TEXT_DIM, false);

		graphics.text(this.font, Component.translatable("gui.alaindustrial.monitor_core.cards"),
				74, 56, TEXT, false);
		graphics.text(this.font,
				Component.translatable("gui.alaindustrial.monitor_core.cards_value",
						this.menu.getSeatedCards(),
						MonitorCoreBlockEntity.CARD_SLOTS),
				74, 75, TEXT_DIM, false);

		graphics.text(this.font, Component.translatable("gui.alaindustrial.monitor_core.energy"),
				74, 90, TEXT, false);
		graphics.text(this.font,
				Component.translatable("gui.alaindustrial.monitor_core.energy_value",
						this.menu.getEnergy(), this.menu.getCapacity()),
				74, 101, TEXT_DIM, false);
		graphics.text(this.font,
				Component.translatable("gui.alaindustrial.monitor_core.upkeep_value",
						this.menu.getUpkeep(), this.menu.getServedPanels()),
				74, 110, TEXT_DIM, false);

		graphics.text(this.font, Component.translatable(statusKey()), 85, 125, statusColour(), false);
	}

	private String statusKey() {
		return switch (lampIndex()) {
			case 1 -> "gui.alaindustrial.monitor_core.status.no_card";
			case 2 -> "gui.alaindustrial.monitor_core.status.no_power";
			default -> "gui.alaindustrial.monitor_core.status.running";
		};
	}

	private int statusColour() {
		return switch (lampIndex()) {
			case 1 -> 0xFF8A6D18;
			case 2 -> 0xFF8C2F28;
			default -> 0xFF3C6E37;
		};
	}
}
