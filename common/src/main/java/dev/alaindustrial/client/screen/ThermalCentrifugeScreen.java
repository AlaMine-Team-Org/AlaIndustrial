package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ThermalCentrifugeStatus;
import dev.alaindustrial.menu.ThermalCentrifugeMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Thermal Centrifuge. Beyond the shared energy bar and progress arrow it shows the one
 * reading this machine has and no other does — how far the rotor has spun up — plus a status line, because
 * two of the machine's four gates (the redstone signal and the heater below) are invisible from inside the
 * GUI and a player who forgot the lever would otherwise have nothing to go on.
 */
public class ThermalCentrifugeScreen extends ProgressMachineScreen<ThermalCentrifugeMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/thermal_centrifuge.png");

	private static final ProgressSpec PROGRESS = new ProgressSpec(
			176, 44, 25, 9,  // sprite u/v/w/h — the shared golden arrow in the atlas service area
			82, 38,           // dest x/y in the 176×166 frame, between the two slots
			false);

	/** Rotor gauge: a vertical bar mirroring the energy bar on the opposite side of the frame. */
	private static final int SPIN_X = 149;
	private static final int SPIN_BOTTOM = 64;
	private static final int SPIN_W = 10;
	private static final int SPIN_H = 44;
	private static final int SPIN_U = 186;
	private static final int SPIN_V = 0;

	/**
	 * Status band between the machine area and the inventory label.
	 *
	 * <p>Narrower than any other machine's, because this screen is the only one with a gauge on BOTH
	 * sides: the energy bar occupies x=17..27 and the rotor gauge x=149..159, and both run down to y=64,
	 * past this row's baseline. Centring across the whole window — which is what shipped first — printed
	 * "Needs a redstone signal" straight over both of them.
	 */
	private static final int STATUS_Y = 61;
	private static final int STATUS_BAND_LEFT = 32;
	private static final int STATUS_BAND_RIGHT = 146;

	public ThermalCentrifugeScreen(ThermalCentrifugeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.drawMachineFrame(graphics, mouseX, mouseY, partialTick);
		int filled = Math.round(SPIN_H * Math.clamp(menu.getSpinPermille(), 0, 1000) / 1000.0F);
		if (filled <= 0) {
			return;
		}
		// Fills bottom-up, so both the source V and the destination Y walk up together with the fill.
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture(),
				leftPos + SPIN_X, topPos + SPIN_BOTTOM - filled,
				SPIN_U, (float) (SPIN_V + SPIN_H - filled), SPIN_W, filled, TEX_SIZE, TEX_SIZE);
	}

	/** Hovering the rotor gauge names it and gives the exact percentage — the bar alone reads as decoration. */
	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (isHovering(SPIN_X, SPIN_BOTTOM - SPIN_H, SPIN_W, SPIN_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font,
					Component.translatable("gui.alaindustrial.thermal_centrifuge.spin",
							menu.getSpinPermille() / 10),
					mouseX, mouseY);
		}
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		ThermalCentrifugeStatus status = menu.getStatus();
		if (!status.isBlocking()) {
			return;
		}
		Component line = Component.translatable(status.translationKey()).withStyle(ChatFormatting.DARK_RED);
		// The band is this screen's own, narrower than the family default: see STATUS_BAND_LEFT/RIGHT above.
		drawFittedStatus(graphics, line, STATUS_Y, STATUS_BAND_LEFT, STATUS_BAND_RIGHT, 0xFF404040);
	}
}
