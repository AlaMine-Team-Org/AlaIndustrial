package dev.alaindustrial.client.tablet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the reactor tablet's animation (MOD-946): the one clock the hands and the screen share, where the
 * screen is drawn while it unfolds, and how the casing reacts.
 *
 * <p>Worth pinning because each of these fails silently in game: a clock that never reports "folded" leaves a screen
 * that cannot be closed, a frame whose inverse is off moves every click away from its button, and an open scale that
 * is not a whole number of window pixels smears the panel's font.
 *
 * @implements MOD-946 tablet animation: lift, unfold, fold, lower on one clock; crisp open scale; casing signals
 */
class ReactorTabletAnimationTest {

	private static final long T0 = 1_000_000L;

	@Test
	void openingRaisesTheHandsFirstThenUnfoldsTheScreen() {
		ReactorTabletTimeline clock = new ReactorTabletTimeline();
		clock.open(T0);
		assertEquals(0.0f, clock.raise(T0), "nothing has moved at the click");
		long midRaise = T0 + ReactorTabletTimeline.RAISE_MS / 2;
		assertTrue(clock.raise(midRaise) > 0.0f && clock.raise(midRaise) < 1.0f);
		assertEquals(0.0f, clock.unfold(midRaise), "the screen waits for the hands");
		long midUnfold = T0 + ReactorTabletTimeline.RAISE_MS + ReactorTabletTimeline.UNFOLD_MS / 2;
		assertEquals(1.0f, clock.raise(midUnfold));
		assertTrue(clock.unfold(midUnfold) > 0.0f && clock.unfold(midUnfold) < 1.0f);
		assertFalse(clock.isOpen(midUnfold), "no input while the screen is still moving");
		long open = T0 + ReactorTabletTimeline.OPEN_MS;
		assertEquals(1.0f, clock.unfold(open));
		assertTrue(clock.isOpen(open), "input is taken once unfolded");
		assertEquals(0.0f, clock.powerOn(open), "the screen is still dark");
		assertEquals(1.0f, clock.powerOn(T0 + ReactorTabletTimeline.TOTAL_MS), "then it is on");
	}

	@Test
	void closingFoldsTheScreenThenLowersTheHandsToRest() {
		ReactorTabletTimeline clock = new ReactorTabletTimeline();
		clock.open(T0);
		long open = T0 + ReactorTabletTimeline.TOTAL_MS + 500L;
		assertTrue(clock.isOpen(open));
		clock.close(open);
		assertFalse(clock.isOpen(open));
		assertFalse(clock.isFolded(open + 1L), "the screen is still unfolded when the close starts");
		assertTrue(clock.powerOn(open + ReactorTabletTimeline.POWER_MS / 2) < 1.0f, "closing powers the screen off");
		long folded = open + ReactorTabletTimeline.POWER_MS + ReactorTabletTimeline.UNFOLD_MS;
		assertTrue(clock.isFolded(folded), "the screen may leave once it is back in the tablet");
		assertEquals(1.0f, clock.raise(folded), "the hands still hold the tablet up");
		long down = folded + ReactorTabletTimeline.RAISE_MS;
		assertEquals(0.0f, clock.raise(down));
		assertTrue(clock.isFolded(down + 5_000L), "a screen that missed the moment still leaves");
	}

	@Test
	void aCloseDuringTheOpeningTurnsBackFromWhereItIs() {
		ReactorTabletTimeline clock = new ReactorTabletTimeline();
		clock.open(T0);
		long mid = T0 + ReactorTabletTimeline.RAISE_MS / 2;
		float raised = clock.raise(mid);
		clock.close(mid);
		assertEquals(raised, clock.raise(mid), "no jump at the turn");
		assertTrue(clock.raise(mid + 40L) < raised, "and it goes back down");
	}

	@Test
	void theFrameInverseMapsTheMouseBackOntoTheSamePanelPoint() {
		for (float unfold : new float[] {0.0f, 0.37f, 1.0f}) {
			ReactorTabletLayout.Frame frame = ReactorTabletLayout.at(unfold, 640, 360, 3, 170, 70, 300, 224);
			for (int x : new int[] {170, 202, 469}) {
				assertEquals(x, frame.toPanelX(frame.toScreenX(x)), 1e-3, "x at unfold " + unfold);
			}
			for (int y : new int[] {70, 88, 293}) {
				assertEquals(y, frame.toPanelY(frame.toScreenY(y)), 1e-3, "y at unfold " + unfold);
			}
		}
	}

	@Test
	void theOpenTabletIsCrispAndFitsTheScreen() {
		// 1920 × 1080 at GUI scale 2, 3 and 4: one panel pixel is four window pixels every time.
		int[][] screens = {{960, 540, 2}, {640, 360, 3}, {480, 270, 4}};
		for (int[] s : screens) {
			float scale = ReactorTabletLayout.openScale(s[0], s[1], s[2], 300, 224);
			float windowPixels = scale * s[2];
			assertEquals(Math.round(windowPixels), windowPixels, 1e-4, "whole window pixels at GUI " + s[2]);
			assertTrue(300 * scale <= s[0] && 224 * scale <= s[1], "fits at GUI " + s[2]);
			ReactorTabletLayout.Frame open = ReactorTabletLayout.at(1.0f, s[0], s[1], s[2], 0, 0, 300, 224);
			float originWindow = open.originX() * s[2];
			assertEquals(Math.round(originWindow), originWindow, 1e-4, "corner on a window pixel");
		}
	}

	@Test
	void theUnfoldStartsOverTheRaisedTabletAndEndsCentred() {
		ReactorTabletLayout.Frame start = ReactorTabletLayout.at(0.0f, 640, 360, 3, 0, 0, 300, 224);
		assertEquals(ReactorTabletLayout.RAISED_WIDTH_OF_VIEW * 360, 300 * start.scale(), 1e-3);
		assertTrue(start.originY() + 224 * start.scale() / 2 < 180, "the raised tablet sits above the centre");
		ReactorTabletLayout.Frame open = ReactorTabletLayout.at(1.0f, 640, 360, 3, 0, 0, 300, 224);
		assertEquals(320, open.originX() + 300 * open.scale() / 2, 0.5);
		assertEquals(180, open.originY() + 224 * open.scale() / 2, 0.5);
	}

	@Test
	void theCasingIsCalmOnlyWhileLinkedAndCalm() {
		for (long ms = 0; ms < 5_000L; ms += 17L) {
			assertEquals(0.0f, ReactorTabletSignal.wash(ReactorTabletSignal.Alarm.NONE, ms));
			assertEquals(0.0f, ReactorTabletSignal.dropout(true, ReactorTabletSignal.Alarm.NONE, ms));
			assertTrue(ReactorTabletSignal.lampLit(true, ReactorTabletSignal.Alarm.NONE, ms));
		}
		assertTrue(flickers(ms -> ReactorTabletSignal.dropout(false, ReactorTabletSignal.Alarm.NONE, ms) > 0),
				"a lost link dims");
		assertTrue(flickers(ms -> !ReactorTabletSignal.lampLit(true, ReactorTabletSignal.Alarm.WARN, ms)),
				"a warning blinks the lamp");
		for (long ms = 0; ms < 5_000L; ms += 17L) {
			assertEquals(0.0f, ReactorTabletSignal.dropout(true, ReactorTabletSignal.Alarm.ALARM, ms),
					"an alarm does not black the screen out: its wash is the warning");
		}
	}

	/**
	 * Nothing on the casing flashes more than three times a second (WCAG 2.3.1), and the washes never jump: at 60 fps
	 * two frames apart differ by a small step, not a flash (owner's playtest, MOD-941).
	 */
	@Test
	void theCasingNeverFlashesFastOrJumps() {
		for (ReactorTabletSignal.Alarm alarm : ReactorTabletSignal.Alarm.values()) {
			for (boolean linked : new boolean[] {true, false}) {
				assertTrue(switchesPerSecond(ms -> ReactorTabletSignal.lampLit(linked, alarm, ms)) <= 6,
						"lamp " + alarm + " linked=" + linked);
			}
			float prev = ReactorTabletSignal.wash(alarm, 0L);
			for (long ms = 16L; ms < 10_000L; ms += 16L) {
				float now = ReactorTabletSignal.wash(alarm, ms);
				assertTrue(Math.abs(now - prev) < 0.08f, "wash " + alarm + " jumps at " + ms + " ms");
				prev = now;
			}
		}
		float prev = ReactorTabletSignal.dropout(false, ReactorTabletSignal.Alarm.NONE, 0L);
		for (long ms = 16L; ms < 10_000L; ms += 16L) {
			float now = ReactorTabletSignal.dropout(false, ReactorTabletSignal.Alarm.NONE, ms);
			assertTrue(now <= ReactorTabletSignal.LOST_DIM + 1e-6f && Math.abs(now - prev) < 0.03f,
					"lost-link dimming jumps at " + ms + " ms");
			prev = now;
		}
		assertTrue(switchesPerSecond(ms -> ReactorTabletSignal.batteryShown(0.1f, ms)) <= 6, "low battery");
	}

	/** On/off changes per second, over ten seconds — two changes make one flash. */
	private static int switchesPerSecond(java.util.function.LongPredicate on) {
		int changes = 0;
		boolean last = on.test(0L);
		for (long ms = 1L; ms < 10_000L; ms++) {
			boolean now = on.test(ms);
			if (now != last) {
				changes++;
				last = now;
			}
		}
		return (changes + 9) / 10;
	}

	@Test
	void aLowBatteryBlinksAndAnyChargeLightsASegment() {
		assertTrue(ReactorTabletSignal.batteryShown(0.9f, 500L), "a healthy battery never blinks");
		assertTrue(ReactorTabletSignal.batteryShown(0.1f, 0L));
		assertFalse(ReactorTabletSignal.batteryShown(0.1f, 500L));
		assertEquals(0, ReactorTabletSignal.litSegments(0.0f, 5));
		assertEquals(1, ReactorTabletSignal.litSegments(0.01f, 5));
		assertEquals(5, ReactorTabletSignal.litSegments(1.0f, 5));
	}

	private static boolean flickers(java.util.function.LongPredicate off) {
		for (long ms = 0; ms < 5_000L; ms += 17L) {
			if (off.test(ms)) {
				return true;
			}
		}
		return false;
	}
}
