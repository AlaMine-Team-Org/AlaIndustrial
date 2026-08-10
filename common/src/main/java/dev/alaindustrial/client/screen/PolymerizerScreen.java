package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.menu.PolymerizerMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * Texture-backed screen for the Polymerizer.
 *
 * <p>Left: the oil tank gauge, drawn with the fluid's real block texture via {@link FluidGauge} (the tank
 * accepts any {@code c:oil} fluid, so the texture, tint and name are all resolved from the synced fluid
 * registry id — none of them is sent over the wire, as all are pure functions of the fluid type).
 * Middle: the two container slots and the progress arrow. Right: the energy bar.
 *
 * <p>Not a {@link ProgressMachineScreen}: that base pairs its progress sprite with the <em>left</em> energy
 * bar, and this machine's left edge is the tank.
 */
public class PolymerizerScreen extends MachineScreen<PolymerizerMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/polymerizer.png");

	// Tank gauge: inner trough x=16-26 (11px wide), y=19-65 (46px tall), filling bottom-up.
	private static final int FLUID_W = 11, FLUID_H = 46;
	private static final int FLUID_X = 16, FLUID_BOTTOM = 65;

	// Progress arrow sprite in the atlas service area (u=176,v=48, 24×17), drawn left-to-right over the
	// dark static arrow at (79,35).
	private static final int ARROW_U = 176, ARROW_V = 48, ARROW_W = 24, ARROW_H = 17;
	private static final int ARROW_X = 79, ARROW_Y = 35;

	/** Tank capacity in mB for the tooltip — read from the block entity so the two cannot drift. */
	private static final int TANK_MB = (int) PolymerizerBlockEntity.TANK_CAPACITY;

	public PolymerizerScreen(PolymerizerMenu menu, Inventory inventory, Component title) {
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

		// Tank level: grows bottom-up proportional to the synced permille, in the fluid's own texture.
		int fluidFill = fluidFillHeight();
		if (fluidFill > 0) {
			FluidGauge.draw(graphics, tankFluid(), x + FLUID_X, y + FLUID_BOTTOM - fluidFill, FLUID_W, fluidFill);
		}

		// Energy fill (right bar).
		renderEnergyBar(graphics, EnergyBarSpec.RIGHT);

		// Progress arrow (left-to-right).
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
	 * The fill slot is the machine's only player-facing input, and nothing on the frame says it wants
	 * oil rather than any bucket — so an oil bucket sits in it as a ghost until the player fills it.
	 * The emptied-container slot below gets none: the machine puts the container there, not the player.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, PolymerizerBlockEntity.FILL_INPUT_SLOT,
				new ItemStack(ModContent.OIL_BUCKET.get()));
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.RIGHT);
		// Tank gauge — fluid name + level in millibuckets.
		if (this.menu.getFluidRegistryId() != PolymerizerBlockEntity.FLUID_ID_NONE
				&& this.isHovering(FLUID_X, FLUID_BOTTOM - FLUID_H, FLUID_W, FLUID_H, mouseX, mouseY)) {
			int mb = this.menu.getFluidPermille() * TANK_MB / 1000;
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.fluid", FluidGauge.displayName(tankFluid()), mb, TANK_MB),
					mouseX, mouseY);
		}
	}

	/** The gauge's fill height in pixels, or 0 when the tank is empty. */
	private int fluidFillHeight() {
		int permille = this.menu.getFluidPermille();
		if (this.menu.getFluidRegistryId() == PolymerizerBlockEntity.FLUID_ID_NONE || permille <= 0) {
			return 0;
		}
		return permille * FLUID_H / 1000;
	}

	/** The fluid the tank holds, resolved client-side from the synced registry id. */
	private Fluid tankFluid() {
		return BuiltInRegistries.FLUID.byId(this.menu.getFluidRegistryId());
	}
}
