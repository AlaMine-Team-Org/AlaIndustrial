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
			new ProgressSpec(176, 44, 25, 9, 79, 38, false);
	private static final int HEAT_X = 66;
	private static final int HEAT_Y = 56;
	private static final int HEAT_W = 9;
	private static final int HEAT_H = 9;

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
		Component line = status.isBlocking()
				? Component.translatable(status.translationKey()).withStyle(ChatFormatting.DARK_RED)
				: Component.translatable("gui.alaindustrial.vulcanizer.status.ready")
						.withStyle(ChatFormatting.DARK_GRAY);
		graphics.text(font, line, leftPos + 45, topPos + 70, 0xFF404040, false);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.drawMachineFrame(graphics, mouseX, mouseY, partialTick);
		int level = menu.getHeatSource().level();
		if (level > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture(), leftPos + HEAT_X, topPos + HEAT_Y,
					176.0F + (level - 1) * HEAT_W, 56.0F, HEAT_W, HEAT_H, TEX_SIZE, TEX_SIZE);
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
