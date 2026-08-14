package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.VulcanizerStatus;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.menu.VulcanizerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Vulcanizer GUI with an actionable status line and external-heat indicator. */
public final class VulcanizerScreen extends ProgressMachineScreen<VulcanizerMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/vulcanizer.png");
	private static final ProgressSpec PROGRESS =
			new ProgressSpec(176, 48, 25, 9, 79, 31, false);
	private static final int HEAT_X = 66;
	private static final int HEAT_Y = 49;
	private static final int HEAT_W = 9;
	private static final int HEAT_H = 9;
	/** Atlas v of the three heat-level icons (bonfire / lava-magma / electric heater). */
	private static final int HEAT_ICON_V = 71;
	/**
	 * Status line baseline: the band between the machine area (ends at y=58) and the vanilla
	 * inventory label (imageHeight - 94 = 72). Centred so the longest translation keeps clear of
	 * both the energy bar on the left and the frame edge on the right.
	 */
	private static final int STATUS_Y = 61;
	/** Left stop for the status line — the energy bar occupies x 17..26 down to y=63. */
	private static final int STATUS_MIN_X = 30;

	public VulcanizerScreen(VulcanizerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		VulcanizerStatus status = menu.getStatus();
		// Only the blocking reasons are worth a line. A working machine says so with its filled
		// arrow and burning heat icon — a "Ready" caption on top of that is noise, so READY draws
		// nothing (its lang key is gone from every locale).
		if (!status.isBlocking()) {
			return;
		}
		Component line = Component.translatable(status.translationKey()).withStyle(ChatFormatting.DARK_RED);
		int textX = Math.max(STATUS_MIN_X, (imageWidth - font.width(line)) / 2);
		graphics.text(font, line, leftPos + textX, topPos + STATUS_Y, 0xFF404040, false);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.drawMachineFrame(graphics, mouseX, mouseY, partialTick);
		// Indexed by SOURCE, not by heat level (MOD-418): a warming Electric Heater supplies level 2, and
		// the old level-indexed lookup would have drawn the lava icon over a plainly electric machine.
		int icon = menu.getHeatSource().iconIndex();
		if (icon >= 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture(), leftPos + HEAT_X, topPos + HEAT_Y,
					176.0F + icon * HEAT_W, HEAT_ICON_V, HEAT_W, HEAT_H, TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (isHovering(HEAT_X, HEAT_Y, HEAT_W, HEAT_H, mouseX, mouseY)) {
			HeatSource heat = menu.getHeatSource();
			graphics.setTooltipForNextFrame(font,
					Component.translatable(heat.translationKey(), heat.level()), mouseX, mouseY);
		}
	}
}
