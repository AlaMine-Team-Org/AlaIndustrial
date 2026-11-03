package dev.alaindustrial.core.structure;

/**
 * A block that stands in a reactor shell as casing (MOD-941). The room scan asks for this interface instead of
 * naming the block: the core is the lowest layer and names no block or registry (ADR-039), so a new shell block
 * that counts as casing implements this rather than adding a line to {@link RoomValidator#kindOf}.
 */
public interface ReactorCasingPart {
}
