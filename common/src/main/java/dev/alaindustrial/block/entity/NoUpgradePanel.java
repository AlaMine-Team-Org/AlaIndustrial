package dev.alaindustrial.block.entity;

/**
 * Marker for a machine with a menu but without the upgrade panel (MOD-080, MOD-393): its inventory gets
 * no four upgrade slots and its screen no gear tab.
 *
 * <p>A panel is a promise — a block that shows upgrade slots is telling the player those upgrades do
 * something there — and for a handful of machines it would be a lie (the energy condenser, the charge
 * pad, the electric heater, the sprinkler, the creative source, the reactor controller). Each says why
 * next to its own declaration.
 *
 * <p>A marker for the same reason as {@link BatteryFed}: the slot layout must be the same on the CLIENT,
 * where there is no block entity, so the answer has to be derivable from the block —
 * {@link dev.alaindustrial.block.entity.machine.SlotLayout#hasPanel} reads it back through the manifest's
 * block→BE-class mapping (MOD-712, BE-1). Before it, each of these machines overrode a method on the block
 * entity AND on its menu, and nothing checked that the two agreed.
 */
public interface NoUpgradePanel {
}
