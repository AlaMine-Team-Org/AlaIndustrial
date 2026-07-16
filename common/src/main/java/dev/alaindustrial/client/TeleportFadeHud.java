package dev.alaindustrial.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The screen going dark as a jump lands (MOD-106) — "you closed your eyes", then you are somewhere
 * else.
 *
 * <p>Loader-neutral like the other overlays here: both loaders hand {@code render} exactly
 * {@code (GuiGraphicsExtractor, DeltaTracker)} (Fabric {@code HudElementRegistry}, NeoForge
 * {@code RegisterGuiLayersEvent}).
 *
 * <p><b>Why this cannot strand a player behind a black screen.</b> It does not track a warmup — it
 * only knows the last darkness level the server sent and how long ago that was. Stop feeding it and
 * it clears itself. So every way a warmup can end — cancelled by a step, by damage, by death, by
 * pulling the network cable — clears the screen without needing anything to be delivered, which is
 * exactly the failure the task called the main risk. The server's explicit zero at the end of a jump
 * only makes the clearing immediate.
 *
 * <p>Darkening tracks the server tick-for-tick, but clearing is a deliberate snap ({@link #FADE_OUT}
 * ≈ five ticks): the jump should feel like eyes opening, not like a slow dissolve.
 */
public final class TeleportFadeHud {

	/** Darkest the screen goes — not pure black, so the player never doubts the game is alive. */
	private static final float MAX_DARKNESS = 0.94f;
	/** Fraction of darkness shed per tick once the levels stop. */
	private static final float FADE_OUT = 0.2f;
	/**
	 * How long a level stays believed. Levels arrive every server tick (50 ms); three ticks of slack
	 * absorbs ordinary jitter while still clearing promptly on a silent cancel.
	 */
	private static final long STALE_MS = 150;

	/** Last level the server sent, and when it landed. Written from the netty/receive path. */
	private static volatile float target;
	private static volatile long stampMs;

	/** What is actually on screen; render-thread only. */
	private static float shown;

	private TeleportFadeHud() {
	}

	/** A darkness level from the server. Called on the client main thread by each loader's receiver. */
	public static void receive(float strength) {
		target = Math.max(0.0f, Math.min(1.0f, strength));
		stampMs = System.currentTimeMillis();
	}

	/** Drop everything — used when leaving a world, so a fade cannot survive into the next one. */
	public static void reset() {
		target = 0.0f;
		stampMs = 0L;
		shown = 0.0f;
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			shown = 0.0f;
			return;
		}
		// A level nobody refreshed is a warmup that ended, however it ended.
		float aim = System.currentTimeMillis() - stampMs > STALE_MS ? 0.0f : target;

		if (aim >= shown) {
			shown = aim; // the server owns the ramp up; just follow it
		} else {
			shown = Math.max(aim, shown - FADE_OUT * delta.getRealtimeDeltaTicks());
		}
		if (shown <= 0.0f) {
			return;
		}

		int alpha = (int) (shown * MAX_DARKNESS * 255.0f);
		if (alpha <= 0) {
			return;
		}
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
	}
}
