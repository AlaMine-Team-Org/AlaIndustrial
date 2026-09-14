package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.block.entity.ReactorIdleReason;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import java.util.List;
import java.util.Locale;

/**
 * What the reactor console says about the reactor it shows (MOD-618): the verdict on the status chip,
 * the advice under the gauges, the rod-depth grid and the heat trend.
 *
 * <p><b>Minecraft-free on purpose.</b> Everything here is a judgement about a state — which trouble
 * outranks which, what a player should do about it — and a judgement is exactly what belongs under an L1
 * test rather than in a screenshot nobody re-reads. The screen turns these answers into pixels; it does
 * not decide anything itself.
 *
 * <p>Every threshold the reactor itself acts on arrives in the {@link Readout} from the server.
 * {@code Config} is not synced to the client, so a console that read its own copy would draw the lines
 * where the local file puts them rather than where this server's reactor melts.
 */
public final class ReactorConsole {

	private static final String KEY = "gui.alaindustrial.reactor_controller.";

	/**
	 * Steam share from which the exhaust counts as blocked: the columns hold water, but it has nowhere to
	 * boil to. The same line the old coolant bar turned amber on, so a player who learned that colour is
	 * not re-taught anything. One number with the «Coolant» tab's per-stack mark (MOD-621).
	 */
	public static final int STEAM_BLOCKED_PERCENT = dev.alaindustrial.core.structure.ReactorZone.STEAM_BLOCKED_PERCENT;

	/**
	 * Instability share the console calls the limit of a bare pile. Three racks settle just under 90, and
	 * a pile that close to the top is one rack from running away.
	 */
	public static final int INSTABILITY_LIMIT_PERCENT = 80;

	/**
	 * Share of the reaction's heat the water must carry before the console stops calling the loop short
	 * (MOD-623). Below it the heat the water leaves behind is climbing — while the rods work nothing else
	 * sheds it — and a notch under a hundred keeps a supply that arrives a millibucket late on some ticks from
	 * flipping the chip back and forth.
	 */
	public static final int SHORT_SHARE_PERCENT = 95;

	/** The throttle's grid. The server accepts any whole percent; the console offers these. */
	public static final int DEPTH_STEP = 5;

	private ReactorConsole() {
	}

	/**
	 * One snapshot of the controller's sync channels, in the units the channels carry.
	 *
	 * @param blast           share of the accident countdown left, 0 when none is running
	 * @param coolantShare    share of the reaction's heat the water carried; 100 while nothing reacts
	 * @param warnPercent     heat from which the reactor reports running hot and sounds the siren
	 * @param meltdownPercent heat from which a sealed room melts its own contents
	 */
	public record Readout(ReactorRoomStatus status, ReactorIdleReason idle, int rods, int output, int heat,
			int water, int steam, int blast, boolean meltdown, int instability, int coolantShare,
			int warnPercent, int meltdownPercent) {

		/** Racks found with no room around them — the rule {@code ReactorControllerMenu#isBare} uses. */
		public boolean bare() {
			return status != ReactorRoomStatus.FORMED && rods > 0;
		}

		public boolean formed() {
			return status == ReactorRoomStatus.FORMED;
		}

		/** A sealed room whose steam has nowhere to go. A bare reactor has no loop to block. */
		public boolean steamBlocked() {
			return formed() && steam >= STEAM_BLOCKED_PERCENT;
		}
	}

	/** How loudly a verdict or a piece of advice should read. */
	public enum Tone {
		GOOD, WARN, ALARM, IDLE
	}

	/** The one-word state on the chip at the top of every tab. */
	public enum Verdict {
		RUNNING(Tone.GOOD, "chip.running"),
		WARNING(Tone.WARN, "chip.warning"),
		IDLE(Tone.IDLE, "chip.idle"),
		BUILDING(Tone.WARN, "chip.building"),
		BARE(Tone.WARN, "mode.bare"),
		MELTDOWN(Tone.ALARM, "chip.meltdown"),
		BLAST(Tone.ALARM, "status.blast");

		private final Tone tone;
		private final String suffix;

		Verdict(Tone tone, String suffix) {
			this.tone = tone;
			this.suffix = suffix;
		}

		public Tone tone() {
			return tone;
		}

		public String translationKey() {
			return KEY + suffix;
		}
	}

	/**
	 * What to tell the player.
	 *
	 * @param bodyArg  the percentage the body quotes, or -1 when it quotes none
	 * @param stepKeys numbered actions under the body; empty when the body is the whole answer
	 */
	public record Advice(Tone tone, String titleKey, String bodyKey, int bodyArg, List<String> stepKeys) {

		public Advice {
			stepKeys = List.copyOf(stepKeys);
		}
	}

	/**
	 * The verdict, worst first.
	 *
	 * <p>The order is the whole design. A countdown outranks everything, including a meltdown, because it
	 * is the one state with a deadline. A bare pile outranks "building" although both report an unsealed
	 * shell — the pile is burning, the shell is not a fault the player is fixing. And a reactor the water
	 * cannot keep up with warns before it can be called idle: while the rods work nothing else cools it, so
	 * such a core is on its way to an accident whether or not it sells power — a full buffer used to show it
	 * as grey "idle" (audit, MOD-623).
	 */
	public static Verdict verdict(Readout r) {
		if (r.blast() > 0) {
			return Verdict.BLAST;
		}
		if (r.meltdown()) {
			return Verdict.MELTDOWN;
		}
		if (r.bare()) {
			return Verdict.BARE;
		}
		if (!r.formed()) {
			return Verdict.BUILDING;
		}
		if (r.heat() >= r.warnPercent() || r.steamBlocked() || r.coolantShare() < SHORT_SHARE_PERCENT) {
			return Verdict.WARNING;
		}
		if (r.output() <= 0) {
			return Verdict.IDLE;
		}
		return Verdict.RUNNING;
	}

	/** The advice that goes with {@link #verdict}. Every verdict has one; a healthy reactor gets one too. */
	public static Advice advice(Readout r) {
		return switch (verdict(r)) {
			case BLAST -> blast(r);
			case MELTDOWN -> new Advice(Tone.ALARM, KEY + "status.meltdown", KEY + "advice.meltdown.body",
					r.meltdownPercent(), List.of());
			case BARE -> r.instability() >= INSTABILITY_LIMIT_PERCENT
					? plain(Tone.ALARM, "bare_limit")
					: plain(Tone.WARN, "bare");
			case BUILDING -> new Advice(Tone.WARN, r.status().translationKey(), fixKey(r.status()), -1, List.of());
			case IDLE -> idle(r.idle());
			case WARNING -> warning(r);
			case RUNNING -> plain(Tone.GOOD, "normal");
		};
	}

	/**
	 * The ways out of a countdown: water and the lever for a room, the lever and a rack taken away for a bare
	 * pile, which has no loop to feed.
	 *
	 * <p>Breaking a wall used to be a third way out of a room, and it is not one. It drops the reactor into
	 * bare mode, where a room's worth of racks — fourteen rods or more — runs away on the instability scale
	 * and starts a fresh countdown with no loop left to stop it (audit, MOD-623).
	 */
	private static Advice blast(Readout r) {
		List<String> steps = r.bare()
				? List.of(KEY + "advice.blast.step.lever", KEY + "advice.blast.step.rack")
				: List.of(KEY + "advice.blast.step.water", KEY + "advice.blast.step.lever");
		return new Advice(Tone.ALARM, KEY + "advice.blast.title", KEY + "advice.blast.body", -1, steps);
	}

	private static Advice warning(Readout r) {
		if (r.steamBlocked()) {
			return plain(Tone.WARN, "steam");
		}
		// Dry at any temperature: nothing else cools a working reactor, so this one is heading for the top.
		String which = r.water() == 0 ? "dry" : r.heat() >= r.warnPercent() ? "hot" : null;
		if (which != null) {
			return new Advice(Tone.WARN, KEY + "advice." + which + ".title", KEY + "advice." + which + ".body",
					r.meltdownPercent(), List.of());
		}
		// Under the line with some water: the loop is short, and the share says by how much.
		return new Advice(Tone.WARN, KEY + "advice.short.title", KEY + "advice.short.body", r.coolantShare(),
				List.of());
	}

	/**
	 * A sealed reactor producing nothing: each cause has its own fix. {@code RUNNING} here means the
	 * reaction is on but too weak to deliver a whole EU per tick; {@code NOT_SEALED} cannot occur in a sealed
	 * room and falls back to the reason's own text rather than to silence.
	 */
	private static Advice idle(ReactorIdleReason reason) {
		String body = switch (reason) {
			case NO_FUEL, RODS_WITHDRAWN, NO_SIGNAL, BUFFER_FULL, RUNNING -> KEY + "advice.idle." + lower(reason.name());
			case NOT_SEALED -> reason.translationKey();
		};
		return new Advice(Tone.IDLE, KEY + "advice.idle.title", body, -1, List.of());
	}

	private static Advice plain(Tone tone, String name) {
		return new Advice(tone, KEY + "advice." + name + ".title", KEY + "advice." + name + ".body", -1, List.of());
	}

	/** What to do about a shell that does not pass its scan. A sealed room has nothing to fix. */
	public static String fixKey(ReactorRoomStatus status) {
		return status == ReactorRoomStatus.FORMED
				? KEY + "status.formed"
				: KEY + "fix." + lower(status.name());
	}

	/** The grid depth nearest a slider position in 0…1. */
	public static int snapDepth(double sliderValue) {
		double clamped = Math.max(0.0, Math.min(1.0, sliderValue));
		return (int) Math.round(clamped * 100.0 / DEPTH_STEP) * DEPTH_STEP;
	}

	/** One grid step up ({@code direction > 0}) or down from {@code depth}, kept on the scale. */
	public static int stepDepth(int depth, int direction) {
		int onGrid = snapDepth(depth / 100.0);
		int next = onGrid + Integer.signum(direction) * DEPTH_STEP;
		return Math.max(0, Math.min(100, next));
	}

	private static String lower(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * Whether the heat is climbing, falling or holding, from the last two seconds the screen has seen.
	 *
	 * <p>Worked out on the client, from the channel it already receives — the server has no reason to spend
	 * a channel on a derivative of one it sends. The window starts empty each time the screen opens, so the
	 * arrow appears two seconds in rather than guessing from nothing.
	 */
	public static final class HeatTrend {

		/** Samples in the window: two seconds at twenty ticks. */
		public static final int WINDOW = 40;

		/**
		 * The smallest change across the window that counts as moving. A reactor sitting on its equilibrium
		 * wobbles by a percent either side, and an arrow that flickered with it would stop meaning anything.
		 */
		public static final int DEADBAND = 2;

		private final int[] samples = new int[WINDOW];
		private int size;
		private int next;

		public void sample(int heat) {
			samples[next] = heat;
			next = (next + 1) % WINDOW;
			if (size < WINDOW) {
				size++;
			}
		}

		/** +1 rising, -1 falling, 0 holding — or 0 until a full window has been seen. */
		public int direction() {
			if (size < WINDOW) {
				return 0;
			}
			// With the ring full, `next` is the slot the next sample overwrites: the oldest one.
			int delta = samples[(next + WINDOW - 1) % WINDOW] - samples[next];
			if (delta >= DEADBAND) {
				return 1;
			}
			return delta <= -DEADBAND ? -1 : 0;
		}
	}
}
