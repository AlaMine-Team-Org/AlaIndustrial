package dev.alaindustrial.client.screen;

/**
 * A rectangle inside a container screen's frame, in GUI pixels relative to the frame's top-left corner — the
 * click area a recipe viewer opens a machine's recipes from (MOD-716, CLI-5).
 *
 * <p>Lives with the screens because each screen publishes its own ({@code PROGRESS_AREA}): the rectangle is
 * part of the screen's layout, next to the sprite it covers, so moving the arrow moves the click area in the
 * same file. {@code MachineRecipeViewerTargets} only names them.
 */
public record GuiRect(int x, int y, int width, int height) {
}
