package dev.alaindustrial.client.hud;

/**
 * Turns a jittery charge fraction into a steady displayed percentage for a HUD readout (MOD-079). A
 * worn Energy Pack tops the held drill up once a second while the drill drains a little per block, so
 * the raw charge fraction rides at the very top and ripples by a percent or two — which makes a plain
 * {@code round(fraction * 100)} flip ("99 %, 100 %, 99 %…") every block. Two stages remove that:
 *
 * <ol>
 * <li><b>Exponential smoothing</b> of the fraction (time-based, ~0.5 s constant) — eases motion and
 * damps the ripple, with a large-jump snap so a real change (item swap, big drain) shows at once.</li>
 * <li><b>Integer hysteresis</b> on the displayed percent — the shown number only changes once the
 * smoothed value has moved a full {@link #PERCENT_DEADBAND} away from it. Sitting at 100 %, a dip to
 * 99.3 % is inside the deadband and is ignored, so the number holds steady; only a genuine drop past
 * the band (mining faster than the pack refills, or no pack at all) moves it. This is what finally
 * kills the boundary flicker that smoothing alone leaves when the mean parks on a rounding edge.</li>
 * </ol>
 *
 * <p>Each HUD owns one instance and calls {@link #displayPercent} every frame; the bar is drawn from
 * the same returned integer so the text and the bar never disagree. Client-only, single-threaded.
 */
final class HudSmoother {

	/** Time constant in ticks (1 tick = 50 ms); ~0.5 s eases the top-up ripple. */
	private static final float TAU_TICKS = 10.0f;
	/** A fraction change larger than this is a real event (item swap, big drain) — snap, don't ease. */
	private static final float SNAP_THRESHOLD = 0.25f;
	/** The shown percent holds until the smoothed value moves at least this many points away from it.
	 * Wider than the ~1-point top-up ripple, so a pack-fed drill reads a rock-steady 100 %. */
	private static final float PERCENT_DEADBAND = 1.5f;

	/** Smoothed fraction (0..1); negative means "not initialised" so the first frame snaps. */
	private float shown = -1.0f;
	/** Last displayed integer percent; negative means "not initialised". */
	private int shownPercent = -1;

	/**
	 * Ease toward {@code target} (0..1) by the frame's real time, then return the displayed integer
	 * percent with hysteresis applied. Framerate-independent — the step comes from {@code dtTicks}.
	 */
	int displayPercent(float target, float dtTicks) {
		float clamped = Math.max(0.0f, Math.min(1.0f, target));
		if (shown < 0.0f || Math.abs(clamped - shown) > SNAP_THRESHOLD) {
			shown = clamped;
		} else {
			float k = 1.0f - (float) Math.exp(-Math.max(0.0f, dtTicks) / TAU_TICKS);
			shown += (clamped - shown) * k;
		}
		// Empty and full must read exactly. Exponential easing only *approaches* its target, so a
		// buffer that has truly hit 0 (or capacity) leaves the smoothed value a hair off the edge;
		// combined with the 1.5-point deadband the shown number then parks on 1 % (empty) or 99 %
		// (full) and never reaches the boundary. A genuine 0 / capacity charge is not a ripple to
		// damp — it is the one reading that has to be precise, so snap it past the hysteresis.
		if (clamped <= 0.0f) {
			shown = 0.0f;
			shownPercent = 0;
			return 0;
		}
		if (clamped >= 1.0f) {
			shown = 1.0f;
			shownPercent = 100;
			return 100;
		}
		float pct = shown * 100.0f;
		if (shownPercent < 0 || Math.abs(pct - shownPercent) >= PERCENT_DEADBAND) {
			shownPercent = Math.round(pct);
		}
		return shownPercent;
	}
}
