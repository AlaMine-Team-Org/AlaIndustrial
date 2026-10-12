package dev.alaindustrial.core.world;

/**
 * A block whose screen has the "show my reach" button (MOD-278, MOD-802): the mob repeller's field and the
 * vacuum collector's suction cube. The client keeps a shown zone only while its block is still one of these
 * ({@code ZonePreviewRenderer}); a block that is broken or replaced takes its zone with it.
 */
public interface ZonePreviewBlock {
}
