package dev.alaindustrial.client.tablet;

/**
 * How the tablet's casing reacts to what it shows (MOD-946): the reactor running hot, a lost link, a low battery.
 *
 * <p>This class only turns states and a clock into blink and pulse values; the states themselves come from where
 * the panel already reads them — the link from the tablet's menu (MOD-941), the reactor's state from the console's own
 * verdict, the charge from the stack in hand — so the casing can never disagree with the panel inside it. Plain Java,
 * tested without Minecraft.
 */
public final class ReactorTabletSignal {

	/** Below this share of a full charge the battery mark turns red and blinks. */
	public static final float LOW_CHARGE = 0.2f;

	private ReactorTabletSignal() {
	}

	/** How loud the reactor is, as the console's verdict tells it. */
	public enum Alarm {
		/** Nothing to warn about, or nothing heard (no link). */
		NONE,
		/** Running hot, steam blocked, coolant short — the console's amber. */
		WARN,
		/** Meltdown, blast countdown, a bare pile at its limit — the console's red. */
		ALARM
	}

	/**
	 * Slowest a blinking mark may change, in ms per half-cycle: one flash a second. The owner's playtest found the
	 * first cut (a 120 ms lamp, 60 ms random dropouts) painful to look at; this keeps every mark well under the three
	 * flashes a second of the photosensitivity guidance (WCAG 2.3.1).
	 */
	static final long BLINK_HALF_MS = 500L;

	/** One breath of the alarm's red wash, in ms. */
	static final long ALARM_PULSE_MS = 1_250L;

	/** One breath of the dimming while the tablet hears nothing, in ms. */
	static final long LOST_PULSE_MS = 2_500L;

	/** How dark the lost-link dimming gets at its deepest, 0 … 1. */
	static final float LOST_DIM = 0.35f;

	/**
	 * Strength of the red or amber wash over the screen, 0 … 1. Both breathe smoothly — an alarm a little faster and
	 * never fully off, a warning slowly; neither jumps from frame to frame.
	 */
	public static float wash(Alarm alarm, long ms) {
		return switch (alarm) {
			case NONE -> 0.0f;
			case WARN -> 0.5f + 0.5f * (float) Math.sin(ms / 1000.0 * Math.PI * 1.2);
			case ALARM -> 0.55f + 0.25f * wave(ms, ALARM_PULSE_MS);
		};
	}

	/**
	 * How much of the screen's light is lost this frame, 0 … 1: a lost link dims and recovers slowly, a linked tablet
	 * holds steady — the alarm already has its wash.
	 */
	public static float dropout(boolean linked, Alarm alarm, long ms) {
		if (!linked) {
			return LOST_DIM * 0.5f * (1.0f + wave(ms, LOST_PULSE_MS));
		}
		return 0.0f;
	}

	/** Whether the link lamp is lit: steady while linked and calm, blinking once a second otherwise. */
	public static boolean lampLit(boolean linked, Alarm alarm, long ms) {
		if (!linked || alarm != Alarm.NONE) {
			return (ms / BLINK_HALF_MS) % 2L == 0L;
		}
		return true;
	}

	/** Whether the charge is low enough to warn about. */
	public static boolean isLow(float charge) {
		return charge < LOW_CHARGE;
	}

	/** Whether the battery mark is drawn this frame: always, except the off half of a low battery's blink. */
	public static boolean batteryShown(float charge, long ms) {
		return !isLow(charge) || (ms / BLINK_HALF_MS) % 2L == 0L;
	}

	/** How many of {@code segments} battery segments are lit; any charge at all lights one. */
	public static int litSegments(float charge, int segments) {
		if (charge <= 0.0f) {
			return 0;
		}
		return Math.clamp((int) Math.ceil(charge * segments), 1, segments);
	}

	/** A smooth wave, -1 … 1, of {@code periodMs}. */
	static float wave(long ms, long periodMs) {
		return (float) Math.sin(2.0 * Math.PI * Math.floorMod(ms, periodMs) / periodMs);
	}
}
