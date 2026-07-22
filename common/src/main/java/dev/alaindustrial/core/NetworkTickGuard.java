package dev.alaindustrial.core;

import dev.alaindustrial.Industrialization;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Isolates a single network's tick from the server tick loop (MOD-186). The energy and item-pipe
 * networks resolve neighbouring blocks — including blocks from <em>other mods</em> — as transfer
 * endpoints and call their {@code insert}/{@code extract}/{@code getAmount} capabilities every tick. If a
 * foreign implementation throws (a bug, a beta mod, a chunk-unload edge, malformed NBT), the exception
 * would otherwise unwind through {@code NetworkManager.tickAll} into the server tick handler — where there
 * is no {@code try/catch} — and crash the whole server with a "Exception ticking world" report whose stack
 * top is {@code dev.alaindustrial}. The player reads that as "your mod is incompatible with my other mods".
 *
 * <p>This guard catches such a throw, keeps the server ticking (the offending network is skipped for that
 * tick and retried next tick), and logs the <b>full stack once per distinct failure</b> so our own bugs
 * stay visible rather than silently swallowed — the same defensive move MOD-176 applied to the worldgen
 * injection, here for the runtime tick path. Logging is de-duplicated (and the dedup set bounded) so a
 * network that throws every tick cannot flood the log.
 */
public final class NetworkTickGuard {
	/** Distinct failure signatures already logged, so a per-tick thrower is logged once, not every tick. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
	/** Hard cap on the dedup set so a pathological stream of unique messages cannot grow it without bound. */
	private static final int WARNED_CAP = 256;

	private NetworkTickGuard() {
	}

	/**
	 * Run one network's tick body, isolating any throw so it cannot crash the server tick.
	 *
	 * @param kind short label for the log line ("energy" / "item")
	 * @param body the network's tick body
	 */
	public static void runIsolated(String kind, Runnable body) {
		try {
			body.run();
		} catch (Exception e) {
			report(kind, e);
		}
	}

	/**
	 * Run one network's tick body that reports EU moved, isolating any throw. Returns the body's value, or
	 * {@code 0} if it threw (nothing moved that tick).
	 */
	public static long tickIsolated(String kind, LongSupplier body) {
		try {
			return body.getAsLong();
		} catch (Exception e) {
			report(kind, e);
			return 0L;
		}
	}

	private static void report(String kind, Exception e) {
		// Key the dedup on the throwable class + its top stack frame (class#method), NOT getMessage(): a
		// foreign message often embeds a block position/coords/entity id, so message-keyed dedup would
		// treat the same recurring fault as a new signature every tick and fill the cap. The throw SITE is
		// stable, so this collapses genuine repeats and keeps distinct real faults distinct.
		String signature = kind + '|' + e.getClass().getName() + '|' + topFrame(e);
		// add() is atomic; the size guard keeps the set bounded without locking (a small race that logs a
		// couple extra lines at the cap boundary is harmless).
		if (WARNED.size() < WARNED_CAP && WARNED.add(signature)) {
			Industrialization.LOGGER.warn(
					"[alaindustrial] A {} network tick threw and was isolated so the server keeps running "
							+ "(the network is skipped this tick and retried next). This is usually a neighbouring "
							+ "mod's capability throwing; if the stack below is ours, please report it. "
							+ "Further identical failures are not logged.",
					kind, e);
		}
	}

	/** The throwable's originating frame as {@code Class#method}, or {@code "?"} if unavailable — a stable
	 * dedup key independent of any coordinates the message may carry. */
	private static String topFrame(Throwable e) {
		StackTraceElement[] frames = e.getStackTrace();
		if (frames == null || frames.length == 0) {
			return "?";
		}
		StackTraceElement f = frames[0];
		return f.getClassName() + '#' + f.getMethodName();
	}
}
