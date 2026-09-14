package dev.alaindustrial.client.screen.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.block.entity.ReactorIdleReason;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.client.screen.reactor.ReactorConsole.Advice;
import dev.alaindustrial.client.screen.reactor.ReactorConsole.Readout;
import dev.alaindustrial.client.screen.reactor.ReactorConsole.Tone;
import dev.alaindustrial.client.screen.reactor.ReactorConsole.Verdict;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ReactorConsole} (MOD-618, MOD-623): which verdict a state earns, which advice goes
 * with it, the throttle grid and the heat trend.
 *
 * <p>The readouts are built from the default thresholds (70 / 85) written as literals rather than read from
 * {@code Config}: the console never reads {@code Config}, and a test that did would stop proving the
 * thresholds come from the readout.
 */
class ReactorConsoleTest {

	private static final String KEY = "gui.alaindustrial.reactor_controller.";

	/** A sealed room at 200 EU/t whose water carries all of its heat — the plain healthy state. */
	private static Readout healthy() {
		return new Readout(ReactorRoomStatus.FORMED, ReactorIdleReason.RUNNING, 16, 200, 10, 70, 20, 0,
				false, 0, 100, 70, 85);
	}

	private static Readout with(Readout base, UnaryOperator<Builder> edit) {
		return edit.apply(new Builder(base)).build();
	}

	// ── Verdict ──────────────────────────────────────────────────────────────────────────────────

	@Test
	void aHealthyRoomIsRunning() {
		assertEquals(Verdict.RUNNING, ReactorConsole.verdict(healthy()));
	}

	@Test
	void aCountdownOutranksEverythingIncludingAMeltdown() {
		Readout r = with(healthy(), b -> b.blast(40).meltdown(true).heat(100));
		assertEquals(Verdict.BLAST, ReactorConsole.verdict(r));
	}

	@Test
	void aMeltdownOutranksRunningHot() {
		Readout r = with(healthy(), b -> b.meltdown(true).heat(90));
		assertEquals(Verdict.MELTDOWN, ReactorConsole.verdict(r));
	}

	@Test
	void racksWithNoRoomAreBareNotBuilding() {
		Readout bare = with(healthy(), b -> b.status(ReactorRoomStatus.CONTROLLER_NOT_IN_WALL).rods(8));
		Readout building = with(healthy(), b -> b.status(ReactorRoomStatus.BREACH).rods(0));
		assertEquals(Verdict.BARE, ReactorConsole.verdict(bare));
		assertEquals(Verdict.BUILDING, ReactorConsole.verdict(building));
	}

	/**
	 * A reaction the water cannot keep up with warns even when it sells nothing: while the rods work nothing
	 * else cools it. A full buffer used to hide such a core behind a grey "idle" (audit, MOD-623).
	 */
	@Test
	void anUnderCooledReactionWarnsEvenWhenItSellsNothing() {
		Readout dryFullBuffer = with(healthy(),
				b -> b.output(0).idle(ReactorIdleReason.BUFFER_FULL).water(0).share(0).heat(20));
		assertEquals(Verdict.WARNING, ReactorConsole.verdict(dryFullBuffer));
		// A scrammed room carries nothing because it makes nothing: that is idle, not short.
		Readout scrammed = with(healthy(),
				b -> b.output(0).idle(ReactorIdleReason.NO_SIGNAL).water(0).share(100).heat(20));
		assertEquals(Verdict.IDLE, ReactorConsole.verdict(scrammed));
	}

	@Test
	void theWarningLineIsInclusiveAndComesFromTheReadout() {
		assertEquals(Verdict.RUNNING, ReactorConsole.verdict(with(healthy(), b -> b.heat(69))));
		assertEquals(Verdict.WARNING, ReactorConsole.verdict(with(healthy(), b -> b.heat(70))));
		// A server that moved its warning line to 50 is obeyed, not the default.
		assertEquals(Verdict.WARNING, ReactorConsole.verdict(with(healthy(), b -> b.heat(55).warn(50))));
	}

	@Test
	void aBlockedExhaustWarnsBeforeTheHeatDoes() {
		assertEquals(Verdict.RUNNING, ReactorConsole.verdict(with(healthy(), b -> b.steam(89))));
		assertEquals(Verdict.WARNING, ReactorConsole.verdict(with(healthy(), b -> b.steam(90))));
	}

	@Test
	void aBarePileHasNoExhaustToBlock() {
		Readout r = with(healthy(), b -> b.status(ReactorRoomStatus.CONTROLLER_NOT_IN_WALL).rods(4).steam(100));
		assertFalse(r.steamBlocked());
	}

	@Test
	void everyVerdictHasItsOwnKey() {
		List<String> keys = new ArrayList<>();
		for (Verdict verdict : Verdict.values()) {
			keys.add(verdict.translationKey());
		}
		assertEquals(keys.size(), keys.stream().distinct().count(), "two verdicts share a key");
	}

	// ── Advice ───────────────────────────────────────────────────────────────────────────────────

	/** Breaking a wall drops a room into bare mode, where a room's worth of racks runs away again. */
	@Test
	void aSealedCountdownOffersWaterAndTheLeverButNotTheWall() {
		Advice advice = ReactorConsole.advice(with(healthy(), b -> b.blast(50)));
		assertEquals(Tone.ALARM, advice.tone());
		assertEquals(List.of(KEY + "advice.blast.step.water", KEY + "advice.blast.step.lever"), advice.stepKeys());
	}

	@Test
	void aBareCountdownOffersOnlyWhatABarePileHas() {
		Readout r = with(healthy(), b -> b.status(ReactorRoomStatus.CONTROLLER_NOT_IN_WALL).rods(16).blast(50));
		assertEquals(List.of(KEY + "advice.blast.step.lever", KEY + "advice.blast.step.rack"),
				ReactorConsole.advice(r).stepKeys());
	}

	@Test
	void aMeltdownQuotesTheServersMeltdownLine() {
		Advice advice = ReactorConsole.advice(with(healthy(), b -> b.meltdown(true).heat(90).melt(77)));
		assertEquals(KEY + "advice.meltdown.body", advice.bodyKey());
		assertEquals(77, advice.bodyArg());
	}

	@Test
	void aBarePileAtTheLimitIsAnAlarm() {
		Readout settling = with(healthy(), b -> b.status(ReactorRoomStatus.BREACH).rods(8).instability(79));
		Readout limit = with(healthy(), b -> b.status(ReactorRoomStatus.BREACH).rods(12).instability(80));
		assertEquals(KEY + "advice.bare.body", ReactorConsole.advice(settling).bodyKey());
		assertEquals(Tone.WARN, ReactorConsole.advice(settling).tone());
		assertEquals(KEY + "advice.bare_limit.body", ReactorConsole.advice(limit).bodyKey());
		assertEquals(Tone.ALARM, ReactorConsole.advice(limit).tone());
	}

	@Test
	void anOpenShellIsTitledByItsStatusAndToldTheFix() {
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			if (status == ReactorRoomStatus.FORMED) {
				continue;
			}
			Advice advice = ReactorConsole.advice(with(healthy(), b -> b.status(status).rods(0)));
			assertEquals(status.translationKey(), advice.titleKey());
			assertEquals(ReactorConsole.fixKey(status), advice.bodyKey());
		}
	}

	@Test
	void everyIdleReasonHasABody() {
		for (ReactorIdleReason reason : ReactorIdleReason.values()) {
			Advice advice = ReactorConsole.advice(with(healthy(), b -> b.output(0).idle(reason)));
			assertNotNull(advice.bodyKey());
			assertEquals(KEY + "advice.idle.title", advice.titleKey(), reason.name());
			assertEquals(Tone.IDLE, advice.tone(), reason.name());
		}
		assertEquals(KEY + "advice.idle.no_signal",
				ReactorConsole.advice(with(healthy(), b -> b.output(0).idle(ReactorIdleReason.NO_SIGNAL))).bodyKey());
	}

	/** The playtest room: rods lowered, no water. It makes power — and is told it is heading for the top. */
	@Test
	void aDryRoomWarnsBelowTheLineAndSaysWhy() {
		Readout dry = with(healthy(), b -> b.water(0).share(0).heat(40));
		assertEquals(Verdict.WARNING, ReactorConsole.verdict(dry));
		Advice advice = ReactorConsole.advice(dry);
		assertEquals(Tone.WARN, advice.tone());
		assertEquals(KEY + "advice.dry.body", advice.bodyKey());
		assertEquals(85, advice.bodyArg());
	}

	@Test
	void aBlockedExhaustIsNamedBeforeTheHeat() {
		Advice advice = ReactorConsole.advice(with(healthy(), b -> b.steam(95).heat(80)));
		assertEquals(KEY + "advice.steam.body", advice.bodyKey());
	}

	@Test
	void runningHotWithNoWaterSaysTheColumnsAreDry() {
		assertEquals(KEY + "advice.dry.body", ReactorConsole.advice(with(healthy(), b -> b.heat(75).water(0))).bodyKey());
		assertEquals(KEY + "advice.hot.body", ReactorConsole.advice(with(healthy(), b -> b.heat(75).water(30))).bodyKey());
	}

	@Test
	void aRoomWhoseWaterCarriesAllTheHeatIsNormal() {
		Advice advice = ReactorConsole.advice(healthy());
		assertEquals(Tone.GOOD, advice.tone());
		assertEquals(KEY + "advice.normal.body", advice.bodyKey());
		assertEquals(-1, advice.bodyArg());
	}

	@Test
	void aShortLoopWarnsAndQuotesTheShareItCarries() {
		Readout shortLoop = with(healthy(), b -> b.share(63));
		assertEquals(Verdict.WARNING, ReactorConsole.verdict(shortLoop));
		Advice advice = ReactorConsole.advice(shortLoop);
		assertEquals(Tone.WARN, advice.tone());
		assertEquals(KEY + "advice.short.body", advice.bodyKey());
		assertEquals(63, advice.bodyArg());
		// A supply a millibucket late on some ticks must not flip the chip back and forth.
		assertEquals(Verdict.RUNNING, ReactorConsole.verdict(with(healthy(), b -> b.share(95))));
		assertEquals(Verdict.WARNING, ReactorConsole.verdict(with(healthy(), b -> b.share(94))));
	}

	/**
	 * A key the code can produce but the language file does not have reaches the player as the raw key.
	 * No gate checks that direction — {@code lang_check} only compares the locales with each other. The
	 * file is searched as text: the L1 classpath carries no JSON library, and the file writes every entry
	 * as {@code "key": "value"}.
	 */
	@Test
	void everyKeyTheConsoleCanProduceExistsInEnglish() throws Exception {
		String english;
		try (InputStream stream = ReactorConsoleTest.class.getClassLoader()
				.getResourceAsStream("assets/alaindustrial/lang/en_us.json")) {
			assertNotNull(stream, "en_us.json is not on the test classpath");
			english = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
		List<String> keys = new ArrayList<>();
		for (Verdict verdict : Verdict.values()) {
			keys.add(verdict.translationKey());
		}
		for (Readout r : everyAdviceBranch()) {
			Advice advice = ReactorConsole.advice(r);
			keys.add(advice.titleKey());
			keys.add(advice.bodyKey());
			keys.addAll(advice.stepKeys());
		}
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			keys.add(ReactorConsole.fixKey(status));
		}
		for (ReactorIdleReason reason : ReactorIdleReason.values()) {
			keys.add(reason.translationKey());
		}
		for (String key : keys) {
			assertTrue(english.contains("\"" + key + "\":"), "missing from en_us.json: " + key);
		}
	}

	private static List<Readout> everyAdviceBranch() {
		List<Readout> all = new ArrayList<>();
		Readout base = healthy();
		all.add(base);
		all.add(with(base, b -> b.share(50)));
		all.add(with(base, b -> b.heat(75)));
		all.add(with(base, b -> b.heat(75).water(0)));
		all.add(with(base, b -> b.steam(95)));
		all.add(with(base, b -> b.blast(10)));
		all.add(with(base, b -> b.blast(10).status(ReactorRoomStatus.BREACH).rods(4)));
		all.add(with(base, b -> b.meltdown(true)));
		all.add(with(base, b -> b.status(ReactorRoomStatus.BREACH).rods(4).instability(10)));
		all.add(with(base, b -> b.status(ReactorRoomStatus.BREACH).rods(4).instability(95)));
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			all.add(with(base, b -> b.status(status).rods(0)));
		}
		for (ReactorIdleReason reason : ReactorIdleReason.values()) {
			all.add(with(base, b -> b.output(0).idle(reason)));
		}
		return all;
	}

	// ── Throttle grid ────────────────────────────────────────────────────────────────────────────

	@Test
	void theSliderSnapsToFivePercentSteps() {
		assertEquals(0, ReactorConsole.snapDepth(0.0));
		assertEquals(0, ReactorConsole.snapDepth(0.024));
		assertEquals(5, ReactorConsole.snapDepth(0.025));
		assertEquals(50, ReactorConsole.snapDepth(0.5));
		assertEquals(75, ReactorConsole.snapDepth(0.76));
		assertEquals(100, ReactorConsole.snapDepth(1.0));
	}

	@Test
	void theSliderStaysOnTheScale() {
		assertEquals(0, ReactorConsole.snapDepth(-3.0));
		assertEquals(100, ReactorConsole.snapDepth(7.0));
	}

	@Test
	void aStepMovesOneGridStepAndStopsAtTheEnds() {
		assertEquals(80, ReactorConsole.stepDepth(75, 1));
		assertEquals(70, ReactorConsole.stepDepth(75, -1));
		assertEquals(100, ReactorConsole.stepDepth(100, 1));
		assertEquals(0, ReactorConsole.stepDepth(0, -1));
		// A server-side depth off the grid first lands on it, then steps.
		assertEquals(80, ReactorConsole.stepDepth(77, 1));
	}

	// ── Heat trend ───────────────────────────────────────────────────────────────────────────────

	@Test
	void theTrendWaitsForAFullWindow() {
		ReactorConsole.HeatTrend trend = new ReactorConsole.HeatTrend();
		for (int i = 0; i < ReactorConsole.HeatTrend.WINDOW - 1; i++) {
			trend.sample(i * 10);
		}
		assertEquals(0, trend.direction());
		trend.sample(1000);
		assertEquals(1, trend.direction());
	}

	@Test
	void theTrendReadsRisingFallingAndHolding() {
		assertEquals(1, fill(40, 42));
		assertEquals(-1, fill(42, 40));
		assertEquals(0, fill(40, 41));
		assertEquals(0, fill(41, 40));
	}

	@Test
	void theTrendComparesTheOldestSampleNotTheFirstOneEver() {
		ReactorConsole.HeatTrend trend = new ReactorConsole.HeatTrend();
		trend.sample(0);
		for (int i = 0; i < ReactorConsole.HeatTrend.WINDOW; i++) {
			trend.sample(50);
		}
		// The 0 has left the window; everything in it is 50.
		assertEquals(0, trend.direction());
	}

	/** A full window whose oldest sample is {@code from} and newest is {@code to}. */
	private static int fill(int from, int to) {
		ReactorConsole.HeatTrend trend = new ReactorConsole.HeatTrend();
		for (int i = 0; i < ReactorConsole.HeatTrend.WINDOW - 1; i++) {
			trend.sample(from);
		}
		trend.sample(to);
		return trend.direction();
	}

	/** Copy-with edits for the thirteen-field readout, so each test names only what it changes. */
	private static final class Builder {
		private ReactorRoomStatus status;
		private ReactorIdleReason idle;
		private int rods;
		private int output;
		private int heat;
		private int water;
		private int steam;
		private int blast;
		private boolean meltdown;
		private int instability;
		private int share;
		private int warn;
		private int melt;

		Builder(Readout r) {
			status = r.status();
			idle = r.idle();
			rods = r.rods();
			output = r.output();
			heat = r.heat();
			water = r.water();
			steam = r.steam();
			blast = r.blast();
			meltdown = r.meltdown();
			instability = r.instability();
			share = r.coolantShare();
			warn = r.warnPercent();
			melt = r.meltdownPercent();
		}

		Builder status(ReactorRoomStatus v) {
			status = v;
			return this;
		}

		Builder idle(ReactorIdleReason v) {
			idle = v;
			return this;
		}

		Builder rods(int v) {
			rods = v;
			return this;
		}

		Builder output(int v) {
			output = v;
			return this;
		}

		Builder heat(int v) {
			heat = v;
			return this;
		}

		Builder water(int v) {
			water = v;
			return this;
		}

		Builder steam(int v) {
			steam = v;
			return this;
		}

		Builder blast(int v) {
			blast = v;
			return this;
		}

		Builder meltdown(boolean v) {
			meltdown = v;
			return this;
		}

		Builder instability(int v) {
			instability = v;
			return this;
		}

		Builder share(int v) {
			share = v;
			return this;
		}

		Builder warn(int v) {
			warn = v;
			return this;
		}

		Builder melt(int v) {
			melt = v;
			return this;
		}

		Readout build() {
			return new Readout(status, idle, rods, output, heat, water, steam, blast, meltdown, instability,
					share, warn, melt);
		}
	}
}
