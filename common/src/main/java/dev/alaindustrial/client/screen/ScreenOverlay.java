package dev.alaindustrial.client.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;

/**
 * One overlay a {@link MachineScreen} carries — the upgrade panel, the statistics panel, the battery drawer
 * (MOD-716, CLI-2). The screen keeps them in one list and asks each the same questions in every hook, so a
 * new overlay is one class and one line in that list instead of a branch in eight methods of the base.
 *
 * <p><b>Two layers per overlay, and the order is the contract.</b> A {@code handle} is what opens an overlay
 * (the gear, the statistics tab, the drawer key); a {@code body} is what it opens (a panel, a drawer). The
 * screen draws every handle first and then every body, in list order, so the body drawn last sits on top;
 * it asks bodies for input in the REVERSE order, so the one on top answers first. Handles never overlap one
 * another (the gear is right of the frame, the statistics tab left of it, the drawer key inside it), so the
 * order they are asked in cannot change which one answers.
 *
 * <p>Every method has an empty default: an overlay implements only the hooks it takes part in.
 */
interface ScreenOverlay {

	/** Once a frame, before anything is drawn: settle what a press animation left pending. */
	default void beginFrame() {
	}

	/** Drawn before the machine frame, so the frame's edge covers it (the drawer sliding out from behind). */
	default void drawBehindFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	/** Drawn right after the machine frame, inside it (the drawer key). */
	default void drawOnFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	/** The handle, drawn above the slots and the screen's own controls, below every body. */
	default void drawHandle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	/** The body, drawn above every handle; a later overlay's body covers an earlier one's. */
	default void drawBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	/** The handle's tooltip; true when the point is on the handle (the tooltip is then this one alone). */
	default boolean handleTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		return false;
	}

	/**
	 * The open body's tooltips; true when the point is on the open body, which is modal over its footprint:
	 * nothing of the screen beneath may show a tooltip there.
	 */
	default boolean bodyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		return false;
	}

	/** Whether the open body covers the point, so a bar or gauge of the screen beneath does not hover there. */
	default boolean coversPoint(double mouseX, double mouseY) {
		return false;
	}

	/** A click on the handle; true when it was taken. */
	default boolean clickHandle(MouseButtonEvent event) {
		return false;
	}

	/** A click on the open body; true when taken — a modal body takes every click on its footprint. */
	default boolean clickBody(MouseButtonEvent event) {
		return false;
	}

	/** A drag of this overlay's body; true when it is the one being dragged. */
	default boolean drag(MouseButtonEvent event) {
		return false;
	}

	/** The end of a drag; true when this overlay was the one being dragged. */
	default boolean release(MouseButtonEvent event) {
		return false;
	}

	/** Whether a click at the point, outside the frame, still lands on this overlay (no dropped stack). */
	default boolean keepsClickInside(double mouseX, double mouseY) {
		return false;
	}

	/** The absolute rectangles recipe viewers must keep clear: the handle always, the body while open. */
	default void addExclusionAreas(List<Rect2i> areas) {
	}
}
