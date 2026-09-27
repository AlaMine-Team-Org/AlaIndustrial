package dev.alaindustrial.network;

/**
 * The arithmetic behind the Network Analyzer's "moved" figure (MOD-665), kept free of Minecraft types
 * so the L1 suite can pin it.
 *
 * <p>Two defects lived here before it was pulled out:
 * <ul>
 *   <li><b>D10 — a stale number.</b> A network ticks only while awake; one that went to sleep (its
 *       generator removed, every consumer full) or that the per-tick budget skipped kept its last
 *       delivery forever, and the analyzer reported it as current. {@link #fresh} drops a reading older
 *       than {@link #FRESH_WINDOW_TICKS}.</li>
 *   <li><b>D11 — energy counted twice.</b> In Traverse mode a store between network A (where it
 *       charges) and network C (where it discharges into machines) put the same EU into the sum twice:
 *       once as delivered into the store in A, once as delivered to the machines in C.
 *       {@link #deliveredAcross} counts what reached machines, plus only the NET gain of the stores.</li>
 * </ul>
 */
public final class AnalyzerTotals {
	/**
	 * How old a network's last tick may be and still count as "now": one second. Wider than one tick on
	 * purpose — with more awake networks than {@code networksPerTick} a network is ticked round-robin, not
	 * every tick, and its reading is still the current one.
	 */
	public static final int FRESH_WINDOW_TICKS = 20;

	private AnalyzerTotals() {
	}

	/**
	 * {@code value} if it was recorded within {@link #FRESH_WINDOW_TICKS} before {@code now}, else 0.
	 * {@code recordedAt == Long.MIN_VALUE} means "never recorded".
	 */
	public static long fresh(long value, long recordedAt, long now) {
		if (recordedAt == Long.MIN_VALUE) {
			return 0L;
		}
		long age = now - recordedAt;
		return age >= 0 && age <= FRESH_WINDOW_TICKS ? value : 0L;
	}

	/**
	 * EU delivered across several networks without counting a store's pass-through twice.
	 *
	 * @param moved       per network: everything delivered on its last tick (machines and stores)
	 * @param toStorage   per network: the part of {@code moved} that went into stores
	 * @param fromStorage per network: what stores discharged into that network's line
	 * @return EU that reached machines, plus the stores' net charge when they gained more than they gave
	 */
	public static long deliveredAcross(long[] moved, long[] toStorage, long[] fromStorage) {
		if (moved.length != toStorage.length || moved.length != fromStorage.length) {
			throw new IllegalArgumentException("per-network arrays differ in length");
		}
		long toMachines = 0;
		long intoStores = 0;
		long outOfStores = 0;
		for (int i = 0; i < moved.length; i++) {
			toMachines += Math.max(0L, moved[i] - toStorage[i]);
			intoStores += toStorage[i];
			outOfStores += fromStorage[i];
		}
		return toMachines + Math.max(0L, intoStores - outOfStores);
	}
}
