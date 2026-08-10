package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnStatus;
import dev.alaindustrial.menu.DistillationColumnMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * The Distillation Column's screen, round 2 (MOD-251): the centre of the GUI is a mini silhouette
 * of the tower itself — its three windows show the real tank levels in place (oil mid, diesel top,
 * fuel oil bottom), bubbles rise through the oil window while a run is in progress, the base storey
 * glows with the warm-up, and a fourth storey appears on the schematic when a Rectification
 * Section is installed on the real tower. Beside it: the coke fouling gauge. The side gauges keep
 * the full-resolution levels (with colour chips), and a volumes line prints the exact mB without
 * hovering.
 */
public class DistillationColumnScreen extends MachineScreen<DistillationColumnMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/distillation_column.png");

	// 200x200 frame (round 2b — the 176px one was cramped). Oil gauge: 11x64 at x=10, bottom 88.
	private static final int OIL_X = 10, OIL_BOTTOM = 88, OIL_W = 11, OIL_H = 64;
	// Fraction gauges (right): 11x28 each — diesel bottom 56, fuel oil bottom 94 (user art v4).
	private static final int FRACTION_X = 104, FRACTION_W = 11, FRACTION_H = 28;
	private static final int DIESEL_BOTTOM = 56, FUEL_OIL_BOTTOM = 94;

	// Tower schematic windows: 13x13 interiors at x=64..76 (diesel/oil/fuel oil, top to bottom).
	private static final int WIN_X = 64, WIN_W = 13, WIN_H = 13;
	private static final int WIN_DIESEL_TOP = 46, WIN_OIL_TOP = 62, WIN_FUEL_TOP = 78;
	// The base storey (heat glow area) and the 4th-storey overlay anchor.
	private static final int BASE_X0 = 63, BASE_Y0 = 78, BASE_X1 = 78, BASE_Y1 = 91;
	// The warm-up bar inside the foundation plate (fills left-to-right with heat).
	private static final int HEAT_X0 = 60, HEAT_Y0 = 93, HEAT_X1 = 81, HEAT_Y1 = 96;
	private static final int SECTION_U = 200, SECTION_V = 92, SECTION_W = 17, SECTION_H = 17;
	private static final int SECTION_X = 62, SECTION_Y = 28;

	// Fouling gauge: 4x42 interior at (86,48)..(89,89), filling bottom-up; fill sprite (200,48).
	private static final int FOUL_X = 86, FOUL_TOP = 48, FOUL_W = 4, FOUL_H = 42;
	private static final int FOUL_U = 200, FOUL_V = 48;

	private static final int STATUS_X = 8, STATUS_Y = 100;
	private static final int VOLUMES_Y = 109;

	/** The 200px frame's own right-side energy bar (interior 10x44 at x=174, bottom 82). */
	private static final EnergyBarSpec ENERGY = new EnergyBarSpec(174, 82, 200, 0);

	private static final int TANK_MB = (int) DistillationColumnBlockEntity.TANK_CAPACITY;

	public DistillationColumnScreen(DistillationColumnMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 200, 200);
	}

	@Override
	protected void init() {
		super.init();
		// The status + volumes band occupies the label's row on the 200px frame — hide it.
		this.inventoryLabelY = -1000;
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		blitStaticFrame(graphics);

		// Side gauges: full-resolution levels in the fluid's own texture.
		drawGauge(graphics, this.menu.getOilFluidId(), this.menu.getOilPermille(),
				x + OIL_X, y + OIL_BOTTOM, OIL_W, OIL_H);
		drawGauge(graphics, this.menu.getDieselFluidId(), this.menu.getDieselPermille(),
				x + FRACTION_X, y + DIESEL_BOTTOM, FRACTION_W, FRACTION_H);
		drawGauge(graphics, this.menu.getFuelOilFluidId(), this.menu.getFuelOilPermille(),
				x + FRACTION_X, y + FUEL_OIL_BOTTOM, FRACTION_W, FRACTION_H);

		renderEnergyBar(graphics, ENERGY);

		// The tower schematic: the same three levels inside the silhouette's windows.
		drawGauge(graphics, this.menu.getDieselFluidId(), this.menu.getDieselPermille(),
				x + WIN_X, y + WIN_DIESEL_TOP + WIN_H, WIN_W, WIN_H);
		drawGauge(graphics, this.menu.getOilFluidId(), this.menu.getOilPermille(),
				x + WIN_X, y + WIN_OIL_TOP + WIN_H, WIN_W, WIN_H);
		drawGauge(graphics, this.menu.getFuelOilFluidId(), this.menu.getFuelOilPermille(),
				x + WIN_X, y + WIN_FUEL_TOP + WIN_H, WIN_W, WIN_H);

		// Warm-up: a heat bar inside the foundation plate, filling left-to-right — reads as a
		// progress bar (the round-2 orange storey wash confused the playtest).
		int heat = this.menu.getHeatPermille();
		if (heat > 0) {
			int wFill = Math.max(1, heat * (HEAT_X1 - HEAT_X0) / 1000);
			graphics.fill(x + HEAT_X0, y + HEAT_Y0, x + HEAT_X0 + wFill, y + HEAT_Y1, 0xFFFF7818);
		}

		// A run in progress: bubbles rising through the oil window (animated off the progress tick).
		DistillationColumnStatus status = this.menu.getStatus();
		if (status == DistillationColumnStatus.WORKING && this.menu.getProgress() > 0) {
			int t = this.menu.getProgress();
			int off1 = (t * 2) % WIN_H;
			int off2 = (t * 2 + 5) % WIN_H;
			int winBottom = y + WIN_OIL_TOP + WIN_H - 1;
			graphics.fill(x + WIN_X + 2, winBottom - off1, x + WIN_X + 3, winBottom - off1 + 1, 0xFFFFA84E);
			graphics.fill(x + WIN_X + 5, winBottom - off2, x + WIN_X + 6, winBottom - off2 + 1, 0xFFFFD08A);
		}

		// Rectification Section installed on the real tower ⇒ the schematic grows a 4th storey.
		if (this.menu.hasSection()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + SECTION_X, y + SECTION_Y, (float) SECTION_U, (float) SECTION_V,
					SECTION_W, SECTION_H, TEX_SIZE, TEX_SIZE);
		}

		// Fouling gauge: coke builds bottom-up; full means the column is stopped for cleaning.
		int fouling = this.menu.getFouling();
		int fill = fouling * FOUL_H / DistillationColumnBlockEntity.FOULING_MAX;
		if (fill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + FOUL_X, y + FOUL_TOP + (FOUL_H - fill),
					(float) FOUL_U, (float) (FOUL_V + (FOUL_H - fill)),
					FOUL_W, fill, TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		graphics.text(this.font, statusLine(), this.leftPos + STATUS_X, this.topPos + STATUS_Y,
				0xFF404040, false);
		drawVolumesLine(graphics);
	}

	/**
	 * Ghost items in the empty IN slots — the "what goes here" hint, no words needed: an oil bucket
	 * in the intake slot, an empty bucket in each drain slot.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, DistillationColumnBlockEntity.OIL_FILL_INPUT_SLOT,
				new ItemStack(ModContent.OIL_BUCKET.get()));
		ghostHint(graphics, DistillationColumnBlockEntity.DIESEL_DRAIN_INPUT_SLOT,
				new ItemStack(Items.BUCKET));
		ghostHint(graphics, DistillationColumnBlockEntity.FUEL_OIL_DRAIN_INPUT_SLOT,
				new ItemStack(Items.BUCKET));
	}

	/** Exact tank volumes without hovering: a colour dot + mB number per tank, one compact line. */
	private void drawVolumesLine(GuiGraphicsExtractor graphics) {
		int x = this.leftPos + STATUS_X;
		int y = this.topPos + VOLUMES_Y;
		x = volumeEntry(graphics, x, y, 0xFF14120E, this.menu.getOilPermille());
		x = volumeEntry(graphics, x, y, 0xFFD09C1C, this.menu.getDieselPermille());
		volumeEntry(graphics, x, y, 0xFF6C4E28, this.menu.getFuelOilPermille());
	}

	private int volumeEntry(GuiGraphicsExtractor graphics, int x, int y, int colour, int permille) {
		graphics.fill(x, y + 1, x + 5, y + 6, colour);
		String text = String.valueOf(permille * TANK_MB / 1000);
		graphics.text(this.font, Component.literal(text), x + 7, y, 0xFF404040, false);
		return x + 7 + this.font.width(text) + 6;
	}

	/**
	 * One line telling the player what the column is doing or waiting for. Red for the stalls only
	 * the player can fix (full tanks point at their tower segment; FOULED wants a wrench), grey for
	 * missing inputs, green for warming/working.
	 */
	private Component statusLine() {
		DistillationColumnStatus status = this.menu.getStatus();
		ChatFormatting colour = switch (status) {
			case DIESEL_FULL, FUEL_OIL_FULL, FOULED -> ChatFormatting.DARK_RED;
			case NO_ENERGY, NO_OIL -> ChatFormatting.DARK_GRAY;
			case WARMING, WORKING -> ChatFormatting.DARK_GREEN;
		};
		if (status == DistillationColumnStatus.WARMING) {
			return Component.translatable(status.translationKey(), this.menu.getHeatPermille() / 10)
					.withStyle(colour);
		}
		return Component.translatable(status.translationKey()).withStyle(colour);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, ENERGY);
		gaugeTooltip(graphics, mouseX, mouseY, this.menu.getOilFluidId(), this.menu.getOilPermille(),
				OIL_X, OIL_BOTTOM, OIL_W, OIL_H, "gui.alaindustrial.distillation_column.tank.oil");
		gaugeTooltip(graphics, mouseX, mouseY, this.menu.getDieselFluidId(), this.menu.getDieselPermille(),
				FRACTION_X, DIESEL_BOTTOM, FRACTION_W, FRACTION_H,
				"gui.alaindustrial.distillation_column.tank.diesel");
		gaugeTooltip(graphics, mouseX, mouseY, this.menu.getFuelOilFluidId(), this.menu.getFuelOilPermille(),
				FRACTION_X, FUEL_OIL_BOTTOM, FRACTION_W, FRACTION_H,
				"gui.alaindustrial.distillation_column.tank.fuel_oil");
		// Tower schematic — heat over the base storey, fouling over its gauge.
		if (this.isHovering(HEAT_X0 - 2, HEAT_Y0 - 2, HEAT_X1 - HEAT_X0 + 4, HEAT_Y1 - HEAT_Y0 + 4, mouseX, mouseY)
				|| this.isHovering(BASE_X0, BASE_Y0, BASE_X1 - BASE_X0, BASE_Y1 - BASE_Y0, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.distillation_column.heat",
							this.menu.getHeatPermille() / 10),
					mouseX, mouseY);
		}
		if (this.isHovering(FOUL_X, FOUL_TOP, FOUL_W, FOUL_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.distillation_column.fouling",
							this.menu.getFouling()),
					mouseX, mouseY);
		}
	}

	private void drawGauge(GuiGraphicsExtractor graphics, int fluidId, int permille,
			int screenX, int screenBottom, int width, int height) {
		if (fluidId == DistillationColumnBlockEntity.FLUID_ID_NONE || permille <= 0) {
			return;
		}
		int fill = Math.max(1, permille * height / 1000);
		FluidGauge.draw(graphics, BuiltInRegistries.FLUID.byId(fluidId),
				screenX, screenBottom - fill, width, fill);
	}

	/** Hover tooltip for one gauge: the tank's role, its fluid's name, level / capacity in mB. */
	private void gaugeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			int fluidId, int permille, int gx, int gBottom, int w, int h, String roleKey) {
		if (!this.isHovering(gx, gBottom - h, w, h, mouseX, mouseY)) {
			return;
		}
		if (fluidId == DistillationColumnBlockEntity.FLUID_ID_NONE) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable(roleKey), mouseX, mouseY);
			return;
		}
		Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
		int mb = permille * TANK_MB / 1000;
		graphics.setTooltipForNextFrame(this.font,
				Component.translatable("gui.alaindustrial.fluid", FluidGauge.displayName(fluid), mb, TANK_MB),
				mouseX, mouseY);
	}
}
