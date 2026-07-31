package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathStatus;
import dev.alaindustrial.menu.GalvanicBathMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;

/**
 * Texture-backed screen for the Galvanic Bath (MOD-127).
 *
 * <p>Left: the water tank gauge, drawn with the fluid's real block texture via {@link FluidGauge} — the
 * texture, tint and name are all resolved from the synced fluid registry id, none of them travels over
 * the wire (all three are pure functions of the fluid type). Middle: the fibre/silver column, the
 * bucket pair and the progress arrow. Right: the energy bar.
 *
 * <p>The layout mirrors the Polymerizer's, whose texture this one is derived from, so the tank trough,
 * the energy bar and the service-area sprite UVs line up with the shared {@link MachineScreen} code.
 * Not a {@link ProgressMachineScreen}: that base pairs its progress sprite with the <em>left</em>
 * energy bar, and this machine's left edge is the tank.
 */
public class GalvanicBathScreen extends MachineScreen<GalvanicBathMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/galvanic_bath.png");

	// Tank gauge: inner trough x=16-26 (11px wide), y=19-65 (46px tall), filling bottom-up.
	private static final int FLUID_W = 11, FLUID_H = 46;
	private static final int FLUID_X = 16, FLUID_BOTTOM = 65;

	// Progress arrow sprite in the atlas service area (u=176,v=48, 24×17), drawn left-to-right over the
	// dark static arrow at (86,35).
	private static final int ARROW_U = 176, ARROW_V = 48, ARROW_W = 24, ARROW_H = 17;
	private static final int ARROW_X = 86, ARROW_Y = 35;

	/**
	 * Slots whose background carries a "what goes here" hint, and the plain patch that covers it.
	 *
	 * <p>The hints are baked into the GUI texture, so an item sitting in the slot is drawn on top of one
	 * — and anything smaller than 16x16, like the Vacuum Capsule, leaves the hint showing around its
	 * edges as visual noise. Stamping the result slot's own interior (the one square of plain slot
	 * background in the atlas) over an occupied hint keeps the hint for empty slots and a clean
	 * background for filled ones.
	 */
	private static final int[][] HINTED_SLOTS = {{40, 23}, {62, 23}, {40, 47}, {62, 47}};
	private static final int PLAIN_U = 117, PLAIN_V = 35, SLOT_INNER = 16;

	/** Tank capacity in mB for the tooltip — read from the block entity so the two cannot drift. */
	private static final int TANK_MB = (int) GalvanicBathBlockEntity.TANK_CAPACITY;

	public GalvanicBathScreen(GalvanicBathMenu menu, Inventory inventory, Component title) {
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

		// Water level: grows bottom-up proportional to the synced permille, in the fluid's own texture.
		int fluidFill = fluidFillHeight();
		if (fluidFill > 0) {
			FluidGauge.draw(graphics, tankFluid(), x + FLUID_X, y + FLUID_BOTTOM - fluidFill, FLUID_W, fluidFill);
		}

		// Cover the background hint of any slot that now holds something (see HINTED_SLOTS).
		for (int[] slot : HINTED_SLOTS) {
			if (isSlotOccupied(slot[0], slot[1])) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + slot[0], y + slot[1],
						(float) PLAIN_U, (float) PLAIN_V, SLOT_INNER, SLOT_INNER, TEX_SIZE, TEX_SIZE);
			}
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

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.RIGHT);
		// Tank gauge — fluid name + level in millibuckets. Hovering an EMPTY tank still explains itself,
		// because "why is nothing happening" is most often "there is no water in it".
		if (this.isHovering(FLUID_X, FLUID_BOTTOM - FLUID_H, FLUID_W, FLUID_H, mouseX, mouseY)) {
			if (this.menu.getTankFluidId() != GalvanicBathBlockEntity.FLUID_ID_NONE) {
				int mb = this.menu.getTankPermille() * TANK_MB / 1000;
				graphics.setTooltipForNextFrame(this.font,
						Component.translatable("gui.alaindustrial.fluid",
								FluidGauge.displayName(tankFluid()), mb, TANK_MB),
						mouseX, mouseY);
			} else {
				graphics.setTooltipForNextFrame(this.font,
						Component.translatable(GalvanicBathStatus.NO_WATER.translationKey()), mouseX, mouseY);
			}
		}
		// Idle reason over the progress arrow: the bath has four independent ways to stall, and water is
		// not part of the recipe, so "no matching recipe" would be a misleading label for most of them.
		GalvanicBathStatus status = this.menu.getStatus();
		if (status != GalvanicBathStatus.READY
				&& this.isHovering(ARROW_X, ARROW_Y, ARROW_W, ARROW_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable(status.translationKey()), mouseX, mouseY);
		}
	}

	/**
	 * Whether the menu slot drawn at these GUI coordinates currently holds an item. Matched by position
	 * rather than by index so the check keeps working if the slot order in the menu ever changes.
	 */
	private boolean isSlotOccupied(int slotX, int slotY) {
		for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
			if (slot.x == slotX && slot.y == slotY) {
				return slot.hasItem();
			}
		}
		return false;
	}

	/** The gauge's fill height in pixels, or 0 when the tank is empty. */
	private int fluidFillHeight() {
		int permille = this.menu.getTankPermille();
		if (this.menu.getTankFluidId() == GalvanicBathBlockEntity.FLUID_ID_NONE || permille <= 0) {
			return 0;
		}
		return permille * FLUID_H / 1000;
	}

	/** The fluid the tank holds, resolved client-side from the synced registry id. */
	private Fluid tankFluid() {
		return BuiltInRegistries.FLUID.byId(this.menu.getTankFluidId());
	}
}
