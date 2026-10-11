package dev.alaindustrial.client.blueprint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Opens a blueprint sheet (MOD-797) — from the held key ({@link BlueprintHold}) or the guide-book button. */
public final class BlueprintInput {

	private BlueprintInput() {
	}

	/** Open the sheet of {@code def}; closing it returns to {@code parent}. */
	public static void open(BlueprintDef def, @Nullable Screen parent) {
		Minecraft.getInstance().gui.setScreen(new BlueprintScreen(def, parent));
	}
}
