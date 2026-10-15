package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.block.entity.ElectricMobTrapBlockEntity;
import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.menu.ElectricMobTrapMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;

/**
 * Screen of the Electric Mob Trap (MOD-818): the machine frame with a 3×3 grid of take-only slots
 * ({@code tools/gen_electric_mob_trap_gui.py}), the energy bar on the left, the liquid-experience gauge on
 * the right and, between them, the reach preview button and why the trap is standing still when it is.
 */
public class ElectricMobTrapScreen extends LayoutMachineScreen<ElectricMobTrapMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/electric_mob_trap.png");

	/** The status band: under the preview button, clear of the gauge on the right. */
	public static final MachineLayout.StatusBand STATUS_BAND = new MachineLayout.StatusBand(50, 100, 146);

	/** The reach preview, in the empty panel right of the grid — the mob repeller's eye button. */
	private static final ZonePreviewButton ZONE = new ZonePreviewButton(116, 18, ElectricMobTrapMenu.BUTTON_TOGGLE_ZONE,
			"gui.alaindustrial.electric_mob_trap.zone.show", "gui.alaindustrial.electric_mob_trap.zone.hide");

	// The trough painted into the atlas: 10 px of inner width, rows 21..66, filling bottom-up. GAUGE_BOTTOM is
	// exclusive, so a full gauge starts at 67 - 46 = 21.
	private static final int GAUGE_X = 150;
	private static final int GAUGE_W = 10, GAUGE_H = 46;
	private static final int GAUGE_BOTTOM = 67;

	private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT)
			.withStatus(STATUS_BAND);

	public ElectricMobTrapScreen(ElectricMobTrapMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, LAYOUT);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		int fill = fillHeight();
		if (fill > 0) {
			FluidGauge.draw(graphics, xpFluid(), this.leftPos + GAUGE_X, this.topPos + GAUGE_BOTTOM - fill,
					GAUGE_W, fill);
		}
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
	protected void gaugeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!this.isHovering(GAUGE_X, GAUGE_BOTTOM - GAUGE_H, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
			return;
		}
		int fluidId = this.menu.getXpFluidId();
		int capacity = ServerBalance.electricMobTrapTankMb();
		Component text = fluidId == ElectricMobTrapBlockEntity.FLUID_ID_NONE
				? Component.translatable("gui.alaindustrial.electric_mob_trap.tank_empty")
				: Component.translatable("gui.alaindustrial.fluid", FluidGauge.displayName(xpFluid()),
						this.menu.getXpPermille() * capacity / 1000, capacity);
		graphics.setTooltipForNextFrame(this.font, text, mouseX, mouseY);
	}

	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ZONE.tooltip(graphics, this.font, this.leftPos, this.topPos, mouseX, mouseY);
	}

	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		return ZONE.clicked(this.minecraft, this.menu.containerId, this.leftPos, this.topPos, event);
	}

	/** The gauge's fill height in pixels, or 0 when the tank is empty. */
	private int fillHeight() {
		if (this.menu.getXpFluidId() == ElectricMobTrapBlockEntity.FLUID_ID_NONE || this.menu.getXpPermille() <= 0) {
			return 0;
		}
		return this.menu.getXpPermille() * GAUGE_H / 1000;
	}

	private Fluid xpFluid() {
		return BuiltInRegistries.FLUID.byId(this.menu.getXpFluidId());
	}
}
