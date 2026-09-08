package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
import dev.alaindustrial.core.machine.ToolUpgradeStatus;
import dev.alaindustrial.menu.UpgradeTableMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Upgrade Table (MOD-482). The frame, the energy fill and the progress arrow are the
 * shared {@link ProgressMachineScreen} treatment; what this screen adds is the teaching.
 *
 * <p>Like the Component Repair Bench, this machine has <b>no recipes</b> — it rewrites the tool in the
 * slot — so a recipe viewer has nothing to show and a first-time player would be left with two grey
 * squares. Hence the same two answers drawn in place: cycling ghost items say what goes where, and the
 * status row never goes silent, so an empty table asks for a tool instead of showing nothing.
 */
public class UpgradeTableScreen extends ProgressMachineScreen<UpgradeTableMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/upgrade_table.png");

	/** The family's arrow: bright twin in the atlas service area, drawn over the muted one in the frame. */
	private static final ProgressSpec PROGRESS = new ProgressSpec(
			176, 48, 24, 17,   // sprite u/v/w/h
			79, 34,            // dest x/y in the 176×166 frame
			true);             // min 1 px, so a long install shows movement early

	/**
	 * Baseline (relative to topPos) of the centred status line, in the clear band between the slot row
	 * and the player inventory label, and the horizontal band it is centred inside — clear of the
	 * energy bar, which occupies x=17..27 and reaches past this row.
	 */
	public static final int STATUS_TEXT_Y = 57;
	private static final int STATUS_BAND_LEFT = 32;
	private static final int STATUS_BAND_RIGHT = 168;

	public UpgradeTableScreen(UpgradeTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/**
	 * Ghost hints for both empty slots: the drill line on the right, the module on the left.
	 *
	 * <p>The tool hint cycles through all three drills because all three are equally valid — a single
	 * frozen picture would read as "only this tier fits", which is exactly the wrong lesson for an
	 * upgrade whose whole point is that it works on any of them.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, UpgradeTableBlockEntity.TOOL_SLOT,
				cyclingHint(List.of(
						ModContent.ELECTRIC_DRILL.get(),
						ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get(),
						ModContent.ELECTRIC_DRILL_NETHERITE_TIP.get())));
		ghostHint(graphics, UpgradeTableBlockEntity.MODULE_SLOT,
				cyclingHint(List.of(ModContent.DRILL_COLUMN_MODULE.get())));
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.drawMachineFrame(graphics, mouseX, mouseY, partialTick);
		drawStatusText(graphics);
	}

	/** One centred row: what the table is doing, or the single reason it is not. Never blank. */
	private void drawStatusText(GuiGraphicsExtractor graphics) {
		ToolUpgradeStatus status = this.menu.getStatus();
		Component label = Component.translatable(status.translationKey());
		int colour = status == ToolUpgradeStatus.READY ? GuiStyle.TEXT : GuiStyle.TEXT_DIM;
		drawFittedStatus(graphics, label, STATUS_TEXT_Y, STATUS_BAND_LEFT, STATUS_BAND_RIGHT, colour);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
	}
}
