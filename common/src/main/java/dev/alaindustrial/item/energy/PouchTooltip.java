package dev.alaindustrial.item.energy;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Tooltip payload for a pouch — the marker {@link TooltipComponent} that
 * {@link PouchItem#getTooltipImage} (and the Shielding Pouch's, MOD-545) hands to the tooltip
 * pipeline. Rendering lives in {@code dev.alaindustrial.client.tooltip.PouchClientTooltip}; each
 * loader maps this class to that renderer (Fabric {@code ClientTooltipComponentCallback}, NeoForge
 * {@code RegisterClientTooltipComponentFactoriesEvent}).
 *
 * <p>{@code capacity} travels with the contents because the renderer draws a fill bar from it and
 * the two pouches hold different amounts; reading a config knob at paint time would label one of
 * them with the other's capacity.
 */
public record PouchTooltip(PouchContents contents, int capacity) implements TooltipComponent {
}
