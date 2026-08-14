package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Wind Mill.
 *
 * Static frame (blit UV 0,0 → 176×166) provides borders, energy-bar track, slot frames and the
 * evolution bar track. Dynamic elements are blitted from the service area (UV x≥176), mirroring
 * SolarPanelScreen:
 *   - Energy bar : UV(176,48..91) orange with tick marks, blitted bottom-up.
 *   - Status gear: UV(177,0..12) blue gear when the mill is generating (breeze/gale/storm).
 *   - Status text: centred translated label explaining why an idle mill is stopped (no rotor /
 *     roofed / obstructed / calm), so the player can fix it without guessing.
 *   - Evo bar    : UV(176,17) orange (day chip → high-altitude) or UV(176,32) blue (night chip → storm).
 *
 * Layout matches the supplied mockup: energy bar on the left, rotor slot in the center, chip slot on
 * the right, evolution bar at the bottom, a status indicator + label at the top.
 */
public class WindMillScreen extends MachineScreen<WindMillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/wind_mill.png");
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 178;

	// ── Status indicator — service-area 13×13 gear tile (UV 177,0..12) ──────────
	private static final int   STATUS_FRAME_X = 150;
	private static final int   STATUS_FRAME_Y = 23;
	private static final int   STATUS_FRAME_W = 13;
	private static final int   STATUS_FRAME_H = 13;
	private static final float STATUS_UV_X     = 177.0F;
	private static final float STATUS_UV_Y     = 0.0F;

	/**
	 * Baseline of the status row (centred, between the rotor slot and the evolution bar).
	 *
	 * <p>Public so the L3 stand can crop its pixel gate to the row's REAL position instead of a copy of
	 * this number (MOD-371, the same reason as {@code WaterMillScreen.STATUS_TEXT_Y}). A copy would
	 * survive a layout change silently: the row would move, the gate would keep measuring the band it
	 * was written for, find nothing changing there, and stay green while showing nothing.
	 *
	 * <p>Deliberately NOT shared with the two T2 wind mills, which declare their own. The three screens
	 * are laid out independently, so a shared constant would tie a change in one of them to the gates of
	 * the other two — and the gate is supposed to follow the screen it belongs to.
	 */
	public static final int STATUS_TEXT_Y = 50;

	// ── Evo bar fill zone (x=55..130, y=70..76 = 76×7) ─────────────────────────
	private static final int   EVO_X          = 55;
	private static final int   EVO_Y          = 70;
	private static final int   EVO_MAX_W      = 75;
	private static final int   EVO_H          = 7;
	private static final float EVO_UV_X        = 176.0F;
	private static final float EVO_UV_Y_ALT    = 17.0F;  // orange (day chip → high-altitude)
	private static final float EVO_UV_Y_STORM  = 32.0F;  // blue (night chip → storm)

	public WindMillScreen(WindMillMenu menu, Inventory inventory, Component title) {
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
		int x = this.leftPos;
		int y = this.topPos;

		// Static frame — borders, slot frames, energy/evolution tracks.
		blitStaticFrame(graphics);

		// ── Energy bar fill (bottom-up) via the shared MachineScreen helper ───────
		renderEnergyBar(graphics, EnergyBarSpec.LEFT_WINDMILL);

		// ── Status indicator — active while generating (any producing mode) ──────
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

		// ── Evolution bar fill (left-to-right) ───────────────────────────────────
		int evoMax = this.menu.getEvolveMax();
		int evoProg = this.menu.getEvolveProgress();
		if (evoMax > 0 && evoProg > 0) {
			int evoFill = Math.max(1, Math.min(evoProg * EVO_MAX_W / evoMax, EVO_MAX_W));
			// The wind mill shares the solar chips: day → high-altitude (orange), night → storm (blue).
			boolean nightChip = this.menu.getSlot(1).getItem().is(ModContent.ALIGNMENT_CHIP_NIGHT.get());
			float evoUvY = nightChip ? EVO_UV_Y_STORM : EVO_UV_Y_ALT;
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + EVO_X, y + EVO_Y,
					EVO_UV_X, evoUvY,
					evoFill, EVO_H,
					TEX_SIZE, TEX_SIZE);
		}

		// ── Status text — current output while running, the reason while idle ────
		// One centred row in the empty band between the rotor slot (ends y≈40) and the evolution bar
		// (y=70) carries both messages, because only one of them is ever meaningful: a running mill
		// answers "how much am I making right now" (MOD-346), a stopped one answers "why am I stopped".
		drawStatusText(graphics, mode, x, y);
	}

	/** Centered status row: production while generating, the idle reason otherwise. */
	private void drawStatusText(GuiGraphicsExtractor graphics, int mode, int x, int y) {
		Component label = modeLabel(mode);
		boolean idle = label != null;
		if (!idle) {
			label = outputLine(this.menu.getProductionRate());
		}
		int tx = x + (this.imageWidth - this.font.width(label)) / 2;
		graphics.text(this.font, label, tx, y + STATUS_TEXT_Y, idle ? GuiStyle.TEXT_DIM : GuiStyle.TEXT, false);
	}

	/** Map a wind-mill mode code to its translated status label, or {@code null} while generating. */
	private static Component modeLabel(int mode) {
		String key = switch (mode) {
			case WindMillBlockEntity.MODE_NO_ROTOR -> "gui.alaindustrial.wind_mill.mode.no_rotor";
			case WindMillBlockEntity.MODE_ROOFED -> "gui.alaindustrial.wind_mill.mode.roofed";
			case WindMillBlockEntity.MODE_OBSTRUCTED -> "gui.alaindustrial.wind_mill.mode.obstructed";
			case WindMillBlockEntity.MODE_INTERFERENCE -> "gui.alaindustrial.wind_mill.mode.interference";
			case WindMillBlockEntity.MODE_CALM -> "gui.alaindustrial.wind_mill.mode.calm";
			default -> null; // breeze/gale/storm — generating, the gear icon is the indicator
		};
		return key == null ? null : Component.translatable(key);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT_WINDMILL);
	}
}
