package dev.alaindustrial.client.screen;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.block.entity.IncubatorStatus;
import dev.alaindustrial.menu.IncubatorMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for the incubator (MOD-118).
 *
 * <p>Beyond the usual energy bar and progress arrow it shows two things unique to this machine: the
 * remaining irradiation charge of the loaded uranium ingot, and which mutation mode the inserted
 * chip selects. With no chip — or with the dome missing — it says so instead of leaving the player
 * guessing why nothing happens.
 */
public class IncubatorScreen extends MachineScreen<IncubatorMenu> {

	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/incubator.png");

	/** Its atlas is 14 pixels taller than the machine standard — the band that holds the status line. */
	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 180;

	/** Progress arrow between the input slot and the result slot. */
	private static final int ARROW_X = 112;
	private static final int ARROW_Y = 20;
	private static final int ARROW_W = 24;
	private static final int ARROW_H = 16;
	private static final int ARROW_SU = 176;
	private static final int ARROW_SV = 46;

	/**
	 * Charge pips, one per remaining attempt, stacked vertically in the narrow trough right of the fuel
	 * slot — the charge reads as a level gauge, so the pips light from the bottom up.
	 */
	private static final int PIP_X = 57;
	private static final int PIP_Y = 62;
	private static final int PIP_SIZE = 4;
	private static final int PIP_GAP = 2;
	private static final int PIP_ON = 0xFF63D863;
	private static final int PIP_OFF = 0xFF3A3A3A;
	/** How many pips the trough in the texture holds; a larger configured charge is clamped to it. */
	private static final int PIP_CAPACITY = 3;

	/** Status line: the clear band between the two slot rows, right of the energy bar. */
	private static final int STATUS_X = 26;
	private static final int STATUS_Y = 44;

	public IncubatorScreen(IncubatorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		blitStaticFrame(graphics);
		renderEnergyBar(graphics, EnergyBarSpec.INCUBATOR);

		int maxProgress = this.menu.getMaxProgress();
		int progress = this.menu.getProgress();
		int fill = maxProgress > 0 ? ARROW_W * progress / maxProgress : 0;
		if (progress > 0 && fill == 0) {
			fill = 1;
		}
		if (fill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + ARROW_X, y + ARROW_Y, (float) ARROW_SU, (float) ARROW_SV,
					fill, ARROW_H, TEX_SIZE, TEX_SIZE);
		}

		// Charge pips: how many attempts the loaded ingot still powers. Clamped to the trough drawn in
		// the texture — a config raising the charge past it would spill the pips onto the panel; the
		// exact numbers stay readable in the tooltip.
		int maxCharge = pipCount();
		int charge = Math.min(this.menu.getCharge(), maxCharge);
		for (int i = 0; i < maxCharge; i++) {
			// i counts charge, the trough counts rows: index 0 is the bottom pip, so it fills upwards.
			int py = y + PIP_Y + (maxCharge - 1 - i) * (PIP_SIZE + PIP_GAP);
			graphics.fill(x + PIP_X, py, x + PIP_X + PIP_SIZE, py + PIP_SIZE,
					i < charge ? PIP_ON : PIP_OFF);
		}
	}

	/**
	 * Ghost hints for the two slots the machine cannot start without.
	 *
	 * <p>The chip hint cycles through all three chips because they are equally valid answers to "what
	 * goes here" — each one merely picks a different mode, and a frozen picture of the transform chip
	 * would read as "only that one fits". The fuel hint is a single item: the fuel tag holds exactly
	 * the uranium ingot, so there is nothing to cycle.
	 *
	 * <p>The input and output slots stay unhinted on purpose: the input takes whatever the loaded mode
	 * has a recipe for, so no single picture would be honest, and the two right-hand slots are filled
	 * by the machine, not by the player.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, IncubatorBlockEntity.CHIP_SLOT, cyclingHint(List.of(
				ModContent.MUTATION_CHIP_TRANSFORM.get(),
				ModContent.MUTATION_CHIP_DUPLICATE.get(),
				ModContent.MUTATION_CHIP_CREATE.get())));
		ghostHint(graphics, IncubatorBlockEntity.FUEL_SLOT,
				new ItemStack(ModContent.URANIUM_INGOT.get()));
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		// The band between the two slot rows, clear of the energy bar on the left: 150 pixels of room,
		// which is what the atlas was made taller for.
		graphics.text(this.font, statusLine(), this.leftPos + STATUS_X, this.topPos + STATUS_Y,
				0xFF404040, false);
	}

	/**
	 * One line telling the player what the machine is set to, or what it is waiting for.
	 *
	 * <p>The machine names the fault itself (MOD-234) — the screen only picks a colour. Before that
	 * every stall except a missing dome or chip printed the mode name, so a machine parked on a
	 * blocked result slot looked identical to a working one.
	 */
	private Component statusLine() {
		IncubatorStatus status = this.menu.getStatus();
		if (status.isBlocking()) {
			// Red for "the machine is stuck and only you can unstick it", grey for "it is simply
			// missing a part".
			ChatFormatting colour = switch (status) {
				case OUTPUT_BLOCKED, NO_DOME -> ChatFormatting.DARK_RED;
				default -> ChatFormatting.DARK_GRAY;
			};
			return Component.translatable(status.translationKey()).withStyle(colour);
		}
		IncubatorMode mode = this.menu.getMode();
		if (mode == null) {
			return Component.translatable(IncubatorStatus.NO_CHIP.translationKey())
					.withStyle(ChatFormatting.DARK_GRAY);
		}
		return Component.translatable(mode.translationKey());
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.INCUBATOR);

		int height = pipCount() * (PIP_SIZE + PIP_GAP) - PIP_GAP;
		if (isHovering(PIP_X, PIP_Y, PIP_SIZE, height, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.incubator.charge",
							this.menu.getCharge(), Math.max(1, Config.mutationAttemptsPerIngot)),
					mouseX, mouseY);
		}
	}

	/** Pips actually drawn: the configured charge, never more than the trough holds. */
	private static int pipCount() {
		return Math.clamp(Config.mutationAttemptsPerIngot, 1, PIP_CAPACITY);
	}
}
