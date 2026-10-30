package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.HoneyCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.HoneyCentrifugeStatus;
import dev.alaindustrial.menu.HoneyCentrifugeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * Texture-backed screen for the Honey Centrifuge (MOD-924): input, arrow and returned container on the left,
 * the honey gauge with its bucket pair and the energy bar on the right. The atlas is the Fermenter's right
 * half with the middle group moved left (tools/gen_honey_centrifuge_textures.py), so the gauge, the pair
 * and the energy bar sit at the Fermenter's coordinates.
 */
public class HoneyCentrifugeScreen extends MachineScreen<HoneyCentrifugeMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/honey_centrifuge.png");

	// The honey trough: 10 px of inner width, rows 21..66, filling bottom-up (GAUGE_BOTTOM exclusive).
	private static final int GAUGE_X = 115;
	private static final int GAUGE_W = 10, GAUGE_H = 46;
	private static final int GAUGE_BOTTOM = 67;

	// Progress arrow, drawn left-to-right over the dark static one; service strip at u=176, v=64.
	private static final int ARROW_U = 176, ARROW_V = 64, ARROW_W = 24, ARROW_H = 17;
	private static final int ARROW_X = 39, ARROW_Y = 35;

	/** Click area of the recipe viewers (MOD-716): exactly the progress arrow. */
	public static final GuiRect PROGRESS_AREA = new GuiRect(ARROW_X, ARROW_Y, ARROW_W, ARROW_H);

	/** The bar's own well on this atlas (the Fermenter's): the 10x44 fill lands on 157/66. */
	private static final EnergyBarSpec ENERGY = new EnergyBarSpec(157, 66, 176, 0);

	/** Tank capacity in mB for the tooltip — read from the block entity so the two cannot drift. */
	private static final int TANK_MB = (int) HoneyCentrifugeBlockEntity.TANK_CAPACITY;

	public HoneyCentrifugeScreen(HoneyCentrifugeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/** The energy bar and its tooltip, drawn by the base (MOD-716). */
	@Override
	protected EnergyBarSpec energyBar() {
		return ENERGY;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		blitStaticFrame(graphics);

		int fill = fillHeight();
		if (fill > 0) {
			FluidGauge.draw(graphics, tankFluid(), x + GAUGE_X, y + GAUGE_BOTTOM - fill, GAUGE_W, fill);
		}

		int max = this.menu.getMaxProgress();
		int filled = max > 0 ? this.menu.getProgress() * ARROW_W / max : 0;
		if (filled == 0 && this.menu.getProgress() > 0) {
			filled = 1; // immediate feedback rather than waiting for the integer fill to reach 1
		}
		if (filled > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + ARROW_X, y + ARROW_Y, (float) ARROW_U, (float) ARROW_V,
					filled, ARROW_H, TEX_SIZE, TEX_SIZE);
		}
	}

	/**
	 * The input shows a honeycomb, the machine's signature input; the container slot shows an EMPTY bucket,
	 * because it takes an empty container and hands it back full — the opposite of the input beside it.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, HoneyCentrifugeBlockEntity.INPUT_SLOT, new ItemStack(Items.HONEYCOMB));
		ghostHint(graphics, HoneyCentrifugeBlockEntity.BUCKET_INPUT_SLOT, new ItemStack(Items.BUCKET));
	}

	@Override
	protected void gaugeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.menu.getFluidRegistryId() != HoneyCentrifugeBlockEntity.FLUID_ID_NONE
				&& this.isHovering(GAUGE_X, GAUGE_BOTTOM - GAUGE_H, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
			int mb = this.menu.getFluidPermille() * TANK_MB / 1000;
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.fluid", FluidGauge.displayName(tankFluid()), mb, TANK_MB),
					mouseX, mouseY);
		}
		// Idle reason over the arrow: a full tank and a blocked bottle slot stop the machine just as an
		// empty input does, and only the tooltip tells them apart.
		HoneyCentrifugeStatus status = this.menu.getStatus();
		if (status != HoneyCentrifugeStatus.READY
				&& this.isHovering(ARROW_X, ARROW_Y, ARROW_W, ARROW_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable(status.translationKey()), mouseX, mouseY);
		}
	}

	/** The gauge's fill height in pixels, or 0 when the tank is empty. */
	private int fillHeight() {
		int permille = this.menu.getFluidPermille();
		if (this.menu.getFluidRegistryId() == HoneyCentrifugeBlockEntity.FLUID_ID_NONE || permille <= 0) {
			return 0;
		}
		return permille * GAUGE_H / 1000;
	}

	/** The fluid the tank holds, resolved client-side from the synced registry id. */
	private Fluid tankFluid() {
		return BuiltInRegistries.FLUID.byId(this.menu.getFluidRegistryId());
	}
}
