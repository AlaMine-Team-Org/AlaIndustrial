package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.shift.LookPick.Candidate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the look rule (MOD-832, ADR-050 point 6): deterministic from the number and the seed, blind to
 * the datapack's order, weighted, and it uses every look.
 *
 * @implements MOD-832-LOOK — a worker's helmet and face follow from its number, deterministically.
 */
class LookPickTest {

	/** The faces of MOD-828: the first sixteen built-in looks. */
	private static final String[] FACES_MOD_828 = {"m1", "m2", "m3", "m4", "f1", "f2", "f3", "f4"};
	/** The faces MOD-851 added. */
	private static final String[] FACES_MOD_851 = {"m5", "m6", "f5", "f6"};

	/** The twenty-four built-in looks: two helmets times twelve faces. */
	private static final List<Candidate> BUILT_IN = looks(FACES_MOD_828, FACES_MOD_851);
	/** The built-in looks before MOD-851: two helmets times eight faces. */
	private static final List<Candidate> BEFORE_MOD_851 = looks(FACES_MOD_828);

	private static List<Candidate> looks(String[]... faceSets) {
		List<Candidate> out = new ArrayList<>();
		for (String helmet : new String[] {"open", "window"}) {
			for (String[] faces : faceSets) {
				for (String face : faces) {
					out.add(new Candidate("alaindustrial:" + helmet + "_" + face, 1));
				}
			}
		}
		return List.copyOf(out);
	}

	@Property
	void sameNumberAndSeedGiveTheSameLook(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index) {
		assertEquals(LookPick.pick(BUILT_IN, seed, index), LookPick.pick(BUILT_IN, seed, index));
	}

	/** The datapack's listing order does not matter: the rule sorts by id. */
	@Property
	void listingOrderDoesNotMatter(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index,
			@ForAll long shuffle) {
		List<Candidate> shuffled = new ArrayList<>(BUILT_IN);
		Collections.shuffle(shuffled, new Random(shuffle));
		assertEquals(LookPick.pick(BUILT_IN, seed, index), LookPick.pick(shuffled, seed, index));
	}

	/**
	 * Over a thousand numbers every one of the twenty-four looks is given, none of them to more than twice its share,
	 * and women and men come about equally often (R3g).
	 */
	@Test
	void everyLookIsUsedAndNoneDominates() {
		Map<String, Integer> counts = new HashMap<>();
		int women = 0;
		for (int index = 0; index < 1_000; index++) {
			String look = LookPick.pick(BUILT_IN, 4242L, index).orElseThrow();
			counts.merge(look, 1, Integer::sum);
			women += look.contains("_f") ? 1 : 0;
		}
		assertEquals(24, counts.size(), "looks used: " + counts.keySet());
		assertTrue(women > 400 && women < 600, women + " women of 1000 workers");
		int share = 1_000 / 24;
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			assertTrue(entry.getValue() < 2 * share, entry + " is more than twice its share " + share);
		}
	}

	/** Two worlds give the same number different looks — not always, but not in step either. */
	@Test
	void theSeedChangesTheLooks() {
		int same = 0;
		for (int index = 0; index < 200; index++) {
			if (LookPick.pick(BUILT_IN, 1L, index).equals(LookPick.pick(BUILT_IN, 2L, index))) {
				same++;
			}
		}
		assertTrue(same < 50, "two seeds agreed on " + same + " of 200 looks");
	}

	@Test
	void weightZeroIsNeverChosenAndHeavierIsChosenMoreOften() {
		List<Candidate> looks = List.of(new Candidate("a", 0), new Candidate("b", 1), new Candidate("c", 3));
		Map<String, Integer> counts = new HashMap<>();
		for (int index = 0; index < 4_000; index++) {
			counts.merge(LookPick.pick(looks, 7L, index).orElseThrow(), 1, Integer::sum);
		}
		assertEquals(null, counts.get("a"));
		assertTrue(counts.get("c") > 2 * counts.get("b"), "weights 1:3 gave " + counts);
	}

	@Test
	void noUsableCandidateGivesNothing() {
		assertEquals(Optional.empty(), LookPick.pick(List.of(), 1L, 5));
		assertEquals(Optional.empty(), LookPick.pick(List.of(new Candidate("x", 0), new Candidate("y", -2)), 1L, 5));
	}

	/** The golden picks of {@link #goldenPicks} over the twenty-four looks (seed 2026, numbers 0 and 117). */
	private static final String GOLDEN_24_AT_0 = "alaindustrial:window_f3";
	private static final String GOLDEN_24_AT_117 = "alaindustrial:open_m2";

	/**
	 * Golden values, pinned from the first run of the rule over the sixteen looks of MOD-828: a change of the rule
	 * would give living workers new faces the next time their look falls back to it, so it must be a deliberate
	 * change of this test too. The second pair is the same numbers over the twenty-four looks of MOD-851.
	 */
	@Test
	void goldenPicks() {
		assertEquals(Optional.of("alaindustrial:open_m3"), LookPick.pick(BEFORE_MOD_851, 2026L, 0));
		assertEquals(Optional.of("alaindustrial:window_m4"), LookPick.pick(BEFORE_MOD_851, 2026L, 117));
		assertEquals(Optional.of(GOLDEN_24_AT_0), LookPick.pick(BUILT_IN, 2026L, 0));
		assertEquals(Optional.of(GOLDEN_24_AT_117), LookPick.pick(BUILT_IN, 2026L, 117));
	}

	/**
	 * New looks change the rule's answer for most numbers — which is why the chosen id is written into the worker's
	 * record and never recomputed while that look is in the datapack (MOD-851): a worker given a face before the
	 * four new faces arrived keeps it. The rule alone could not promise that; the record does
	 * ({@code shift_worker_keeps_its_look}).
	 */
	@Test
	void newLooksMoveMostNumbersSoTheRecordMustKeepTheChoice() {
		int moved = 0;
		for (int index = 0; index < 1_000; index++) {
			if (!LookPick.pick(BEFORE_MOD_851, 77L, index).equals(LookPick.pick(BUILT_IN, 77L, index))) {
				moved++;
			}
		}
		assertTrue(moved > 500, "only " + moved + " of 1000 numbers moved");
	}
}
