package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Texture-backed screen for the Geothermal Generator.
 *
 * <p>Left bar: lava level (bottom-up), drawn with lava's real block texture via {@link FluidGauge} —
 * the same tiled, animated rendering the Pump uses (MOD-099), instead of a flat baked sprite. The
 * generator only ever burns lava, so the fluid is hardcoded rather than read off a synced registry id.
 * Right bar: energy (orange sprite, bottom-up) — shows stored EU / buffer capacity.
 * Slots: lava-bucket input at (60,34), empty-bucket output at (98,34).
 */
public class GeothermalGeneratorScreen extends MachineScreen<GeothermalGeneratorMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/geothermal_generator.png");

	// Lava level fill (LEFT bar): inner trough x=16-26 (11px wide), y=19-65 (44px tall).
	private static final int LAVA_W = 11, LAVA_H = 44;
	private static final int LAVA_X = 16, LAVA_BOTTOM = 65;

	// Lava burn buffer expressed in millibuckets for the tooltip. The tank holds 10 buckets
	// (see GeothermalGeneratorBlockEntity.TANK_CAPACITY); 10 buckets × 1000 mB = 10000 mB.
	private static final int LAVA_TANK_MB = 10_000;

	public GeothermalGeneratorScreen(GeothermalGeneratorMenu menu, Inventory inventory, Component title) {
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

		// Lava level fill: grows bottom-up proportional to remaining lava ticks, in lava's own animated texture.
		int maxLava = this.menu.getMaxProgress();
		int lavaFill = maxLava > 0 ? (int) ((long) this.menu.getProgress() * LAVA_H / maxLava) : 0;
		if (lavaFill > 0) {
			FluidGauge.draw(graphics, Fluids.LAVA, x + LAVA_X, y + LAVA_BOTTOM - lavaFill, LAVA_W, lavaFill);
		}

		// Energy fill (right bar): blit the segmented orange sprite (bottom-up) via the shared helper.
		renderEnergyBar(graphics, EnergyBarSpec.RIGHT);
	}

	/**
	 * A lava bucket in the intake slot: the generator burns lava and nothing else, and the two bare
	 * slots said none of that. The emptied bucket lands in the slot beside it and needs no hint —
	 * the machine puts it there.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Right bar — stored EU / buffer.
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.RIGHT);
		// Left bar — lava burn buffer as millibuckets. Derive mB from the progress/maxProgress ratio
		// (tank = 10000 mB) so it stays correct even if geothermalBurnTicks changes in config.
		int maxProgress = this.menu.getMaxProgress();
		if (maxProgress > 0 && this.isHovering(LAVA_X, LAVA_BOTTOM - LAVA_H, LAVA_W, LAVA_H, mouseX, mouseY)) {
			int lavaMb = (int) ((long) this.menu.getProgress() * LAVA_TANK_MB / maxProgress);
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.lava", lavaMb, LAVA_TANK_MB),
					mouseX, mouseY);
		}
	}
}
