package dev.alaindustrial.client.screen;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.menu.SprinklerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;

/**
 * Texture-backed screen for the Sprinkler (MOD-525).
 *
 * <p>One gauge and one container pair — the whole machine. There is no energy bar because the block
 * draws no EU, and no progress arrow because a spray is instantaneous; what the player wants is the
 * level, and how many sprays that buys.
 *
 * <p>The gauge's pixel bounds must match the trough painted into the GUI art. Nothing checks that
 * pairing automatically, so both were authored from one list of coordinates.
 */
public class SprinklerScreen extends MachineScreen<SprinklerMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/sprinkler.png");

	// Solution trough, measured off the atlas: x 27..36, rows 19..64, filling bottom-up.
	// GAUGE_BOTTOM is exclusive, so a full gauge starts at 65 - 46 = 19.
	private static final int GAUGE_X = 27;
	private static final int GAUGE_W = 10, GAUGE_H = 46;
	private static final int GAUGE_BOTTOM = 65;

	/** Tank capacity in mB, read from config so the tooltip cannot drift from the block. */
	private static int tankMb() {
		return Math.max(1, Config.sprinklerTankMb);
	}

	public SprinklerScreen(SprinklerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		int fill = fillHeight();
		if (fill > 0) {
			FluidGauge.draw(graphics, tankFluid(), this.leftPos + GAUGE_X,
					this.topPos + GAUGE_BOTTOM - fill, GAUGE_W, fill);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (!this.isHovering(GAUGE_X, GAUGE_BOTTOM - GAUGE_H, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
			return;
		}
		int capacity = tankMb();
		if (this.menu.getSolutionFluidId() != SprinklerBlockEntity.FLUID_ID_NONE) {
			int mb = this.menu.getSolutionPermille() * capacity / 1000;
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.fluid",
							FluidGauge.displayName(tankFluid()), mb, capacity),
					mouseX, mouseY);
		} else {
			// An empty gauge is the single most common reason a sprinkler is doing nothing, so it says
			// what it wants rather than staying blank.
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.sprinkler.empty"), mouseX, mouseY);
		}
	}

	/** The gauge's fill height in pixels, or 0 when the tank is empty. */
	private int fillHeight() {
		int permille = this.menu.getSolutionPermille();
		if (this.menu.getSolutionFluidId() == SprinklerBlockEntity.FLUID_ID_NONE || permille <= 0) {
			return 0;
		}
		return permille * GAUGE_H / 1000;
	}

	/** The fluid the tank holds, resolved client-side from the synced registry id. */
	private Fluid tankFluid() {
		return BuiltInRegistries.FLUID.byId(this.menu.getSolutionFluidId());
	}
}
