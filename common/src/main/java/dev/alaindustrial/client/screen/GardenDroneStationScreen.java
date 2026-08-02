package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.GardenDroneStatus;
import dev.alaindustrial.menu.GardenDroneStationMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Garden Drone Station (MOD-277).
 *
 * <p>Left bar: energy. Below the slots: one status line, because a station that has stopped is the
 * thing the player actually needs to diagnose — "out of seeds" and "farm fully tended" look the same
 * from the outside, and only one of them wants attention. Same rule the incubator follows (MOD-234):
 * if the machine is idle, the screen says why.
 */
public class GardenDroneStationScreen extends MachineScreen<GardenDroneStationMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/garden_drone_station.png");

	private static final int GUI_WIDTH = 176;
	/**
	 * Fourteen pixels taller than the machine standard — the band that holds the status line, the same
	 * trick the incubator uses. Without it the status text and vanilla's "Inventory" label land on top
	 * of each other, which is exactly what happened the first time this screen was drawn.
	 */
	private static final int GUI_HEIGHT = 180;

	/** The clear band under the slot rows, right of the energy bar. */
	private static final int STATUS_X = 30;
	private static final int STATUS_Y = 70;

	public GardenDroneStationScreen(GardenDroneStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		renderEnergyBar(graphics, EnergyBarSpec.LEFT);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		graphics.text(this.font, statusLine(), this.leftPos + STATUS_X, this.topPos + STATUS_Y,
				0xFF404040, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
	}

	/**
	 * One line naming what the station is doing. The station diagnoses its own state server-side; the
	 * screen only picks a colour — red for a hard stop (no power), amber for a refill the player can
	 * fix, plain for working or finished.
	 */
	private Component statusLine() {
		GardenDroneStatus status = this.menu.getStatus();
		ChatFormatting colour = switch (status) {
			case NO_ENERGY, NO_DRONE -> ChatFormatting.RED;
			case NO_RESOURCES -> ChatFormatting.GOLD;
			case WORKING, IDLE -> ChatFormatting.DARK_GRAY;
		};
		return Component.translatable(status.translationKey()).withStyle(colour);
	}
}
