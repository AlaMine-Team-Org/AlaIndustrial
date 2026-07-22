package dev.alaindustrial.client.hud;

import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.item.JetpackItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.alaindustrial.client.AlaClientConfig;

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
 * <p>Toggled with the keybind in {@link dev.alaindustrial.client.ModKeyMappings} (default: H); the state lives in
 * {@link AlaClientConfig#energyHudEnabled} and survives a restart.
 */
public final class EnergyPackHud {

	// Top-left, clear of the vanilla hotbar/effects. 16px icon, bar under the text. X/Y and PANEL_H are
	// package-visible so the drill readout ({@link ElectricDrillHud}) can stack itself directly below
	// this panel when a pack is also worn.
	static final int X = 8;
	static final int Y = 8;
	private static final int ICON = 16;
	private static final int BAR_W = 44;
	private static final int BAR_H = 3;
	private static final int PAD = 3;
	/** Fixed panel height (the bar sits inside it) — the vertical step for anything stacked below. */
	static final int PANEL_H = PAD + ICON + PAD;

	private static final int BG = 0x90101418;      // translucent panel behind the readout
	private static final int BAR_BG = 0xFF2B2F36;
	// ChatFormatting no longer exposes a numeric colour in 26.2, so the three states carry their own.
	private static final int OK = 0xFF5CD65C;      // comfortable
	private static final int LOW = 0xFFFFAA00;     // worth noticing
	private static final int CRITICAL = 0xFFFF5555; // nearly dead

	/** Eases the pack's shown charge so a worn pack topping the held drill up doesn't jitter its own %. */
	private static final HudSmoother SMOOTHER = new HudSmoother();

	private EnergyPackHud() {
	}

	/** Draw the readout, if the player is wearing a pack and the overlay is on. */
	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = hudContext();
		if (mc == null || !AlaClientConfig.energyHudEnabled || !isWorn(mc.player)) {
			return;
		}
		ItemStack pack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
		int percent = SMOOTHER.displayPercent(fractionOf(pack), delta.getRealtimeDeltaTicks());
		drawReadout(graphics, mc, Y, pack, percent);
	}

	/** Current charge as a 0..1 fraction (0 when the item has no buffer). */
	static float fractionOf(ItemStack stack) {
		long capacity = ItemEnergy.capacity(stack);
		return capacity <= 0 ? 0.0f : (float) ItemEnergy.get(stack) / capacity;
	}

	/**
	 * Draw one charge readout (translucent panel, item icon, percentage, bar) with its top-left at
	 * {@code (X, y)} from an already smoothed + hysteretic {@code percent} (0..100). Shared by the pack
	 * readout above and the drill readout ({@link ElectricDrillHud}); the text and the bar are both
	 * derived from the one integer, so they can never disagree or flicker independently.
	 */
	static void drawReadout(GuiGraphicsExtractor graphics, Minecraft mc, int y, ItemStack stack, int percent) {
		Component label = Component.literal(percent + "%");
		int textW = mc.font.width(label);
		int panelW = PAD + ICON + PAD + Math.max(textW, BAR_W) + PAD;

		graphics.fill(X, y, X + panelW, y + PANEL_H, BG);
		graphics.item(stack, X + PAD, y + PAD);

		int textX = X + PAD + ICON + PAD;
		graphics.text(mc.font, label, textX, y + PAD, colorFor(percent));

		// Charge bar right under the percentage — from the same integer, so bar and number stay in step.
		int barY = y + PAD + mc.font.lineHeight + 2;
		graphics.fill(textX, barY, textX + BAR_W, barY + BAR_H, BAR_BG);
		int filled = BAR_W * percent / 100;
		if (filled > 0) {
			graphics.fill(textX, barY, textX + filled, barY + BAR_H, colorFor(percent));
		}
	}

	/**
	 * Shared entry gate for the mod's charge HUDs: there is a player, they are not a spectator, and the
	 * HUD is not hidden (F1). The per-HUD on/off flag is checked by each caller ({@link #render} and
	 * {@link ElectricDrillHud}) so the pack and the drill readouts toggle independently. Returns the
	 * {@link Minecraft} instance when a HUD may draw, else null.
	 *
	 * <p>F1 hides it, spectators have no gear to speak of. It deliberately stays visible while a screen
	 * is open: the readout is most useful exactly when the Battery Box GUI is up and the player is
	 * watching the charge fill — and every vanilla HUD element (hearts, hotbar) renders under screens
	 * too. NOTE: NeoForge does not gate mod layers on the F1 state, so this check is load-bearing.
	 */
	static Minecraft hudContext() {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || player.isSpectator() || mc.gui.hud.isHidden()) {
			return null;
		}
		return mc;
	}

	/** Whether the pack panel is currently on screen (enabled AND a usable pack is worn). */
	static boolean isShowing(Player player) {
		return AlaClientConfig.energyHudEnabled && isWorn(player);
	}

	/** Whether a usable chest-slot EU device (Energy Pack, or the Jetpack since MOD-148) is worn. */
	static boolean isWorn(Player player) {
		ItemStack pack = player.getItemBySlot(EquipmentSlot.CHEST);
		return (pack.getItem() instanceof EnergyPackItem || pack.getItem() instanceof JetpackItem)
				&& ItemEnergy.capacity(pack) > 0;
	}

	/** Green while comfortable, gold when it is worth noticing, red when the pack is nearly dead. */
	static int colorFor(int percent) {
		if (percent <= 10) {
			return CRITICAL;
		}
		if (percent <= 33) {
			return LOW;
		}
		return OK;
	}
}
