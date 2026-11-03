package dev.alaindustrial.client.tablet;

/**
 * The one clock of the reactor tablet's animation (MOD-946): the hands lift the tablet, then its screen unfolds;
 * closing plays the same back.
 *
 * <p><b>One value, two readers.</b> Progress runs over {@link #TOTAL_MS}: the first {@link #RAISE_MS} are the hands
 * ({@link #raise}), read by the first-person hand mixin; then the screen unfolds ({@link #unfold}) and powers on
 * ({@link #powerOn}), read by the tablet's screen. Opening and closing only change the direction, so a close in the
 * middle of an opening turns back from where the animation is, never jumps.
 *
 * <p><b>It outlives the screen.</b> The screen leaves as soon as it has folded back into the tablet, and the hands
 * keep lowering on this clock with no screen open, so the player gets control back while the arms finish. The clock
 * is real time in milliseconds handed in by the caller, so this class knows nothing of Minecraft and is tested as
 * plain Java.
 */
public final class ReactorTabletTimeline {

	/** How long the hands take to bring the tablet up from below the view. */
	public static final long RAISE_MS = 320L;
	/** How long the screen takes to unfold out of the raised tablet. */
	public static final long UNFOLD_MS = 280L;
	/** How long the unfolded screen takes to power on, like an old monitor warming up. */
	public static final long POWER_MS = 260L;
	/** From here on the screen is open and takes input; the power-on plays over a working screen. */
	public static final long OPEN_MS = RAISE_MS + UNFOLD_MS;
	public static final long TOTAL_MS = OPEN_MS + POWER_MS;

	/** The client's tablet: the screen and the hand mixin share this one. */
	public static final ReactorTabletTimeline CLIENT = new ReactorTabletTimeline();

	private long progressMs;
	private long lastMs;
	/** +1 opening, −1 closing, 0 at rest. */
	private int direction;

	/** Starts (or turns back to) opening. */
	public void open(long nowMs) {
		advance(nowMs);
		direction = 1;
	}

	/** Starts (or turns back to) closing. */
	public void close(long nowMs) {
		advance(nowMs);
		direction = -1;
	}

	/**
	 * Holds the animation at {@code progressMs} into it: open (and taking input) at {@link #TOTAL_MS}, frozen anywhere
	 * short of it. For the L3 frames, which must show one exact moment whatever the test clock does.
	 */
	public void pin(long nowMs, long progressMs) {
		advance(nowMs);
		this.progressMs = Math.clamp(progressMs, 0L, TOTAL_MS);
		direction = this.progressMs == TOTAL_MS ? 1 : 0;
	}

	/** Whether the animation is opening or open — false while closing or at rest. */
	public boolean isOpening() {
		return direction > 0;
	}

	/** How far the hands have lifted the tablet, eased, 0 (out of view) … 1 (in front of the face). */
	public float raise(long nowMs) {
		advance(nowMs);
		return easeInOut(Math.min(1.0f, progressMs / (float) RAISE_MS));
	}

	/** How far the screen has unfolded, eased, 0 (folded into the tablet) … 1 (open). */
	public float unfold(long nowMs) {
		advance(nowMs);
		return easeOut(Math.max(0.0f, (progressMs - RAISE_MS) / (float) UNFOLD_MS));
	}

	/** How far the unfolded screen has powered on, 0 (dark) … 1 (on); played back as a power-off on closing. */
	public float powerOn(long nowMs) {
		advance(nowMs);
		return Math.clamp((progressMs - OPEN_MS) / (float) POWER_MS, 0.0f, 1.0f);
	}

	/** Whether the screen is open all the way — the only state in which it takes input. */
	public boolean isOpen(long nowMs) {
		advance(nowMs);
		return direction > 0 && progressMs >= OPEN_MS;
	}

	/**
	 * Whether a closing screen has folded back into the tablet and may leave. Also true once the hands are down
	 * altogether, so a screen that missed the moment (a long frame) still leaves.
	 */
	public boolean isFolded(long nowMs) {
		advance(nowMs);
		return direction <= 0 && progressMs <= RAISE_MS;
	}

	private void advance(long nowMs) {
		long elapsed = Math.max(0L, nowMs - lastMs);
		lastMs = nowMs;
		if (direction == 0) {
			return;
		}
		progressMs = Math.clamp(progressMs + direction * elapsed, 0L, TOTAL_MS);
		if (direction < 0 && progressMs == 0L) {
			direction = 0;
		}
	}

	/** Smoothstep: the arms start and stop gently. */
	static float easeInOut(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	/** Cubic ease-out: the screen leaps out of the tablet and settles. */
	static float easeOut(float t) {
		float u = 1.0f - Math.min(1.0f, t);
		return 1.0f - u * u * u;
	}
}
