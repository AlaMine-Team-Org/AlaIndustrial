package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** L1 coverage for {@link ThrottledSnapshot} (MOD-620): when a zone snapshot for an open screen goes out. */
class ThrottledSnapshotTest {

	@Test
	void anOpenedScreenIsFilledAtOnceThenOncePerInterval() {
		ThrottledSnapshot<String> sync = new ThrottledSnapshot<>(20);
		assertTrue(sync.due(), "the first tick after opening is due");
		for (int tick = 1; tick < 20; tick++) {
			assertFalse(sync.due(), "tick " + tick + " is inside the interval");
		}
		assertTrue(sync.due(), "the twentieth tick after the last one is due");
	}

	@Test
	void anUnchangedSnapshotIsNotSentTwice() {
		ThrottledSnapshot<List<Integer>> sync = new ThrottledSnapshot<>(1);
		assertTrue(sync.changed(List.of(1, 2)), "the first snapshot always goes out");
		assertFalse(sync.changed(List.of(1, 2)), "an equal one does not");
		assertTrue(sync.changed(List.of(1, 3)), "a changed one does");
		assertTrue(sync.changed(List.of(1, 2)), "and going back is a change too");
	}
}
