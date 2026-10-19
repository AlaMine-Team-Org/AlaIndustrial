package dev.alaindustrial.settlement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.ReceptionCapsuleRules.Material;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Situation;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Verdict;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

/**
 * L1 for the reception capsule's rules (MOD-833; epic MOD-824, R6, R39): the price of one worker, what is still
 * missing, the order of the refusals, and that a restoration only moves while the capsule may restore — halted, it
 * keeps what it had.
 */
class ReceptionCapsuleRulesTest {

	private static final int[] NONE = {0, 0, 0};

	/** Everything in place: a town, its core home and lit, the capsule inside, claimed, room, price in. */
	private static Situation ready() {
		int[] price = ReceptionCapsuleRules.materialPrice(false);
		return new Situation(true, true, true, true, true, 0, 4,
				ReceptionCapsuleRules.euPrice(false), ReceptionCapsuleRules.euPrice(false), price, price, true);
	}

	private static Situation with(Situation s, boolean hasTown, boolean corePlaced, boolean inside, boolean lit,
			boolean claimed, int living, int limit, long eu, int[] have, boolean archive) {
		return new Situation(hasTown, corePlaced, inside, lit, claimed, living, limit, eu, s.euPrice(), have, s.need(),
				archive);
	}

	@Test
	void theShippedPriceIsTheBalancedOne() {
		// The number itself lives in the knob (and in the config golden file), not here (MOD-844).
		long perWorker = SettlementConfig.capsuleEuPerWorker;
		assertEquals(perWorker, ReceptionCapsuleRules.euPrice(false));
		assertEquals(2 * perWorker, ReceptionCapsuleRules.euPrice(true), "R39: bringing the dead back costs double");
		assertEquals(2 * perWorker, ReceptionCapsuleRules.bufferCapacity(), "the buffer holds the dearest restoration");
		assertArrayEquals(new int[] {1, 8, 4}, ReceptionCapsuleRules.materialPrice(false));
		assertArrayEquals(new int[] {2, 16, 8}, ReceptionCapsuleRules.materialPrice(true));
		assertEquals(24000, ReceptionCapsuleRules.restoreTicks(), "MOD-847: one game day outside development");
	}

	/** MOD-847: a started restoration shows at least one percent, on a fixed per-mille scale. */
	@Test
	void aStartedRestorationShowsAtLeastOnePercent() {
		int day = 24000;
		assertEquals(0, ReceptionCapsuleRules.shownProgress(0, day), "not started: nothing shown");
		assertEquals(ReceptionCapsuleRules.SHOWN_FLOOR, ReceptionCapsuleRules.shownProgress(1, day),
				"the first tick of a day shows one percent");
		assertEquals(1, 100 * ReceptionCapsuleRules.SHOWN_FLOOR / ReceptionCapsuleRules.SHOWN_SCALE,
				"the floor is one percent of the scale");
		assertEquals(500, ReceptionCapsuleRules.shownProgress(day / 2, day), "half way is half the scale");
		assertEquals(ReceptionCapsuleRules.SHOWN_SCALE - 1, ReceptionCapsuleRules.shownProgress(day - 1, day),
				"one tick short is not done");
		assertEquals(ReceptionCapsuleRules.SHOWN_SCALE, ReceptionCapsuleRules.shownProgress(day, day));
		assertEquals(ReceptionCapsuleRules.SHOWN_SCALE, ReceptionCapsuleRules.shownProgress(day + 5, day),
				"a duration shortened mid-way shows done, never past the scale");
		assertEquals(ReceptionCapsuleRules.SHOWN_FLOOR, ReceptionCapsuleRules.shownProgress(1, 200),
				"ten seconds: the first tick is half a percent, shown as one");
		assertTrue(ReceptionCapsuleRules.SHOWN_SCALE <= Short.MAX_VALUE, "the scale fits a menu channel");
	}

	@Test
	void allInPlaceIsReady() {
		assertEquals(Verdict.READY, ReceptionCapsuleRules.verdict(ready()));
	}

	/** Each condition alone refuses, with its own reason. */
	@Test
	void eachMissingConditionRefusesWithItsReason() {
		Situation r = ready();
		int[] have = r.have();
		long eu = r.euStored();
		assertEquals(Verdict.NO_CORE, ReceptionCapsuleRules.verdict(with(r, false, true, true, true, true, 0, 4, eu,
				have, true)));
		assertEquals(Verdict.CORE_AWAY, ReceptionCapsuleRules.verdict(with(r, true, false, true, true, true, 0, 4, eu,
				have, true)));
		assertEquals(Verdict.OUTSIDE, ReceptionCapsuleRules.verdict(with(r, true, true, false, true, true, 0, 4, eu,
				have, true)));
		assertEquals(Verdict.DARK, ReceptionCapsuleRules.verdict(with(r, true, true, true, false, true, 0, 4, eu,
				have, true)));
		assertEquals(Verdict.UNCLAIMED, ReceptionCapsuleRules.verdict(with(r, true, true, true, true, false, 0, 4, eu,
				have, true)));
		assertEquals(Verdict.AT_LIMIT, ReceptionCapsuleRules.verdict(with(r, true, true, true, true, true, 4, 4, eu,
				have, true)));
		assertEquals(Verdict.NEED_MATERIALS, ReceptionCapsuleRules.verdict(with(r, true, true, true, true, true, 0, 4,
				eu, NONE, true)));
		assertEquals(Verdict.NEED_ENERGY, ReceptionCapsuleRules.verdict(with(r, true, true, true, true, true, 0, 4,
				eu - 1, have, true)));
		assertEquals(Verdict.ARCHIVE_SILENT, ReceptionCapsuleRules.verdict(with(r, true, true, true, true, true, 0, 4,
				eu, have, false)));
	}

	/** A town at its limit refuses whatever else is in; one below it does not refuse for the limit. */
	@Property
	void theLimitIsACeiling(@ForAll @IntRange(min = 0, max = 50) int living,
			@ForAll @IntRange(min = 0, max = 50) int limit) {
		Situation r = ready();
		Verdict verdict = ReceptionCapsuleRules.verdict(with(r, true, true, true, true, true, living, limit,
				r.euStored(), r.have(), true));
		assertEquals(living >= limit ? Verdict.AT_LIMIT : Verdict.READY, verdict);
	}

	/**
	 * READY exactly when every condition holds — never with one missing (the refusal that charges nothing). The six
	 * yes/no conditions come packed in one number, so the generator explores their combinations without an
	 * explosion of edge cases.
	 */
	@Property
	void readyOnlyWhenEverythingHolds(@ForAll @IntRange(min = 0, max = 63) int flags,
			@ForAll @IntRange(min = 0, max = 8) int living, @ForAll @LongRange(min = 0, max = 40_000) long eu,
			@ForAll @IntRange(min = 0, max = 20) int rubber) {
		Situation r = ready();
		boolean hasTown = (flags & 1) != 0;
		boolean corePlaced = (flags & 2) != 0;
		boolean inside = (flags & 4) != 0;
		boolean lit = (flags & 8) != 0;
		boolean claimed = (flags & 16) != 0;
		boolean archive = (flags & 32) != 0;
		int[] have = {r.need()[0], rubber, r.need()[2]};
		Verdict verdict = ReceptionCapsuleRules.verdict(with(r, hasTown, corePlaced, inside, lit, claimed, living, 4,
				eu, have, archive));
		boolean all = hasTown && corePlaced && inside && lit && claimed && living < 4 && eu >= r.euPrice()
				&& ReceptionCapsuleRules.hasMaterials(have, r.need()) && archive;
		assertEquals(all, verdict == Verdict.READY, "verdict " + verdict);
	}

	@Test
	void shortfallNeverGoesNegative() {
		assertEquals(16_000L, ReceptionCapsuleRules.euMissing(0, 16_000));
		assertEquals(1L, ReceptionCapsuleRules.euMissing(15_999, 16_000));
		assertEquals(0L, ReceptionCapsuleRules.euMissing(32_000, 16_000));
		assertEquals(16_000L, ReceptionCapsuleRules.euMissing(-5, 16_000));
		assertEquals(3, ReceptionCapsuleRules.missing(5, 8));
		assertEquals(0, ReceptionCapsuleRules.missing(64, 8));
		assertEquals(8, ReceptionCapsuleRules.missing(-1, 8));
		assertTrue(ReceptionCapsuleRules.hasMaterials(new int[] {1, 8, 4}, new int[] {1, 8, 4}));
		assertFalse(ReceptionCapsuleRules.hasMaterials(new int[] {1, 7, 4}, new int[] {1, 8, 4}));
	}

	/** A restoration moves one tick at a time while ready and stands still, keeping its progress, otherwise. */
	@Test
	void progressMovesOnlyWhileReadyAndIsKeptWhenHalted() {
		assertEquals(1, ReceptionCapsuleRules.advance(0, 200, Verdict.READY));
		assertEquals(200, ReceptionCapsuleRules.advance(199, 200, Verdict.READY));
		assertEquals(200, ReceptionCapsuleRules.advance(200, 200, Verdict.READY), "never past the end");
		for (Verdict halted : Verdict.values()) {
			if (halted != Verdict.READY) {
				assertEquals(120, ReceptionCapsuleRules.advance(120, 200, halted), halted + " must keep the progress");
			}
		}
		assertEquals(100, ReceptionCapsuleRules.advance(300, 100, Verdict.DARK), "a shorter restoration clamps");
	}

	/** An operator's material count never leaves one stack, also when it is doubled for a return (R39). */
	@Test
	void materialCountsStayWithinOneStack() {
		int saved = SettlementConfig.capsuleRubberPerWorker;
		try {
			SettlementConfig.capsuleRubberPerWorker = 40;
			assertEquals(40, Material.RUBBER.price(false));
			assertEquals(ReceptionCapsuleRules.STACK_LIMIT, Material.RUBBER.price(true));
			SettlementConfig.capsuleRubberPerWorker = 500;
			assertEquals(ReceptionCapsuleRules.STACK_LIMIT, Material.RUBBER.price(false));
			SettlementConfig.capsuleRubberPerWorker = -3;
			assertEquals(0, Material.RUBBER.price(false));
		} finally {
			SettlementConfig.capsuleRubberPerWorker = saved;
		}
	}

	@Test
	void aVerdictSurvivesItsChannel() {
		for (Verdict verdict : Verdict.values()) {
			assertEquals(verdict, Verdict.byOrdinal(verdict.ordinal()));
		}
		assertEquals(Verdict.NO_CORE, Verdict.byOrdinal(-1));
		assertEquals(Verdict.NO_CORE, Verdict.byOrdinal(Verdict.values().length));
		assertFalse(Verdict.READY.blocking());
		assertTrue(Verdict.ARCHIVE_SILENT.blocking());
	}
}
