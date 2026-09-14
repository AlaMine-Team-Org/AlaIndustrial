package dev.alaindustrial.client.screen.reactor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One tab of the reactor controller's screen (MOD-617).
 *
 * <p>The screen owns the frame every tab shares — the panel, the tab strip, the status chip and the
 * countdown bar — and hands the rest of the panel to whichever page is selected. A page never draws the
 * frame and never decides which page is showing.
 */
public interface ReactorTabPage {

	/** The tab's name: the header while it is open, the tooltip over its tab. */
	Component title();

	/** The item drawn on the tab. */
	ItemStack icon();

	/**
	 * Colour of the marker on this page's tab while another page is open, or 0 for none. It is how a
	 * player reading one tab learns that another has something to say.
	 */
	int badgeColour();

	/** (Re)creates the page's widgets. Called from the screen's {@code init}, i.e. on every resize. */
	void init();

	/** Shows or hides the page's widgets; a hidden widget must not take clicks either. */
	void setShown(boolean shown);

	/** Once per client tick, whether or not the page is showing — a trend has to keep sampling. */
	void tick();

	/** Draws the page's contents inside the panel. */
	void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

	/** Sets a tooltip for whatever is under the mouse; returns whether it did. */
	boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

	/** A mouse release the screen passes on, so a drag begun on this page always ends. */
	void mouseReleased(MouseButtonEvent event);

	/** A left click on the panel, offered before the widgets see it; returns whether the page took it. */
	default boolean mouseClicked(MouseButtonEvent event) {
		return false;
	}
}
