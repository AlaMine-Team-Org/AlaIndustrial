package dev.alaindustrial.client;

import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.ItemEnergy;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Charge readout for a worn Energy Pack (MOD-065) — the mod's first HUD element. Shows the pack's
 * icon, a percentage and a small charge bar in the top-left corner, but only while the pack is
 * actually on the player's chest: an overlay that is always there is clutter, one that appears with
 * the gear it describes is information.
 *
 * <p>Loader-neutral on purpose: Fabric's {@code HudElement} and NeoForge's {@code GuiLayer} both
 * hand over exactly {@code (GuiGraphicsExtractor, DeltaTracker)}, so both loaders register a lambda
 * onto {@link #render} and there is one implementation of the drawing.
 *
 * <p>Toggled with the keybind in {@link ModKeyMappings} (default: H); the state lives in
 * {@link AlaClientConfig#energyHudEnabled} and survives a restart.
 */
public final class EnergyPackHud {

	// Top-left, clear of the vanilla hotbar/effects. 16px icon, bar under the text.
	private static final int X = 8;
	private static final int Y = 8;
	private static final int ICON = 16;
	private static final int BAR_W = 44;
	private static final int BAR_H = 3;
	private static final int PAD = 3;

	private static final int BG = 0x90101418;      // translucent panel behind the readout
	private static final int BAR_BG = 0xFF2B2F36;
	// ChatFormatting no longer exposes a numeric colour in 26.2, so the three states carry their own.
	private static final int OK = 0xFF5CD65C;      // comfortable
	private static final int LOW = 0xFFFFAA00;     // worth noticing
	private static final int CRITICAL = 0xFFFF5555; // nearly dead

	private EnergyPackHud() {
	}

	/** Draw the readout, if the player is wearing a pack and the overlay is on. */
	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!AlaClientConfig.energyHudEnabled) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		// F1 hides it, spectators have no pack to speak of. It deliberately stays visible while a screen
		// is open: the readout is most useful exactly when the Battery Box GUI is up and the player is
		// watching the pack fill — and every vanilla HUD element (hearts, hotbar) renders under screens
		// too. NOTE: NeoForge does not gate mod layers on the F1 state, so this check is load-bearing.
		if (player == null || player.isSpectator() || mc.gui.hud.isHidden()) {
			return;
		}
		ItemStack pack = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(pack.getItem() instanceof EnergyPackItem)) {
			return;
		}
		long capacity = ItemEnergy.capacity(pack);
		if (capacity <= 0) {
			return;
		}
		long stored = ItemEnergy.get(pack);
		int percent = (int) (stored * 100 / capacity);

		Component label = Component.literal(percent + "%");
		int textW = mc.font.width(label);
		int panelW = PAD + ICON + PAD + Math.max(textW, BAR_W) + PAD;
		int panelH = PAD + ICON + PAD;

		graphics.fill(X, Y, X + panelW, Y + panelH, BG);
		graphics.item(pack, X + PAD, Y + PAD);

		int textX = X + PAD + ICON + PAD;
		graphics.text(mc.font, label, textX, Y + PAD, colorFor(percent));

		// Charge bar right under the percentage — the same fill the item bar shows, at HUD size.
		int barY = Y + PAD + mc.font.lineHeight + 2;
		graphics.fill(textX, barY, textX + BAR_W, barY + BAR_H, BAR_BG);
		int filled = (int) (BAR_W * stored / capacity);
		if (filled > 0) {
			graphics.fill(textX, barY, textX + filled, barY + BAR_H, colorFor(percent));
		}
	}

	/** Green while comfortable, gold when it is worth noticing, red when the pack is nearly dead. */
	private static int colorFor(int percent) {
		if (percent <= 10) {
			return CRITICAL;
		}
		if (percent <= 33) {
			return LOW;
		}
		return OK;
	}
}
