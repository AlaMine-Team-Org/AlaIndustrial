package dev.alaindustrial.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 for the warehouse shelf's ranking (MOD-885, R157): the four kinds it holds the most of, summed over slots,
 * biggest first, ties in order of first appearance, fewer kinds give fewer cards.
 */
class MostHeldKindsTest {

	/** A slot as {@code "kind:count"}; {@code ""} is an empty slot. */
	private static List<String> top(int limit, String... slots) {
		return MostHeldKinds.top(Arrays.asList(slots), String::isEmpty,
				s -> Integer.parseInt(s.substring(s.indexOf(':') + 1)),
				(a, b) -> a.substring(0, a.indexOf(':')).equals(b.substring(0, b.indexOf(':'))), limit);
	}

	private static List<String> kinds(List<String> slots) {
		return slots.stream().map(s -> s.substring(0, s.indexOf(':'))).toList();
	}

	@Test
	void theFourBiggestKindsBiggestFirst() {
		assertEquals(List.of("coal", "iron", "sand", "dirt"),
				kinds(top(4, "dirt:5", "iron:20", "coal:64", "sand:9", "wool:1")));
	}

	@Test
	void countsAreSummedOverSlots() {
		// Wool spread over three slots outweighs one bigger stack of iron.
		assertEquals(List.of("wool", "iron"), kinds(top(4, "iron:40", "wool:16", "", "wool:16", "wool:16")));
	}

	@Test
	void equalTotalsKeepFirstAppearanceOrder() {
		assertEquals(List.of("b", "a", "c"), kinds(top(4, "b:8", "a:8", "c:8")));
		assertEquals(List.of("a", "b", "c"), kinds(top(4, "a:8", "b:8", "c:8")));
	}

	@Test
	void theRepresentativeIsTheKindsFirstSlot() {
		assertEquals(List.of("iron:3", "coal:2"), top(4, "iron:3", "coal:2", "iron:1"));
	}

	@Test
	void fewerKindsGiveFewerCardsAndAnEmptyContainerNone() {
		assertEquals(2, top(4, "a:1", "", "b:2").size());
		assertTrue(top(4, "", "", "").isEmpty());
		assertTrue(top(4).isEmpty());
	}

	@Test
	void theLimitCapsTheList() {
		assertEquals(List.of("e", "d"), kinds(top(2, "a:1", "b:2", "c:3", "d:4", "e:5")));
	}

	@Test
	void nullSlotsAreSkipped() {
		assertEquals(List.of("a"), kinds(top(4, null, "a:1")));
	}
}
