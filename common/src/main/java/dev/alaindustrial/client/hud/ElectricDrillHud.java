package dev.alaindustrial.client.hud;

import dev.alaindustrial.item.ElectricDrillItem;
import dev.alaindustrial.item.ItemEnergy;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.alaindustrial.client.AlaClientConfig;

/**
 * Charge readout for a held Electric Drill (MOD-079) — the sibling of {@link EnergyPackHud}. Shows the
 * drill's icon, charge percentage and a bar in the top-left corner, but only while the drill is in the
 * player's hand: like the pack readout, an overlay that appears with the gear it describes is
 * information, not clutter.
 *
 * <p>It shares everything with the pack readout — the same entry gate, the same drawing, the same
 * {@link AlaClientConfig#energyHudEnabled} toggle (default key H) and the same three-state colour — so
 * the two rows look identical. When a pack is also worn, this row stacks directly below the pack panel;
 * otherwise it takes the top slot itself.
 *
 * <p>Loader-neutral: both loaders register {@link #render} exactly as they register the pack readout
 * (Fabric {@code HudElementRegistry}, NeoForge {@code RegisterGuiLayersEvent}).
 */
public final class ElectricDrillHud {

	/** Vertical gap between stacked readout panels. */
	private static final int GAP = 2;

	/** Eases the drill's shown charge so a worn pack topping it up mid-mining doesn't jitter the %. */
	private static final HudSmoother SMOOTHER = new HudSmoother();

	private ElectricDrillHud() {
	}

	/** Draw the readout, if the player is holding a drill and the drill overlay is on. */
	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = EnergyPackHud.hudContext();
		if (mc == null || !AlaClientConfig.drillHudEnabled) {
			return;
		}
		ItemStack drill = heldDrill(mc.player);
		if (drill == null) {
			return;
		}
		// Stack under the pack panel when it is on screen, so the two rows never overlap; otherwise take
		// the top. Keyed on isShowing (enabled AND worn), not just worn: a hidden pack frees the top slot.
		int y = EnergyPackHud.isShowing(mc.player)
				? EnergyPackHud.Y + EnergyPackHud.PANEL_H + GAP
				: EnergyPackHud.Y;
		int percent = SMOOTHER.displayPercent(EnergyPackHud.fractionOf(drill), delta.getRealtimeDeltaTicks());
		EnergyPackHud.drawReadout(graphics, mc, y, drill, percent);
	}

	/** The drill the player is holding (main hand first, then offhand), or null if neither hand holds one. */
	private static ItemStack heldDrill(Player player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof ElectricDrillItem && ItemEnergy.capacity(main) > 0) {
			return main;
		}
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		if (off.getItem() instanceof ElectricDrillItem && ItemEnergy.capacity(off) > 0) {
			return off;
		}
		return null;
	}
}
