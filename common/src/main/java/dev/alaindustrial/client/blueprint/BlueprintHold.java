package dev.alaindustrial.client.blueprint;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.compat.client.Keyboard;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Hold-to-open for the blueprint key (MOD-797): a sheet opens only after the key has been held for
 * {@link #HOLD_MS} over the same item, so a stray press does nothing. Inside a screen the item is the
 * one whose tooltip is showing — inventory, creative tab, JEI and REI alike, since every tooltip passes
 * through {@link BlueprintTooltip}; in the world it is the item in the main hand. The tooltip draws
 * {@link #progress} as a filling bar while the key is held.
 *
 * <p>Ticked from {@code ModKeyMappings.handleInput}, which both loaders call every client tick, and it
 * polls the key itself: a {@code KeyMapping} is not updated while a screen is open.
 */
public final class BlueprintHold {

	/** How long the key must be held, in milliseconds. */
	public static final long HOLD_MS = 1200;

	private static @Nullable BlueprintDef target;
	private static long startedAt = -1;
	/** Set after a sheet opened, until the key is let go — holding on must not open it again. */
	private static boolean waitForRelease;

	private BlueprintHold() {
	}

	/** One client tick: start, continue, finish or drop the hold. */
	public static void tick() {
		if (!keyDown()) {
			reset();
			waitForRelease = false;
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.gui.screen();
		Optional<BlueprintDef> aimed = aimedAt(mc, screen);
		if (waitForRelease || aimed.isEmpty() || (target != null && target != aimed.get())) {
			reset();
			return;
		}
		if (target == null) {
			target = aimed.get();
			startedAt = System.currentTimeMillis();
		}
		if (System.currentTimeMillis() - startedAt >= HOLD_MS) {
			BlueprintDef opening = target;
			reset();
			waitForRelease = true;
			BlueprintInput.open(opening, screen);
		}
	}

	/** How far the hold on {@code def} has run, 0..1; 0 when that item is not being held for. */
	public static float progress(BlueprintDef def) {
		if (target != def || startedAt < 0) {
			return 0f;
		}
		return Math.min(1f, (System.currentTimeMillis() - startedAt) / (float) HOLD_MS);
	}

	private static Optional<BlueprintDef> aimedAt(Minecraft mc, @Nullable Screen screen) {
		if (screen == null) {
			return mc.player == null ? Optional.empty() : BlueprintTooltip.forStack(mc.player.getMainHandItem());
		}
		if (screen instanceof BlueprintScreen || screen.getFocused() instanceof EditBox) {
			return Optional.empty();
		}
		return BlueprintTooltip.hoveredNow();
	}

	/** The bound key, polled directly; a mouse binding falls back to the mapping's own state. */
	private static boolean keyDown() {
		InputConstants.Key key = InputConstants.getKey(ModKeyMappings.OPEN_BLUEPRINT.saveString());
		if (ModKeyMappings.OPEN_BLUEPRINT.isUnbound()) {
			return false;
		}
		if (key.getType() == Keyboard.keyMappingType()) {
			return Keyboard.isDown(key.getValue());
		}
		return ModKeyMappings.OPEN_BLUEPRINT.isDown();
	}

	private static void reset() {
		target = null;
		startedAt = -1;
	}
}
