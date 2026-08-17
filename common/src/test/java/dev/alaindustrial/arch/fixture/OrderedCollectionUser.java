package dev.alaindustrial.arch.fixture;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Clean counterpart of {@link UnorderedCollectionViolator} (MOD-435): every idiom the rule points
 * to as the fix, plus the one it deliberately allows — an EMPTY {@code Set.of()}. The negative
 * control asserts this class is NOT in the report, so the check cannot pass by flagging everything.
 */
public final class OrderedCollectionUser {
	private OrderedCollectionUser() {
	}

	static Set<Integer> linkedSet() {
		return new LinkedHashSet<>();
	}

	static Supplier<Map<Integer, Integer>> linkedMapReference() {
		return LinkedHashMap::new;
	}

	static Set<Integer> emptyFactory() {
		return Set.of();
	}

	static List<Integer> listFactory() {
		return List.of(1, 2);
	}

	static Set<Integer> orderedCollector() {
		return Stream.of(1, 2).collect(Collectors.toCollection(LinkedHashSet::new));
	}
}
