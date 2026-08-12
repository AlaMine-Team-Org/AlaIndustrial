package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.CanningMachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Canning Machine GUI (MOD-383): the usual energy bar and progress arrow, plus a calorie readout.
 *
 * <p>The readout exists because this machine's input and output are decoupled — food goes into a
 * buffer and rations come out of it — so an idle machine holding half a ration's worth of calories
 * would otherwise look identical to an empty one, and the player would have no way to tell that
 * feeding it one more carrot finishes a can.
 */
public final class CanningMachineScreen extends ProgressMachineScreen<CanningMachineMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/canning_machine.png");
	/** Same arrow geometry as the other two-input machines, so the shared atlas strip lines up. */
	private static final ProgressSpec PROGRESS = new ProgressSpec(176, 48, 25, 9, 79, 31, false);
	/** Baseline between the machine area and the vanilla inventory label, as in VulcanizerScreen. */
	private static final int READOUT_Y = 61;
	/** Left stop — the energy bar occupies x 17..26 down to y=63. */
	private static final int READOUT_MIN_X = 30;

	public CanningMachineScreen(CanningMachineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		int perRation = menu.getValuePerRation();
		if (perRation <= 0) {
			return;
		}
		int percent = Math.min(100, menu.getFoodBuffer() * 100 / perRation);
		Component line = Component.translatable("gui.alaindustrial.canning_machine.calories", percent)
				.withStyle(ChatFormatting.DARK_GRAY);
		int textX = Math.max(READOUT_MIN_X, (imageWidth - font.width(line)) / 2);
		graphics.text(font, line, leftPos + textX, topPos + READOUT_Y, 0xFF404040, false);
	}
}
