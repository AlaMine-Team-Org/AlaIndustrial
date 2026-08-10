package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.menu.PumpMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * Texture-backed screen for the Pump.
 *
 * <p>Left bar: fluid level (bottom-up), drawn with the fluid's <b>real block texture</b>. MOD-099: the pump
 * now holds any fluid, so the two baked lava/water sprites in our own atlas could not cover it — but a flat
 * colour rectangle looked cheap. Instead the tank is tiled with the fluid's own still texture, taken from
 * vanilla's fluid model, so water looks like water and a modded fluid looks like itself, with no per-fluid
 * asset of ours. That drawing lives in {@link FluidGauge}, shared with the Polymerizer since MOD-019.
 * Texture, tint and name are all derived <em>client-side</em> from the synced fluid registry id (channel 6);
 * none is sent over the wire, as all are pure functions of the fluid type. The fill height comes from the
 * permille ratio (channels 4/5), independent of the 10-bucket capacity.
 * Right bar: energy (orange sprite, bottom-up) — stored EU / buffer capacity.
 * Slots: fluid-bucket input at (60,23), empty-bucket output at (98,23).
 */
public class PumpScreen extends MachineScreen<PumpMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/pump.png");

	// Fluid level fill (LEFT bar): the fluid's own texture, tiled, growing bottom-up. inner trough
	// x=16-26 (11px wide), y=19-65 (46px tall). Texture/tint derive from the synced fluid id (channel 6).
	private static final int FLUID_W = 11, FLUID_H = 46;
	private static final int FLUID_X = 16, FLUID_BOTTOM = 65;

	// Tank capacity in mB for the tooltip (matches PumpBlockEntity.TANK_CAPACITY).
	private static final int TANK_MB = 10_000;

	public PumpScreen(PumpMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		// Static frame: imageWidth × imageHeight region at (0,0) of the 256×256 atlas.
		blitStaticFrame(graphics);

		// Fluid level fill: grows bottom-up proportional to the tank permille, in the fluid's own texture.
		// Only drawn when a fluid is present (registry id != NONE).
		int fluidId = this.menu.getFluidRegistryId();
		int denom = this.menu.getFluidPermilleMax();
		int permille = denom > 0 ? this.menu.getFluidPermille() : 0;
		if (fluidId != PumpBlockEntity.FLUID_ID_NONE && permille > 0) {
			int fluidFill = (int) ((long) permille * FLUID_H / denom);
			if (fluidFill > 0) {
				FluidGauge.draw(graphics, BuiltInRegistries.FLUID.byId(fluidId),
						x + FLUID_X, y + FLUID_BOTTOM - fluidFill, FLUID_W, fluidFill);
			}
		}

		// Energy fill (right bar): blit the segmented orange sprite (bottom-up) via the shared helper.
		renderEnergyBar(graphics, EnergyBarSpec.RIGHT);
	}

	/**
	 * The 2×2 grid is two operations, not four slots, and which row does what is invisible on the
	 * frame. The hints spell it out: a <em>full</em> bucket goes in the top-left to fill the tank, an
	 * <em>empty</em> one in the bottom-right to draw from it; the two slots facing them are where the
	 * machine puts the swapped container back.
	 *
	 * <p>The fill hint is a water bucket by convention, not by rule — the tank takes any fluid
	 * ({@link PumpBlockEntity} accepts every non-empty one), so this picture names the commonest case
	 * rather than a restriction, exactly as the empty bucket stands in for any container.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, PumpBlockEntity.FILL_INPUT_SLOT, new ItemStack(Items.WATER_BUCKET));
		ghostHint(graphics, PumpBlockEntity.DRAIN_INPUT_SLOT, new ItemStack(Items.BUCKET));
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Right bar — stored EU / buffer.
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.RIGHT);
		// Left bar — fluid name + level as millibuckets. The name is resolved client-side from the synced
		// fluid registry id (channel 6), so it shows the right label for any fluid, not just lava/water.
		int fluidId = this.menu.getFluidRegistryId();
		int denom = this.menu.getFluidPermilleMax();
		int permille = denom > 0 ? this.menu.getFluidPermille() : 0;
		if (fluidId != IdMap.DEFAULT
				&& this.isHovering(FLUID_X, FLUID_BOTTOM - FLUID_H, FLUID_W, FLUID_H, mouseX, mouseY)) {
			Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
			Component name = FluidGauge.displayName(fluid);
			int mb = (int) ((long) permille * TANK_MB / denom);
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.fluid", name, mb, TANK_MB),
					mouseX, mouseY);
		}
	}

}
