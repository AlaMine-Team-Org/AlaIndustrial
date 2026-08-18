package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.menu.LightningRodGeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Texture-backed screen for the lightning rod generator (MOD-386).
 *
 * <p>Two gauges rather than one: the usual energy buffer on the left, and the <b>capacitor</b> on the
 * right — the reserve a caught strike is banked in and bled out of. They are deliberately separate
 * bars, because the whole feature is about the difference between them: the capacitor is what a
 * strike fills instantly and what must have room when the next one lands, while the buffer is the
 * ordinary trickle the network drains.
 *
 * <p>The status row underneath says, in words, why nothing is happening — the one thing a player
 * standing under a clear sky with an empty rod cannot otherwise work out.
 */
public class LightningRodGeneratorScreen extends MachineScreen<LightningRodGeneratorMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/lightning_rod_generator.png");

	/** Capacitor gauge trough: inner 10×44 column on the right, mirroring the energy bar's metric. */
	private static final int CAP_X = 149;
	private static final int CAP_BOTTOM = 64;
	private static final int CAP_W = 10;
	private static final int CAP_H = 44;
	/** Where the capacitor fill sprite lives in the atlas service area (x ≥ 176). */
	private static final float CAP_UV_X = 186.0F;
	private static final float CAP_UV_Y = 0.0F;

	/** Centred status row, between the tip slot and the inventory label. */
	private static final int STATUS_TEXT_Y = 58;

	public LightningRodGeneratorScreen(LightningRodGeneratorMenu menu, Inventory inventory, Component title) {
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
		renderEnergyBar(graphics, EnergyBarSpec.LEFT);

		// Capacitor fill, bottom-up. The menu publishes permille, not raw EU: a DataSlot carries 16
		// bits and a capacitor holds up to 32 000, so the raw number would wrap on the way here.
		int fill = this.menu.getCapacitorPermille() * CAP_H / 1000;
		if (fill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + CAP_X, y + CAP_BOTTOM - fill,
					CAP_UV_X, CAP_UV_Y + (CAP_H - fill),
					CAP_W, fill, TEX_SIZE, TEX_SIZE);
		}

		drawStatusText(graphics, x, y);
	}

	/** Running: the current output. Otherwise: the reason, so the player can fix it. */
	private void drawStatusText(GuiGraphicsExtractor graphics, int x, int y) {
		int mode = this.menu.getMode();
		Component label = modeLabel(mode);
		boolean idle = label != null;
		if (!idle) {
			label = outputLine(this.menu.getProductionRate());
		}
		int tx = x + (this.imageWidth - this.font.width(label)) / 2;
		graphics.text(this.font, label, tx, y + STATUS_TEXT_Y, idle ? GuiStyle.TEXT_DIM : GuiStyle.TEXT, false);
	}

	/** Mode code to its translated label, or {@code null} while the capacitor is paying out. */
	private static Component modeLabel(int mode) {
		String key = switch (mode) {
			case LightningRodGeneratorBlockEntity.MODE_NO_TIP -> "gui.alaindustrial.lightning_rod.mode.no_tip";
			case LightningRodGeneratorBlockEntity.MODE_IDLE -> "gui.alaindustrial.lightning_rod.mode.idle";
			case LightningRodGeneratorBlockEntity.MODE_SHELTERED -> "gui.alaindustrial.lightning_rod.mode.sheltered";
			case LightningRodGeneratorBlockEntity.MODE_ARMED -> "gui.alaindustrial.lightning_rod.mode.armed";
			case LightningRodGeneratorBlockEntity.MODE_OVERLOAD -> "gui.alaindustrial.lightning_rod.mode.overload";
			default -> null;
		};
		return key == null ? null : Component.translatable(key);
	}

	/** An empty rod says what it wants, instead of showing one blank slot and no explanation. */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, LightningRodGeneratorBlockEntity.TIP_SLOT,
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP.get()));
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
		if (this.isHovering(CAP_X, CAP_BOTTOM - CAP_H, CAP_W, CAP_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.lightning_rod.capacitor",
							this.menu.getCapacitorPermille() / 10),
					mouseX, mouseY);
		}
	}
}
