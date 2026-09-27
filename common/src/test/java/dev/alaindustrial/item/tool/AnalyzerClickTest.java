package dev.alaindustrial.item.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.item.tool.AnalyzerClick.Action;
import org.junit.jupiter.api.Test;

/**
 * L1 — the whole click table of the Network Analyzer (MOD-665). The row that was missing: a plain click
 * into the air clears the highlight, as the spec always said (D9).
 */
class AnalyzerClickTest {

	/**
	 * @implements MOD-665-D9 — a plain right-click that hits no network (air or a foreign block) clears
	 * @covers MOD-665
	 */
	@Test
	void plainClickOffNetworkClears() {
		assertEquals(Action.CLEAR, AnalyzerClick.decide(false, false));
	}

	/** @implements MOD-665-D9 — the rest of the table is unchanged: scan on a network, Shift elsewhere switches */
	@Test
	void restOfTheTable() {
		assertEquals(Action.SCAN, AnalyzerClick.decide(true, false));
		assertEquals(Action.SCAN, AnalyzerClick.decide(true, true), "Shift on a cable still scans (MOD-047 Q1)");
		assertEquals(Action.SWITCH_MODE, AnalyzerClick.decide(false, true));
	}
}
