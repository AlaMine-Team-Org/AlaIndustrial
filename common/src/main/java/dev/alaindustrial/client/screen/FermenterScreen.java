package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.block.entity.FermenterStatus;
import dev.alaindustrial.menu.FermenterMenu;
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
 * Texture-backed screen for the Fermenter (MOD-146).
 *
 * <p>Two gauges rather than one, because this machine has a fluid on both sides: water comes in on
 * the left, biofuel leaves on the right, and the biomass item leaves through the middle. Both are
 * drawn with the fluid's own block texture through {@link FluidGauge} — texture, tint and name are
 * pure functions of the fluid type, so only the registry id travels over the wire.
 *
 * <p>The gauges' pixel bounds below must match the troughs painted into the GUI art; the same is
 * true of every slot coordinate in {@link FermenterMenu}. Nothing checks that pairing automatically,
 * so both were authored from one list of coordinates rather than measured off the picture by eye.
 */
public class FermenterScreen extends MachineScreen<FermenterMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/fermenter.png");

	// Both troughs, measured off the atlas rather than eyeballed: 10 px of inner width, rows 21..66,
	// filling bottom-up. GAUGE_BOTTOM is exclusive, so a full gauge starts at 67 - 46 = 21.
	private static final int WATER_X = 7;
	private static final int BIOFUEL_X = 115;
	private static final int GAUGE_W = 10, GAUGE_H = 46;
	private static final int GAUGE_BOTTOM = 67;

	// Progress arrow, drawn left-to-right over the dark static one. The service strip sits at the
	// usual u=176, immediately right of the 176-wide frame. The sprite is 17 rows against the dark
	// arrow's 15, so it is anchored one row above it and overhangs evenly.
	private static final int ARROW_U = 176, ARROW_V = 64, ARROW_W = 24, ARROW_H = 17;
	private static final int ARROW_X = 65, ARROW_Y = 35;

	/** The bar's own well on this atlas: rows 23..64 at x=158, so the 10x44 fill lands on 157/66. */
	private static final EnergyBarSpec ENERGY = new EnergyBarSpec(157, 66, 176, 0);

	/** Tank capacity in mB for the tooltips — read from the block entity so the two cannot drift. */
	private static final int TANK_MB = (int) FermenterBlockEntity.TANK_CAPACITY;

	public FermenterScreen(FermenterMenu menu, Inventory inventory, Component title) {
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

		blitStaticFrame(graphics);

		int waterFill = fillHeight(this.menu.getWaterPermille(), this.menu.getWaterFluidId());
		if (waterFill > 0) {
			FluidGauge.draw(graphics, waterFluid(), x + WATER_X, y + GAUGE_BOTTOM - waterFill,
					GAUGE_W, waterFill);
		}
		int biofuelFill = fillHeight(this.menu.getBiofuelPermille(), this.menu.getBiofuelFluidId());
		if (biofuelFill > 0) {
			FluidGauge.draw(graphics, biofuelFluid(), x + BIOFUEL_X, y + GAUGE_BOTTOM - biofuelFill,
					GAUGE_W, biofuelFill);
		}

		renderEnergyBar(graphics, ENERGY);

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
	 * Both container slots are hinted, because neither is guessable from the recipe.
	 *
	 * <p>Water is not part of the recipe at all, so nothing else on the screen says the machine wants
	 * any — a full water bucket says it. The biofuel side is the subtler one: that slot takes an
	 * EMPTY container and hands it back full, which is the opposite direction to every other input on
	 * this screen, so it is hinted with an empty bucket rather than a filled one.
	 *
	 * <p>What may be fermented is deliberately not hinted: that is the recipe's business, it changes
	 * with datapacks, and it reads fine from the recipe viewer.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, FermenterBlockEntity.WATER_FILL_INPUT_SLOT, new ItemStack(Items.WATER_BUCKET));
		ghostHint(graphics, FermenterBlockEntity.BIOFUEL_DRAIN_INPUT_SLOT, new ItemStack(Items.BUCKET));
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, ENERGY);

		// An EMPTY gauge still explains itself: "why is nothing happening" is most often "no water".
		if (this.isHovering(WATER_X, GAUGE_BOTTOM - GAUGE_H, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
			gaugeTooltip(graphics, mouseX, mouseY, this.menu.getWaterFluidId(),
					this.menu.getWaterPermille(), waterFluid(), FermenterStatus.NO_WATER);
		}
		if (this.isHovering(BIOFUEL_X, GAUGE_BOTTOM - GAUGE_H, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
			gaugeTooltip(graphics, mouseX, mouseY, this.menu.getBiofuelFluidId(),
					this.menu.getBiofuelPermille(), biofuelFluid(), null);
		}

		// Idle reason over the arrow: this machine has five independent ways to stall, and neither the
		// water nor the biofuel is part of the recipe, so "no matching recipe" would mislabel most.
		FermenterStatus status = this.menu.getStatus();
		if (status != FermenterStatus.READY
				&& this.isHovering(ARROW_X, ARROW_Y, ARROW_W, ARROW_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable(status.translationKey()), mouseX, mouseY);
		}
	}

	/**
	 * Fluid name plus level for a gauge, or {@code emptyStatus}' label when it holds nothing. An empty
	 * output gauge has nothing useful to say, so it passes {@code null} and stays quiet.
	 */
	private void gaugeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int fluidId,
			int permille, Fluid fluid, FermenterStatus emptyStatus) {
		if (fluidId != FermenterBlockEntity.FLUID_ID_NONE) {
			int mb = permille * TANK_MB / 1000;
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.fluid",
							FluidGauge.displayName(fluid), mb, TANK_MB),
					mouseX, mouseY);
		} else if (emptyStatus != null) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable(emptyStatus.translationKey()), mouseX, mouseY);
		}
	}

	/** A gauge's fill height in pixels, or 0 when it is empty. */
	private static int fillHeight(int permille, int fluidId) {
		if (fluidId == FermenterBlockEntity.FLUID_ID_NONE || permille <= 0) {
			return 0;
		}
		return permille * GAUGE_H / 1000;
	}

	private Fluid waterFluid() {
		return BuiltInRegistries.FLUID.byId(this.menu.getWaterFluidId());
	}

	private Fluid biofuelFluid() {
		return BuiltInRegistries.FLUID.byId(this.menu.getBiofuelFluidId());
	}
}
