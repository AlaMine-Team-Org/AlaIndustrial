package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.menu.VacuumCollectorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the Vacuum Collector (MOD-802): the machine frame with a 3×3 grid of take-only slots
 * ({@code tools/gen_vacuum_collector_gui.py}), the energy bar on the left and, right of the grid, the reach
 * preview button and why the collector is standing still when it is.
 */
public class VacuumCollectorScreen extends LayoutMachineScreen<VacuumCollectorMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/vacuum_collector.png");

	/** The status band: right of the grid, under the preview button. */
	public static final MachineLayout.StatusBand STATUS_BAND = new MachineLayout.StatusBand(50, 100, 168);

	/** The reach preview, in the empty panel right of the grid — the mob repeller's eye button. */
	private static final ZonePreviewButton ZONE = new ZonePreviewButton(123, 18, VacuumCollectorMenu.BUTTON_TOGGLE_ZONE,
			"gui.alaindustrial.vacuum_collector.zone.show", "gui.alaindustrial.vacuum_collector.zone.hide");

	private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT)
			.withStatus(STATUS_BAND);

	public VacuumCollectorScreen(VacuumCollectorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, LAYOUT);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
	}

	@Override
	protected StatusLine status() {
		return this.menu.getStatus();
	}

	/** Under both panels, so an open statistics panel covers the button (MOD-693). */
	@Override
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ZONE.draw(graphics, this.leftPos, this.topPos, mouseX, mouseY);
	}

	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ZONE.tooltip(graphics, this.font, this.leftPos, this.topPos, mouseX, mouseY);
	}

	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		return ZONE.clicked(this.minecraft, this.menu.containerId, this.leftPos, this.topPos, event);
	}
}
