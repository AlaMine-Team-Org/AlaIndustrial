package dev.alaindustrial.network;

/**
 * How many positions of each kind the Network Analyzer payload may carry (MOD-665, D4). Kept free of
 * Minecraft types so the L1 suite can pin it.
 *
 * <p>The payload used to ship every position of every traversed network. A position is 8 bytes and
 * vanilla refuses a custom payload over {@code ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE} (1 MiB),
 * so a large enough base in Traverse mode would drop the connection — on Fabric, which has no packet
 * splitting for it; NeoForge splits. Rather than rely on one loader's splitter, the payload is capped
 * here for both, and flagged as truncated so the player is told the picture is partial.
 */
public final class PayloadBudget {
	/**
	 * Most positions one payload carries: 16 384 × 8 bytes = 128 KiB of positions plus one byte of face
	 * mask per endpoint — an eighth of the vanilla limit, and far more than a screen can show at once.
	 */
	public static final int MAX_POSITIONS = 16_384;

	/**
	 * Most bytes the config-sync payload may carry (MOD-695). A knob costs its name, a type byte and at
	 * most eight bytes of value — some 40 bytes — so even a snapshot of all 459 knobs is under 20 KiB;
	 * the ~70 client-visible ones are a few KiB. 32 KiB leaves room to grow and stays at 1/32 of the
	 * vanilla limit. {@code KnobSnapshotTest} encodes every knob and holds it under this ceiling.
	 */
	public static final int MAX_CONFIG_SNAPSHOT_BYTES = 32_768;

	private PayloadBudget() {
	}

	/**
	 * Fits the four counts into {@code max}, endpoints first — producers, consumers, storage, then cables
	 * with whatever is left. Endpoints are few and they are what the player is looking for; a missing
	 * cable only shortens a tube.
	 *
	 * @return the allowed counts, in the order {@code {cables, producers, consumers, storage}}
	 */
	public static int[] fit(int cables, int producers, int consumers, int storage, int max) {
		int left = Math.max(0, max);
		int p = Math.min(producers, left);
		left -= p;
		int c = Math.min(consumers, left);
		left -= c;
		int s = Math.min(storage, left);
		left -= s;
		int k = Math.min(cables, left);
		return new int[] {k, p, c, s};
	}

	/** Whether {@link #fit} had to drop anything. */
	public static boolean truncates(int cables, int producers, int consumers, int storage, int max) {
		return (long) cables + producers + consumers + storage > Math.max(0, max);
	}
}
