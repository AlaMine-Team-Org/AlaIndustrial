package dev.alaindustrial.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Tooltip payload for the Battery Pouch (MOD-052) — the marker {@link TooltipComponent} that
 * {@link PouchItem#getTooltipImage} hands to the tooltip pipeline. Rendering lives in
 * {@code dev.alaindustrial.client.tooltip.PouchClientTooltip}; each loader maps this class to that
 * renderer (Fabric {@code ClientTooltipComponentCallback}, NeoForge
 * {@code RegisterClientTooltipComponentFactoriesEvent}).
 */
public record PouchTooltip(PouchContents contents) implements TooltipComponent {
}
