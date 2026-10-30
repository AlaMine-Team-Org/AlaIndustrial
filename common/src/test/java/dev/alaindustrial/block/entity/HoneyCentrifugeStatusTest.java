package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link HoneyCentrifugeStatus} (MOD-924) — the wire-and-lang face of the honey centrifuge's
 * idle diagnosis.
 *
 * <p>Small, but not trivial: this enum is where a machine's reason for standing still becomes an
 * ordinal on a sync channel and a translation key on screen. Both break silently. A missing
 * {@code switch} arm stops compiling; a <em>reordered</em> constant does not, and it would make every
 * client read the wrong reason — "tank full" while the slot is empty.
 */
class HoneyCentrifugeStatusTest {

	/** The ordinal is the wire format: channel {@code STATUS} carries nothing else. */
	@Test
	void ordinalsAreTheWireFormat() {
		assertEquals(0, HoneyCentrifugeStatus.READY.ordinal());
		assertEquals(1, HoneyCentrifugeStatus.NO_INPUT.ordinal());
		assertEquals(2, HoneyCentrifugeStatus.NO_RECIPE.ordinal());
		assertEquals(3, HoneyCentrifugeStatus.TANK_FULL.ordinal());
		assertEquals(4, HoneyCentrifugeStatus.OUTPUT_BLOCKED.ordinal());
	}

	/** Round-trip through the wire: what the server sends is what the client reads back. */
	@Test
	void byOrdinalIsTheInverseOfOrdinal() {
		for (HoneyCentrifugeStatus status : HoneyCentrifugeStatus.values()) {
			assertSame(status, HoneyCentrifugeStatus.byOrdinal(status.ordinal()));
		}
	}

	/**
	 * An out-of-range ordinal must not throw on the client.
	 *
	 * <p>This is not hypothetical: the channel is a signed short written by the server, and a desync,
	 * a stale packet or a future constant the client does not know about all arrive here as a number
	 * outside the enum. Falling back to READY shows a working machine, which is the harmless reading;
	 * an exception would take the screen down.
	 */
	@Test
	void outOfRangeOrdinalsFallBackToReady() {
		assertSame(HoneyCentrifugeStatus.READY, HoneyCentrifugeStatus.byOrdinal(-1));
		assertSame(HoneyCentrifugeStatus.READY, HoneyCentrifugeStatus.byOrdinal(HoneyCentrifugeStatus.values().length));
		assertSame(HoneyCentrifugeStatus.READY, HoneyCentrifugeStatus.byOrdinal(Integer.MAX_VALUE));
		assertSame(HoneyCentrifugeStatus.READY, HoneyCentrifugeStatus.byOrdinal(Integer.MIN_VALUE));
	}

	/** Every constant has its own key under the machine's namespace, in lower case. */
	@Test
	void translationKeysAreDistinctAndNamespaced() {
		for (HoneyCentrifugeStatus status : HoneyCentrifugeStatus.values()) {
			String key = status.translationKey();
			assertTrue(key.startsWith("gui.alaindustrial.honey_centrifuge.status."),
					() -> "unexpected namespace: " + key);
			assertEquals(key.toLowerCase(Locale.ROOT), key, () -> "key must be lower case: " + key);
			assertTrue(key.endsWith(status.name().toLowerCase(Locale.ROOT)),
					() -> key + " does not end with " + status.name());
		}
		long distinct = java.util.Arrays.stream(HoneyCentrifugeStatus.values())
				.map(HoneyCentrifugeStatus::translationKey)
				.distinct()
				.count();
		assertEquals(HoneyCentrifugeStatus.values().length, distinct,
				"two statuses share a translation key, so one of them can never be shown");
	}
}
