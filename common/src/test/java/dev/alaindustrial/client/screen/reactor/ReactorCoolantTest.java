package dev.alaindustrial.client.screen.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.structure.ReactorZone;
import java.util.List;
import org.junit.jupiter.api.Test;

/** L1 coverage for {@link ReactorCoolant} (MOD-621): which fault the «Coolant» tab names, and how loudly. */
class ReactorCoolantTest {

	private static final String KEY = "gui.alaindustrial.reactor_controller.coolant.advice.";
	private static final int CARRYING = 100;
	private static final int LEAVING_HEAT = ReactorConsole.SHORT_SHARE_PERCENT - 1;

	/** A stack whose two faults are given, as the server judged them; the tanks do not matter to the advice. */
	private static ReactorZone.Stack stack(int x, int z, boolean dry, boolean blocked) {
		return new ReactorZone.Stack(x, z, 1, 4, 0, 0, 0, 0, 0,
				new ReactorZone.Coolant(dry ? 0 : 2000, 4000, blocked ? 4000 : 0, 4000, dry, blocked));
	}

	private static ReactorZone.Stack ok(int x, int z) {
		return stack(x, z, false, false);
	}

	@Test
	void aLoopWithWaterAndAnExhaustEverywhereIsInOrder() {
		ReactorCoolant.Advice advice = ReactorCoolant.advice(true, false, List.of(ok(0, 0), ok(1, 0)), CARRYING);
		assertEquals(ReactorConsole.Tone.GOOD, advice.tone());
		assertEquals(KEY + "ok.title", advice.titleKey());
		assertEquals(ReactorConsole.Tone.GOOD, ReactorCoolant.badge(List.of(ok(0, 0)), CARRYING), "no badge");
	}

	/** Steam with nowhere to go stops the boiling even where the pipe keeps the water full, so it is fixed first. */
	@Test
	void aBlockedExhaustOutranksADryStackAndNamesItsStack() {
		List<ReactorZone.Stack> stacks = List.of(ok(0, 0), stack(1, 0, true, false), stack(2, 3, false, true));
		ReactorCoolant.Advice advice = ReactorCoolant.advice(true, false, stacks, CARRYING);
		assertEquals(KEY + "blocked.body", advice.bodyKey());
		assertEquals(List.of(3, 4), advice.args(), "the stack as the player counts it, from one");
		ReactorCoolant.Survey survey = ReactorCoolant.survey(stacks);
		assertEquals(1, survey.dry());
		assertEquals(1, survey.blocked());
		assertEquals(1, survey.firstDry());
		assertEquals(2, survey.firstBlocked());
		assertFalse(survey.allDry());
	}

	/** One dry stack beside wet ones is the side-by-side plumbing mistake, so the advice points at that stack. */
	@Test
	void aDryStackBesideWetOnesIsNamed() {
		ReactorCoolant.Advice advice = ReactorCoolant.advice(true, false,
				List.of(ok(0, 0), stack(4, 1, true, false)), CARRYING);
		assertEquals(KEY + "dry.body", advice.bodyKey());
		assertEquals(List.of(5, 2), advice.args());
		assertEquals(ReactorConsole.Tone.WARN, advice.tone(), "the water still carries the heat");
	}

	/** No water anywhere is one message about the supply, not a stack to walk to. */
	@Test
	void aLoopWithNoWaterAtAllIsAboutTheSupply() {
		List<ReactorZone.Stack> stacks = List.of(stack(0, 0, true, false), stack(1, 0, true, false));
		assertTrue(ReactorCoolant.survey(stacks).allDry());
		ReactorCoolant.Advice advice = ReactorCoolant.advice(true, false, stacks, CARRYING);
		assertEquals(KEY + "dry_all.body", advice.bodyKey());
		assertEquals(List.of(), advice.args());
	}

	/** A fault reads as an alarm, and turns the badge red, once the water stops carrying the reaction's heat. */
	@Test
	void aFaultLeavingHeatBehindIsAnAlarm() {
		List<ReactorZone.Stack> stacks = List.of(ok(0, 0), stack(1, 0, true, false));
		assertEquals(ReactorConsole.Tone.ALARM, ReactorCoolant.advice(true, false, stacks, LEAVING_HEAT).tone());
		assertEquals(ReactorConsole.Tone.ALARM, ReactorCoolant.badge(stacks, LEAVING_HEAT));
		assertEquals(ReactorConsole.Tone.WARN, ReactorCoolant.badge(stacks, ReactorConsole.SHORT_SHARE_PERCENT),
				"at the line the water still carries enough");
	}

	/** Short with no stack at fault: the advice quotes the share, and the badge is left to the «Console» tab. */
	@Test
	void aShortLoopWithNoFaultQuotesTheShare() {
		ReactorCoolant.Advice advice = ReactorCoolant.advice(true, false, List.of(ok(0, 0)), 80);
		assertEquals(KEY + "short.body", advice.bodyKey());
		assertEquals(List.of(80), advice.args());
		assertEquals(ReactorConsole.Tone.GOOD, ReactorCoolant.badge(List.of(ok(0, 0)), 80));
	}

	/** With no loop the advice says why, whatever the tanks of a bare pile still hold. */
	@Test
	void noLoopSaysWhy() {
		List<ReactorZone.Stack> dryPile = List.of(stack(0, 0, true, true));
		assertEquals(KEY + "bare.body", ReactorCoolant.advice(false, true, dryPile, CARRYING).bodyKey());
		assertEquals(KEY + "not_built.body", ReactorCoolant.advice(false, false, List.of(), CARRYING).bodyKey());
		assertEquals(KEY + "empty.body", ReactorCoolant.advice(true, false, List.of(), CARRYING).bodyKey());
		assertEquals(ReactorConsole.Tone.IDLE, ReactorCoolant.advice(false, true, dryPile, CARRYING).tone());
	}
}
