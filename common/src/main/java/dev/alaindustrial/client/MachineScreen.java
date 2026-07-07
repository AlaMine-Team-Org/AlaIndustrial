package dev.alaindustrial.client;

import dev.alaindustrial.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for slotted Industrialization machines (generator, macerator). Shows stored energy as a
 * bar plus exact numbers, a progress arrow with a percentage readout, and recessed slot
 * backgrounds. Built on the 26.2 render-state model via {@link GuiStyle}.
 */
public class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {
	public MachineScreen(T menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;

		GuiStyle.panel(graphics, x, y, this.imageWidth, this.imageHeight);
		for (Slot slot : this.menu.slots) {
			GuiStyle.slot(graphics, x + slot.x - 1, y + slot.y - 1);
		}

		GuiStyle.energyBar(graphics, x + 8, y + 20, 12, 46, this.menu.getEnergy(), this.menu.getCapacity());

		int max = this.menu.getMaxProgress();
		int pct = max > 0 ? this.menu.getProgress() * 100 / max : 0;
		graphics.text(this.font, Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
				x + 26, y + 22, GuiStyle.TEXT, false);
		graphics.text(this.font, Component.translatable("gui.alaindustrial.progress", pct), x + 26, y + 34, GuiStyle.TEXT_DIM, false);

		GuiStyle.progress(graphics, x + 74, y + 53, 24, this.menu.getProgress(), max);
	}
}
