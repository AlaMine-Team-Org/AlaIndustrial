package dev.alaindustrial.block.entity;

/**
 * Marker for a machine that an overclocker chip (MOD-392) actually does something to.
 *
 * <p><b>Why a marker and not a boolean override.</b> The upgrade panel's overclock arm has to refuse
 * the chip on machines that cannot use it — a generator took one happily and then ignored it, which
 * reads as a broken chip rather than a wrong slot. Refusing means deciding it inside
 * {@code Slot#mayPlace}, and that runs on the CLIENT too, where the menu is backed by a dummy
 * container with no block entity at all. So the answer must be derivable from the block, on both
 * sides, without an instance.
 *
 * <p>A hand-written list of overclockable blocks would do it and would drift from the block entities
 * the first time someone adds a machine. This interface avoids the second list entirely: the block
 * entity's own type is the single truth, and
 * {@link dev.alaindustrial.registry.ContentManifest#isOverclockable} reads it back through the
 * manifest's existing block→BE-class mapping. Implement this and both the effect and the slot follow;
 * there is nothing else to remember.
 *
 * <p>Deliberately empty: it says what the machine IS, and {@link MachineBlockEntity} already carries
 * every method that acts on it ({@code overclockerCap}, {@code effectiveDuration},
 * {@code effectiveEuPerTick}).
 */
public interface Overclockable {
}
