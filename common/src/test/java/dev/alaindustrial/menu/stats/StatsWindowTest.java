package dev.alaindustrial.menu.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link StatsWindow} — the statistics panel's packet timer and the EU it carries per
 * window (MOD-722).
 *
 * <p>The window used to be divided down to an integer EU/t on the server, which turned the Garden Drone
 * Station's 8 EU per 40 ticks into a zero. These tests pin that the raw EU and the window's real length in
 * game ticks survive, so the client's fraction is honest.
 */
class StatsWindowTest {

	/** @implements MOD-722-WINDOW — the window carries every EU that went through, not an integer EU/t */
	@Test
	void windowCarriesTheRawEuSinceThePreviousSample() {
		StatsWindow window = new StatsWindow();
		window.sample(0L, 1_000L, 0);
		assertEquals(new StatsWindow.Sample(8L, 40), window.sample(40L, 1_008L, 0),
				"one drone action: 8 EU over 40 ticks, not 8 / 40 = 0");
		assertEquals(new StatsWindow.Sample(0L, 40), window.sample(80L, 1_008L, 0),
				"a window in which nothing moved carries zero");
		assertEquals(new StatsWindow.Sample(5_000L, 40), window.sample(120L, 6_008L, 0));
	}

	/** @implements MOD-722-WINDOW — the window's length is the game time it really spanned */
	@Test
	void windowLengthIsTheGameTimeBetweenSamples() {
		StatsWindow window = new StatsWindow();
		window.sample(1_000L, 0L, 0);
		assertEquals(new StatsWindow.Sample(30L, 60), window.sample(1_060L, 30L, 0), "a late packet: 60 ticks");
		assertEquals(new StatsWindow.Sample(0L, 1), window.sample(1_060L, 30L, 0), "never shorter than a tick");
	}

	/** @implements MOD-722-WINDOW — the first sample stretches the instantaneous rate over one interval */
	@Test
	void firstSampleReportsTheInstantRateOverAWholeWindow() {
		StatsWindow window = new StatsWindow();
		assertEquals(new StatsWindow.Sample(32L * StatsWindow.INTERVAL_TICKS, StatsWindow.INTERVAL_TICKS),
				window.sample(100L, 123_456L, 32), "no predecessor: the block's current EU/t, so the panel reads it");
		assertEquals(new StatsWindow.Sample(4L, 40), window.sample(140L, 123_460L, 999),
				"the instant rate is used only once");
	}

	/** @implements MOD-722-WINDOW — pulling the chip forgets the sample; the next one starts over */
	@Test
	void resetStartsOverFromTheInstantRate() {
		StatsWindow window = new StatsWindow();
		window.sample(0L, 0L, 0);
		window.sample(40L, 100L, 0);
		window.reset();
		assertEquals(new StatsWindow.Sample(3L * StatsWindow.INTERVAL_TICKS, StatsWindow.INTERVAL_TICKS),
				window.sample(400L, 5_000L, 3), "not 4900 EU over 360 ticks of a chip that was out");
	}

	/** @implements MOD-722-WINDOW — a total that went backwards reads as nothing moved */
	@Test
	void totalThatWentBackwardsReadsAsZero() {
		StatsWindow window = new StatsWindow();
		window.sample(0L, 500L, 0);
		assertEquals(0L, window.sample(40L, 100L, 0).eu());
		assertEquals(0L, new StatsWindow().sample(0L, 0L, -7).eu(), "a negative instant rate is clamped");
	}

	/** @implements MOD-722-WINDOW — one packet per interval of game time, the first one at once */
	@Test
	void dueOncePerIntervalOfGameTime() {
		StatsWindow window = new StatsWindow();
		assertTrue(window.due(500L), "the first broadcast after opening sends");
		for (long tick = 501L; tick < 500L + StatsWindow.INTERVAL_TICKS; tick++) {
			assertFalse(window.due(tick), "tick " + tick + " is inside the interval");
		}
		assertTrue(window.due(500L + StatsWindow.INTERVAL_TICKS), "a full interval later it sends again");
	}

	/**
	 * The server also calls {@code broadcastChanges} on every click and button press in the menu, so a
	 * window counted in calls closed early while the client still divided by a full window.
	 *
	 * @implements MOD-722-WINDOW — extra broadcasts within a tick do not shorten the window
	 */
	@Test
	void extraBroadcastsWithinATickDoNotShortenTheWindow() {
		StatsWindow window = new StatsWindow();
		int sends = 0;
		for (long tick = 1L; tick <= StatsWindow.INTERVAL_TICKS; tick++) {
			for (int call = 0; call < 2; call++) {
				if (window.due(tick)) {
					sends++;
				}
			}
		}
		assertEquals(1, sends, "one window of game time, two broadcasts per tick");
	}
}
