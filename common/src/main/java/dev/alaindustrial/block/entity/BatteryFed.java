package dev.alaindustrial.block.entity;

/**
 * Marker for a machine with a battery drawer (MOD-679): one extra slot, opened by the bolt key beside
 * the energy bar, whose item the machine drains into its own buffer. It lets a machine run far from the
 * grid on batteries carried there.
 *
 * <p>A marker for the same reason as {@link Overclockable}: the menu has to lay out the slot on the
 * CLIENT too, where there is no block entity, so the answer must be derivable from the block —
 * {@link dev.alaindustrial.registry.ContentManifest#isBatteryFed} reads it back through the manifest's
 * block→BE-class mapping. Implement this and the slot, the drain and the key all follow.
 *
 * <p>Only consumers implement it. A generator draining a battery would pass the charge straight on into
 * the wire, and the Battery Box and the CESU already have discharge slots of their own.
 */
public interface BatteryFed {
}
