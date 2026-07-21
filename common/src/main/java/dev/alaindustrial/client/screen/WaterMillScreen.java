package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.WaterMillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Water Mill. Static frame from atlas PNG; dynamic layer draws
 * the energy fill (bottom-up orange bar). No burn flame — the water mill has no fuel.
 */
public class WaterMillScreen extends MachineScreen<WaterMillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/generator.png");

	public WaterMillScreen(WaterMillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Static frame: visible imageWidth × imageHeight region at top-left of the 256×256 atlas.
		blitStaticFrame(graphics);

		// Energy fill: blit the segmented orange sprite (bottom-up) via the shared MachineScreen helper.
		renderEnergyBar(graphics, EnergyBarSpec.LEFT);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
	}
}
