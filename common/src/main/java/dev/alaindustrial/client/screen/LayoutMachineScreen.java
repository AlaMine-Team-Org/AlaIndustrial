package dev.alaindustrial.client.screen;

import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * A machine screen declared by its {@link MachineLayout} (MOD-716, CLI-3 item 3): the base answers
 * {@link #texture()} and {@link #energyBar()} from the layout and writes the layout's status row, so a
 * screen with no logic of its own names one constant and, if it has a status row, its {@link #status()}.
 *
 * <p>The status row is written after everything the base draws — the frame, the slots, the ghost hints and
 * every overlay — exactly where the screens of this family wrote it themselves before the layout existed.
 */
public abstract class LayoutMachineScreen<T extends MachineMenu> extends MachineScreen<T> {

	private final MachineLayout layout;

	protected LayoutMachineScreen(T menu, Inventory inventory, Component title, MachineLayout layout) {
		super(menu, inventory, title);
		this.layout = layout;
	}

	/** This screen's declaration. */
	protected final MachineLayout layout() {
		return layout;
	}

	@Override
	protected Identifier texture() {
		return layout.texture();
	}

	@Override
	protected EnergyBarSpec energyBar() {
		return layout.energyBar();
	}

	/** The status the layout's band shows; only asked when the layout declares a band. */
	protected StatusLine status() {
		throw new IllegalStateException(getClass().getSimpleName() + " declares a status band but no status()");
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		MachineLayout.StatusBand band = layout.statusBand();
		if (band == null) {
			return;
		}
		StatusLine status = status();
		if (status.isBlocking()) {
			drawFittedStatus(graphics, Component.translatable(status.translationKey()),
					band.y(), band.left(), band.right(), GuiStyle.STATUS_BLOCKING);
		}
	}
}
