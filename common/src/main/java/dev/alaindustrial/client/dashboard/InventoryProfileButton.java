package dev.alaindustrial.client.dashboard;

import dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The compact "open profile" button placed at the feet of the 3D player model in the inventory
 * screen (MOD-133) — the second entry point beside the {@code P} hotkey. Loader-neutral: each loader
 * adds the button on its screen-init event ({@link #install}); {@link #tick} (driven from the shared
 * client-tick path) re-anchors it to {@code leftPos}/{@code topPos} every tick, because opening the
 * recipe book shifts those without re-firing screen init. The button self-clears when its screen
 * closes, so it never lingers onto an unrelated screen.
 */
public final class InventoryProfileButton {
	/** Small square button tucked into the top-right corner of the inventory GUI. */
	private static final int SIZE = 12;
	private static final int MARGIN = 6;

	private static Button active;
	private static Screen activeScreen;

	private InventoryProfileButton() {
	}

	/** Build the button for {@code screen}, anchor it, and remember it for per-tick repositioning. */
	public static Button install(Screen screen) {
		Button button = Button.builder(Component.literal("☰"), b -> open())
				.bounds(0, 0, SIZE, SIZE)
				.tooltip(Tooltip.create(Component.translatable("gui.alaindustrial.dashboard.title")))
				.build();
		reposition(button, screen);
		active = button;
		activeScreen = screen;
		return button;
	}

	/** Re-anchor the button each client tick while its screen is open; drop the reference otherwise. */
	public static void tick() {
		Screen current = Minecraft.getInstance().gui.screen();
		if (active != null && current == activeScreen && current != null) {
			reposition(active, current);
		} else {
			active = null;
			activeScreen = null;
		}
	}

	private static void reposition(Button button, Screen screen) {
		if (screen instanceof AbstractContainerScreenAccessor accessor) {
			// Top-right corner of the GUI panel, just inside its border.
			button.setX(accessor.alaindustrial$getLeftPos() + accessor.alaindustrial$getImageWidth() - SIZE - MARGIN);
			button.setY(accessor.alaindustrial$getTopPos() + MARGIN);
		}
	}

	private static void open() {
		Minecraft.getInstance().gui.setScreen(new DashboardScreen());
	}
}
