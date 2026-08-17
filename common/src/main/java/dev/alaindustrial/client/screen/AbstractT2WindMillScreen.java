package dev.alaindustrial.client.screen;

import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.menu.WindMillReadout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen shared by the two T2 wind mills (storm, high-altitude): one rotor slot, an
 * energy bar, a gear indicator while generating and a centred status row. The two mills draw the same
 * layout over different atlases, so the body lives here once and each concrete screen only supplies
 * its {@link #texture()} (MOD-439, the {@link ProgressMachineScreen} shape). The concrete classes stay:
 * the menu&#8594;screen manifest binds one screen class per menu type via {@code Foo::new}, and the
 * parity gate parses exactly that.
 *
 * @param <M> the concrete T2 wind-mill menu; distinct per mill, hence the readout interface
 */
public abstract class AbstractT2WindMillScreen<M extends MachineMenu & WindMillReadout> extends MachineScreen<M> {
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 178;

	private static final int STATUS_FRAME_X = 150;
	private static final int STATUS_FRAME_Y = 23;
	private static final int STATUS_FRAME_W = 13;
	private static final int STATUS_FRAME_H = 13;
	private static final float STATUS_UV_X = 177.0F;
	private static final float STATUS_UV_Y = 0.0F;

	/**
	 * Centred idle-status label row (between the rotor slot and the inventory label).
	 *
	 * <p>Public so the L3 stand crops its pixel gate to the row's real position rather than to a copy of
	 * this number (MOD-371): a copy would keep measuring the old band after a layout change and stay
	 * green over a row that had moved out of it. Shared by the two T2 mills because they share this
	 * layout — a change here moves both rows and both gates together. The T1 mill keeps its own
	 * constant: its layout is independent, and so is its gate.
	 */
	public static final int STATUS_TEXT_Y = 50;

	protected AbstractT2WindMillScreen(M menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 84;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;
		Identifier texture = texture();

		blitStaticFrame(graphics);

		// Energy bar fill (bottom-up) via the shared MachineScreen helper.
		renderEnergyBar(graphics, EnergyBarSpec.LEFT_WINDMILL);

		int mode = this.menu.getMode();
		boolean generating = mode == WindMillBlockEntity.MODE_BREEZE
				|| mode == WindMillBlockEntity.MODE_GALE
				|| mode == WindMillBlockEntity.MODE_STORM;
		if (generating) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
					x + STATUS_FRAME_X, y + STATUS_FRAME_Y,
					STATUS_UV_X, STATUS_UV_Y,
					STATUS_FRAME_W, STATUS_FRAME_H,
					TEX_SIZE, TEX_SIZE);
		}

		// Running: show the current output (MOD-346). Idle: explain why so the player can fix it.
		drawStatusText(graphics, mode, x, y);
	}

	/** Centred status row: production while generating, the idle reason otherwise. */
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
			default -> null;
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
