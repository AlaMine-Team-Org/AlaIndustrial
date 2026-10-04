package dev.alaindustrial.client.screen;

import dev.alaindustrial.menu.MachineMenu;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * What a {@link ScreenOverlay} may ask of the {@link MachineScreen} it sits on (MOD-716): where the frame is,
 * its font, its menu, and the two container actions vanilla keeps protected. The screen hands each overlay a
 * private instance, so none of this becomes public API of the screen itself.
 */
interface OverlayHost {

	MachineMenu menu();

	/** The frame's left edge on screen ({@code leftPos}). */
	int left();

	/** The frame's top edge on screen ({@code topPos}). */
	int top();

	/** The whole screen's width, for keeping a dragged body on it. */
	int screenWidth();

	/** The whole screen's height. */
	int screenHeight();

	Font font();

	/** Whether this screen shows the statistics tab ({@link MachineScreen#hasStatsTab()}). */
	boolean hasStatsTab();

	/** A click on a slot, routed the way a click on the frame's own slots is. */
	void clickSlot(Slot slot, int button, ContainerInput input);

	/** The tooltip lines vanilla shows for a stack in a container. */
	List<Component> containerTooltip(ItemStack stack);

	/** Open or close the battery drawer, locally and on the server, with the button click sound. */
	void toggleBatteryDrawer();
}
