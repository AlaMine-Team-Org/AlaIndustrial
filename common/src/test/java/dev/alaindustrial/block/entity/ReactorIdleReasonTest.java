package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ReactorIdleReason} (MOD-468 stage 3).
 *
 * <p>The enum travels as an ordinal over the menu's {@code ContainerData}, which makes two things
 * worth pinning: a value outside the range must not invent a fault, and every constant must own a
 * distinct translation key — a duplicate would silently show the player the wrong reason, and the
 * only symptom would be a fix that does not work.
 */
class ReactorIdleReasonTest {

	@Test
	void anOrdinalOutsideTheRangeReadsAsRunning() {
		assertEquals(ReactorIdleReason.RUNNING, ReactorIdleReason.byOrdinal(-1));
		assertEquals(ReactorIdleReason.RUNNING, ReactorIdleReason.byOrdinal(ReactorIdleReason.values().length));
		assertEquals(ReactorIdleReason.RUNNING, ReactorIdleReason.byOrdinal(Integer.MAX_VALUE));
	}

	@Test
	void everyOrdinalInRangeRoundTrips() {
		for (ReactorIdleReason reason : ReactorIdleReason.values()) {
			assertEquals(reason, ReactorIdleReason.byOrdinal(reason.ordinal()));
		}
	}

	@Test
	void everyReasonHasItsOwnKey() {
		Set<String> keys = new HashSet<>();
		for (ReactorIdleReason reason : ReactorIdleReason.values()) {
			String key = reason.translationKey();
			assertEquals(true, key.startsWith("gui.alaindustrial.reactor_controller.idle."),
					"unexpected key namespace: " + key);
			assertEquals(true, keys.add(key), "duplicate translation key: " + key);
		}
		assertNotEquals(ReactorIdleReason.NO_SIGNAL.translationKey(),
				ReactorIdleReason.NO_FUEL.translationKey());
	}
}
